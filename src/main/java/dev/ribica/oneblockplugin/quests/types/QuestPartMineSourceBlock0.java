package dev.ribica.oneblockplugin.quests.types;

import com.google.gson.JsonObject;
import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.playerdata.User;
import dev.ribica.oneblockplugin.quests.Quest;
import dev.ribica.oneblockplugin.quests.QuestPart;
import dev.ribica.oneblockplugin.quests.QuestsManager;
import lombok.Getter;
import lombok.NonNull;
import org.bson.Document;

import javax.annotation.Nullable;

public class QuestPartMineSourceBlock0 extends QuestPart {
    private @Getter int currentProgress;
    private @Getter int targetProgress;
    private boolean tracking = true;

    public QuestPartMineSourceBlock0(Quest quest, int currentProgress, int targetProgress) {
        super(quest);
        this.currentProgress = currentProgress;
        this.targetProgress = targetProgress;
    }

    @Override
    public void onStart() {

    }

    @Override
    public void onResume() {
        if (isCompleted())
            tracking = false;
    }

    @Override
    public void onComplete() {
        tracking = false;
    }

    public void progress(User user) {
        if (user != getQuest().getUser()) {
            throw new IllegalStateException("Cannot progress quest part for a different user.");
        }
        if (!tracking) {
            OneBlockPlugin.getInstance().getLogger().warning("Ignoring to progress a completed quest part for " +
                    user.getPlayer().getName());
            return;
        }
        OneBlockPlugin.getInstance().getLogger().info("Progressing mine source block quest part for " + user.getPlayer().getName());
        currentProgress++;
        if (currentProgress >= targetProgress) {
            finish0();
        }
    }

    public static QuestPartMineSourceBlock0 deserialize(
            @NonNull Quest quest,
            @NonNull QuestsManager.QuestPartMetadata metadata,
            @NonNull Document data
    ) {
        JsonObject params = metadata.params();
        return new QuestPartMineSourceBlock0(
                quest,
                data.getInteger("progress", 0),
                params.get("count").getAsInt()
        );
    }

    @Override
    public void serializeInto(Document document) {
        document.put("progress", currentProgress);
    }
}
