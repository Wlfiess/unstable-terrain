package dev.greatsea.mixin;

import dev.greatsea.UnderworldGameModeData;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.storage.ReadView;
import net.minecraft.storage.WriteView;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerEntity.class)
public abstract class UnderworldGameModeDataMixin implements UnderworldGameModeData {
    @Unique private boolean greatsea$automaticUnderworldAdventure;
    @Override public boolean greatsea$underworldAdventure(){return greatsea$automaticUnderworldAdventure;}
    @Override public void greatsea$underworldAdventure(boolean automatic){greatsea$automaticUnderworldAdventure=automatic;}
    @Inject(method="readCustomData",at=@At("RETURN"))
    private void greatsea$loadUnderworldMode(ReadView view,CallbackInfo ci){
        greatsea$automaticUnderworldAdventure=view.getBoolean("unstableterrain.UnderworldAdventure",false);
    }
    @Inject(method="writeCustomData",at=@At("RETURN"))
    private void greatsea$saveUnderworldMode(WriteView view,CallbackInfo ci){
        view.putBoolean("unstableterrain.UnderworldAdventure",greatsea$automaticUnderworldAdventure);
    }
}
