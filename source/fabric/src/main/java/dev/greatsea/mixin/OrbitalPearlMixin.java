package dev.greatsea.mixin;

import dev.greatsea.OrbitalRuntime;
import net.minecraft.entity.projectile.thrown.EnderPearlEntity;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.hit.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EnderPearlEntity.class)
public abstract class OrbitalPearlMixin {
    @Inject(method="onCollision",at=@At("HEAD"))
    private void unstableterrain$pearl(HitResult hit,CallbackInfo callback){
        if(((EnderPearlEntity)(Object)this).getOwner() instanceof ServerPlayerEntity player)
            OrbitalRuntime.beforePull(player);
    }
}
