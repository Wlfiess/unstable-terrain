package dev.greatsea.mixin;

import dev.greatsea.UnstableMobSpawns;
import net.minecraft.entity.Entity;
import net.minecraft.server.world.ServerWorld;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import java.util.stream.Stream;

@Mixin(ServerWorld.class)
public abstract class UnstableMobSpawnMixin {
    @Inject(method="spawnEntity",at=@At("HEAD"),cancellable=true)
    private void unstableterrain$rareSpawn(Entity entity,CallbackInfoReturnable<Boolean> cir) {
        if(!UnstableMobSpawns.allow((ServerWorld)(Object)this,entity))cir.setReturnValue(false);
    }
    // New entities stored in generation chunks use this path, unlike loadEntities.
    @ModifyVariable(method="addEntities",at=@At("HEAD"),argsOnly=true)
    private Stream<Entity> unstableterrain$rareGeneration(Stream<Entity> entities) {
        return entities.filter(entity->UnstableMobSpawns.allow((ServerWorld)(Object)this,entity));
    }
}
