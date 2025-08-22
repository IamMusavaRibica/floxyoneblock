package dev.ribica.oneblockplugin.quests;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.playerdata.User;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import org.bson.Document;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class Quest {
    // The Quest class represents a player's active in-game quest.
    // It provides methods to serialize to and deserialize from Bson, to allow storing the player's
    // half-done quest in the database. Each quest consists of multiple parts and each part can either
    // be instantly completed or require some progress that needs to be stored as well.
    private @Getter final User user;
    private @Getter final QuestsManager.QuestMetadata metadata;
    private @Getter final List<QuestPart> parts;
    private @Getter QuestPart activePart;
    private static final Logger log = Logger.getLogger(Quest.class.getName());
    private @Getter @Setter boolean resumed = false;

    public void resume() {
        if (resumed) {
            throw new IllegalStateException("Quest is already resumed.");
        }
        log.info("Resumed quest " + metadata.id() + " (" + metadata.name() + ") for user " +
                user.getPlayer().getName());
        resumed = true;
        activePart.onResume();
    }

    public void continueQuest() {
        // QuestPart calls this method to continue onto the next part
        for (var qp : parts) {
            if (!qp.isCompleted()) {
                assert qp != activePart;
                OneBlockPlugin.getInstance().getLogger().info(
                        "Continuing quest " + metadata.id() + " (" + metadata.name() + ") for user " +
                                user.getPlayer().getName() + " at part: " + qp.getClass().getSimpleName()
                );
                activePart = qp;
                qp.start0();
                return;
            }
        }

        // If we reach here, all parts are completed
        OneBlockPlugin.getInstance().getLogger().info("User " + user.getPlayer().getName() +
                " has completed quest " + metadata.id() + " (" + metadata.name() + ")");
        user.quests.completeQuest(metadata.id());
        activePart = null;
    }


    public static @Nullable Quest create(@NonNull User user, @NonNull QuestsManager.QuestMetadata metadata) {
        return deserialize(user, metadata, null);
    }

    public static @Nullable Quest deserialize(
            @NonNull User user,
            @NonNull QuestsManager.QuestMetadata metadata,
            @Nullable Document document
    ) {
        var parts = new ArrayList<QuestPart>();
        Quest instance = new Quest(user, metadata, parts);

        if (document == null) {  // instantiating a new Quest
            log.warning("Creating new quest " + metadata.id() + " for user " + user.getUuid());
            for (var partMeta : metadata.parts()) {
                QuestPart qp = partMeta.resolve(instance);
                if (qp != null)
                    parts.add(qp);
            }
            instance.setResumed(true);
        } else {                 // loading from database
            log.warning("Loading quest " + metadata.id() + " for user " + user.getUuid() + " from database");
            var partsDoc = document.getList("parts", Document.class, Collections.emptyList());
            if (partsDoc.size() != metadata.parts().size()) {
                log.severe("Quest " + metadata.id() + " user " + user.getUuid() + " parts count mismatch!");
                return null;  // or throw an exception if you prefer
            }
            for (int i = 0; i < partsDoc.size(); i++) {
                var partMeta = metadata.parts().get(i);
                var partDoc = partsDoc.get(i);
                QuestPart qp = partMeta.resolve(instance, partDoc.get("data", Document.class));
                if (qp != null) {
                    qp.setCompleted(partDoc.getBoolean("completed"));
                    parts.add(qp);
                } else {
                    log.warning("Failed to resolve quest part #" + i + " for user " +
                            user.getUuid());
                }
            }
        }

        instance.update();
        return instance;
    }

    public void update() {
        for (var part : parts) {
            if (!part.isCompleted()) {
                activePart = part;
                break;
            }
        }
    }


    public Document serialize() {
        var doc = new Document();
        doc.append("id", metadata.id());

        var partsDoc = new ArrayList<Document>();
        for (var part : parts) {
            partsDoc.add(part.serialize0());
        }
        doc.append("parts", partsDoc);
        return doc;
    }
}
