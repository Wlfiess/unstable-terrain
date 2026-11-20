package dev.greatsea.mixin;

import dev.greatsea.SeedAccess;
import dev.greatsea.UnderworldTerrain;
import dev.greatsea.UnstableNether;
import net.minecraft.block.BlockState;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.chunk.Chunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(NoiseChunkGenerator.class)
public abstract class UnderworldNoiseChunkGeneratorMixin {
    @Inject(method="buildSurface(Lnet/minecraft/world/ChunkRegion;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/chunk/Chunk;)V",at=@At("RETURN"))
    private void greatsea$underworldSurface(ChunkRegion region,StructureAccessor structures,NoiseConfig noise,Chunk chunk,CallbackInfo ci) {
        if (UnstableNether.supported((NoiseChunkGenerator)(Object)this))
            UnderworldTerrain.fill(chunk,((SeedAccess)(Object)noise).greatsea$seed());
    }
    @Inject(method="getColumnSample",at=@At("RETURN"),cancellable=true)
    private void greatsea$underworldColumn(int x,int z,HeightLimitView world,NoiseConfig noise,CallbackInfoReturnable<VerticalBlockSample> cir) {
        if (!UnstableNether.supported((NoiseChunkGenerator)(Object)this)) return;
        long seed=((SeedAccess)(Object)noise).greatsea$seed();
        int bottom=world.getBottomY();
        BlockState[] states=new BlockState[world.getHeight()];
        for (int i=0;i<states.length;i++) {
            int y=bottom+i;
            states[i]=y<0 ? UnderworldTerrain.state(seed,x,y,z) : cir.getReturnValue().getState(y);
        }
        cir.setReturnValue(new VerticalBlockSample(bottom,states));
    }
}
