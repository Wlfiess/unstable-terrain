package dev.greatsea;
import net.minecraft.world.level.chunk.ChunkGenerator;
public final class UnstableWorldGen {
    public static boolean isUnstable(ChunkGenerator generator){return generator instanceof PaperGenerator;}
}
