package dev.ribica.oneblockplugin.oneblock;

import org.bukkit.Material;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Manages the weighted block selection system for the OneBlock
 */
public class BlockWeights {
    // Map of stage to weighted blocks
    private static final Map<Stage, List<WeightedBlock>> STAGE_BLOCKS = new HashMap<>();

    // Initialize the weighted blocks for each stage
    static {
        // STAGE 1: PLAINS
        List<WeightedBlock> plainsBlocks = Arrays.asList(
            new WeightedBlock(Material.GRASS_BLOCK, 35),
            new WeightedBlock(Material.DIRT, 30),
            new WeightedBlock(Material.OAK_LOG, 20),
            new WeightedBlock(Material.STONE, 10),
            new WeightedBlock(Material.COAL_ORE, 3),
            new WeightedBlock(Material.IRON_ORE, 2)
        );

        // STAGE 2: UNDERGROUND
        List<WeightedBlock> undergroundBlocks = Arrays.asList(
            new WeightedBlock(Material.STONE, 40),
            new WeightedBlock(Material.DEEPSLATE, 20),
            new WeightedBlock(Material.ANDESITE, 10),
            new WeightedBlock(Material.DIORITE, 10),
            new WeightedBlock(Material.GRANITE, 10),
            new WeightedBlock(Material.COAL_ORE, 5),
            new WeightedBlock(Material.IRON_ORE, 3),
            new WeightedBlock(Material.GOLD_ORE, 1),
            new WeightedBlock(Material.DIAMOND_ORE, 1)
        );

        // STAGE 3: OCEAN
        List<WeightedBlock> oceanBlocks = Arrays.asList(
            new WeightedBlock(Material.SAND, 25),
            new WeightedBlock(Material.GRAVEL, 20),
            new WeightedBlock(Material.CLAY, 15),
            new WeightedBlock(Material.PRISMARINE, 10),
            new WeightedBlock(Material.SEAGRASS, 10),
            new WeightedBlock(Material.KELP, 10),
            new WeightedBlock(Material.SEA_LANTERN, 5),
            new WeightedBlock(Material.PRISMARINE_BRICKS, 3),
            new WeightedBlock(Material.DARK_PRISMARINE, 2)
        );

        // STAGE 4: JUNGLE
        List<WeightedBlock> jungleBlocks = Arrays.asList(
            new WeightedBlock(Material.JUNGLE_LOG, 30),
            new WeightedBlock(Material.JUNGLE_LEAVES, 25),
            new WeightedBlock(Material.MOSS_BLOCK, 20),
            new WeightedBlock(Material.PODZOL, 15),
            new WeightedBlock(Material.BAMBOO, 5),
            new WeightedBlock(Material.COCOA, 3),
            new WeightedBlock(Material.MELON, 2)
        );

        // STAGE 5: DESERT
        List<WeightedBlock> desertBlocks = Arrays.asList(
            new WeightedBlock(Material.SAND, 40),
            new WeightedBlock(Material.SANDSTONE, 30),
            new WeightedBlock(Material.TERRACOTTA, 15),
            new WeightedBlock(Material.CACTUS, 10),
            new WeightedBlock(Material.DEAD_BUSH, 3),
            new WeightedBlock(Material.GOLD_BLOCK, 2)
        );

        // STAGE 6: ICE PLAINS
        List<WeightedBlock> iceBlocks = Arrays.asList(
            new WeightedBlock(Material.SNOW_BLOCK, 30),
            new WeightedBlock(Material.ICE, 25),
            new WeightedBlock(Material.PACKED_ICE, 20),
            new WeightedBlock(Material.BLUE_ICE, 15),
            new WeightedBlock(Material.POWDER_SNOW, 5),
            new WeightedBlock(Material.SPRUCE_LOG, 5)
        );

        // STAGE 7: NETHER
        List<WeightedBlock> netherBlocks = Arrays.asList(
            new WeightedBlock(Material.NETHERRACK, 35),
            new WeightedBlock(Material.NETHER_BRICKS, 20),
            new WeightedBlock(Material.SOUL_SAND, 15),
            new WeightedBlock(Material.MAGMA_BLOCK, 10),
            new WeightedBlock(Material.GLOWSTONE, 10),
            new WeightedBlock(Material.ANCIENT_DEBRIS, 5),
            new WeightedBlock(Material.NETHER_GOLD_ORE, 3),
            new WeightedBlock(Material.NETHER_QUARTZ_ORE, 2)
        );

        // STAGE 8: END
        List<WeightedBlock> endBlocks = Arrays.asList(
            new WeightedBlock(Material.END_STONE, 40),
            new WeightedBlock(Material.END_STONE_BRICKS, 25),
            new WeightedBlock(Material.PURPUR_BLOCK, 20),
            new WeightedBlock(Material.OBSIDIAN, 10),
            new WeightedBlock(Material.CHORUS_FLOWER, 3),
            new WeightedBlock(Material.DRAGON_EGG, 1),
            new WeightedBlock(Material.END_ROD, 1)
        );

        // Add all blocks to the stage map
        STAGE_BLOCKS.put(Stage.STAGE_1_PLAINS, plainsBlocks);
        STAGE_BLOCKS.put(Stage.STAGE_2_UNDERGROUND, undergroundBlocks);
        STAGE_BLOCKS.put(Stage.STAGE_3_OCEAN, oceanBlocks);
        STAGE_BLOCKS.put(Stage.STAGE_4_JUNGLE, jungleBlocks);
        STAGE_BLOCKS.put(Stage.STAGE_5_DESERT, desertBlocks);
        STAGE_BLOCKS.put(Stage.STAGE_6_ICE, iceBlocks);
        STAGE_BLOCKS.put(Stage.STAGE_7_NETHER, netherBlocks);
        STAGE_BLOCKS.put(Stage.STAGE_8_END, endBlocks);
    }

    /**
     * Get a random block for the specified stage based on the weight distribution
     *
     * @param stage The current stage
     * @return A randomly selected Material based on weights
     */
    public static Material getRandomBlock(Stage stage) {
        List<WeightedBlock> blocks = STAGE_BLOCKS.get(stage);
        if (blocks == null || blocks.isEmpty()) {
            // Fallback to plains if no blocks defined
            blocks = STAGE_BLOCKS.get(Stage.STAGE_1_PLAINS);
        }

        // Calculate total weight
        int totalWeight = blocks.stream()
                .mapToInt(WeightedBlock::getWeight)
                .sum();

        // Get a random value between 0 and totalWeight
        int randomWeight = ThreadLocalRandom.current().nextInt(totalWeight);

        // Find the block that corresponds to this weight
        int currentWeight = 0;
        for (WeightedBlock block : blocks) {
            currentWeight += block.getWeight();
            if (randomWeight < currentWeight) {
                return block.getMaterial();
            }
        }

        // Fallback to a default block (shouldn't happen)
        return Material.STONE;
    }

    /**
     * A simple class to represent a block with a weight for random selection
     */
    private static class WeightedBlock {
        private final Material material;
        private final int weight;

        public WeightedBlock(Material material, int weight) {
            this.material = material;
            this.weight = weight;
        }

        public Material getMaterial() {
            return material;
        }

        public int getWeight() {
            return weight;
        }
    }
}
