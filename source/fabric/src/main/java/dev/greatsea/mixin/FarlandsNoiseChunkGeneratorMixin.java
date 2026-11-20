package dev.greatsea.mixin;

import dev.greatsea.*;
import net.minecraft.util.Util;
import net.minecraft.world.ChunkRegion;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.Heightmap;
import net.minecraft.world.biome.source.BiomeAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.Blender;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.chunk.VerticalBlockSample;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.block.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;

@Mixin(NoiseChunkGenerator.class)
public abstract class FarlandsNoiseChunkGeneratorMixin {
    private boolean unstableterrain$handles(Chunk chunk) {
        return GreatSea.FARLANDS.enabled && FarlandsWorld.supported((NoiseChunkGenerator)(Object)this) && FarlandsGeneration.intersects(chunk);
    }
    @Inject(method="populateNoise(Lnet/minecraft/world/gen/chunk/Blender;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/chunk/Chunk;)Ljava/util/concurrent/CompletableFuture;",at=@At("HEAD"),cancellable=true)
    private void unstableterrain$fill(Blender blender,NoiseConfig noise,StructureAccessor structures,Chunk chunk,CallbackInfoReturnable<CompletableFuture<Chunk>> cir) {
        if(unstableterrain$handles(chunk) && FarlandsGeneration.fullyManaged(chunk)) {
            long seed=((SeedAccess)(Object)noise).greatsea$seed();
            cir.setReturnValue(CompletableFuture.supplyAsync(()->FarlandsGeneration.fill(chunk,seed),Util.getMainWorkerExecutor()));
        }
    }
    @Inject(method="populateNoise(Lnet/minecraft/world/gen/chunk/Blender;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/chunk/Chunk;)Ljava/util/concurrent/CompletableFuture;",at=@At("RETURN"),cancellable=true)
    private void unstableterrain$edge(Blender blender,NoiseConfig noise,StructureAccessor structures,Chunk chunk,CallbackInfoReturnable<CompletableFuture<Chunk>> cir) {
        if(unstableterrain$handles(chunk) && !FarlandsGeneration.fullyManaged(chunk)) {
            long seed=((SeedAccess)(Object)noise).greatsea$seed();
            cir.setReturnValue(cir.getReturnValue().thenApply(c->FarlandsGeneration.fill(c,seed)));
        }
    }
    @Inject(method="buildSurface(Lnet/minecraft/world/ChunkRegion;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/chunk/Chunk;)V",at=@At("HEAD"),cancellable=true)
    private void unstableterrain$surface(ChunkRegion region,StructureAccessor structures,NoiseConfig noise,Chunk chunk,CallbackInfo ci) {
        if(unstableterrain$handles(chunk) && FarlandsGeneration.fullyManaged(chunk))ci.cancel();
    }
    @Inject(method="buildSurface(Lnet/minecraft/world/ChunkRegion;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/chunk/Chunk;)V",at=@At("RETURN"))
    private void unstableterrain$edgeSurface(ChunkRegion region,StructureAccessor structures,NoiseConfig noise,Chunk chunk,CallbackInfo ci) {
        if(unstableterrain$handles(chunk) && !FarlandsGeneration.fullyManaged(chunk))FarlandsGeneration.fill(chunk,((SeedAccess)(Object)noise).greatsea$seed());
    }
    @Inject(method="carve",at=@At("HEAD"),cancellable=true)
    private void unstableterrain$carve(ChunkRegion region,long seed,NoiseConfig noise,BiomeAccess biomes,StructureAccessor structures,Chunk chunk,CallbackInfo ci) {
        if(unstableterrain$handles(chunk))ci.cancel();
    }
    @Inject(method="getColumnSample",at=@At("HEAD"),cancellable=true)
    private void unstableterrain$column(int x,int z,HeightLimitView world,NoiseConfig noise,CallbackInfoReturnable<VerticalBlockSample> cir) {
        if(!FarlandsWorld.supported((NoiseChunkGenerator)(Object)this)||!FarlandsWorld.contains(x,z))return;
        long seed=((SeedAccess)(Object)noise).greatsea$seed();
        BlockState[] states=new BlockState[world.getHeight()];
        for(int i=0;i<states.length;i++)states[i]=FarlandsGeneration.state(seed,x,world.getBottomY()+i,z);
        cir.setReturnValue(new VerticalBlockSample(world.getBottomY(),states));
    }
    @Inject(method="getHeight",at=@At("HEAD"),cancellable=true)
    private void unstableterrain$height(int x,int z,Heightmap.Type type,HeightLimitView world,NoiseConfig noise,CallbackInfoReturnable<Integer> cir) {
        if(!FarlandsWorld.supported((NoiseChunkGenerator)(Object)this)||!FarlandsWorld.contains(x,z))return;
        long seed=((SeedAccess)(Object)noise).greatsea$seed();
        int top=world.getBottomY()-1;
        for(int y=world.getTopYInclusive();y>=world.getBottomY();y--)if(type.getBlockPredicate().test(FarlandsGeneration.state(seed,x,y,z))){top=y;break;}
        cir.setReturnValue(top+1);
    }
}
