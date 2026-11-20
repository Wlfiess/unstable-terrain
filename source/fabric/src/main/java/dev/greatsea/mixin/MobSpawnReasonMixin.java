package dev.greatsea.mixin;
import dev.greatsea.MobSpawnReason;
import net.minecraft.entity.EntityData;
import net.minecraft.entity.SpawnReason;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.world.LocalDifficulty;
import net.minecraft.world.ServerWorldAccess;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MobEntity.class)
public abstract class MobSpawnReasonMixin implements MobSpawnReason {
    @Unique private SpawnReason unstableterrain$spawnReason=SpawnReason.CHUNK_GENERATION;
    public SpawnReason unstableterrain$reason(){return unstableterrain$spawnReason;}
    public void unstableterrain$reason(SpawnReason reason){unstableterrain$spawnReason=reason;}
    @Inject(method="initialize",at=@At("HEAD"))
    private void unstableterrain$initialize(ServerWorldAccess world,LocalDifficulty difficulty,SpawnReason reason,EntityData data,CallbackInfoReturnable<EntityData> cir){unstableterrain$spawnReason=reason;}
}
