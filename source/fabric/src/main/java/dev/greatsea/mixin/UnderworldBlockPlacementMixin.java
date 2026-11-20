package dev.greatsea.mixin;

import dev.greatsea.UnderworldProtection;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemPlacementContext;
import net.minecraft.util.ActionResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Reject placement before CanPlaceOn components or custom block placement logic. */
@Mixin(BlockItem.class)
public abstract class UnderworldBlockPlacementMixin {
    @Inject(method="place(Lnet/minecraft/item/ItemPlacementContext;)Lnet/minecraft/util/ActionResult;",at=@At("HEAD"),cancellable=true)
    private void greatsea$rejectUnderworldPlacement(ItemPlacementContext context,CallbackInfoReturnable<ActionResult> cir) {
        if (UnderworldProtection.protectedPlayer(context.getPlayer())) cir.setReturnValue(ActionResult.FAIL);
    }
}
