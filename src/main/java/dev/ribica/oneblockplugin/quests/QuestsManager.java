package dev.ribica.oneblockplugin.quests;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.ribica.oneblockplugin.OneBlockPlugin;
import dev.ribica.oneblockplugin.playerdata.User;
import dev.ribica.oneblockplugin.quests.requirements.QuestRequirement;
import dev.ribica.oneblockplugin.quests.types.QuestPartMineSourceBlock0;
import dev.ribica.oneblockplugin.quests.types.QuestPartShowInfoMessage;
import lombok.Getter;
import org.bson.Document;

import javax.annotation.Nullable;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;

public class QuestsManager {
    private static @Getter QuestsManager instance;
    private final OneBlockPlugin plugin;
    private final Gson gson = new Gson();
    private final Map<Integer, QuestMetadata> quests = new HashMap<>();

    public QuestsManager(OneBlockPlugin plugin) {
        instance = this;
        this.plugin = plugin;
        loadQuests();
    }

    public @Nullable QuestMetadata getQuestMetadata(int questId) {
        return quests.get(questId);
    }

    public Collection<QuestMetadata> getAllQuests() {
        return quests.values();
    }

    public void loadQuests() {
        File questsFile = new File(plugin.getDataFolder(), "quests.json");
        if (!questsFile.exists()) {
            plugin.saveResource("quests.json", false);
        }

        try {
            JsonObject root = gson.fromJson(
                    Files.readString(questsFile.toPath(), StandardCharsets.UTF_8), JsonObject.class
            );
            JsonArray questsArray = root.getAsJsonArray("quests");
            for (JsonElement element : questsArray) {
                JsonObject questObj = element.getAsJsonObject();
                QuestMetadata metadata = QuestMetadata.fromJson(questObj);
                quests.put(metadata.id(), metadata);
            }
        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load quests.json");
            throw new RuntimeException(e);
        }
    }


    public record QuestMetadata(int id, String name, String description, List<QuestPartMetadata> parts, List<QuestRequirement> requirements) {
        /*
        We use List<QuestRequirement> instead of Predicate#and because we want to be able to access every individual
        requirement and show to the player which ones they do not meet.
         */

        public static QuestMetadata fromJson(JsonObject json) {
            int id = json.get("id").getAsInt();
            String name = json.get("name").getAsString();
            String description = json.get("description").getAsString();
            List<QuestPartMetadata> parts = new ArrayList<>();
            for (JsonElement partElement : json.getAsJsonArray("parts")) {
                parts.add(new QuestPartMetadata(partElement.getAsJsonObject()));
            }

            List<QuestRequirement> requirements = new ArrayList<>();
            if (json.has("requirements")) {
                for (JsonElement reqElement : json.getAsJsonArray("requirements")) {
                    var requirement = QuestRequirement.fromJson(reqElement.getAsJsonObject());
                    requirements.add(requirement);
                }
            }

            return new QuestMetadata(id, name, description, parts, requirements);
        }

        public boolean meetsRequirements(User user) {
            return requirements.stream().allMatch(req -> req.test(user));
        }

    }

    public record QuestPartMetadata(String type, JsonObject params) {
        public QuestPartMetadata(JsonObject jsonObj) {
            this(
                    jsonObj.get("type").getAsString(),
                    jsonObj.get("parameters").getAsJsonObject()
            );
        }

        public QuestPart resolve(Quest quest) {
            return resolve(quest, null);
        }

        public QuestPart resolve(Quest quest, @Nullable Document data) {
            // TODO don't just remove Nullable, check where this is called first
            if (data == null) {
                data = new Document();
            }
            return switch (type) {
                case "SHOW_INFO_MESSAGE" -> QuestPartShowInfoMessage.deserialize(quest, this, data);
                case "MINE_SOURCE_BLOCK_0" -> QuestPartMineSourceBlock0.deserialize(quest, this, data);
                default -> {
                    OneBlockPlugin.getInstance().getLogger().severe("(resolve2) Unknown quest part type: " + type);
                    yield null;
                }
            };
        }
    }
}
