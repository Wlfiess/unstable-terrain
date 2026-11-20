package dev.greatsea;

import com.mojang.serialization.JsonOps;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.RegistryOps;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.tag.WorldPresetTags;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.dimension.DimensionOptions;
import net.minecraft.world.gen.WorldPresets;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import java.util.List;

/** Exercises loaded data registries and the same codec used to save world generators. */
public final class WorldPresetVerification {
    private WorldPresetVerification() {}
    public static void verify(MinecraftServer server,List<String> report) {
        var registries=server.getRegistryManager();
        var presets=registries.getOrThrow(RegistryKeys.WORLD_PRESET);
        var unstable=presets.getOrThrow(UnstableWorldGen.PRESET);
        check(unstable.isIn(WorldPresetTags.NORMAL),"Unstable missing from normal World Type list");
        var dimensions=unstable.value().createDimensionsRegistryHolder();
        check(UnstableWorldGen.isUnstable(dimensions.getChunkGenerator()),"Unstable Overworld did not retain its settings key");
        var normalDimensions=presets.getOrThrow(WorldPresets.DEFAULT).value().createDimensionsRegistryHolder();
        var normal=normalDimensions.getChunkGenerator();
        check(!UnstableWorldGen.isUnstable(normal),"Normal preset incorrectly opted in");
        check(!SeaGeneration.supported(normal),"Great Sea activated in Normal preset");
        var nether=dimensions.getOrEmpty(DimensionOptions.NETHER).orElseThrow();
        check(UnstableNether.supported(nether.chunkGenerator()),"Unstable Nether did not retain its Underworld settings key");
        check(nether.dimensionTypeEntry().value().minY()==UnstableNether.MIN_Y &&
                nether.dimensionTypeEntry().value().height()==UnstableNether.HEIGHT,"Unstable Nether did not extend its build limits");
        check(nether.chunkGenerator().getBiomeSource() instanceof UnderworldBiomeSource,"Unstable Nether did not retain its layered biome source");
        verifyVanillaDimension(normalDimensions.getOrEmpty(DimensionOptions.NETHER).orElseThrow().chunkGenerator(),ChunkGeneratorSettings.NETHER);
        verifyVanillaDimension(dimensions.getOrEmpty(DimensionOptions.END).orElseThrow().chunkGenerator(),ChunkGeneratorSettings.END);
        var ops=RegistryOps.of(JsonOps.INSTANCE,registries);
        var saved=ChunkGenerator.CODEC.encodeStart(ops,dimensions.getChunkGenerator()).getOrThrow();
        var loaded=ChunkGenerator.CODEC.parse(ops,saved).getOrThrow();
        check(UnstableWorldGen.isUnstable(loaded),"Generator codec reload lost Unstable selection");
        check(WorldPresets.getWorldPreset(dimensions).filter(UnstableWorldGen.PRESET::equals).isPresent(),"Re-Create screen did not recognize Unstable preset");
        var netherSaved=ChunkGenerator.CODEC.encodeStart(ops,nether.chunkGenerator()).getOrThrow();
        var netherLoaded=ChunkGenerator.CODEC.parse(ops,netherSaved).getOrThrow();
        check(UnstableNether.supported(netherLoaded) && netherLoaded.getBiomeSource() instanceof UnderworldBiomeSource,
                "Generator codec reload lost Underworld selection");
        var normalSaved=ChunkGenerator.CODEC.encodeStart(ops,normal).getOrThrow();
        check(!UnstableWorldGen.isUnstable(ChunkGenerator.CODEC.parse(ops,normalSaved).getOrThrow()),"Normal generator codec reload opted in");
        report.add("World presets: Unstable retains Great Sea and extended Underworld Nether settings through save/reload; Normal retains vanilla Nether; End stays inactive.");
    }
    private static void verifyVanillaDimension(ChunkGenerator generator,RegistryKey<ChunkGeneratorSettings> settings) {
        check(generator instanceof NoiseChunkGenerator noise && noise.matchesSettings(settings),"Unstable changed "+settings.getValue());
        check(!UnstableWorldGen.isUnstable(generator),"Great Sea activated in "+settings.getValue());
        check(!UnstableNether.supported(generator),"Underworld activated in "+settings.getValue());
    }
    private static void check(boolean condition,String message) { if (!condition) throw new IllegalStateException(message); }
}
