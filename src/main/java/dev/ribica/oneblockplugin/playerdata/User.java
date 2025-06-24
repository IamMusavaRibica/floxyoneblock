package dev.ribica.oneblockplugin.playerdata;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.islands.Island;
import lombok.*;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@RequiredArgsConstructor
public class User {
    private final OneBlockPlugin plugin;
    private final @Getter UUID uuid;
    private @Getter Player player;

    private @Getter @Setter(AccessLevel.PROTECTED) UUID ownedIslandUuid;
    private @Getter @Setter(AccessLevel.PROTECTED) Island activeIsland;
    private @Getter @Setter(AccessLevel.PROTECTED) Date joinedAt;
    private @Getter List<UUID> profiles = new ArrayList<>();

    // Track mined blocks from the one block across all islands
    private @Getter Map<Material, Integer> minedBlocks = new HashMap<>();

    // Public method to track a mined block for this user
    public void trackBlockMined(Material material) {
        minedBlocks.put(material, minedBlocks.getOrDefault(material, 0) + 1);
    }

    protected void setPlayer(@NonNull Player player) {
        if (this.player != null)
            throw new IllegalStateException("Player already set");
        if (player.getUniqueId() != uuid)
            throw new IllegalArgumentException("Player UUID does not match User UUID");
        this.player = player;
    }

    protected void ensureLoaded() {
        String invalid = "";
//        if (player == null)         invalid += "player, ";
        if (activeIsland == null)   invalid += "activeIsland, ";
        if (joinedAt == null)       invalid += "joinedAt, ";
        if (ownedIslandUuid == null) invalid += "ownedIslandUuid, ";
        if (minedBlocks == null)    invalid += "minedBlocks, ";  // TODO this is never null

        if (!invalid.isEmpty()) {
            throw new IllegalStateException("User missing properties: " + invalid.substring(0, invalid.length() - 2));
        }
    }

    public void updateStats() {
        if (player == null) {
            plugin.getLogger().severe("Cannot update stats for user " + uuid + " because player is null");
            return;
        }
        plugin.getLogger().info("TODO: Updating stats for " + player.getName());
    }

    public boolean isOnOwnedIsland() {
        // do not use == to compare UUIDs
        return activeIsland.getOwner().getId().getUuid().equals(uuid) && activeIsland == getCurrentIsland();
    }

    public CompletableFuture<Island> switchActiveIsland(@NonNull UUID islandUuid) {
        assert player != null;
        CompletableFuture<Island> future = new CompletableFuture<>();

        plugin.runTaskAsync(() -> {
            try {
                Island nextIsland = Islands.loadIsland(islandUuid).get();
                Island prevIsland = this.activeIsland;
                this.activeIsland = nextIsland;
                plugin.runTaskAsync(prevIsland::unloadIfSafe);
                plugin.runTask(() -> player.teleport(nextIsland.getSpawnLocation()));
                future.complete(nextIsland);
            } catch (Exception ex) {
                future.completeExceptionally(ex);
            }
        });
        return future;
    }

    /**
     * Gets the island the player is physically located on
     * This may be different from the player's active island if they are visiting another player's island
     * @return The island the player is currently on, or null if they are not on any island
     */
    public @Nullable Island getCurrentIsland() {
        if (player == null || !player.isOnline()) {
            return null;
        }
        org.bukkit.Location playerLoc = player.getLocation();

        // Check all allocated islands to see if player is within bounds of any of them
        for (Island island : plugin.getIslandAllocator().getForwardMapping().values()) {
            org.bukkit.Location origin = island.getOrigin();
            if (origin == null || !playerLoc.getWorld().equals(origin.getWorld())) {
                continue;
            }
            if (island.getBoundary().contains(BukkitAdapter.adapt(playerLoc).toBlockPoint())) {
                return island;
            }
        }
        return null; // Player is not on any island
    }
}
