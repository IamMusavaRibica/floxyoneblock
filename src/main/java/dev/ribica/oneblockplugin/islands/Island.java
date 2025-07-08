package dev.ribica.oneblockplugin.islands;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.CuboidRegion;
import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.oneblock.Stage;
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
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

public class Island {
    private final OneBlockPlugin plugin;
    private final @Getter UUID uuid;
    private final @Getter IslandMember owner;  // Member representing the owner's stats - not stored in members map
    private Component name;
    private final Map<UUID, IslandMember> members = new HashMap<>();

    // Holds the sum of all challenge progresses by members for this island (key: challengeId, value: total progress)
    private final Map<String, Integer> challengeProgress = new HashMap<>();

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
    }

    public void removeMember(@NonNull UUID memberUuid) {
        if (memberUuid.equals(owner.getId().getUuid())) {
            throw new IllegalArgumentException("Cannot remove the owner from the island");
        }
        IslandMember member = members.get(memberUuid);
        if (member == null) {
            throw new IllegalArgumentException("Member with UUID " + memberUuid + " (name: " + UUIDNamePair.of(memberUuid).getName() + ") is not part of this island");
        }
        // no removing, just set permissions to 0
        member.setPermissions(0);
        plugin.getStorageProvider().removeMemberFromUserDataProfiles(member.getId(), this.uuid);
    }

    public void putMember(@NonNull IslandMember member) {
        if (member.getId().equals(owner.getId())) {
            throw new IllegalArgumentException("Cannot put the owner in the members map");
        }
        members.put(member.getId().getUuid(), member);
    }

    public List<IslandMember> getMembers() {
        return members.values().stream().filter(im -> im.getPermissions() != 0).toList();
    }

    public List<IslandMember> getAllMembers() {
        // i ovi koji imaju permissions == 0, bivši članovi čije podatke moramo čuvati
        return new ArrayList<>(members.values());
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
        // Check if it's the owner
        IslandMember member = playerUuid.equals(owner.getId().getUuid()) ? owner : getMember(playerUuid);
        if (member == null) {
            plugin.getLogger().warning("Unknown player " + playerName + " (" + playerUuid + ") mined a block on island " + uuid);
            return;
        }
        member.trackBlockMined(material);
    }

    /**
     * Get the current stage of this island based on progression
     * @return The current Stage enum value
     */
    public Stage getStage() {
        int progress = challengeProgress.getOrDefault("stages", 0);
        int stageLevel;
        if (progress > 70) {
            stageLevel = 8;
        } else {
            stageLevel = (progress / 10) + 1;
            if (stageLevel > 8) stageLevel = 8;
        }
        return Stage.getByLevel(stageLevel);
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
        for (UUID memberUuid : members.keySet()) {
            Player p = Bukkit.getPlayer(memberUuid);
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
        IslandMember member = getMember(memberUuid);
        if (member != null) {
            members.put(memberUuid, new IslandMember(this.uuid, member.getId(), newPermissions, member.getAddedAt()));
        }
    }

    public @NotNull Component getName() {
        if (name != null) {
            return name;
        }
        return Component.text(owner.getId().getName() + "'s island", NamedTextColor.GRAY);
    }

    public void addMember(@NonNull UUIDNamePair memberId, int permission, Date addedAt) {
        if (owner.getId() == memberId) {
            throw new IllegalArgumentException("Cannot add owner as a member");
        }
        members.put(memberId.getUuid(), new IslandMember(this.uuid, memberId, permission, addedAt));
    }

    public @Nullable IslandMember getMember(@NonNull UUID memberUuid) {
        return members.get(memberUuid);
    }

    public Location getOrigin() {
        return plugin.getIslandAllocator().getIslandOrigin(this);
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

    public Serialized serialize() {
        byte[] gzipSchematicData;
        try {
            gzipSchematicData = WorldUtils.getRawSchematic(this.getBoundary(), this.getOrigin());
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to get schematic for island " + this.uuid, e);
            return null;
        }
        byte[] zstdSchematicData;
        try {
            zstdSchematicData = Compression.zstdCompressAndVerify(Compression.gzipDecompress(gzipSchematicData), 15);
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().log(Level.SEVERE, "Failed to compress island schematic for island " + this.uuid, e);
            return new Serialized(this, gzipSchematicData, "gzip");
        }
        return new Serialized(this, zstdSchematicData, "zstd");
    }

    // --- CHALLENGE PROGRESS AGGREGATION ---
    public Map<String, Integer> getChallengeProgress() {
        return challengeProgress;
    }

    /**
     * Recalculate the total challenge progress for this island from all members (including owner and ex-members)
     * This should be called after loading all members from storage.
     */
    public void recalculateChallengeProgress() {
        challengeProgress.clear();
        // Include owner
        aggregateMemberChallengeProgress(owner);
        // Include all members (including ex-members)
        for (IslandMember member : members.values()) {
            aggregateMemberChallengeProgress(member);
        }
    }

    private void aggregateMemberChallengeProgress(IslandMember member) {
        if (member == null) return;
        Map<String, Integer> memberProgress = member.getChallengeProgress();
        for (Map.Entry<String, Integer> entry : memberProgress.entrySet()) {
            challengeProgress.merge(entry.getKey(), entry.getValue(), Integer::sum);
        }
    }

    @RequiredArgsConstructor
    public static class Serialized {
        private final @Getter Island island;
        private final @Getter byte[] rawData;
        private final @Getter String compressionType;

        public byte[] decompress() throws IOException {
            return compressionType.equals("zstd") ? Compression.zstdDecompress(rawData) : Compression.gzipDecompress(rawData);
        }
    }
}
