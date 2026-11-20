package dev.greatsea.mixin;

import dev.greatsea.FarlandsMechanics;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FireworkRocketItem;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Refuse the flight use before consuming a rocket; decorative fireworks remain usable. */
@Mixin(FireworkRocketItem.class)
public abstract class FarlandsFireworkItemMixin {
    @Inject(method="use",at=@At("HEAD"),cancellable=true)
    private void greatsea$disableDeepFarlandsBoost(World world,PlayerEntity player,Hand hand,CallbackInfoReturnable<ActionResult> cir) {
        if (player.isGliding() && FarlandsMechanics.fireworksDisabled(player)) cir.setReturnValue(ActionResult.FAIL);
    }
}
