package dev.greatsea.mixin;

import dev.greatsea.UnderworldProtection;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.item.ItemUsageContext;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Native container/block activation occurs before useOnBlock, and remains usable. */
@Mixin(ItemStack.class)
public abstract class UnderworldItemProtectionMixin {
    @Inject(method="useOnBlock",at=@At("HEAD"),cancellable=true)
    private void greatsea$rejectUnderworldBlockItems(ItemUsageContext context,CallbackInfoReturnable<ActionResult> cir) {
        if (UnderworldProtection.protectedPlayer(context.getPlayer()) &&
                UnderworldProtection.changesBlocks(((ItemStack)(Object)this).getItem())) cir.setReturnValue(ActionResult.FAIL);
    }
    @Inject(method="use",at=@At("HEAD"),cancellable=true)
    private void greatsea$rejectUnderworldBucketUse(World world,PlayerEntity player,Hand hand,CallbackInfoReturnable<ActionResult> cir) {
        if (UnderworldProtection.protectedPlayer(player) &&
                UnderworldProtection.changesBlocks(((ItemStack)(Object)this).getItem())) cir.setReturnValue(ActionResult.FAIL);
    }
}
