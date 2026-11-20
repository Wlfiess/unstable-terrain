package dev.greatsea;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.world.gen.densityfunction.DensityFunctionTypes;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.noise.NoiseParametersKeys;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The seeded Overworld ocean density field, rather than an independent height-noise bed.
 * Continentalness is restricted to the vanilla ocean part of its terrain splines. All
 * other vanilla density functions, including the 3D terrain and noise caves, are kept.
 * A vertical translation lowers the ordinary ocean bed to roughly Y=-20. Water height
 * and the sand surface are supplied by the Great Sea generator independently.
 */
public final class VanillaOceanTerrain {
    private static final int Y_SHIFT=58;
    private static final int MIN_Y=-64,MAX_Y=80,CELL_Y=8;
    private static final int LEVELS=(MAX_Y-MIN_Y)/CELL_Y+1;
    private static final Map<Long,OceanField> FIELDS=new ConcurrentHashMap<>();
    private VanillaOceanTerrain() {}

    /** Call after NoiseConfig construction, or before generating/querying a sea chunk. */
    public static void register(long seed,NoiseConfig noise) {
        if (FIELDS.containsKey(seed)) return;
        int[] continents={0};
        DensityFunction density=noise.getNoiseRouter().finalDensity().apply(function -> {
            if (function instanceof DensityFunctionTypes.ShiftedNoise shifted &&
                    (shifted.noise().noiseData().matchesKey(NoiseParametersKeys.CONTINENTALNESS) ||
                     shifted.noise().noiseData().matchesKey(NoiseParametersKeys.CONTINENTALNESS_LARGE))) {
                continents[0]++;
                // Preserve broad, seed-dependent ocean/deep-ocean changes without islands.
                return DensityFunctionTypes.add(DensityFunctionTypes.constant(-.49),
                        DensityFunctionTypes.mul(DensityFunctionTypes.constant(.26),function.clamp(-1,1)));
            }
            return function;
        });
        // Nether/End NoiseConfigs must not replace the Overworld field for the same seed.
        if (continents[0]>0) {
            FIELDS.putIfAbsent(seed,new OceanField(density));
            SeaGeneration.registerSurface(seed,noise);
            SeaBiomeContext.registerOcean(noise.getMultiNoiseSampler(),seed);
        }
    }

    public static boolean available(long seed) { return FIELDS.containsKey(seed); }

    /** Continuous native ocean surface, including native low ridges and channels. */
    public static double floorHeight(SeaConfig config,long seed,int x,int z) {
        OceanField field=FIELDS.get(seed);
        // Pure terrain tests/previews have no Minecraft NoiseConfig. Production registers
        // the native field at NoiseConfig construction before any generation takes place.
        if (field==null) return SeaTerrain.floorHeight(config,seed,x,z);
        // The existing floor config defines the depth's center, rather than clipping the
        // natural ocean hills and ravines to an artificial height band.
        double nativeTop=field.column(x,z).top()+floorOffset(config);
        // Native ravines can reach below the playable world after translation. A smooth
        // basement leaves enough solid foundation for the sand skin above the bedrock.
        double basement=MIN_Y+8;
        return basement+2*Math.log1p(Math.exp((nativeTop-basement)/2));
    }

    /** Native density below the sand skin; negative-density caves are water-filled. */
    public static boolean solidAt(long seed,int x,int y,int z) {
        OceanField field=FIELDS.get(seed);
        return field==null || y<=MIN_Y+4 || field.column(x,z).sample(y)>0;
    }

    public static boolean solidAt(SeaConfig config,long seed,int x,int y,int z) {
        OceanField field=FIELDS.get(seed);
        return field==null || y<=MIN_Y+4 || field.column(x,z).sample(y-floorOffset(config))>0;
    }

    private static double floorOffset(SeaConfig config) {
        return (config.minFloorY+config.maxFloorY)/2.0+20;
    }

    /** Optional generator helper: keep native seabed cave shapes under the sand surface. */
    public static BlockState state(SeaConfig config,long seed,int x,int y,int z,int top,int bottom) {
        return state(config,seed,x,y,z,top,bottom,OceanFloorMaterials.gravel(config,seed,x,z));
    }

    /** One stable material choice covers the entire skin and its solid foundation. */
    public static BlockState state(SeaConfig config,long seed,int x,int y,int z,int top,int bottom,boolean gravel) {
        if (y<=bottom+(int)(SeaTerrain.unit(SeaTerrain.hash(seed,x,z))*4)) return Blocks.BEDROCK.getDefaultState();
        if (y>top) return y<config.seaLevel ? Blocks.WATER.getDefaultState() : Blocks.AIR.getDefaultState();
        if (y>top-5) return (gravel ? Blocks.GRAVEL : Blocks.SAND).getDefaultState();
        if (y>top-8) return (gravel ? (y<0 ? Blocks.DEEPSLATE : Blocks.STONE) : Blocks.SANDSTONE).getDefaultState();
        if (!solidAt(config,seed,x,y,z)) return Blocks.WATER.getDefaultState();
        return (y<0 ? Blocks.DEEPSLATE : Blocks.STONE).getDefaultState();
    }

