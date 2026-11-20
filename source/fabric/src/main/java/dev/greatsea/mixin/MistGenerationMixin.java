package dev.greatsea.mixin;
import dev.greatsea.*;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.Heightmap;
import net.minecraft.world.HeightLimitView;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.*;
import net.minecraft.world.gen.noise.NoiseConfig;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.*;
import java.util.concurrent.CompletableFuture;
@Mixin(NoiseChunkGenerator.class)
public abstract class MistGenerationMixin {
 @Inject(method="populateNoise(Lnet/minecraft/world/gen/chunk/Blender;Lnet/minecraft/world/gen/noise/NoiseConfig;Lnet/minecraft/world/gen/StructureAccessor;Lnet/minecraft/world/chunk/Chunk;)Ljava/util/concurrent/CompletableFuture;",at=@At("RETURN"),cancellable=true)
 private void mist$freeze(Blender blender,NoiseConfig noise,StructureAccessor structures,Chunk chunk,CallbackInfoReturnable<CompletableFuture<Chunk>> cir){
  if(!UnstableWorldGen.isUnstable((NoiseChunkGenerator)(Object)this)||!MistRegion.intersects(chunk.getPos().getStartX(),chunk.getPos().getStartZ()))return;
  long seed=((SeedAccess)(Object)noise).greatsea$seed();cir.setReturnValue(cir.getReturnValue().thenApply(c->MistTerrain.freeze(c,seed)));
 }
 @Inject(method="getColumnSample",at=@At("RETURN"),cancellable=true)
 private void mist$column(int x,int z,HeightLimitView world,NoiseConfig noise,CallbackInfoReturnable<VerticalBlockSample> cir){
  if(!UnstableWorldGen.isUnstable((NoiseChunkGenerator)(Object)this)||!MistRegion.frost(((SeedAccess)(Object)noise).greatsea$seed(),x,z))return;
  cir.setReturnValue(new VerticalBlockSample(world.getBottomY(),MistTerrain.column(cir.getReturnValue(),world.getBottomY(),world.getHeight())));
 }
 @Inject(method="getHeight",at=@At("RETURN"),cancellable=true)
 private void mist$height(int x,int z,Heightmap.Type type,HeightLimitView world,NoiseConfig noise,CallbackInfoReturnable<Integer> cir){
  if(type!=Heightmap.Type.OCEAN_FLOOR&&type!=Heightmap.Type.OCEAN_FLOOR_WG)return;
  var g=(NoiseChunkGenerator)(Object)this;if(!UnstableWorldGen.isUnstable(g)||!MistRegion.frost(((SeedAccess)(Object)noise).greatsea$seed(),x,z))return;
  var col=g.getColumnSample(x,z,world,noise);for(int y=world.getTopYInclusive();y>=world.getBottomY();y--)if(type.getBlockPredicate().test(col.getState(y))){cir.setReturnValue(y+1);return;}
 }
}
