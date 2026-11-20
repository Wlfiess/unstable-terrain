package dev.greatsea;

import net.minecraft.registry.RegistryEntryLookup;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.chunk.ChunkGeneratorSettings;
import net.minecraft.world.gen.chunk.NoiseChunkGenerator;
import net.minecraft.world.gen.noise.NoiseConfig;
import net.minecraft.world.gen.noise.NoiseRouter;
import net.minecraft.world.gen.densityfunction.DensityFunction;
import net.minecraft.util.dynamic.CodecHolder;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/** The vanilla Amplified preset in a bounded square ring inside the Great Sea. */
public final class AmplifiedTerrain {
    public static final int START=128_000,END=240_000;
    public static final int TRANSITION=1536;
    /** Smooth at both ends, with zero slope at the untouched native terrain. */
    public static double weight(int x,int z){
        long radius=Math.max(Math.abs((long)x),Math.abs((long)z));
        double t=Math.max(0,Math.min(1,Math.min((radius-START)/(double)TRANSITION,(END-radius)/(double)TRANSITION)));
        return t*t*t*(t*(t*6-15)+10);
    }
    private record Blend(DensityFunction normal,DensityFunction amplified) implements DensityFunction {
        public double sample(NoisePos pos){
            double w=weight(pos.blockX(),pos.blockZ());
            if(w<=0)return normal.sample(pos);
            if(w>=1)return amplified.sample(pos);
            double a=normal.sample(pos);return a+(amplified.sample(pos)-a)*w;
        }
        public void fill(double[] values,EachApplier applier){applier.fill(values,this);}
        public DensityFunction apply(DensityFunctionVisitor visitor){return visitor.apply(new Blend(normal.apply(visitor),amplified.apply(visitor)));}
        public double minValue(){return Math.min(normal.minValue(),amplified.minValue());}
        public double maxValue(){return Math.max(normal.maxValue(),amplified.maxValue());}
        public CodecHolder<? extends DensityFunction> getCodecHolder(){return normal.getCodecHolder();}
    }
    /** Replace only the private, seeded router before the delegate is published.
     * Field lookup by type survives both Fabric remapping and the Paper core port. */
    public static void blendNoise(NoiseConfig normal,NoiseConfig amplified){
        var a=normal.getNoiseRouter();var b=amplified.getNoiseRouter();
        var router=new NoiseRouter(b.barrierNoise(),b.fluidLevelFloodednessNoise(),b.fluidLevelSpreadNoise(),b.lavaNoise(),
            b.temperature(),b.vegetation(),b.continents(),b.erosion(),b.depth(),b.ridges(),
            new Blend(a.preliminarySurfaceLevel(),b.preliminarySurfaceLevel()),new Blend(a.finalDensity(),b.finalDensity()),
            b.veinToggle(),b.veinRidged(),b.veinGap());
        try{
            for(var field:NoiseConfig.class.getDeclaredFields())if(field.getType()==NoiseRouter.class){
                field.setAccessible(true);field.set(amplified,router);return;
            }
            throw new IllegalStateException("Seeded noise router field missing");
        }catch(ReflectiveOperationException e){throw new IllegalStateException("Cannot blend Amplified terrain",e);}
    }
    public record Delegate(NoiseChunkGenerator generator,NoiseConfig noise) {}
    private record Context(RegistryEntry<ChunkGeneratorSettings> settings,NoiseConfig noise,
                           Map<NoiseChunkGenerator,Delegate> generators) {}
    private static final Map<NoiseConfig,Context> CONTEXTS=Collections.synchronizedMap(new WeakHashMap<>());
    private AmplifiedTerrain() {}
    public static boolean contains(int x,int z) {
        long radius=Math.max(Math.abs((long)x),Math.abs((long)z));
        return radius>=START && radius<Math.min(END,GreatSea.CONFIG.innerBoundary-16L)
                && !SeaTerrain.intersects(GreatSea.CONFIG,x&~15,z&~15);
    }
    public static boolean contains(Chunk chunk) { return contains(chunk.getPos().getStartX(),chunk.getPos().getStartZ()); }
    public static void register(NoiseConfig original,RegistryEntryLookup.RegistryLookup registries,long seed) {
        var settings=registries.getOrThrow(RegistryKeys.CHUNK_GENERATOR_SETTINGS).getOrThrow(ChunkGeneratorSettings.AMPLIFIED);
        var noise=NoiseConfig.create(settings.value(),registries.getOrThrow(RegistryKeys.NOISE_PARAMETERS),seed);
        blendNoise(original,noise);
        CONTEXTS.put(original,new Context(settings,noise,new WeakHashMap<>()));
    }
    public static Delegate delegate(NoiseChunkGenerator original,NoiseConfig noise) {
        if(!UnstableWorldGen.isUnstable(original))return null;
        var context=CONTEXTS.get(noise);if(context==null)return null;
        synchronized(context.generators()) {
            return context.generators().computeIfAbsent(original,g->new Delegate(new NoiseChunkGenerator(g.getBiomeSource(),context.settings()),context.noise()));
        }
    }
}
