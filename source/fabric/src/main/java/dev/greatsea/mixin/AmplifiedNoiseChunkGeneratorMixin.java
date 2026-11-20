package dev.greatsea.mixin;

import dev.greatsea.AmplifiedTerrain;
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
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.concurrent.CompletableFuture;

@Mixin(NoiseChunkGenerator.class)
public abstract class AmplifiedNoiseChunkGeneratorMixin {
    private AmplifiedTerrain.Delegate unstableterrain$delegate(NoiseConfig noise,Chunk chunk) {
        return AmplifiedTerrain.contains(chunk)?AmplifiedTerrain.delegate((NoiseChunkGenerator)(Object)this,noise):null;
    }
    @Inject(method="populateBiomes",at=@At("HEAD"),cancellable=true)
    private void unstableterrain$biomes(NoiseConfig noise,Blender blender,StructureAccessor structures,Chunk chunk,CallbackInfoReturnable<CompletableFuture<Chunk>> cir) {
        var d=unstableterrain$delegate(noise,chunk);if(d!=null)cir.setReturnValue(d.generator().populateBiomes(d.noise(),blender,structures,chunk));
    }
    @Inject(method="populateNoise(Lnet/minecraft/world/gen/chunk/Blender;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/chunk/Chunk;)Ljava/util/concurrent/CompletableFuture;",at=@At("HEAD"),cancellable=true)
    private void unstableterrain$noise(Blender blender,NoiseConfig noise,StructureAccessor structures,Chunk chunk,CallbackInfoReturnable<CompletableFuture<Chunk>> cir) {
        var d=unstableterrain$delegate(noise,chunk);if(d!=null)cir.setReturnValue(d.generator().populateNoise(blender,d.noise(),structures,chunk));
    }
    @Inject(method="buildSurface(Lnet/minecraft/world/ChunkRegion;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/chunk/Chunk;)V",at=@At("HEAD"),cancellable=true)
    private void unstableterrain$surface(ChunkRegion region,StructureAccessor structures,NoiseConfig noise,Chunk chunk,CallbackInfo ci) {
        var d=unstableterrain$delegate(noise,chunk);if(d!=null){d.generator().buildSurface(region,structures,d.noise(),chunk);ci.cancel();}
    }
    @Inject(method="carve",at=@At("HEAD"),cancellable=true)
    private void unstableterrain$carve(ChunkRegion region,long seed,NoiseConfig noise,BiomeAccess biomes,StructureAccessor structures,Chunk chunk,CallbackInfo ci) {
        var d=unstableterrain$delegate(noise,chunk);if(d!=null){d.generator().carve(region,seed,d.noise(),biomes,structures,chunk);ci.cancel();}
    }
    @Inject(method="getHeight",at=@At("HEAD"),cancellable=true)
    private void unstableterrain$height(int x,int z,Heightmap.Type type,HeightLimitView world,NoiseConfig noise,CallbackInfoReturnable<Integer> cir) {
        if(!AmplifiedTerrain.contains(x,z))return;
        var d=AmplifiedTerrain.delegate((NoiseChunkGenerator)(Object)this,noise);
        if(d!=null)cir.setReturnValue(d.generator().getHeight(x,z,type,world,d.noise()));
    }
    @Inject(method="getColumnSample",at=@At("HEAD"),cancellable=true)
    private void unstableterrain$column(int x,int z,HeightLimitView world,NoiseConfig noise,CallbackInfoReturnable<VerticalBlockSample> cir) {
        if(!AmplifiedTerrain.contains(x,z))return;
        var d=AmplifiedTerrain.delegate((NoiseChunkGenerator)(Object)this,noise);
        if(d!=null)cir.setReturnValue(d.generator().getColumnSample(x,z,world,d.noise()));
    }
}
