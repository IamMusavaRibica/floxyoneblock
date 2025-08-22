package dev.ribica.oneblockplugin.islands;

import dev.ribica.oneblockplugin.util.UUIDNamePair;
import lombok.*;
import org.bson.Document;
import org.bukkit.Material;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.logging.Logger;

/**
 * Represents a member of an island, including the owner.
 * Tracks block mining statistics for each member.
 */
@AllArgsConstructor
@Getter
public class IslandMember {
    private static final Logger logger = Logger.getLogger(IslandMember.class.getName());
    private final @NonNull UUID islandUuid;
    private final @NonNull UUIDNamePair id;
    private @Setter int permissions;  // TODO: make this 'Long' later
    private final @NonNull Date addedAt;
    private final Map<Material, Integer> minedBlocks = new HashMap<>();

    public void trackBlockMined(Material material) {
        minedBlocks.put(material, minedBlocks.getOrDefault(material, 0) + 1);
    }

    public int getBlockCount(Material material) {
        return minedBlocks.getOrDefault(material, 0);
    }

    public UUID getUuid() {
        return id.getUuid();
    }

    /**
     * Serializes this IslandMember to a BSON document for mongodb
     * @return Document with member data
     */
    public Document serialize() {
        Document doc = id.serialize(new Document());
        doc.append("permissions", permissions);
        doc.append("added_at", addedAt);

        // Add collected blocks as a subdocument
        Document collectionsDoc = new Document();
        minedBlocks.forEach((material, count) -> collectionsDoc.append(material.name(), count));
        if (!collectionsDoc.isEmpty()) {
            doc.append("collections", collectionsDoc);
        }

//        Document progressDoc = new Document();
//        challengeProgress.forEach(progressDoc::append);
//        if (!progressDoc.isEmpty()) {
//            doc.append("challenge_progress", progressDoc);
//        }

        return doc;
    }

    /**
     * Deserializes a MongoDB document into an IslandMember instance
     *
     * @param doc The MongoDB document containing member data
     * @param islandUuid The UUID of the island this member belongs to
     * @return A new IslandMember instance
     */
    public static IslandMember deserialize(@NonNull Document doc, @NonNull UUID islandUuid) {
        UUIDNamePair memberId = UUIDNamePair.of(doc);
        int permissions = doc.getInteger("permissions");
        Date addedAt = doc.getDate("added_at");

        IslandMember member = new IslandMember(islandUuid, memberId, permissions, addedAt);

        // load collections data
        Document collectionsDoc = doc.get("collections", Document.class);
        if (collectionsDoc != null) {
            for (String key : collectionsDoc.keySet()) {
                try {
                    Material material = Material.valueOf(key);
                    int count = collectionsDoc.getInteger(key, 0);
                    if (count > 0) {
                        member.minedBlocks.put(material, count);
                    }
                } catch (IllegalArgumentException e) {
                    logger.warning("Invalid material for mined blocks: " + key);
                }
            }
        }

        // load challenge progress
//        Document progressDoc = doc.get("challenge_progress", Document.class);
//        if (progressDoc != null) {
//            for (String key : progressDoc.keySet()) {
//                member.challengeProgress.put(key, progressDoc.getInteger(key, 0));
//            }
//        }

        return member;
    }
}
