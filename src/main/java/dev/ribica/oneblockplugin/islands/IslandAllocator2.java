package dev.ribica.oneblockplugin.islands;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.util.StringUtils;
import dev.ribica.oneblockplugin.util.TimeUtils;
import dev.ribica.oneblockplugin.util.WorldUtils;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.*;

import javax.annotation.Nullable;
import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

@RequiredArgsConstructor
public class IslandAllocator2 {
    private final OneBlockPlugin plugin;
    private final @Getter Map<UUID, World> loadedIslandWorlds = new ConcurrentHashMap<>();
    private final Map<World, Island> worldToIslandMapping = new ConcurrentHashMap<>();
    private final Map<Island, World> islandToWorldMapping = new ConcurrentHashMap<>();

    // Constants similar to the original allocator
    public final int ORIGIN_Y = 64;

    /**
     * Get the slot/ID for an island (for compatibility with old allocator interface)
     * Since each island has its own world, we use a simple numeric ID based on world name
     */
    public int getSlot(@NonNull Island island) {
        World world = islandToWorldMapping.get(island);
        if (world == null) {
            return -1;
        }
        // Extract numeric part from world name for compatibility
        String worldName = world.getName();
        if (worldName.startsWith("island_")) {
            try {
                return Math.abs(worldName.hashCode()) % 1000000; // Simple numeric ID
            } catch (Exception e) {
                return -1;
            }
        }
        return -1;
    }

    public Island getIslandByUUID(@NonNull UUID uuid) {
        for (Island island : islandToWorldMapping.keySet()) {
            if (island.getUuid().equals(uuid)) {
                return island;
            }
        }
        return null;
    }

    /**
     * Allocate a new world for the island and return the spawn location
     */
    public synchronized Location allocate(@NonNull Island island) {
        if (islandToWorldMapping.containsKey(island)) {
            plugin.getLogger().severe("called allocate() on an island that already has a world: " + island.getUuid());
            return getIslandOrigin(island);
        }

        // Create world name using island UUID
        String worldName = "island_" + StringUtils.UUIDtoString(island.getUuid());

        // Check if world already exists (shouldn't happen but safety check)
        World existingWorld = Bukkit.getWorld(worldName);
        if (existingWorld != null) {
            plugin.getLogger().warning("World " + worldName + " already exists, using existing world");
            registerIslandWorld(island, existingWorld);
            return new Location(existingWorld, 0, ORIGIN_Y, 0);
        }

        // Create and configure world (must happen on main thread)
        World world = runOnMainThread(() -> {
            World newWorld = createIslandWorld(worldName);
            if (newWorld != null) {
                configureIslandWorld(newWorld);
            }
            return newWorld;
        });

        if (world == null) {
            plugin.getLogger().severe("Failed to create world for island " + island.getUuid());
            return null;
        }

        registerIslandWorld(island, world);

        plugin.getComponentLogger().info(Component.text("Allocated island " + island.getUuid() +
                " in world " + worldName, TextColor.color(0x77ff77)));

        return new Location(world, 0, ORIGIN_Y, 0);
    }

    /**
     * Helper method to run code on the main thread, whether we're already on it or not
     */
    private <T> T runOnMainThread(java.util.function.Supplier<T> task) {
        if (Bukkit.isPrimaryThread()) {
            return task.get();
        } else {
            try {
                return plugin.getServer().getScheduler().callSyncMethod(plugin, task::get).get();
            } catch (Exception e) {
                plugin.getLogger().severe("Failed to execute task on main thread: " + e.getMessage());
                return null;
            }
        }
    }

    /**
     * Create a new island world - must be called from main thread
     */
    private World createIslandWorld(String worldName) {
        return WorldUtils.newVoidWorld(worldName);
    }

    /**
     * Configure world settings for an island world
     */
    private void configureIslandWorld(World world) {
        world.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        world.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        world.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
        world.setGameRule(GameRule.DO_TRADER_SPAWNING, false);
        world.setGameRule(GameRule.ANNOUNCE_ADVANCEMENTS, false);
        world.setGameRule(GameRule.KEEP_INVENTORY, true);
        world.setGameRule(GameRule.TNT_EXPLODES, false);
        world.setGameRule(GameRule.MOB_GRIEFING, false);
        world.setGameRule(GameRule.RANDOM_TICK_SPEED, 0);
        world.setGameRule(GameRule.DO_FIRE_TICK, false);
        world.setTime(6000); // Set to noon
        world.setStorm(false);
        world.setThundering(false);
    }

