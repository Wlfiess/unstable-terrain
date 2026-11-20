package dev.greatsea.mixin;

import dev.greatsea.UnderworldProtection;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Also runs on the client, so mining/placement prediction follows the local restriction. */
@Mixin(PlayerEntity.class)
public abstract class UnderworldPlayerProtectionMixin {
    @Inject(method="canModifyBlocks",at=@At("HEAD"),cancellable=true)
    private void greatsea$underworldBuilding(CallbackInfoReturnable<Boolean> cir) {
        if (UnderworldProtection.protectedPlayer((PlayerEntity)(Object)this)) cir.setReturnValue(false);
    }
    @Inject(method="canPlaceOn",at=@At("HEAD"),cancellable=true)
    private void greatsea$underworldPlacement(BlockPos pos,Direction side,ItemStack stack,CallbackInfoReturnable<Boolean> cir) {
        if (UnderworldProtection.protectedPlayer((PlayerEntity)(Object)this)) cir.setReturnValue(false);
    }
}
