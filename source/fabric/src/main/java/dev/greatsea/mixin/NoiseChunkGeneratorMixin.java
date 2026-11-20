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
import net.minecraft.block.Blocks;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;

@Mixin(NoiseChunkGenerator.class)
public abstract class NoiseChunkGeneratorMixin {
    private boolean greatsea$handles(Chunk chunk) {
        return SeaGeneration.supported((NoiseChunkGenerator)(Object)this) && SeaGeneration.intersects(chunk);
    }
    @Inject(method="populateNoise(Lnet/minecraft/world/gen/chunk/Blender;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/chunk/Chunk;)Ljava/util/concurrent/CompletableFuture;", at=@At("HEAD"),cancellable=true)
    private void greatsea$generate(Blender blender,NoiseConfig noise,StructureAccessor structures,Chunk chunk,
            CallbackInfoReturnable<CompletableFuture<Chunk>> cir) {
        if (greatsea$handles(chunk) && !SeaGeneration.needsVanilla(chunk,((SeedAccess)(Object)noise).greatsea$seed())) {
            long seed=((SeedAccess)(Object)noise).greatsea$seed();
            cir.setReturnValue(CompletableFuture.supplyAsync(() -> SeaGeneration.fill(chunk,seed,false),Util.getMainWorkerExecutor()));
        }
    }
    @Inject(method="populateNoise(Lnet/minecraft/world/gen/chunk/Blender;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/chunk/Chunk;)Ljava/util/concurrent/CompletableFuture;", at=@At("RETURN"),cancellable=true)
    private void greatsea$blendEdges(Blender blender,NoiseConfig noise,StructureAccessor structures,Chunk chunk,
            CallbackInfoReturnable<CompletableFuture<Chunk>> cir) {
        if (greatsea$handles(chunk) && SeaGeneration.needsVanilla(chunk,((SeedAccess)(Object)noise).greatsea$seed())) {
            long seed=((SeedAccess)(Object)noise).greatsea$seed();
            cir.setReturnValue(cir.getReturnValue().thenApply(c -> SeaGeneration.fill(c,seed,true)));
        }
    }
    @Inject(method="buildSurface(Lnet/minecraft/world/ChunkRegion;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/chunk/Chunk;)V",at=@At("HEAD"),cancellable=true)
    private void greatsea$surface(ChunkRegion region,StructureAccessor structures,NoiseConfig noise,Chunk chunk,CallbackInfo ci) {
        if (greatsea$handles(chunk) && !SeaGeneration.needsVanilla(chunk,((SeedAccess)(Object)noise).greatsea$seed())) ci.cancel();
    }
    @Inject(method="buildSurface(Lnet/minecraft/world/ChunkRegion;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/chunk/Chunk;)V",at=@At("RETURN"))
    private void greatsea$edgeSurface(ChunkRegion region,StructureAccessor structures,NoiseConfig noise,Chunk chunk,CallbackInfo ci) {
        if (greatsea$handles(chunk) && SeaGeneration.needsVanilla(chunk,((SeedAccess)(Object)noise).greatsea$seed())) SeaGeneration.finishShoreSurface(chunk);
    }
    @Redirect(method="buildSurface(Lnet/minecraft/world/ChunkRegion;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/chunk/Chunk;)V",
            at=@At(value="INVOKE",target="Lnet/minecraft/world/gen/chunk/NoiseChunkGenerator;buildSurface(Lnet/minecraft/world/chunk/Chunk;Lnet/minecraft/world/gen/HeightContext;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/biome/source/BiomeAccess;Lnet/minecraft/registry/Registry;Lnet/minecraft/world/gen/chunk/Blender;)V"))
    private void greatsea$nativeMainlandSkin(NoiseChunkGenerator generator,Chunk chunk,
            net.minecraft.world.gen.HeightContext context,NoiseConfig noise,StructureAccessor structures,BiomeAccess access,
            net.minecraft.registry.Registry<net.minecraft.world.biome.Biome> biomes,Blender blender) {
        if (greatsea$handles(chunk) && SeaGeneration.needsVanilla(chunk,((SeedAccess)(Object)noise).greatsea$seed()))
            access=SeaGeneration.nativeSurfaceBiomes(access,generator.getBiomeSource(),noise.getMultiNoiseSampler());
        generator.buildSurface(chunk,context,noise,structures,access,biomes,blender);
    }
    @Inject(method="carve",at=@At("HEAD"),cancellable=true)
    private void greatsea$noCaves(ChunkRegion region,long seed,NoiseConfig noise,BiomeAccess biomes,StructureAccessor structures,Chunk chunk,CallbackInfo ci) {
        if (greatsea$handles(chunk)) ci.cancel();
    }
    @Inject(method="getHeight",at=@At("RETURN"),cancellable=true)
    private void greatsea$height(int x,int z,Heightmap.Type type,HeightLimitView world,NoiseConfig noise,CallbackInfoReturnable<Integer> cir) {
        if (!SeaGeneration.supported((NoiseChunkGenerator)(Object)this) || !SeaTerrain.contains(GreatSea.CONFIG,x,z)) return;
        long seed=((SeedAccess)(Object)noise).greatsea$seed();
        SeaConfig c=GreatSea.CONFIG;
        boolean ocean=SeaTerrain.deepFloor(c,seed,x,z);
        double profile=SeaTerrain.height(c,seed,x,z,cir.getReturnValue()-1,
                ocean ? VanillaOceanTerrain.floorHeight(c,seed,x,z) : SeaTerrain.floorBaseline(c));
        int top=(int)Math.round(profile);
        boolean wet=SeaTerrain.shoreDistance(GreatSea.CONFIG,((SeedAccess)(Object)noise).greatsea$seed(),x,z)>=GreatSea.CONFIG.beachWidth;
        if (!wet) {
            int originalTop=cir.getReturnValue()-1;
            int ceiling=(int)Math.round(MainlandJoin.ceiling(c,originalTop,profile));
            if (originalTop<=ceiling) top=Math.max(top,originalTop);
            else if (top<ceiling) {
                var original=SeaGeneration.nativeQuery(()->((NoiseChunkGenerator)(Object)this).getColumnSample(x,z,world,noise));
                for (int y=ceiling;y>top;y--) if (type.getBlockPredicate().test(original.getState(y))) {
                    top=y;break;
                }
            }
        }
        if (wet && (type==Heightmap.Type.WORLD_SURFACE || type==Heightmap.Type.WORLD_SURFACE_WG || type==Heightmap.Type.MOTION_BLOCKING || type==Heightmap.Type.MOTION_BLOCKING_NO_LEAVES))
            top=Math.max(top,GreatSea.CONFIG.seaLevel-1);
        cir.setReturnValue(top+1);
    }
    @Inject(method="getColumnSample",at=@At("RETURN"),cancellable=true)
    private void greatsea$column(int x,int z,HeightLimitView world,NoiseConfig noise,CallbackInfoReturnable<VerticalBlockSample> cir) {
        if (!SeaGeneration.supported((NoiseChunkGenerator)(Object)this) || !SeaTerrain.contains(GreatSea.CONFIG,x,z)) return;
        long seed=((SeedAccess)(Object)noise).greatsea$seed();
        int bottom=world.getBottomY(),old=bottom;
        for (int y=bottom+world.getHeight()-1;y>=bottom;y--)
            if (cir.getReturnValue().getState(y).isSolid()) { old=y; break; }
        SeaConfig c=GreatSea.CONFIG;
        boolean ocean=SeaTerrain.deepFloor(c,seed,x,z);
        boolean gravel=ocean && OceanFloorMaterials.gravel(c,seed,x,z);
        double profile=SeaTerrain.height(c,seed,x,z,old,
                ocean ? VanillaOceanTerrain.floorHeight(c,seed,x,z) : SeaTerrain.floorBaseline(c));
        int top=(int)Math.round(profile);
        int ceiling=(int)Math.round(MainlandJoin.ceiling(c,old,profile));
        boolean dry=SeaTerrain.shoreDistance(GreatSea.CONFIG,seed,x,z)<GreatSea.CONFIG.beachWidth;
        BlockState[] states=new BlockState[world.getHeight()];
        for (int i=0;i<states.length;i++) {
            int y=i+bottom;
            states[i]=dry && y>top ? (y<=ceiling ? cir.getReturnValue().getState(y) : Blocks.AIR.getDefaultState()) :
                    ocean ? VanillaOceanTerrain.state(c,seed,x,y,z,top,bottom,gravel) : SeaGeneration.state(seed,x,y,z,top,bottom);
        }
        cir.setReturnValue(new VerticalBlockSample(bottom,states));
    }
}
