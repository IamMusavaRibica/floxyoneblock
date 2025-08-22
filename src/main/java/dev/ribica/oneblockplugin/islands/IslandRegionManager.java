package dev.ribica.oneblockplugin.islands;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.domains.DefaultDomain;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.RegionGroup;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.managers.RegionManager;
import com.sk89q.worldguard.protection.regions.ProtectedCuboidRegion;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.util.StringUtils;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;

/**
 * Manages WorldGuard regions for islands
 * Now works per-world since each island has its own world
 * Uses a simple permission model: owners/members have full permissions, guests have none
 */
public class IslandRegionManager {
    private final OneBlockPlugin plugin;
    private final World legacyWorld; // Keep for legacy operations only
    private final RegionContainer container;

    public IslandRegionManager(OneBlockPlugin plugin, World legacyWorld) {
        this.plugin = plugin;
        this.legacyWorld = legacyWorld;
        this.container = WorldGuard.getInstance().getPlatform().getRegionContainer();
    }

    /**
     * Get region manager for a specific world
     */
    private RegionManager getRegionManager(World world) {
        RegionManager regionManager = container.get(BukkitAdapter.adapt(world));
        if (regionManager == null) {
            throw new IllegalStateException("Couldn't get WorldGuard region manager for world: " + world.getName());
        }
        return regionManager;
    }

    /**
     * Clears all existing island regions on server startup (legacy main world only)
     * This prevents orphaned regions from persisting after server crashes or similar situations
     */
    public void clearAllIslandRegions() {
        try {
            RegionManager regions = getRegionManager(legacyWorld);
            // Find and remove all island regions (ids starting with "island_")
            List<String> regionsToRemove = new ArrayList<>();
            regions.getRegions().keySet().forEach(region -> {
                if (region.startsWith("island_"))
                    regionsToRemove.add(region);
            });
            regionsToRemove.forEach(regions::removeRegion);
            if (!regionsToRemove.isEmpty())
                plugin.getLogger().warning("Cleared " + regionsToRemove.size() + " leftover island regions from legacy world");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error clearing island regions on startup", e);
        }
    }

    /**
     * Register a WorldGuard region for the specified island in its own world
     * @param island The island to create a region for
     */
    public void registerRegion(Island island) {
        World islandWorld = plugin.getIslandAllocator2().getIslandWorld(island);
        if (islandWorld == null) {
            plugin.getLogger().warning("Cannot register region for island " + island.getUuid() + " - world not found");
            return;
        }

        try {
            RegionManager regions = getRegionManager(islandWorld);
            String regionId = "island_" + StringUtils.UUIDtoString(island.getUuid());
            String spawnRegionId = regionId + "_spawn";
            String sourceRegionId = regionId + "_source";

            // Remove existing regions if they exist
            if (regions.hasRegion(regionId)) {
                plugin.getLogger().warning("WorldGuard region " + regionId + " already exists, updating it");
                regions.removeRegion(regionId);
            }
            if (regions.hasRegion(spawnRegionId)) {
                plugin.getLogger().warning("WorldGuard region " + spawnRegionId + " already exists, updating it");
                regions.removeRegion(spawnRegionId);
            }
            if (regions.hasRegion(sourceRegionId)) {
                plugin.getLogger().warning("WorldGuard region " + sourceRegionId + " already exists, updating it");
                regions.removeRegion(sourceRegionId);
            }

            // Create main island region
            com.sk89q.worldedit.regions.CuboidRegion boundary = island.getBoundary();
            BlockVector3 min = boundary.getMinimumPoint();
            BlockVector3 max = boundary.getMaximumPoint();
            ProtectedCuboidRegion region = new ProtectedCuboidRegion(regionId, true, min, max);
            configureRegion(region, island);
            regions.addRegion(region);

            // Add the spawn-protection region (3 blocks above the one block)
            Location origin = island.getOrigin();
            BlockVector3 spawnMin = BlockVector3.at(origin.getBlockX(), origin.getBlockY() + 1, origin.getBlockZ());
            BlockVector3 spawnMax = BlockVector3.at(origin.getBlockX(), origin.getBlockY() + 3, origin.getBlockZ());
            ProtectedCuboidRegion spawnRegion = new ProtectedCuboidRegion(spawnRegionId, true, spawnMin, spawnMax);
            // Set priority higher than the main region
            spawnRegion.setPriority(region.getPriority() + 10);
            // Deny all building for everyone (including owner)
            spawnRegion.setFlag(Flags.BUILD, StateFlag.State.DENY);
            spawnRegion.setFlag(Flags.BUILD.getRegionGroupFlag(), RegionGroup.ALL);
            regions.addRegion(spawnRegion);

            // Add the source block protection region (just the one block source)
            BlockVector3 sourceBlock = BlockVector3.at(origin.getBlockX(), origin.getBlockY(), origin.getBlockZ());
            ProtectedCuboidRegion sourceRegion = new ProtectedCuboidRegion(sourceRegionId, true, sourceBlock, sourceBlock);

            // set higher priority
            sourceRegion.setPriority(region.getPriority() + 20);

            // prevent gravity blocks from falling, but allow breaking
            sourceRegion.setFlag(Flags.BUILD, StateFlag.State.DENY);
            sourceRegion.setFlag(Flags.BUILD.getRegionGroupFlag(), RegionGroup.ALL);
            sourceRegion.setFlag(Flags.BLOCK_BREAK, StateFlag.State.ALLOW);
            sourceRegion.setFlag(Flags.BLOCK_BREAK.getRegionGroupFlag(), RegionGroup.MEMBERS);
            sourceRegion.setFlag(Flags.CORAL_FADE, StateFlag.State.DENY);

            regions.addRegion(sourceRegion);

            regions.save();
            plugin.getLogger().info("Registered WorldGuard regions for island " + island.getUuid() + " in world " + islandWorld.getName());
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to register region for island " + island.getUuid(), e);
        }
    }

