package dev.ribica.oneblockplugin.util;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.generator.WorldInfo;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.Random;

public class VoidChunkGenerator extends ChunkGenerator {

    @Override
    public @NotNull List<BlockPopulator> getDefaultPopulators(@NotNull World world) { return List.of(); }

    @Override
    public void generateNoise(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ,
                              @NotNull ChunkData chunkData) {
        // No need to generate noise, we want an empty world
    }
    @Override
    public void generateSurface(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ,
                                @NotNull ChunkData chunkData) {
        // No need to generate surface, we want an empty world
    }
    @Override
    public void generateBedrock(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ,
                                @NotNull ChunkData chunkData) {
        // No need to generate bedrock, we want an empty world
    }
    @Override
    public void generateCaves(@NotNull WorldInfo worldInfo, @NotNull Random random, int chunkX, int chunkZ,
                              @NotNull ChunkData chunkData) {
        // No need to generate caves, we want an empty world
    }

    @Override
    public BiomeProvider getDefaultBiomeProvider(@NotNull WorldInfo worldInfo) { return new VoidBiomeProvider(); }

    @Override
    public boolean canSpawn(@NotNull World world, int x, int z) { return true; }

    @Override
    public Location getFixedSpawnLocation(@NotNull World world, @NotNull Random random) { return new Location(world, 0, 100, 0); }
}