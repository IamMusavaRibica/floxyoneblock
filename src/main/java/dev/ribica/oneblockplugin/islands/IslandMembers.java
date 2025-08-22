package dev.ribica.oneblockplugin.islands;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.util.UUIDNamePair;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bson.Document;

import javax.annotation.Nullable;
import java.util.*;
import java.util.logging.Logger;

/**
 * Manages the members of an island, providing methods to add, remove, and query members.
 * Similar to UserQuestsHolder, this class encapsulates member management functionality.
 */
@RequiredArgsConstructor
public class IslandMembers {
    private static final Logger logger = Logger.getLogger(IslandMembers.class.getName());
    private final @NonNull Island island;
    private final Map<UUID, IslandMember> members = new HashMap<>();

    /**
     * Gets a member by UUID
     * @param memberUuid UUID of the member to get
     * @return The IslandMember, or null if not found
     */
    public @Nullable IslandMember getMember(@NonNull UUID memberUuid) {
        return members.get(memberUuid);
    }

    /**
     * Adds a new member to the island
     * @param memberId The UUID and name of the member
     * @param permission The permission level to assign
     * @param addedAt When the member was added
     */
    public void addMember(@NonNull UUIDNamePair memberId, int permission, Date addedAt) {
        if (island.getOwner().getId().equals(memberId)) {
            throw new IllegalArgumentException("Cannot add owner as a member");
        }
        members.put(memberId.getUuid(), new IslandMember(island.getUuid(), memberId, permission, addedAt));
    }

    /**
     * Removes a member from the island
     * @param memberUuid UUID of the member to remove
     */
    public void removeMember(@NonNull UUID memberUuid) {
        if (memberUuid.equals(island.getOwner().getId().getUuid())) {
            throw new IllegalArgumentException("Cannot remove the owner from the island");
        }
        IslandMember member = members.get(memberUuid);
        if (member == null) {
            throw new IllegalArgumentException("Member with UUID " + memberUuid + " is not part of this island");
        }
        // no deleting data, just set permissions to 0
        member.setPermissions(0);
        OneBlockPlugin.getInstance().getStorageProvider().removeMemberFromUserDataProfiles(member.getId(), island.getUuid());
    }

    /**
     * Updates a member's permissions
     * @param memberUuid UUID of the member
     * @param newPermissions New permission level
     */
    public void updatePermissions(UUID memberUuid, int newPermissions) {
        IslandMember member = getMember(memberUuid);
        if (member != null) {
            members.put(memberUuid, new IslandMember(island.getUuid(), member.getId(), newPermissions, member.getAddedAt()));
        }
    }

    /**
     * Adds a member directly using an IslandMember object
     * @param member The member to add
     */
    public void putMember(@NonNull IslandMember member) {
        if (member.getId().equals(island.getOwner().getId())) {
            throw new IllegalArgumentException("Cannot put the owner in the members map");
        }
        members.put(member.getId().getUuid(), member);
    }

    /**
     * Checks if a UUID is a member of this island
     * @param memberUuid UUID to check
     * @param mustBeCurrentMember If true, only returns true for active members (permissions != 0)
     * @return True if the UUID is a member
     */
    public boolean hasMember(@NonNull UUID memberUuid, boolean mustBeCurrentMember) {
        IslandMember member = getMember(memberUuid);
        return member != null && (!mustBeCurrentMember || member.getPermissions() != 0);
    }

    /**
     * Gets all current members (permissions != 0)
     * @return List of current members
     */
    public List<IslandMember> getCurrentMembers() {
        return members.values().stream().filter(im -> im.getPermissions() != 0).toList();
    }

    /**
     * Gets all members, including former members (permissions == 0)
     * @return List of all members
     */
    public List<IslandMember> getAllMembers() {
        return new ArrayList<>(members.values());
    }

    /**
     * Serializes all members to a list of Documents
     * @return List of serialized member documents
     */
    public List<Document> serialize() {
        return getAllMembers().stream()
                .map(IslandMember::serialize)
                .toList();
    }

    /**
     * Deserializes a list of member documents
     * @param memberDocs List of member documents
     */
    public void deserialize(List<Document> memberDocs) {
        memberDocs.forEach(memberDoc -> {
            IslandMember member = IslandMember.deserialize(memberDoc, island.getUuid());
            putMember(member);
        });
    }
}
