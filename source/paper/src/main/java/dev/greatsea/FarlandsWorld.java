package dev.greatsea;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.ChunkGenerator;
public final class FarlandsWorld {
    public record Point(int depth,int tangent){}
    public static boolean supported(ChunkGenerator generator){return generator instanceof PaperGenerator g && !g.nether;}
    public static boolean supported(ServerLevel level){return level.dimension()==Level.OVERWORLD && supported(level.getChunkSource().getGenerator());}
    public static boolean enabledOverworld(ServerLevel level){return GreatSea.FARLANDS.enabled && supported(level);}
    public static boolean contains(double x,double z){return GreatSea.FARLANDS.enabled && Math.max(Math.abs(x),Math.abs(z))>=GreatSea.FARLANDS.entryBoundary;}
    public static double depth(double x,double z){return Math.max(0,Math.max(Math.abs(x),Math.abs(z))-GreatSea.FARLANDS.entryBoundary);}
    private static final class Projection {int x,z,boundary;Point point;}
    private static final ThreadLocal<Projection> PROJECTION=ThreadLocal.withInitial(Projection::new);
    public static Point project(int x,int z){
        var cached=PROJECTION.get();int boundary=GreatSea.FARLANDS.entryBoundary;
        if(cached.point==null||cached.x!=x||cached.z!=z||cached.boundary!=boundary){
            cached.x=x;cached.z=z;cached.boundary=boundary;
            cached.point=Math.abs((long)x)>=Math.abs((long)z)?new Point((int)depth(x,z),z):new Point((int)depth(x,z),x);
        }
        return cached.point;
    }
}
