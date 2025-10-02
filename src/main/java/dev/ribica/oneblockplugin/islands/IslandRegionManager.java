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

import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Manages WorldGuard regions for islands
 * Now works per-world since each island has its own world
 * Uses a simple permission model: owners/members have full permissions, guests have none
 */
public class IslandRegionManager {
    private final Logger logger = Logger.getLogger("IslandRegionManager");
    private final OneBlockPlugin plugin;
    private final RegionContainer container;

    public IslandRegionManager(OneBlockPlugin plugin) {
        this.plugin = plugin;
        this.container = WorldGuard.getInstance().getPlatform().getRegionContainer();
    }

    private RegionManager getRegionManager(World world) {
        var regionManager = container.get(BukkitAdapter.adapt(world));
        if (regionManager == null) {
            throw new IllegalStateException("Couldn't get WorldGuard region manager for world: " + world.getName());
        }
        return regionManager;
    }


    /**
     * Register a WorldGuard region for the specified island in its own world
     * @param island The island to create a region for
     */
    public void registerRegion(Island island) {
        World islandWorld = plugin.getIslandAllocator2().getIslandWorld(island);
        if (islandWorld == null) {
            logger.warning("Cannot register region for island " + island.getUuid() + " - world not found");
            return;
        }

        try {
            RegionManager regions = getRegionManager(islandWorld);
            String regionId = "island_" + StringUtils.UUIDtoString(island.getUuid());
            String spawnRegionId = regionId + "_spawn";
            String sourceRegionId = regionId + "_source";

            // Remove existing regions but they shouldnt they exist
            regions.removeRegion(regionId);
            regions.removeRegion(spawnRegionId);
            regions.removeRegion(sourceRegionId);

            // Create main island region
            var boundary = island.getBoundary();
            var region = new ProtectedCuboidRegion(regionId, true, boundary.getMinimumPoint(), boundary.getMaximumPoint());
            configureRegion(region, island);
            configureRegionMembers(region, island);

            // Add the spawn-protection region (3 blocks above the one block)
            Location origin = island.getSourceBlockLocation();
            var originBV = BlockVector3.at(origin.x(), origin.y(), origin.z());
            var spawnMin = originBV.add(0, 1, 0);
            var spawnMax = originBV.add(0, 3, 0);
            var spawnRegion = new ProtectedCuboidRegion(spawnRegionId, true, spawnMin, spawnMax);
            spawnRegion.setPriority(region.getPriority() + 10);  // Set priority higher than the main region
            spawnRegion.setFlag(Flags.BUILD, StateFlag.State.DENY);
            spawnRegion.setFlag(Flags.BUILD.getRegionGroupFlag(), RegionGroup.ALL);

            // Add the source block protection region (just the one block source)
            var sourceRegion = new ProtectedCuboidRegion(sourceRegionId, true, originBV, originBV);
            configureRegionMembers(sourceRegion, island);
            sourceRegion.setPriority(region.getPriority() + 20);
            sourceRegion.setFlag(Flags.BUILD, StateFlag.State.DENY);
            sourceRegion.setFlag(Flags.BUILD.getRegionGroupFlag(), RegionGroup.ALL);
            sourceRegion.setFlag(Flags.BLOCK_BREAK, StateFlag.State.ALLOW);
            sourceRegion.setFlag(Flags.BLOCK_BREAK.getRegionGroupFlag(), RegionGroup.MEMBERS);
            sourceRegion.setFlag(Flags.CORAL_FADE, StateFlag.State.DENY);

            regions.addRegion(region);
            regions.addRegion(spawnRegion);
            regions.addRegion(sourceRegion);
            regions.save();
            logger.info("Registered WorldGuard regions for island " + island.getUuid() + " in world " + islandWorld.getName());
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to register region for island " + island.getUuid(), e);
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

    public void configureRegionMembers(ProtectedRegion region, Island island) {
        // Set up owners domain (island owner)
        DefaultDomain owners = new DefaultDomain();
        owners.addPlayer(island.getOwner().getId().getUuid());
        region.setOwners(owners);

        // Set up members domain (all island members)
        DefaultDomain members = new DefaultDomain();
        island.members.getCurrentMembers().forEach(im -> members.addPlayer(im.getId().getUuid()));
        region.setMembers(members);
    }

    /**
     * Configure a region with the appropriate permissions for an island
     * @param region The WorldGuard region to configure
     * @param island The island to configure permissions for
     */
    private void configureRegion(ProtectedRegion region, Island island) {
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
            regions.removeRegion(regionId);
            regions.removeRegion(regionId + "_spawn");
            regions.removeRegion(regionId + "_source");
            regions.save();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to unregister region for island " + island.getUuid(), e);
        }
    }
}
