package dev.greatsea;
import com.mojang.serialization.MapCodec;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.*;
import java.util.stream.Stream;

final class PaperBiomes extends BiomeSource {
    final BiomeSource nativeSource;
    final ServerLevel level;
    final boolean nether;
    PaperBiomes(BiomeSource source,ServerLevel level,boolean nether){this.nativeSource=source;this.level=level;this.nether=nether;}
    @SuppressWarnings({"rawtypes","unchecked"}) @Override protected MapCodec<? extends BiomeSource> codec(){return (MapCodec)MultiNoiseBiomeSource.CODEC;}
    private Holder<Biome> biome(ResourceKey<Biome> key){return level.registryAccess().lookupOrThrow(Registries.BIOME).getOrThrow(key);}
    @Override protected Stream<Holder<Biome>> collectPossibleBiomes(){
        var originals=nativeSource.possibleBiomes().stream();
        return nether?Stream.concat(originals,Stream.of(underworld())).distinct():
            Stream.concat(originals,Stream.of(biome(Biomes.CRIMSON_FOREST),biome(Biomes.WARPED_FOREST),biome(SpokelandsTerrain.RED_BIOME),biome(SpokelandsTerrain.DARK_BIOME),biome(MistBiomes.OCEAN),biome(MistBiomes.DEEP_OCEAN))).distinct();
    }
    private Holder<Biome> underworld(){return biome(ResourceKey.create(Registries.BIOME,Identifier.fromNamespaceAndPath("greatsea","underworld")));}
    @Override public Holder<Biome> getNoiseBiome(int x,int y,int z,Climate.Sampler sampler){
        if(nether)return y*4<UnstableNether.UNDERWORLD_TOP?underworld():nativeSource.getNoiseBiome(x,y,z,sampler);
        int bx=x*4+2,bz=z*4+2;
        if(SpokelandsTerrain.contains(bx,bz))return biome(SpokelandsTerrain.biome(level.getSeed(),bx,bz));
        if(FarlandsWorld.contains(bx,bz) && nativeSource instanceof MultiNoiseBiomeSource multi){
            return FarlandsBiomes.get(multi,level.getSeed(),bx,bz,sampler);
        }
        if(SeaTerrain.contains(GreatSea.CONFIG,bx,bz))return biome(SeaBiomeContext.biomeKey(GreatSea.CONFIG,level.getSeed(),bx,bz,sampler));
        var original=nativeSource.getNoiseBiome(x,y,z,sampler);
        if(MistRegion.contains(bx,bz))return biome(MistBiomes.key(level.getSeed(),bx,bz,original.unwrapKey().orElse(Biomes.PLAINS)));
        return original;
    }
}

