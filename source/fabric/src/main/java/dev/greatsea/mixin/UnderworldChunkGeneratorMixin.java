package dev.greatsea.mixin;

import dev.greatsea.UnderworldTerrain;
import dev.greatsea.UnstableNether;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.StructureAccessor;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ChunkGenerator.class)
public abstract class UnderworldChunkGeneratorMixin {
    @Inject(method="generateFeatures",at=@At("RETURN"))
    private void greatsea$underworldFeatures(StructureWorldAccess world,Chunk chunk,StructureAccessor structures,CallbackInfo ci) {
        if (UnstableNether.supported((ChunkGenerator)(Object)this)) UnderworldTerrain.decorate(chunk,world);
    }
}
