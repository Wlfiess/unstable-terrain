package dev.greatsea.mixin;

import dev.greatsea.SeaBiomeContext;
import dev.greatsea.SeedAccess;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import net.minecraft.world.gen.chunk.AquiferSampler;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.ChunkNoiseSampler;
import net.minecraft.world.gen.chunk.GenerationShapeConfig;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.noise.NoiseRouter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.List;

/** Chunk biome generation uses a derived climate sampler, separate from the world's sampler. */
@Mixin(ChunkNoiseSampler.class)
public abstract class ChunkNoiseSamplerMixin {
    @Unique private long greatsea$seed;
    @Unique private boolean greatsea$unstable;
    @Inject(method="<init>",at=@At("TAIL"))
    private void greatsea$rememberContext(int horizontalCellCount,NoiseConfig noise,int startBlockX,int startBlockZ,
            GenerationShapeConfig shape,DensityFunctionTypes.Beardifying beardifying,ChunkGeneratorSettings settings,
            AquiferSampler.FluidLevelSampler fluid,Blender blender,CallbackInfo ci) {
        greatsea$seed=((SeedAccess)(Object)noise).greatsea$seed();
        greatsea$unstable=SeaBiomeContext.unstable(noise.getMultiNoiseSampler());
    }
    @Inject(method="createMultiNoiseSampler",at=@At("RETURN"))
    private void greatsea$propagateContext(NoiseRouter router,List<MultiNoiseUtil.NoiseHypercube> spawnTarget,
            CallbackInfoReturnable<MultiNoiseUtil.MultiNoiseSampler> cir) {
        SeaBiomeContext.register(cir.getReturnValue(),greatsea$seed,greatsea$unstable);
    }
}
