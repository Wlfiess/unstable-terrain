package dev.greatsea;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
public final class UnstableNether {
    public static final int MIN_Y=-320,HEIGHT=576,UNDERWORLD_TOP=-128;
    public static boolean supported(ChunkGenerator generator){return generator instanceof PaperGenerator g && g.nether;}
    public static boolean supported(ServerLevel level){return level.dimension()==Level.NETHER && supported(level.getChunkSource().getGenerator());}
}
