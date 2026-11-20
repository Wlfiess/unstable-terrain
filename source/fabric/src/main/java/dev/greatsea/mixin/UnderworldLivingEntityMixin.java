package dev.greatsea.mixin;

import dev.greatsea.UnderworldEntry;
import dev.greatsea.UnstableNether;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LivingEntity.class)
public abstract class UnderworldLivingEntityMixin {
    @Inject(method="handleFallDamage",at=@At("HEAD"),cancellable=true)
    private void greatsea$netherFloorEntry(double fallDistance,float multiplier,DamageSource source,CallbackInfoReturnable<Boolean> cir) {
        LivingEntity entity=(LivingEntity)(Object)this;
        if (entity instanceof ServerPlayerEntity && entity.getEntityWorld() instanceof ServerWorld world &&
                UnstableNether.supported(world) && UnderworldEntry.protectsLanding(entity.getY(),fallDistance)) {
            entity.fallDistance=0;
            cir.setReturnValue(false);
        }
    }
}
