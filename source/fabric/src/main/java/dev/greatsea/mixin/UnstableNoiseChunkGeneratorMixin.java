package dev.greatsea.mixin;

import dev.greatsea.UnstableWorldGen;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.source.BiomeSource;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseChunkGenerator.class)
public abstract class UnstableNoiseChunkGeneratorMixin {
    @Inject(method="<init>",at=@At("TAIL"))
    private void greatsea$rememberWorldType(BiomeSource biomes,RegistryEntry<ChunkGeneratorSettings> settings,CallbackInfo ci) {
        UnstableWorldGen.register(settings);
    }
}