    /**
     * Register the bidirectional mapping between island and world
     */
    private void registerIslandWorld(Island island, World world) {
        loadedIslandWorlds.put(island.getUuid(), world);
        worldToIslandMapping.put(world, island);
        islandToWorldMapping.put(island, world);
    }

    /**
     * Free/unload the world for an island
     */
    public synchronized void free(Island island) {
        World world = islandToWorldMapping.get(island);
        if (world == null) {
            plugin.getLogger().severe("called free() on an island that doesn't have a world: " + island.getUuid());
            return;
        }

        long start = TimeUtils.ms();
        String worldName = world.getName();

        plugin.getComponentLogger().info(Component.text(
                "Unloading world " + worldName + " for island " + island.getUuid(),
                NamedTextColor.LIGHT_PURPLE
        ));

        // Remove from mappings first
        loadedIslandWorlds.remove(island.getUuid());
        worldToIslandMapping.remove(world);
        islandToWorldMapping.remove(island);

        // Teleport any remaining players out of the world and save + unload world - must happen on main thread
        runOnMainThread(() -> {
            var worldPlayers = world.getPlayers();
            if (!worldPlayers.isEmpty()) {
                plugin.getLogger().warning("World " + worldName + " still has players and cannot be unloaded!");
                return null;
            }

            boolean success = Bukkit.unloadWorld(world, true);
            if (success) {
                plugin.getLogger().info("Successfully saved and unloaded world " + worldName + " in " + TimeUtils.msSince(start) + " ms");
            } else {
                plugin.getLogger().warning("Failed to unload world " + worldName);
            }
            return null;
        });
    }

    /**
     * Get the origin location for an island (similar to old allocator interface)
     */
    public Location getIslandOrigin(Island island) {
        World world = islandToWorldMapping.get(island);
        if (world == null) {
            plugin.getLogger().severe("called getIslandOrigin() on an island that doesn't have a world: " + island.getUuid());
            return null;
        }
        return new Location(world, 0, ORIGIN_Y, 0);
    }

    public @Nullable World getIslandWorld(@NonNull Island island) {
        return islandToWorldMapping.get(island);
    }

    public @Nullable Island getIslandForWorld(@NonNull World world) {
        return worldToIslandMapping.get(world);
    }

    public void checkAndUnloadEmptyWorlds() {
        List<Island> islandsToUnload = new ArrayList<>();

        for (var entry : islandToWorldMapping.entrySet()) {
            Island island = entry.getKey();
            World world = entry.getValue();

            if (!world.getPlayers().isEmpty()) {
                continue;
            }
            islandsToUnload.add(island);
        }

        islandsToUnload.forEach(Island::unloadIfSafe);
    }

    /**
     * Get forward mapping for compatibility (returns world names as slot numbers)
     */
    public Map<Integer, Island> getForwardMapping() {
        Map<Integer, Island> result = new HashMap<>();
        for (Map.Entry<Island, World> entry : islandToWorldMapping.entrySet()) {
            int slot = getSlot(entry.getKey());
            if (slot != -1) {
                result.put(slot, entry.getKey());
            }
        }
        return result;
    }

    /**
     * Delete world files from disk (use with caution)
     */
    private void deleteWorldFiles(String worldName) {
        File worldFolder = new File(Bukkit.getWorldContainer(), worldName);
        if (worldFolder.exists() && worldFolder.isDirectory()) {
            try {
                deleteDirectory(worldFolder);
                plugin.getLogger().info("Deleted world files for " + worldName);
            } catch (Exception e) {
                plugin.getLogger().warning("Failed to delete world files for " + worldName + ": " + e.getMessage());
            }
        }
    }

    /**
     * Recursively delete a directory
     */
    private void deleteDirectory(File directory) {
        File[] files = directory.listFiles();
        if (files != null) {
            for (File file : files) {
                if (file.isDirectory()) {
                    deleteDirectory(file);
                } else {
                    file.delete();
                }
            }
        }
        directory.delete();
    }

    /**
     * Unload all island worlds (called during server shutdown)
     */
    public void unloadAllWorlds() {
        plugin.getLogger().info("Unloading all island worlds...");
        List<Island> allIslands = new ArrayList<>(islandToWorldMapping.keySet());
        for (Island island : allIslands) {
            free(island);
        }
    }
}
