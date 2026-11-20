package dev.greatsea.mixin;

import dev.greatsea.FarlandsCoordinateData;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class FarlandsCoordinateCopyMixin {
    @Inject(method="copyFrom",at=@At("RETURN"))
    private void greatsea$copyCoordinateOrigin(ServerPlayerEntity oldPlayer,boolean alive,CallbackInfo ci) {
        ((FarlandsCoordinateData)this).greatsea$coordinateOrigin(((FarlandsCoordinateData)oldPlayer).greatsea$coordinateOrigin());
    }
}
