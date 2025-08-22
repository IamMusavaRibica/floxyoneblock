package dev.ribica.oneblockplugin.util;

import org.bukkit.block.Biome;
import org.bukkit.generator.BiomeProvider;
import org.bukkit.generator.WorldInfo;

import java.util.List;

public class VoidBiomeProvider extends BiomeProvider {

    @Override
    public Biome getBiome(WorldInfo worldInfo, int x, int y, int z) { return Biome.THE_VOID; }

    @Override
    public List<Biome> getBiomes(WorldInfo worldInfo) { return List.of(Biome.THE_VOID); }

}