    private static final class OceanField {
        private final DensityFunction density;
        private final Map<Long,Cell> cells=new ConcurrentHashMap<>();
        // Adjacent 4x4 cells share their density-lattice vertices. Sampling a vertex
        // once keeps the identical native density while avoiding four expensive evaluations.
        private final Map<Long,double[]> vertices=new ConcurrentHashMap<>();
        private double[] vertex(int x,int z) {
            long key=((long)x<<32)^(z&0xffffffffL);
            return vertices.computeIfAbsent(key,k -> {
                double[] values=new double[LEVELS];
                for(int i=0;i<LEVELS;i++)values[i]=density.sample(new DensityFunction.UnblendedNoisePos(
                        x,MIN_Y+i*CELL_Y+Y_SHIFT,z));
                return values;
            });
        }
        private static final class LastColumn {int x,z;Column column;}
        private final ThreadLocal<LastColumn> last=ThreadLocal.withInitial(LastColumn::new);
        private OceanField(DensityFunction density) { this.density=density; }
        private Column column(int x,int z) {
            var cached=last.get();
            if(cached.column!=null && cached.x==x && cached.z==z)return cached.column;
            int cx=Math.floorDiv(x,4),cz=Math.floorDiv(z,4);
            long key=((long)cx<<32)^(cz&0xffffffffL);
            // Bound memory while retaining thousands of neighboring chunks' density grids.
            if (cells.size()>32768) { cells.clear();vertices.clear(); }
            Cell cell=cells.computeIfAbsent(key,k -> new Cell(this,cx*4,cz*4));
            cached.x=x;cached.z=z;
            cached.column=new Column(cell,Math.floorMod(x,4)/4.0,Math.floorMod(z,4)/4.0,density,x,z);
            return cached.column;
        }
    }

    /** The vanilla Overworld's 4x8x4 terrain lattice, shared by all columns in a cell. */
    private static final class Cell {
        private final double[][] corners=new double[4][];
        private Cell(OceanField field,int x,int z) {
            for(int corner=0;corner<4;corner++)corners[corner]=field.vertex(x+(corner&1)*4,z+(corner>>1)*4);
        }
        private double level(int index,double x,double z) {
            return SeaTerrain.lerp(SeaTerrain.lerp(corners[0][index],corners[1][index],x),
                    SeaTerrain.lerp(corners[2][index],corners[3][index],x),z);
        }
    }
    private static final class Column {
        private final double[] levels=new double[LEVELS];
        private final DensityFunction density;
        private final int worldX,worldZ;
        private Column(Cell cell,double x,double z,DensityFunction density,int worldX,int worldZ) {
            this.density=density;this.worldX=worldX;this.worldZ=worldZ;
            // The bilinear result is invariant throughout this column. Keep the same
            // arithmetic/order as Cell.level, once per lattice level rather than per voxel.
            for(int i=0;i<LEVELS;i++)levels[i]=cell.level(i,x,z);
        }
        private double sample(double y) {
            if (y<MIN_Y || y>MAX_Y) {
                // Non-default depth centers can translate samples beyond the cached range.
                // Continue sampling the native density rather than extending a flat edge.
                int iy=(int)Math.floor(y);
                double lower=density.sample(new DensityFunction.UnblendedNoisePos(worldX,iy+Y_SHIFT,worldZ));
                if (y==iy) return lower;
                return SeaTerrain.lerp(lower,density.sample(new DensityFunction.UnblendedNoisePos(worldX,iy+1+Y_SHIFT,worldZ)),y-iy);
            }
            if (y==MAX_Y) return levels[LEVELS-1];
            int i=(int)Math.floor((y-MIN_Y)/CELL_Y);
            return SeaTerrain.lerp(levels[i],levels[i+1],(y-MIN_Y-i*CELL_Y)/(double)CELL_Y);
        }
        private double top() {
            double upper=levels[LEVELS-1];
            for (int i=LEVELS-2;i>=0;i--) {
                double lower=levels[i];
                if (lower>0) {
                    if (upper>0) return MIN_Y+(i+1)*CELL_Y;
                    return MIN_Y+i*CELL_Y+CELL_Y*lower/(lower-upper);
                }
                upper=lower;
            }
            return MIN_Y+5;
        }
    }
}
