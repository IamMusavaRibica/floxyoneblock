package dev.ribica.oneblockplugin.islands;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.oneblock.StageManager;
import dev.ribica.oneblockplugin.playerdata.Islands;
import dev.ribica.oneblockplugin.util.Compression;
import dev.ribica.oneblockplugin.util.UUIDNamePair;
import dev.ribica.oneblockplugin.util.WorldUtils;
import lombok.*;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Level;

public class Island {
    private final OneBlockPlugin plugin;
    private final @Getter UUID uuid;
    private final @Getter IslandMember owner;  // Member representing the owner's stats - not stored in members map
    private Component name;
    public final @Getter IslandMembers members;  // Public final field for direct access

    private @Getter @Setter int currentStageId = 0;
    private @Getter @Setter int blocksMinedSinceLastStage = 0;
    private @Getter @Setter int stageCycles = 0;


    private @Getter @Setter boolean unloaded = false;
    private @Getter volatile boolean obsolete = false;

    public Island(@NonNull UUID uuid, @NonNull IslandMember owner, Component name) {
        this(uuid, owner, name, new Date());
    }

    /**
     * Create a new island
     * @param uuid Island UUID
     * @param owner Island owner
     * @param name Custom island name (can be null)
     * @param creationDate Date when the island was created, will be used as owner's addedAt
     */
    public Island(@NonNull UUID uuid, @NonNull IslandMember owner, Component name, Date creationDate) {
        this.plugin = OneBlockPlugin.getInstance();
        this.uuid = uuid;
        this.owner = owner;
        this.name = name;
        this.members = new IslandMembers(this);
    }

    /**
     * Get the date when this island was created
     * @return The island creation date (same as owner's addedAt)
     */
    public Date getCreatedAt() {
        return owner.getAddedAt();
    }

    public Location getSpawnLocation() {
        return getOrigin().add(0.5, 1.00123, 0.5);
    }

    /**
     * Records a block mined by a player on this island
     * @param playerUuid UUID of the player who mined the block
     * @param playerName Name of the player who mined the block
     * @param material Material of the mined block
     */
    public void trackBlockMined(UUID playerUuid, String playerName, Material material) {
        blocksMinedSinceLastStage++;

        // Check if it's the owner
        IslandMember member = playerUuid.equals(owner.getId().getUuid()) ? owner : getMember(playerUuid);
        if (member == null) {
            plugin.getLogger().warning("Unknown player " + playerName + " (" + playerUuid + ") mined a block on island " + uuid);
            return;
        }
        member.trackBlockMined(material);
    }

    /**
     * Get the current stage ID of this island based on progression
     * @return The current stage ID
     */
    public StageManager.Stage getStage() {
        return plugin.getStageManager().getStage(currentStageId);
    }

    /**
     * Attempts to advance to the next stage
     * @return true if advancement was successful, false if not enough blocks mined
     */
    public boolean advanceToNextStage() {
        // Check if player has mined at least 50 blocks in current stage
        if (blocksMinedSinceLastStage < 50) {
            return false;
        }

        var stagesMap = plugin.getStageManager().getAllStages();
        if (stagesMap.isEmpty()) {
            plugin.getLogger().warning("No stages available for advancement");
            return false;
        }

        // Find the highest stage ID (last stage)
        int maxStageId = stagesMap.keySet().stream().mapToInt(Integer::intValue).max().orElse(0);

        // If at the last stage, go back to stage 1 (not 0 since 0 is tutorial)
        if (currentStageId >= maxStageId) {
            currentStageId = 1;
            stageCycles++;
        } else {
            currentStageId++;
        }

        // Reset block counter for the new stage
        blocksMinedSinceLastStage = 0;
        return true;
    }

    /**
     * Get the number of blocks needed to advance to the next stage
     * @return Number of blocks still needed (0 if can advance)
     */
    public int getBlocksNeededForNextStage() {
        return Math.max(0, getNextStageBlocksRequirement() - blocksMinedSinceLastStage);
    }

    public int getNextStageBlocksRequirement() {
        return 50;
    }

    public void markObsolete() {
        if (this.obsolete)
            throw new IllegalStateException("Island#markObsolete(): called twice on the same Island instance");
        this.obsolete = true;
    }

    public boolean isSafeToUnload() {
        // safe to call this asynchronously
        if (unloaded) {
            return false; // already unloaded
        }
        Player ownerPlayer = Bukkit.getPlayer(owner.getId().getUuid());
        if (ownerPlayer != null && ownerPlayer.isOnline() && plugin.getUser(ownerPlayer).getActiveIsland() == this) {
            return false;
        }
        for (IslandMember member : members.getAllMembers()) {
            Player p = Bukkit.getPlayer(member.getUuid());
            if (p != null && p.isOnline() && plugin.getUser(p).getActiveIsland() == this) {
                return false;
            }
        }
        return true;
    }

    public void unloadIfSafe() {
        // This method is blocking for the saving part, but not for the unloading part

        // Even though Islands.java is probably able to handle this:
        // if (isSafeToUnload()) {
        //     Islands.unloadIsland(this);
        // }
        // we have an extra safety check here, just for good measures
        // assuming that island is discarded as soon after it is unloaded

        synchronized (this) {
            if (isSafeToUnload()) {
                unloaded = true;
                Islands.unloadIsland(this.getUuid());
            }
        }
    }

    public boolean hasCustomName() {
        return name != null;
    }

    public void updateMemberPermissions(UUID memberUuid, int newPermissions) {
        members.updatePermissions(memberUuid, newPermissions);
    }

    public @NotNull Component getName() {
        if (name != null) {
            return name;
        }
        return Component.text(owner.getId().getName() + "'s island", NamedTextColor.GRAY);
    }

    public void addMember(@NonNull UUIDNamePair memberId, int permission, Date addedAt) {
        members.addMember(memberId, permission, addedAt);
    }

    public Location getOrigin() {
        return plugin.getIslandAllocator2().getIslandOrigin(this);
    }

    public CuboidRegion getBoundary() {
        return this.getBoundary(false);
    }

    public CuboidRegion getBoundary(boolean restricted) {
        // TODO: implement parameter `restricted` to return the maximum allowed region (determined by island upgrades, etc.)
        // right now, this returns the maximum allowed region

        Location origin = getOrigin();
        BlockVector3 min = BlockVector3.at(origin.x() - 75,   1, origin.z() - 75);
        BlockVector3 max = BlockVector3.at(origin.x() + 75, 128, origin.z() + 75);
        return new CuboidRegion(BukkitAdapter.adapt(origin.getWorld()), min, max);
    }

    public @Nullable IslandMember getMember(@NonNull UUID memberUuid) {
        return members.getMember(memberUuid);
    }

    public void removeMember(@NonNull UUID memberUuid) {
        members.removeMember(memberUuid);
    }

    public void putMember(@NonNull IslandMember member) {
        members.putMember(member);
    }

    public boolean hasMember(@NonNull UUID memberUuid, boolean mustBeCurrentMember) {
        return members.hasMember(memberUuid, mustBeCurrentMember);
    }

    public boolean isCurrentMemberOrOwner(@NonNull UUID memberUuid) {
        return memberUuid.equals(owner.getUuid()) || hasMember(memberUuid, true);
    }

    public List<IslandMember> getCurrentMembers() {
        return members.getCurrentMembers();
    }

    public List<IslandMember> getAllMembers() {
        // i ovi koji imaju permissions == 0, bivši članovi čije podatke moramo čuvati
        return members.getAllMembers();
    }
}
