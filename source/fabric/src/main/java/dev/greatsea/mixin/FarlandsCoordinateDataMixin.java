package dev.greatsea.mixin;

import dev.greatsea.FarlandsCoordinateData;
import dev.greatsea.FarlandsCoordinates;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class FarlandsCoordinateDataMixin implements FarlandsCoordinateData {
    @Unique private FarlandsCoordinates.Origin greatsea$displayOrigin;
    @Override public FarlandsCoordinates.Origin greatsea$coordinateOrigin() {return greatsea$displayOrigin;}
    @Override public void greatsea$coordinateOrigin(FarlandsCoordinates.Origin origin) {greatsea$displayOrigin=origin;}
    @Inject(method="readCustomData",at=@At("RETURN"))
    private void greatsea$loadCoordinates(ReadView view,CallbackInfo ci) {
        greatsea$displayOrigin=view.getBoolean("unstableterrain.FarlandsCoordinateOrigin",false)?
                new FarlandsCoordinates.Origin(view.getLong("unstableterrain.FarlandsOriginX",0),
                        view.getLong("unstableterrain.FarlandsOriginZ",0)):null;
    }
    @Inject(method="writeCustomData",at=@At("RETURN"))
    private void greatsea$saveCoordinates(WriteView view,CallbackInfo ci) {
        view.putBoolean("unstableterrain.FarlandsCoordinateOrigin",greatsea$displayOrigin!=null);
        if(greatsea$displayOrigin!=null) {
            view.putLong("unstableterrain.FarlandsOriginX",greatsea$displayOrigin.x());
            view.putLong("unstableterrain.FarlandsOriginZ",greatsea$displayOrigin.z());
        }
    }
}
