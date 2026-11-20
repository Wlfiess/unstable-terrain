package dev.greatsea.mixin;

import dev.greatsea.UnstableNether;
import net.minecraft.block.BlockState;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.ChunkRegion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Vanilla Nether features and structures cannot spill into the void gap or Underworld. */
@Mixin(ChunkRegion.class)
public abstract class UnderworldChunkRegionMixin {
    @Inject(method="setBlockState",at=@At("HEAD"),cancellable=true)
    private void greatsea$protectUnderworld(BlockPos pos,BlockState state,int flags,int maxUpdateDepth,CallbackInfoReturnable<Boolean> cir) {
        if (pos.getY()<0 && UnstableNether.supported(((ChunkRegion)(Object)this).toServerWorld())) cir.setReturnValue(false);
    }
}
