package dev.greatsea.mixin;

import dev.greatsea.SeedAccess;
import dev.greatsea.SeaBiomeContext;
import dev.greatsea.VanillaOceanTerrain;
import dev.greatsea.UnstableWorldGen;
import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.util.math.noise.DoublePerlinNoiseSampler;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NoiseConfig.class)
public abstract class NoiseConfigMixin implements SeedAccess {
    @Inject(method="create(Lnet/minecraft/registry/RegistryEntryLookup$RegistryLookup;Lnet/minecraft/registry/RegistryKey;J)Lnet/minecraft/world/gen/noise/NoiseConfig;",at=@At("RETURN"))
    private static void unstableterrain$amplified(RegistryEntryLookup.RegistryLookup registries,
            net.minecraft.registry.RegistryKey<ChunkGeneratorSettings> key,long seed,
            org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable<NoiseConfig> cir) {
        if(key.equals(UnstableWorldGen.SETTINGS)){
            dev.greatsea.AmplifiedTerrain.register(cir.getReturnValue(),registries,seed);
            var settings=registries.getOrThrow(net.minecraft.registry.RegistryKeys.CHUNK_GENERATOR_SETTINGS).getOrThrow(ChunkGeneratorSettings.FLOATING_ISLANDS);
            dev.greatsea.VanillaSkylands.register(seed,NoiseConfig.create(settings.value(),registries.getOrThrow(net.minecraft.registry.RegistryKeys.NOISE_PARAMETERS),seed));
        }
    }
    @Unique private long greatsea$worldSeed;
    @Inject(method = "<init>", at = @At("TAIL"))
    private void greatsea$captureSeed(ChunkGeneratorSettings settings,
            RegistryEntryLookup<DoublePerlinNoiseSampler.NoiseParameters> lookup, long seed, CallbackInfo ci) {
        greatsea$worldSeed = seed;
        NoiseConfig noise=(NoiseConfig)(Object)this;
        SeaBiomeContext.register(noise.getMultiNoiseSampler(),seed,UnstableWorldGen.isUnstable(settings));
        VanillaOceanTerrain.register(seed,noise);
    }
    @Override public long greatsea$seed() { return greatsea$worldSeed; }
}
