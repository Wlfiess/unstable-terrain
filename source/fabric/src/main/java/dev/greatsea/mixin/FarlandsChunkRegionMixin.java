package dev.greatsea.mixin;

import dev.greatsea.*;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ChunkRegion.class)
public abstract class FarlandsChunkRegionMixin {
    @Inject(method="setBlockState",at=@At("HEAD"),cancellable=true)
    private void unstableterrain$voidChunks(BlockPos pos,BlockState state,int flags,int maxUpdateDepth,CallbackInfoReturnable<Boolean> cir) {
        var world=((ChunkRegion)(Object)this).toServerWorld();
        if(!FarlandsWorld.supported(world)||!FarlandsWorld.contains(pos.getX(),pos.getZ()))return;
        if(!SpokelandsTerrain.contains(pos.getX(),pos.getZ())&&FarlandsEmpire.contains(pos.getX(),pos.getY(),pos.getZ())){cir.setReturnValue(false);return;}
        // Structure pieces have their own bounds and clipping. The corrupted terrain
        // mask may erase surrounding land, never the rooms or corridors of a chamber.
        if(FarlandsStructures.placing())return;
        if(FarlandsDecoration.blocksFeatureWrite(world.getSeed(),pos.getX(),pos.getZ(),FarlandsDecoration.isGenerating(),FarlandsStructures.placing()))cir.setReturnValue(false);
    }
}
