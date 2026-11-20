package dev.greatsea.mixin;

import dev.greatsea.FarlandsMechanics;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.projectile.FireworkRocketEntity;
import net.minecraft.util.math.Vec3d;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/** An already-active rocket also loses its boost when crossing into the deep region. */
@Mixin(FireworkRocketEntity.class)
public abstract class FarlandsFireworkEntityMixin {
    @Redirect(method="tick",at=@At(value="INVOKE",target="Lnet/minecraft/entity/LivingEntity;setVelocity(Lnet/minecraft/util/math/Vec3d;)V"))
    private void greatsea$skipDeepFarlandsAcceleration(LivingEntity shooter,Vec3d velocity) {
        if (!(shooter instanceof PlayerEntity player) || !FarlandsMechanics.fireworksDisabled(player)) shooter.setVelocity(velocity);
    }
}
