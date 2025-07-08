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
import lombok.RequiredArgsConstructor;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Manages WorldGuard regions for islands
 * Uses a simple permission model: owners/members have full permissions, guests have none
 */
public class IslandRegionManager {
    private final OneBlockPlugin plugin;
    private final World islandsWorld;
    private final RegionContainer container;
    private final RegionManager regions;

    public IslandRegionManager(OneBlockPlugin plugin, World world) {
        this.plugin = plugin;
        this.islandsWorld = world;
        this.container = WorldGuard.getInstance().getPlatform().getRegionContainer();
        this.regions = container.get(BukkitAdapter.adapt(islandsWorld));
        if (this.regions == null) {
            throw new IllegalStateException("Couldn't get WorldGuard region manager");
        }
    }

    /**
     * Clears all existing island regions on server startup
     * This prevents orphaned regions from persisting after server crashes or similar situations
     */
    public void clearAllIslandRegions() {
        try {
            // Find and remove all island regions (ids starting with "island_")
            List<String> regionsToRemove = new ArrayList<>();
            regions.getRegions().keySet().forEach(region -> {
                if (region.startsWith("island_"))
                    regionsToRemove.add(region);
            });
            regionsToRemove.forEach(regions::removeRegion);
            if (!regionsToRemove.isEmpty())
                plugin.getLogger().warning("Cleared " + regionsToRemove.size() + " leftover island regions");
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Error clearing island regions on startup", e);
        }
    }

    /**
     * Register a WorldGuard region for the specified island
     * @param island The island to create a region for
     */
    public void registerRegion(Island island) {
        try {
            String regionId = "island_" + StringUtils.UUIDtoString(island.getUuid());
            String spawnRegionId = regionId + "_spawn";
            String sourceRegionId = regionId + "_source";

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
            // Set priority higher than both main and spawn regions
            sourceRegion.setPriority(region.getPriority() + 20);

            // Explicitly allow building/breaking for owners and members
            sourceRegion.setFlag(Flags.BUILD, StateFlag.State.ALLOW);
            sourceRegion.setFlag(Flags.BUILD.getRegionGroupFlag(), RegionGroup.MEMBERS);

            // Only block pistons for everyone (including owners)
            sourceRegion.setFlag(Flags.PISTONS, StateFlag.State.DENY);
            sourceRegion.setFlag(Flags.PISTONS.getRegionGroupFlag(), RegionGroup.ALL);

            regions.addRegion(sourceRegion);

            regions.save();
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to register region for island " + island.getUuid(), e);
        }
    }

    /**
     * Update the permissions for all members of an island in its WorldGuard region
     * @param island The island to update permissions for
     */
    public void updateMemberPermissions(Island island) {
        try {
            Location origin = island.getOrigin();
            if (origin == null || origin.getWorld() == null) {
                plugin.getLogger().warning("Failed to update member permissions: Island origin or world is null");
                return;
            }

            String regionId = "island_" + island.getUuid().toString().replace("-", "");
            String spawnRegionId = regionId + "_spawn";
            ProtectedRegion region = regions.getRegion(regionId);
            ProtectedRegion spawnRegion = regions.getRegion(spawnRegionId);
            if (region == null) {
                plugin.getLogger().warning("Failed to update member permissions: Region " + regionId + " not found");
                return;
            }
            configureRegion(region, island);
            // No need to update spawnRegion flags, as it always denies build for all
            regions.save();
            plugin.getLogger().info("Updated permissions for island " + island.getUuid() + " - Members: " +
                (region.getMembers().size() + region.getOwners().size()));

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
        island.getMembers().forEach(im -> members.addPlayer(im.getId().getUuid()));
        region.setMembers(members);

        // Set default flags for non-members (guests)

        // Allow entry/exit for everyone
        region.setFlag(Flags.ENTRY, StateFlag.State.ALLOW);
        region.setFlag(Flags.EXIT, StateFlag.State.ALLOW);

        // Block building/interacting for non-members
//        region.setFlag(Flags.BUILD, StateFlag.State.DENY);
//        region.setFlag(Flags.BUILD.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);

//        region.setFlag(Flags.INTERACT, StateFlag.State.DENY);
//        region.setFlag(Flags.INTERACT.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);

        // Block chest access for non-members
//        region.setFlag(Flags.CHEST_ACCESS, StateFlag.State.DENY);
//        region.setFlag(Flags.CHEST_ACCESS.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);

        // Block item pick-up for non-members
        region.setFlag(Flags.ITEM_PICKUP, StateFlag.State.DENY);
        region.setFlag(Flags.ITEM_PICKUP.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);

        // Block item dropping for non-members
        region.setFlag(Flags.ITEM_DROP, StateFlag.State.DENY);
        region.setFlag(Flags.ITEM_DROP.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);

        region.setFlag(Flags.FALL_DAMAGE, StateFlag.State.DENY);
        region.setFlag(Flags.FALL_DAMAGE.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);

//        region.setFlag(Flags.MOB_DAMAGE, StateFlag.State.DENY);
//        region.setFlag(Flags.MOB_DAMAGE.getRegionGroupFlag(), RegionGroup.NON_MEMBERS);

        region.setFlag(Flags.CREEPER_EXPLOSION, StateFlag.State.DENY);
        region.setFlag(Flags.OTHER_EXPLOSION, StateFlag.State.DENY);
        region.setFlag(Flags.ENDER_BUILD, StateFlag.State.DENY);
        region.setFlag(Flags.PVP, StateFlag.State.DENY);
        region.setFlag(Flags.TNT, StateFlag.State.DENY);
//        region.setFlag(Flags.PISTONS, StateFlag.State.DENY);
    }

    /**
     * Unregister a WorldGuard region for the specified island
     * @param island The island to remove the region for
     */
    public void unregisterRegion(Island island) {
        String regionId = "island_" + StringUtils.UUIDtoString(island.getUuid());
        String spawnRegionId = regionId + "_spawn";
        String sourceRegionId = regionId + "_source";
//        plugin.getLogger().info("Unregistering region " + regionId);
        if (regions.hasRegion(regionId))
            regions.removeRegion(regionId);
        else
            plugin.getLogger().warning("Tried to unregister non-existing region " + regionId + " for island " + island.getUuid());
        if (regions.hasRegion(spawnRegionId))
            regions.removeRegion(spawnRegionId);
        if (regions.hasRegion(sourceRegionId))
            regions.removeRegion(sourceRegionId);
    }
}
