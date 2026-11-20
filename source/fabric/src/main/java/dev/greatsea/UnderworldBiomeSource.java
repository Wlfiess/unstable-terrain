package dev.greatsea;

import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import java.util.stream.Stream;

/** Native Nether climate above the void gap, and a saved custom biome beneath it. */
public final class UnderworldBiomeSource extends BiomeSource {
    public static final MapCodec<UnderworldBiomeSource> CODEC=RecordCodecBuilder.mapCodec(instance -> instance.group(
            BiomeSource.CODEC.fieldOf("nether").forGetter(source -> source.nether),
            Biome.REGISTRY_CODEC.fieldOf("underworld").forGetter(source -> source.underworld)
    ).apply(instance,UnderworldBiomeSource::new));
    private final BiomeSource nether;
    private final RegistryEntry<Biome> underworld;
    public UnderworldBiomeSource(BiomeSource nether,RegistryEntry<Biome> underworld) {
        this.nether=nether;this.underworld=underworld;
    }
    public BiomeSource netherSource() { return nether; }
    @Override protected MapCodec<? extends BiomeSource> getCodec() { return CODEC; }
    @Override protected Stream<RegistryEntry<Biome>> biomeStream() {
        return Stream.concat(nether.getBiomes().stream(),Stream.of(underworld));
    }
    @Override public RegistryEntry<Biome> getBiome(int x,int y,int z,MultiNoiseUtil.MultiNoiseSampler sampler) {
        return y*4<UnstableNether.UNDERWORLD_TOP ? underworld : nether.getBiome(x,y,z,sampler);
    }
}
