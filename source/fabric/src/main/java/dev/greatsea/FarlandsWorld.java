package dev.greatsea;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;

public final class FarlandsWorld {
    public static final Identifier ID=Identifier.of("unstableterrain","farlands");
    private FarlandsWorld() {}
    public static boolean supported(ChunkGenerator generator) {
        return UnstableWorldGen.isUnstable(generator);
    }
    public static boolean supported(ServerWorld world) {
        return enabledOverworld(world);
    }
    public static boolean enabledOverworld(ServerWorld world) {
        return GreatSea.FARLANDS.enabled && world.getRegistryKey().equals(World.OVERWORLD) &&
                UnstableWorldGen.isUnstable(world.getChunkManager().getChunkGenerator());
    }
    public static boolean contains(double x,double z) {
        return GreatSea.FARLANDS.enabled && Math.max(Math.abs(x),Math.abs(z))>=GreatSea.FARLANDS.entryBoundary;
    }
    public static double depth(double x,double z) {
        return Math.max(0,Math.max(Math.abs(x),Math.abs(z))-GreatSea.FARLANDS.entryBoundary);
    }
    public static boolean activePlayer(PlayerEntity player) {
        if (!(player.getEntityWorld() instanceof ServerWorld))return FarlandsCoordinates.clientActive(player);
        if (!player.getEntityWorld().getRegistryKey().equals(World.OVERWORLD) || !contains(player.getX(),player.getZ()))return false;
        return supported((ServerWorld)player.getEntityWorld());
    }
    public record Point(int depth,int tangent) {}
    private static final class Projection {int x,z,boundary;Point point;}
    private static final ThreadLocal<Projection> PROJECTION=ThreadLocal.withInitial(Projection::new);
    public static Point project(int x,int z) {
        var cached=PROJECTION.get();int boundary=GreatSea.FARLANDS.entryBoundary;
        if(cached.point==null || cached.x!=x || cached.z!=z || cached.boundary!=boundary) {
            cached.x=x;cached.z=z;cached.boundary=boundary;
            cached.point=Math.abs((long)x)>=Math.abs((long)z) ? new Point((int)depth(x,z),z):new Point((int)depth(x,z),x);
        }
        return cached.point;
    }
}
