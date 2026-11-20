package dev.greatsea.mixin;
import dev.greatsea.*;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(ChunkGenerator.class)
public abstract class MistFeaturesMixin {
 @Inject(method="generateFeatures",at=@At("RETURN"))
 private void mist$snow(StructureWorldAccess world,Chunk chunk,StructureAccessor structures,CallbackInfo ci){
  if(UnstableWorldGen.isUnstable((ChunkGenerator)(Object)this))MistTerrain.finish(chunk,world);
 }
}