    /**
     * Update the permissions for all members of an island in its WorldGuard region
     * @param island The island to update permissions for
     */
    public void updateMemberPermissions(Island island) {
        World islandWorld = plugin.getIslandAllocator2().getIslandWorld(island);
        if (islandWorld == null) {
            plugin.getLogger().warning("Cannot update member permissions for island " + island.getUuid() + " - world not found");
            return;
        }

        try {
            RegionManager regions = getRegionManager(islandWorld);
            String regionId = "island_" + StringUtils.UUIDtoString(island.getUuid());
            String spawnRegionId = regionId + "_spawn";

            ProtectedRegion region = regions.getRegion(regionId);
            ProtectedRegion spawnRegion = regions.getRegion(spawnRegionId);

            if (region == null) {
                plugin.getLogger().warning("Failed to update member permissions: Region " + regionId + " not found in world " + islandWorld.getName());
                return;
            }

            configureRegion(region, island);
            // No need to update spawnRegion flags, as it always denies build for all
            regions.save();

            plugin.getLogger().info("Updated permissions for island " + island.getUuid() + " in world " + islandWorld.getName() +
                " - Members: " + (region.getMembers().size() + region.getOwners().size()));

        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to update member permissions for island " + island.getUuid(), e);
        }
    }

    /**
     * Configure a region with the appropriate permissions for an island
     * @param region The WorldGuard region to configure
     * @param island The island to configure permissions for
     */
    private void configureRegion(ProtectedRegion region, Island island) {
        // Set up owners domain (island owner)
        DefaultDomain owners = new DefaultDomain();
        owners.addPlayer(island.getOwner().getId().getUuid());
        region.setOwners(owners);

        // Set up members domain (all island members)
        DefaultDomain members = new DefaultDomain();
        island.getCurrentMembers().forEach(im -> members.addPlayer(im.getId().getUuid()));
        region.setMembers(members);

        // Set default flags for non-members (guests)

        // Allow entry/exit for everyone
        region.setFlag(Flags.ENTRY, StateFlag.State.ALLOW);
        region.setFlag(Flags.EXIT, StateFlag.State.ALLOW);

        // Block item pick-up for non-members
        region.setFlag(Flags.ITEM_PICKUP, StateFlag.State.DENY);
        region.setFlag(Flags.ITEM_PICKUP.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);

        // Block item dropping for non-members
        region.setFlag(Flags.ITEM_DROP, StateFlag.State.DENY);
        region.setFlag(Flags.ITEM_DROP.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);

        region.setFlag(Flags.FALL_DAMAGE, StateFlag.State.DENY);
        region.setFlag(Flags.FALL_DAMAGE.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);

        region.setFlag(Flags.CREEPER_EXPLOSION, StateFlag.State.DENY);
        region.setFlag(Flags.OTHER_EXPLOSION, StateFlag.State.DENY);
        region.setFlag(Flags.ENDER_BUILD, StateFlag.State.DENY);
        region.setFlag(Flags.PVP, StateFlag.State.DENY);
        region.setFlag(Flags.TNT, StateFlag.State.DENY);
        region.setFlag(Flags.PISTONS, StateFlag.State.DENY);  // Important!
    }

    /**
     * Unregister a WorldGuard region for the specified island from its own world
     * @param island The island to remove the region for
     */
    public void unregisterRegion(Island island) {
        World islandWorld = plugin.getIslandAllocator2().getIslandWorld(island);
        if (islandWorld == null) {
            plugin.getLogger().warning("Cannot unregister region for island " + island.getUuid() + " - world not found");
            return;
        }

        try {
            RegionManager regions = getRegionManager(islandWorld);
            String regionId = "island_" + StringUtils.UUIDtoString(island.getUuid());
            String spawnRegionId = regionId + "_spawn";
            String sourceRegionId = regionId + "_source";

            plugin.getLogger().info("Unregistering regions for island " + island.getUuid() + " from world " + islandWorld.getName());

            if (regions.hasRegion(regionId))
                regions.removeRegion(regionId);
            else
                plugin.getLogger().warning("Tried to unregister non-existing region " + regionId + " for island " + island.getUuid());

            if (regions.hasRegion(spawnRegionId))
                regions.removeRegion(spawnRegionId);

            if (regions.hasRegion(sourceRegionId))
                regions.removeRegion(sourceRegionId);

            regions.save();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to unregister region for island " + island.getUuid(), e);
        }
    }
}
