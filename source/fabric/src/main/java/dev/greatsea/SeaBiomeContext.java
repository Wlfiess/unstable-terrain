package dev.greatsea;

import net.minecraft.registry.RegistryKey;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import java.lang.ref.WeakReference;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentHashMap;

/** Native Overworld climate selects the ordinary ocean family at the sea's raised water level. */
public final class SeaBiomeContext {
    private static final int COLD_SHORE_WIDTH=32;
    public enum OceanClimate { FROZEN, COLD, TEMPERATE, LUKEWARM, WARM }
    private record Context(long seed,boolean unstable) {}
    private static final Map<MultiNoiseUtil.MultiNoiseSampler,Context> CONTEXTS=Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Long,WeakReference<MultiNoiseUtil.MultiNoiseSampler>> OCEAN_SAMPLERS=new ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> NATIVE_BIOMES=ThreadLocal.withInitial(()->false);
    private static final java.util.concurrent.atomic.AtomicLong BINDING_VERSION=new java.util.concurrent.atomic.AtomicLong();
    private static final class SamplerContext {MultiNoiseUtil.MultiNoiseSampler sampler;long version=-1;Context context;}
    private static final ThreadLocal<SamplerContext> SAMPLER_CONTEXT=ThreadLocal.withInitial(SamplerContext::new);
    private static Context context(MultiNoiseUtil.MultiNoiseSampler sampler){
        var cached=SAMPLER_CONTEXT.get();long version=BINDING_VERSION.get();
        if(cached.sampler==sampler&&cached.version==version)return cached.context;
        // MultiNoiseSampler is a record: its structural hash walks the density graph.
        // Resolve a binding once per sampler/worker instead of once per biome block.
        cached.sampler=sampler;cached.context=CONTEXTS.get(sampler);cached.version=version;return cached.context;
    }
    private SeaBiomeContext() {}
    public static void register(MultiNoiseUtil.MultiNoiseSampler sampler,long seed) { register(sampler,seed,false); }
    public static void register(MultiNoiseUtil.MultiNoiseSampler sampler,long seed,boolean unstable) {
        CONTEXTS.put(sampler,new Context(seed,unstable));BINDING_VERSION.incrementAndGet();
        if (unstable) registerOcean(sampler,seed);
    }
    /** Called only for a verified Overworld density field; Nether/End cannot overwrite it. */
    public static void registerOcean(MultiNoiseUtil.MultiNoiseSampler sampler,long seed) {
        OCEAN_SAMPLERS.putIfAbsent(seed,new WeakReference<>(sampler));
        WeakReference<MultiNoiseUtil.MultiNoiseSampler> previous=OCEAN_SAMPLERS.get(seed);
        if (previous.get()==null) OCEAN_SAMPLERS.put(seed,new WeakReference<>(sampler));
    }
    public static Long seed(MultiNoiseUtil.MultiNoiseSampler sampler) {
        Context context=context(sampler);return context==null ? null : context.seed();
    }
    public static boolean unstable(MultiNoiseUtil.MultiNoiseSampler sampler) {
        Context context=context(sampler);return context!=null && context.unstable();
    }
    public static boolean samplingNative() { return NATIVE_BIOMES.get(); }
    /** Preserve native mainland surface rules while the stored dry shore remains a beach biome. */
    public static <T> T nativeBiomeQuery(java.util.function.Supplier<T> query) {
        boolean previous=NATIVE_BIOMES.get();NATIVE_BIOMES.set(true);
        try { return query.get(); } finally { NATIVE_BIOMES.set(previous); }
    }
    private static MultiNoiseUtil.MultiNoiseSampler sampler(long seed) {
        var reference=OCEAN_SAMPLERS.get(seed);return reference==null ? null : reference.get();
    }
    private static int quartCenter(int value) { return Math.floorDiv(value,4)*4+2; }
    private static double temperature(SeaConfig config,long seed,int x,int z,MultiNoiseUtil.MultiNoiseSampler sampler) {
        x=quartCenter(x);z=quartCenter(z);
        // Preserve the established warm coast, then blend naturally into the seeded climate.
        double inland=SeaTerrain.shoreDistance(config,x,z)-config.coldOceanInset;
        if (inland<=0) return 1;
        double nativeTemperature=sampler==null ? Math.max(-1,Math.min(1,
                2.35*(.82*SeaTerrain.noise(seed+603,x,z,8192)+.18*SeaTerrain.noise(seed+604,x,z,2048)))) :
                MultiNoiseUtil.toFloat(sampler.sample(x>>2,0,z>>2).temperatureNoise());
        return SeaTerrain.lerp(1,nativeTemperature,SeaTerrain.smooth(inland/2048));
    }
    public static OceanClimate climate(SeaConfig config,long seed,int x,int z) {
        return climate(config,seed,x,z,sampler(seed));
    }
    public static OceanClimate climate(SeaConfig config,long seed,int x,int z,MultiNoiseUtil.MultiNoiseSampler sampler) {
        double temperature=temperature(config,seed,x,z,sampler);
        // These are the vanilla Overworld ocean temperature bands.
        if (temperature<-.45) return OceanClimate.FROZEN;
        if (temperature<-.15) return OceanClimate.COLD;
        if (temperature<.2) return OceanClimate.TEMPERATE;
        if (temperature<.55) return OceanClimate.LUKEWARM;
        return OceanClimate.WARM;
    }
    public static boolean deep(long seed,int x,int z,MultiNoiseUtil.MultiNoiseSampler sampler) {
        x=quartCenter(x);z=quartCenter(z);
        double continents=sampler==null ? SeaTerrain.noise(seed+607,x,z,6144) :
                MultiNoiseUtil.toFloat(sampler.sample(x>>2,0,z>>2).continentalnessNoise());
        return sampler==null ? continents<0 : continents<-.455;
    }
    /** All sandy dry shores use beach, including the inland rise and its narrow upper cap. */
    public static boolean beach(SeaConfig config,long seed,int x,int z) {
        double raw=SeaTerrain.shoreDistance(config,x,z);
        if(!config.enabled||raw<0)return false;
        // The fully transitioned fallback bed is guaranteed below seaLevel-32 by
        // configuration validation, so no deep-ocean column can be a dry beach.
        if(raw>=config.beachWidth+config.underwaterSlopeWidth+config.coastWaviness+SeaTerrain.FLOOR_TRANSITION_WIDTH)return false;
        double distance=SeaTerrain.shoreDistance(config,seed,x,z);
        return distance<config.beachWidth || Math.round(SeaTerrain.height(config,seed,x,z,64))>=config.seaLevel-1;
    }
    public static RegistryKey<Biome> biomeKey(SeaConfig config,long seed,int x,int z) {
        return biomeKey(config,seed,x,z,sampler(seed));
    }
    /** Sea biomes are horizontal columns. Keep a bounded per-worker cache across Y samples. */
    private static final class BiomeColumns {
        SeaConfig config;long seed,settings;MultiNoiseUtil.MultiNoiseSampler sampler;
        final long[] positions=new long[4096];
        @SuppressWarnings("unchecked") final RegistryKey<Biome>[] keys=(RegistryKey<Biome>[])new RegistryKey<?>[4096];
    }
    private static final ThreadLocal<BiomeColumns> BIOME_COLUMNS=ThreadLocal.withInitial(BiomeColumns::new);
    private static long settings(SeaConfig c){
        long h=c.enabled?1:0;
        h=h*31+c.innerBoundary;h=h*31+c.outerBoundary;h=h*31+c.beachWidth;h=h*31+c.coastWaviness;h=h*31+c.underwaterSlopeWidth;h=h*31+c.seaLevel;h=h*31+c.beachPeakY;h=h*31+c.mainlandJoinY;h=h*31+c.mainlandPreserveTopY;h=h*31+c.minFloorY;h=h*31+c.maxFloorY;h=h*31+c.coldOceanInset;
        return h;
    }
    /** The same API is used by Fabric's biome mixin and the standalone Paper adapter. */
    public static RegistryKey<Biome> biomeKey(SeaConfig config,long seed,int x,int z,MultiNoiseUtil.MultiNoiseSampler sampler) {
        x=quartCenter(x);z=quartCenter(z);
        var cache=BIOME_COLUMNS.get();long stamp=settings(config);
        if(cache.config!=config||cache.seed!=seed||cache.settings!=stamp||cache.sampler!=sampler){
            java.util.Arrays.fill(cache.keys,null);cache.config=config;cache.seed=seed;cache.settings=stamp;cache.sampler=sampler;
        }
        long position=((long)x<<32)^(z&0xffffffffL);long mixed=position^(position>>>33);mixed*=0xff51afd7ed558ccdL;mixed^=mixed>>>33;
        int slot=(int)mixed&4095;
        if(cache.keys[slot]!=null&&cache.positions[slot]==position)return cache.keys[slot];
        var key=uncachedBiomeKey(config,seed,x,z,sampler);cache.positions[slot]=position;cache.keys[slot]=key;return key;
    }
    static RegistryKey<Biome> uncachedBiomeKey(SeaConfig config,long seed,int x,int z,MultiNoiseUtil.MultiNoiseSampler sampler){
        if (beach(config,seed,x,z)) return BiomeKeys.BEACH;
        if(coldShore(config,seed,x,z))return BiomeKeys.COLD_OCEAN;
        var climate=climate(config,seed,x,z,sampler);
        if(climate==OceanClimate.WARM)return BiomeKeys.WARM_OCEAN;
        boolean deep=deep(seed,x,z,sampler);
        return switch (climate) {
            case FROZEN -> deep ? BiomeKeys.DEEP_FROZEN_OCEAN : BiomeKeys.FROZEN_OCEAN;
            case COLD -> deep ? BiomeKeys.DEEP_COLD_OCEAN : BiomeKeys.COLD_OCEAN;
            case TEMPERATE -> deep ? BiomeKeys.DEEP_OCEAN : BiomeKeys.OCEAN;
            case LUKEWARM -> deep ? BiomeKeys.DEEP_LUKEWARM_OCEAN : BiomeKeys.LUKEWARM_OCEAN;
            case WARM -> BiomeKeys.WARM_OCEAN;
        };
    }
    /** Thirteen horizontal blocks of water next to the actual dry sand, following its curves. */
    public static boolean coldShore(SeaConfig config,long seed,int x,int z){
        if(!config.enabled||!SeaTerrain.contains(config,x,z)||beach(config,seed,x,z))return false;
        double raw=SeaTerrain.shoreDistance(config,x,z);
        if(raw>config.beachWidth+config.coastWaviness+config.underwaterSlopeWidth)return false;
        double distance=SeaTerrain.wetShoreOffset(config,seed,x,z);
        if(distance<0||distance>2*COLD_SHORE_WIDTH)return false;
        // Normalize the contour gradient so square-corner diagonals and wavy sections
        // keep the same physical width instead of widening at the turns.
        double dx=SeaTerrain.wetShoreOffset(config,seed,x+1,z)-distance;
        double dz=SeaTerrain.wetShoreOffset(config,seed,x,z+1)-distance;
        return distance<COLD_SHORE_WIDTH*Math.max(.1,Math.hypot(dx,dz));
    }
    public static boolean isOcean(RegistryKey<Biome> key) {
        return key.equals(BiomeKeys.WARM_OCEAN) || key.equals(BiomeKeys.LUKEWARM_OCEAN) ||
                key.equals(BiomeKeys.DEEP_LUKEWARM_OCEAN) || key.equals(BiomeKeys.OCEAN) ||
                key.equals(BiomeKeys.DEEP_OCEAN) || key.equals(BiomeKeys.COLD_OCEAN) ||
                key.equals(BiomeKeys.DEEP_COLD_OCEAN) || key.equals(BiomeKeys.FROZEN_OCEAN) ||
                key.equals(BiomeKeys.DEEP_FROZEN_OCEAN);
    }
    public static boolean cold(SeaConfig config,long seed,int x,int z) {
        var climate=climate(config,seed,x,z);return climate==OceanClimate.COLD || climate==OceanClimate.FROZEN;
    }
    /** A continuous transition lets coherent sand/gravel patches overlap climate boundaries. */
    public static double coldBlend(SeaConfig config,long seed,int x,int z) {
        return SeaTerrain.smooth((-.11-temperature(config,seed,x,z,sampler(seed)))/.08);
    }
}
