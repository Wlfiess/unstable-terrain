package dev.greatsea;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.Collections;
import java.util.HashMap;

public final class FarlandsBiomes {
    private static final Map<MultiNoiseBiomeSource,Map<RegistryKey<Biome>,RegistryEntry<Biome>>> LOOKUPS=Collections.synchronizedMap(new WeakHashMap<>());
    private static volatile Map<RegistryKey<Biome>,RegistryEntry<Biome>> EXTRA_BIOMES=Map.of();
    public static void registerBiomes(net.minecraft.registry.Registry<Biome> registry){
        var entries=new HashMap<RegistryKey<Biome>,RegistryEntry<Biome>>();
        for(var key:registry.getKeys())registry.getEntry(key.getValue()).ifPresent(entry->entries.put(key,entry));
        EXTRA_BIOMES=Map.copyOf(entries);LOOKUPS.clear();
    }
    private static final java.util.List<RegistryKey<Biome>> CHUNKS=java.util.List.of(BiomeKeys.PLAINS,BiomeKeys.FOREST,BiomeKeys.BIRCH_FOREST,BiomeKeys.JUNGLE,
            BiomeKeys.SNOWY_PLAINS,BiomeKeys.SNOWY_TAIGA,BiomeKeys.DARK_FOREST,BiomeKeys.FLOWER_FOREST,
            BiomeKeys.DESERT,BiomeKeys.WINDSWEPT_HILLS,BiomeKeys.BADLANDS,BiomeKeys.MUSHROOM_FIELDS,
            BiomeKeys.ICE_SPIKES,BiomeKeys.CHERRY_GROVE,BiomeKeys.SAVANNA,BiomeKeys.OLD_GROWTH_SPRUCE_TAIGA);
    private record Context(MultiNoiseBiomeSource source,net.minecraft.world.biome.source.util.MultiNoiseUtil.MultiNoiseSampler sampler){}
    private static final Map<Long,Context> CONTEXT=new java.util.concurrent.ConcurrentHashMap<>();
    private static final ThreadLocal<Boolean> NATIVE=ThreadLocal.withInitial(()->false);
    public static boolean samplingNative(){return NATIVE.get();}
    private static RegistryKey<Biome> natural(long seed,int x,int z) {
        Context c=CONTEXT.get(seed);
        if(c!=null) {
            NATIVE.set(true);
            try{return c.source.getBiome(x>>2,20,z>>2,c.sampler).getKey().orElse(BiomeKeys.PLAINS);}
            finally{NATIVE.remove();}
        }
        // Pure tests run without registries. Runtime uses Minecraft's climate sampler above.
        double climate=SeaTerrain.noise(seed+859,x,z,240);
        return climate<-.45?BiomeKeys.SNOWY_TAIGA:climate<-.15?BiomeKeys.TAIGA:climate<.15?BiomeKeys.FOREST:climate<.45?BiomeKeys.PLAINS:BiomeKeys.DESERT;
    }
    /** Land climate sampled independently of the sea surrounding the physical Farlands. */
    static RegistryKey<Biome> floating(long seed,int x,int z) {
        var p=FarlandsWorld.project(x,z);
        int a=p.depth()-FarlandsTerrain.start(FarlandsTerrain.Stage.FLOATING);
        int b=p.tangent();
        Context c=CONTEXT.get(seed);
        if(c!=null) {
            int climateX=a+(int)(SeaTerrain.hash(seed+8581,1,0)&65535)-32768;
            int climateZ=b+(int)(SeaTerrain.hash(seed+8582,0,1)&65535)-32768;
            NATIVE.set(true);
            try {
                var biome=c.source.getBiome(climateX>>2,20,climateZ>>2,c.sampler).getKey().orElse(BiomeKeys.PLAINS);
                String name=biome.getValue().getPath();
                if(!name.contains("ocean") && !name.contains("river") && !name.contains("beach"))return biome;
            } finally {NATIVE.remove();}
        }
        // Ocean climate is converted into land ecosystems so the sky void cannot become
        // an invisible ocean biome. These fields also make pure generation previews
        // show the same broad, softly changing forest, snow, dry and jungle regions.
        double heat=SeaTerrain.noise(seed+8583,a,b,620),wet=SeaTerrain.noise(seed+8584,a,b,510);
        if(heat<-.28)return wet>-.12?BiomeKeys.SNOWY_TAIGA:BiomeKeys.SNOWY_PLAINS;
        if(heat<-.05)return wet>.15?BiomeKeys.TAIGA:wet>-.15?BiomeKeys.BIRCH_FOREST:BiomeKeys.WINDSWEPT_HILLS;
        if(heat<.22)return wet>.25?BiomeKeys.FLOWER_FOREST:wet>.02?BiomeKeys.FOREST:wet>-.23?BiomeKeys.MEADOW:BiomeKeys.PLAINS;
        return wet>.30?BiomeKeys.JUNGLE:wet>-.10?BiomeKeys.SAVANNA:BiomeKeys.DESERT;
    }
    static int skin(long seed,int x,int z) {
        var biome=key(seed,x,z);
        if(biome.equals(BiomeKeys.DESERT))return 5;
        if(biome.equals(BiomeKeys.BADLANDS) || biome.equals(BiomeKeys.ERODED_BADLANDS))return 37;
        if(biome.equals(BiomeKeys.MUSHROOM_FIELDS))return 39;
        if(biome.equals(BiomeKeys.ICE_SPIKES) || biome.equals(BiomeKeys.SNOWY_PLAINS) || biome.equals(BiomeKeys.SNOWY_TAIGA) || biome.equals(BiomeKeys.SNOWY_SLOPES))return 7;
        return 4;
    }
    private FarlandsBiomes() {}
    public static RegistryKey<Biome> key(long seed,int x,int z) {
        if(SpokelandsTerrain.contains(x,z))return SpokelandsTerrain.biome(seed,x,z);
        var p=FarlandsWorld.project(x,z);
        return switch(FarlandsTerrain.stage(x,z)) {
            case GRAVEL,GRAVEL_AFTER_SPIKES,PLAINS,REPEATING,CORRUPTED,ORBITALS,PLAINS_HILLS -> BiomeKeys.PLAINS;
            case SPIKES -> BiomeKeys.SNOWY_SLOPES;
            case SPLIT -> FarlandsTerrain.bareSplit(x,z)?BiomeKeys.PLAINS:
                    Math.hypot(p.depth()-FarlandsTemple.center(),p.tangent())<640?BiomeKeys.FOREST:natural(seed,x,z);
            case CHERRY -> BiomeKeys.CHERRY_GROVE;
            case SAND_DUNES,SAND_FARLANDS -> BiomeKeys.DESERT;
            case FLOATING -> floating(seed,x,z);
            case CHUNKS -> CHUNKS.get(FarlandsTerrain.chunkBiome(seed,x,z));
            case DECAY -> CHUNKS.get((int)(SeaTerrain.hash(seed+860,Math.floorDiv(p.tangent(),16),0)&7));
            case WATER -> BiomeKeys.OCEAN;
            case SKYGRID -> BiomeKeys.PLAINS;
        };
    }
    public static RegistryEntry<Biome> get(MultiNoiseBiomeSource source,long seed,int x,int z,net.minecraft.world.biome.source.util.MultiNoiseUtil.MultiNoiseSampler sampler) {
        CONTEXT.putIfAbsent(seed,new Context(source,sampler));
        Map<RegistryKey<Biome>,RegistryEntry<Biome>> map=LOOKUPS.computeIfAbsent(source,s->{
            var result=new HashMap<RegistryKey<Biome>,RegistryEntry<Biome>>();
            for(var biome:s.getBiomes())biome.getKey().ifPresent(k->result.put(k,biome));return result;
        });
        var wanted=key(seed,x,z);var biome=map.get(wanted);
        return biome!=null?biome:EXTRA_BIOMES.get(wanted);
    }
}
