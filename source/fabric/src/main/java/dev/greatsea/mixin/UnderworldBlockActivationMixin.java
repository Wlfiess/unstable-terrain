package dev.greatsea.mixin;

import dev.greatsea.UnderworldProtection;
import net.minecraft.block.AbstractBlock;
import net.minecraft.block.BlockState;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Covers blocks that handle placement/mutation directly, before the item-use branch. */
@Mixin(AbstractBlock.AbstractBlockState.class)
public abstract class UnderworldBlockActivationMixin {
    @Inject(method="onUseWithItem",at=@At("HEAD"),cancellable=true)
    private void greatsea$rejectUnderworldMutation(ItemStack stack,World world,PlayerEntity player,Hand hand,BlockHitResult hit,
                                                 CallbackInfoReturnable<ActionResult> cir) {
        if (UnderworldProtection.protectedPlayer(player) &&
                UnderworldProtection.changesBlockActivation(stack,((BlockState)(Object)this).getBlock())) cir.setReturnValue(ActionResult.FAIL);
    }
}
