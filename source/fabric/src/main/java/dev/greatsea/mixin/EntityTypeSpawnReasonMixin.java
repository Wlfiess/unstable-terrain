package dev.greatsea.mixin;
import dev.greatsea.MobSpawnReason;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnReason;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityType.class)
public abstract class EntityTypeSpawnReasonMixin {
    @Inject(method="create(Lnet/minecraft/world/World;Lnet/minecraft/entity/SpawnReason;)Lnet/minecraft/entity/Entity;",at=@At("RETURN"))
    private void unstableterrain$created(World world,SpawnReason reason,CallbackInfoReturnable<Entity> cir){
        if(cir.getReturnValue() instanceof MobSpawnReason mob)mob.unstableterrain$reason(reason);
    }
}
