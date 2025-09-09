package dev.ribica.oneblockplugin.oneblock;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.ribica.oneblockplugin.OneBlockPlugin;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.bukkit.Material;
import org.bukkit.block.data.BlockData;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;


public class StageManager {
    private final OneBlockPlugin plugin;
    private final File stagesFile;
    private final Gson gson = new Gson();
    private final Map<Integer, Stage> stages = new HashMap<>();
    private final Random random = new Random();

    public StageManager(OneBlockPlugin plugin) {
        this.plugin = plugin;
        this.stagesFile = new File(plugin.getDataFolder(), "stages.json");
        loadStages();
    }

    /**
     * Get a stage by its ID
     * @param stageId The stage ID
     * @return The stage, or null if not found
     */
    public Stage getStage(int stageId) {
        return stages.get(stageId);
    }

    /**
     * Get all available stages
     * @return Map of stage ID to Stage
     */
    public Map<Integer, Stage> getAllStages() {
        return new HashMap<>(stages);
    }

    /**
     * Get a random block from a stage based on weights
     * @param stageId The stage ID
     * @return A random material from the stage, or STONE if stage not found
     */
    public BlockData getRandomBlock(int stageId) {
        Stage stage = stages.get(stageId);
        if (stage == null || stage.blocks().isEmpty()) {
            return Material.STONE.createBlockData(); // fallback
        }

        int totalWeight = stage.blocks().stream().mapToInt(WeightedBlock::weight).sum();
        int randomValue = random.nextInt(totalWeight);

        int currentWeight = 0;
        for (WeightedBlock weightedBlock : stage.blocks()) {
            currentWeight += weightedBlock.weight();
            if (randomValue < currentWeight) {
                return weightedBlock.material.createBlockData();
            }
        }
        plugin.getLogger().warning("No more weight in stage " + stageId + "!");
        // Fallback to first block if something goes wrong
        return stage.blocks().getFirst().material.createBlockData();
    }

    /**
     * Get all block materials from a stage
     * @param stageId The stage ID
     * @return List of materials in the stage
     */
    public List<Material> getStageBlocks(int stageId) {
        Stage stage = stages.get(stageId);
        if (stage == null) {
            return new ArrayList<>();
        }
        return stage.blocks().stream().map(WeightedBlock::material).toList();
    }

    private void loadStages() {
        if (!stagesFile.exists()) {
            plugin.saveResource("stages.json", false);
        }

        try {
            JsonArray stagesArray = gson.fromJson(
                    Files.readString(stagesFile.toPath(), StandardCharsets.UTF_8), JsonObject.class
            ).getAsJsonArray("stages");

            for (JsonElement element : stagesArray) {
                try {
                    JsonObject stageObj = element.getAsJsonObject();
                    int id = stageObj.get("id").getAsInt();
                    String name = stageObj.get("name").getAsString();
                    Material icon = Material.valueOf(stageObj.get("icon").getAsString());

                    List<WeightedBlock> blocks = new ArrayList<>();
                    stageObj.getAsJsonObject("blocks").asMap().forEach(
                            (mat, weight) -> blocks.add(new WeightedBlock(
                                    Material.valueOf(mat),
                                    weight.getAsInt()
                            ))
                    );

                    // TODO: load mobs

                    stages.put(id, new Stage(id, name, icon, blocks));

                } catch (Exception e) {
                    plugin.getLogger().warning("Failed to load stage: " + element + " - " + e.getMessage());
                }
            }

        } catch (IOException e) {
            plugin.getLogger().severe("Failed to load stages.json: " + e.getMessage());
        }
    }

    public record WeightedBlock(Material material, int weight) {}
    public record Stage(int id, String name, Material icon, List<WeightedBlock> blocks) {}
}
