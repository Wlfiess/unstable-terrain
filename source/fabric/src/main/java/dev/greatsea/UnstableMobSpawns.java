package dev.greatsea;

import net.minecraft.entity.Entity;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.server.world.ServerWorld;

/** Controls creature admission in all dimensions of an Unstable world. */
public final class UnstableMobSpawns {
    public static final int DENOMINATOR=10_000;
    static final boolean VERIFY=Boolean.getBoolean("unstableterrain.verifyOrbitals");
    static long verificationRolls;
    private UnstableMobSpawns() {}
    public static boolean supported(ServerWorld world) {
        return UnstableWorldGen.isUnstable(world.getServer().getOverworld().getChunkManager().getChunkGenerator());
    }
    public static boolean allow(ServerWorld world,Entity entity) {
        if(GameplaySettings.Rule.MOB_SPAWNING.enabled() || !(entity instanceof MobEntity)
                || OrbitalRuntime.controlMob(entity.getCommandTags()) || !supported(world))return true;
        if(VERIFY)verificationRolls++;
        return false;
    }
    public static boolean natural(net.minecraft.entity.SpawnReason reason) {
        return reason==net.minecraft.entity.SpawnReason.NATURAL || reason==net.minecraft.entity.SpawnReason.CHUNK_GENERATION;
    }
}
