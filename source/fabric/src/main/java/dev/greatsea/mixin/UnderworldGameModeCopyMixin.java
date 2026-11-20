package dev.greatsea.mixin;

import dev.greatsea.UnderworldGameModeData;
import net.minecraft.server.network.ServerPlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayerEntity.class)
public abstract class UnderworldGameModeCopyMixin {
    @Inject(method="copyFrom",at=@At("RETURN"))
    private void greatsea$copyUnderworldMode(ServerPlayerEntity oldPlayer,boolean alive,CallbackInfo ci){
        ((UnderworldGameModeData)this).greatsea$underworldAdventure(((UnderworldGameModeData)oldPlayer).greatsea$underworldAdventure());
    }
}
