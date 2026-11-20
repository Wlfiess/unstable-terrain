package dev.greatsea;

import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;
import net.minecraft.world.gen.noise.NoiseConfig;
import java.util.concurrent.ConcurrentHashMap;

/** Minecraft's floating_islands density, sampled on its native 8x4x8 lattice. */
public final class VanillaSkylands {
    private static final ConcurrentHashMap<Long,Field> FIELDS=new ConcurrentHashMap<>();
    private VanillaSkylands(){}
    public static void register(long seed,NoiseConfig noise){
        final DensityFunction[] raw={null};
        noise.getNoiseRouter().finalDensity().apply(function->{
            if(function instanceof DensityFunctionTypes.Wrapper marker && ((Enum<?>)marker.type()).ordinal()==0)raw[0]=marker.wrapped();
            return function;
        });
        if(raw[0]==null)throw new IllegalStateException("Native floating_islands interpolation missing");
        FIELDS.put(seed,new Field(raw[0]));
    }
    public static boolean available(long seed){return FIELDS.containsKey(seed);}
    public static boolean solid(long seed,int x,int y,int z){
        if(y<0||y>=256)return false;
        Field field=FIELDS.get(seed);
        if(field==null)return fallback(seed,x,y,z)>0;
        return field.sample(x,y,z)>0;
    }
    private static double fallback(long seed,int x,int y,int z){
        // Registry-free probes only. Production always registers Mojang's seeded router.
        double n=FarlandsNatural.noise3(seed,x/70.0,y/35.0,z/70.0);
        return n-.25-Math.pow((y-95)/92.0,4);
    }
    private static final class Field {
        final DensityFunction density;
        final ConcurrentHashMap<Long,double[][]> cells=new ConcurrentHashMap<>();
        private static final class LastCell {long key;double[][] corners;}
        final ThreadLocal<LastCell> last=ThreadLocal.withInitial(LastCell::new);
        Field(DensityFunction density){this.density=density;}
        double sample(int x,int y,int z){
            int cx=Math.floorDiv(x,8),cz=Math.floorDiv(z,8);
            long key=((long)cx<<32)|(cz&0xffffffffL);
            var cached=last.get();
            if(cached.corners==null || cached.key!=key) {
            if(cells.size()>8192)cells.clear();
            cached.corners=cells.computeIfAbsent(key,k->{
                double[][] values=new double[4][65];
                for(int c=0;c<4;c++)for(int i=0;i<=64;i++)values[c][i]=density.sample(new DensityFunction.UnblendedNoisePos(cx*8+(c&1)*8,i*4,cz*8+(c>>1)*8));
                return values;
            });
            cached.key=key;
            }
            double[][] corners=cached.corners;
            int i=y>>2;double tx=Math.floorMod(x,8)/8.0,tz=Math.floorMod(z,8)/8.0,ty=(y&3)/4.0;
            double low=SeaTerrain.lerp(SeaTerrain.lerp(corners[0][i],corners[1][i],tx),SeaTerrain.lerp(corners[2][i],corners[3][i],tx),tz);
            double high=SeaTerrain.lerp(SeaTerrain.lerp(corners[0][i+1],corners[1][i+1],tx),SeaTerrain.lerp(corners[2][i+1],corners[3][i+1],tx),tz);
            return SeaTerrain.lerp(low,high,ty);
        }
    }
}
