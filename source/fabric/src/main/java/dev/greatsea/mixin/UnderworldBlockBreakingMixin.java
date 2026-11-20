package dev.greatsea.mixin;

import dev.greatsea.UnderworldProtection;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.s2c.play.BlockUpdateS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.network.ServerPlayerInteractionManager;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Authoritative rejection also covers tagged tools and forged mining packets. */
@Mixin(ServerPlayerInteractionManager.class)
public abstract class UnderworldBlockBreakingMixin {
    @Shadow @Final protected ServerPlayerEntity player;
    @Inject(method="processBlockBreakingAction",at=@At("HEAD"),cancellable=true)
    private void greatsea$rejectUnderworldMining(BlockPos pos,PlayerActionC2SPacket.Action action,Direction side,int worldHeight,int sequence,CallbackInfo ci) {
        if (!UnderworldProtection.protectedPlayer(player)) return;
        if (player.networkHandler!=null) player.networkHandler.sendPacket(new BlockUpdateS2CPacket(player.getEntityWorld(),pos));
        ci.cancel();
    }
    @Inject(method="tryBreakBlock",at=@At("HEAD"),cancellable=true)
    private void greatsea$rejectUnderworldBreak(BlockPos pos,CallbackInfoReturnable<Boolean> cir) {
        if (UnderworldProtection.protectedPlayer(player)) cir.setReturnValue(false);
    }
}
