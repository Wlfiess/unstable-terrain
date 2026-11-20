package dev.greatsea.mixin;

import dev.greatsea.FarlandsMechanics;
import dev.greatsea.FarlandsWorld;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** A handler already open at the boundary must not keep exposing the previous chest. */
@Mixin(ServerPlayerEntity.class)
public abstract class FarlandsRegionMenuMixin {
    @Unique private boolean greatsea$regionInitialized;
    @Unique private boolean greatsea$previousFarlands;
    @Inject(method="tick",at=@At("HEAD"))
    private void greatsea$closePreviousRegionMenu(CallbackInfo ci) {
        var player=(ServerPlayerEntity)(Object)this;
        FarlandsMechanics.closeStaleEnderMenu(player);
        boolean current=FarlandsWorld.activePlayer(player);
        if (greatsea$regionInitialized) FarlandsMechanics.closeRegionMenu(player,greatsea$previousFarlands,current);
        greatsea$previousFarlands=current;
        greatsea$regionInitialized=true;
    }
}
