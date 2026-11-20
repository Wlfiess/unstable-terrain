package dev.greatsea;
import java.util.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.*;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;

public final class MistBiomes {
 private MistBiomes(){}
 public static final RegistryKey<Biome> OCEAN=RegistryKey.of(net.minecraft.registry.RegistryKeys.BIOME,net.minecraft.util.Identifier.of("greatsea","mist_frozen_ocean")),DEEP_OCEAN=RegistryKey.of(net.minecraft.registry.RegistryKeys.BIOME,net.minecraft.util.Identifier.of("greatsea","mist_deep_frozen_ocean"));
 private static volatile Map<RegistryKey<Biome>,RegistryEntry<Biome>> EXTRA=Map.of();
 public static void registerBiomes(net.minecraft.registry.Registry<Biome> registry){var map=new HashMap<RegistryKey<Biome>,RegistryEntry<Biome>>();for(var key:List.of(OCEAN,DEEP_OCEAN))map.put(key,registry.getOrThrow(key));EXTRA=Map.copyOf(map);LOOKUPS.clear();}
 private static final Set<RegistryKey<Biome>> COLD=Set.of(BiomeKeys.SNOWY_PLAINS,BiomeKeys.SNOWY_TAIGA,BiomeKeys.SNOWY_SLOPES,BiomeKeys.GROVE,BiomeKeys.FROZEN_PEAKS,BiomeKeys.JAGGED_PEAKS,BiomeKeys.ICE_SPIKES,BiomeKeys.FROZEN_RIVER,BiomeKeys.FROZEN_OCEAN,BiomeKeys.DEEP_FROZEN_OCEAN,BiomeKeys.SNOWY_BEACH);
 private static final Map<MultiNoiseBiomeSource,Map<RegistryKey<Biome>,RegistryEntry<Biome>>> LOOKUPS=Collections.synchronizedMap(new WeakHashMap<>());
 public static boolean cold(RegistryKey<Biome> key){return COLD.contains(key)||key.equals(OCEAN)||key.equals(DEEP_OCEAN);}
 public static RegistryKey<Biome> key(long seed,int x,int z,RegistryKey<Biome> original){
  if(!MistRegion.frost(seed,x,z))return original;
  if(original.equals(BiomeKeys.FROZEN_OCEAN))return OCEAN;
  if(original.equals(BiomeKeys.DEEP_FROZEN_OCEAN))return DEEP_OCEAN;
  if(cold(original))return original;
  String id=original.getValue().getPath();
  if(id.contains("ocean"))return id.contains("deep")?DEEP_OCEAN:OCEAN;
  if(id.contains("river"))return BiomeKeys.FROZEN_RIVER;
  if(id.contains("beach")||id.equals("stony_shore"))return BiomeKeys.SNOWY_BEACH;
  if(id.contains("peaks"))return BiomeKeys.FROZEN_PEAKS;
  if(id.contains("windswept")||id.contains("slopes"))return BiomeKeys.SNOWY_SLOPES;
  double n=SeaTerrain.noise(seed+14702,x,z,900);
  return n>.36?BiomeKeys.GROVE:n>-.18?BiomeKeys.SNOWY_TAIGA:BiomeKeys.SNOWY_PLAINS;
 }
 public static RegistryEntry<Biome> map(MultiNoiseBiomeSource source,long seed,int x,int z,RegistryEntry<Biome> original){
  var key=key(seed,x,z,original.getKey().orElse(BiomeKeys.PLAINS));
  if(original.matchesKey(key))return original;
  var lookup=LOOKUPS.computeIfAbsent(source,s->{var m=new HashMap<RegistryKey<Biome>,RegistryEntry<Biome>>();s.getBiomes().forEach(b->b.getKey().ifPresent(k->m.put(k,b)));return Map.copyOf(m);});
  var result=EXTRA.getOrDefault(key,lookup.get(key));if(result==null)throw new IllegalStateException("Missing cold biome "+key);return result;
 }
}
