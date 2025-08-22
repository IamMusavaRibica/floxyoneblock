package dev.ribica.oneblockplugin.quests;

import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.playerdata.User;
import lombok.AllArgsConstructor;
import lombok.RequiredArgsConstructor;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.translation.GlobalTranslator;
import net.kyori.adventure.translation.Translator;
import org.bson.Document;
import org.joml.Matrix4f;

import java.util.*;
import java.util.logging.Logger;

@RequiredArgsConstructor
public class UserQuestsHolder {
    private static final Logger logger = Logger.getLogger(UserQuestsHolder.class.getName());
    private final User user;
    private final Set<Integer> completedQuestIDs;
    private final Map<Integer, Quest> activeQuests;

    public UserQuestsHolder(User user) {
        this(user, new HashSet<>(), new HashMap<>());
    }

    public Map<Integer, Quest> getActiveQuests() {
        return Collections.unmodifiableMap(activeQuests);
    }

    public boolean hasCompleted(int questID) {
        return completedQuestIDs.contains(questID);
    }

    public List<Integer> getStartableQuests() {
        final var plugin = OneBlockPlugin.getInstance();
        List<Integer> ret = new ArrayList<>();
        for (var quest : QuestsManager.getInstance().getAllQuests()) {
            if (canStartQuest(quest.id())) {
                ret.add(quest.id());
            }
        }

        plugin.getLogger().info(String.format(
                "User %s has these startable quests: %s", user.getPlayer().getName(),
                String.join(", ", ret.stream().map(String::valueOf).toList())
        ));

        return ret;
    }

    public boolean canStartQuest(int questID) {
        var quest = QuestsManager.getInstance().getQuestMetadata(questID);
        if (quest == null) {
            OneBlockPlugin.getInstance().getLogger().warning("Checked non existent quest ID: " + questID);
            return false;
        }
        return !hasCompleted(questID) && !activeQuests.containsKey(questID) && quest.meetsRequirements(user);
    }

    public void beginQuest(int questId) {
        // This method is used only for actually *beginning* a quest
        // If we load an active_quest from the database, we should *resume* it instead
        if (!canStartQuest(questId)) {
            throw new IllegalStateException("user " + user.getPlayer().getName() + "cannot start quest " + questId);
        }
        final QuestsManager qm = QuestsManager.getInstance();
        var questMetadata = qm.getQuestMetadata(questId);
        Quest quest = Quest.create(user, questMetadata);
        if (quest != null) {
            activeQuests.put(questId, quest);
            quest.continueQuest();
        }

    }

    protected void completeQuest(int questId) {
        var player = user.getPlayer();
        if (!activeQuests.containsKey(questId)) {
            throw new IllegalStateException("user " + player.getName() + " cannot complete quest " + questId +
                    " because it is not active.");
        }
        Quest quest = activeQuests.remove(questId);
        completedQuestIDs.add(questId);

        player.sendMessage(GlobalTranslator.render(
                Component.translatable("quests.completed", Component.text(quest.getMetadata().name())),
                player.locale()
        ));

    }

    public Document serialize() {
        var doc = new Document();
        doc.put("completed_quests", List.copyOf(completedQuestIDs));

        // serialize active quests
        var activeQuestsDoc = new ArrayList<Document>();
        for (var quest : activeQuests.values())
            activeQuestsDoc.add(quest.serialize());
        doc.put("active_quests", activeQuestsDoc);

        return doc;
    }

    public static UserQuestsHolder deserialize(Document questsDoc, User user) {
        // player  { quests: { completed_quests: [...], active_quests: [{id: 1, parts: [...]}, ...] } }
        // this is called async
        // make sure to call QuestPart#onResume for each active quest part in player join event
        Set<Integer> completedQuests = new HashSet<>(questsDoc.getList("completed_quests", Integer.class, Collections.emptyList()));

        Map<Integer, Quest> activeQuests = new HashMap<>();
        for (var questDoc : questsDoc.getList("active_quests", Document.class, Collections.emptyList())) {
            int questId = questDoc.getInteger("id");
            var questMetadata = QuestsManager.getInstance().getQuestMetadata(questId);
            if (questMetadata != null) {
                Quest quest = Quest.deserialize(user, questMetadata, questDoc);
                logger.warning("Deserialized quest ID: " + questId + " for user " + user.getUuid());
                if (quest != null)
                    activeQuests.put(questId, quest);
                else
                    logger.severe("deserialized null ^^^^ !!!!");
            } else {
                logger.warning("Deserialized non-existent quest ID: " + questId);
            }
        }

        return new UserQuestsHolder(user, completedQuests, activeQuests);
//        return new UserQuestsHolder(user);
    }
}
