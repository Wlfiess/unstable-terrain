package dev.greatsea.mixin;

import dev.greatsea.OrbitalRuntime;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.FishingRodItem;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.ActionResult;
import net.minecraft.util.Hand;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(FishingRodItem.class)
public abstract class OrbitalRodMixin {
    @Inject(method="use",at=@At("HEAD"),cancellable=true)
    private void unstableterrain$rod(World world,PlayerEntity player,Hand hand,CallbackInfoReturnable<ActionResult> result){
        if(player instanceof ServerPlayerEntity server&&OrbitalRuntime.rod(server,hand,player.fishHook==null))
            result.setReturnValue(ActionResult.SUCCESS);
    }
}
