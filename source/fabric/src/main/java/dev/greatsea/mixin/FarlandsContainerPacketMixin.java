package dev.greatsea.mixin;

import dev.greatsea.FarlandsMechanics;
import net.minecraft.network.packet.c2s.play.ClickSlotC2SPacket;
import net.minecraft.server.network.ServerPlayNetworkHandler;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Reject stale ender-chest clicks even if movement crossed the boundary between ticks. */
@Mixin(ServerPlayNetworkHandler.class)
public abstract class FarlandsContainerPacketMixin {
    @Shadow public ServerPlayerEntity player;
    @Inject(method="onClickSlot",at=@At("HEAD"),cancellable=true)
    private void greatsea$rejectPreviousRegionClick(ClickSlotC2SPacket packet,CallbackInfo ci) {
        // Vanilla reschedules Netty callbacks on the server thread; check on that invocation.
        if (player.getEntityWorld().getServer().isOnThread() && FarlandsMechanics.closeStaleEnderMenu(player)) ci.cancel();
    }
}
