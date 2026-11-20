package dev.greatsea.mixin;

import dev.greatsea.FarlandsData;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class FarlandsRespawnDataMixin {
    @Inject(method="copyFrom",at=@At("RETURN"))
    private void greatsea$copyFarlandsInventory(ServerPlayerEntity oldPlayer,boolean alive,CallbackInfo ci) {
        ((FarlandsData)this).greatsea$copyFarlandsData((FarlandsData)oldPlayer);
    }
}
