package dev.greatsea.mixin;

import dev.greatsea.GreatSea;
import dev.greatsea.SeaTerrain;
import dev.greatsea.SeaBiomeContext;
import dev.greatsea.FarlandsWorld;
import dev.greatsea.FarlandsBiomes;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.biome.BiomeKeys;
import net.minecraft.world.biome.source.MultiNoiseBiomeSource;
import net.minecraft.world.biome.source.util.MultiNoiseUtil;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(MultiNoiseBiomeSource.class)
public abstract class MultiNoiseBiomeSourceMixin {
    @Unique private volatile java.util.Map<net.minecraft.registry.RegistryKey<Biome>,RegistryEntry<Biome>> greatsea$oceanBiomes;
    @Unique private volatile boolean greatsea$lookedUp;
    @Inject(method="getBiome(IIILnet/minecraft/world/biome/source/util/MultiNoiseUtil$MultiNoiseSampler;)Lnet/minecraft/registry/entry/RegistryEntry;",at=@At("HEAD"),cancellable=true)
    private void greatsea$uniformBiome(int x,int y,int z,MultiNoiseUtil.MultiNoiseSampler sampler,CallbackInfoReturnable<RegistryEntry<Biome>> cir) {
        if (FarlandsBiomes.samplingNative() || SeaBiomeContext.samplingNative() || !SeaBiomeContext.unstable(sampler)) return;
        if (FarlandsWorld.contains(x*4+2,z*4+2)) {
            Long seed=SeaBiomeContext.seed(sampler);
            var biome=FarlandsBiomes.get((MultiNoiseBiomeSource)(Object)this,seed==null ? 0:seed,x*4+2,z*4+2,sampler);
            if (biome!=null)cir.setReturnValue(biome);
            return;
        }
        if (!SeaTerrain.contains(GreatSea.CONFIG,x*4+2,z*4+2)) return;
        Long seed=SeaBiomeContext.seed(sampler);
        if (!greatsea$lookedUp) {
            java.util.Map<net.minecraft.registry.RegistryKey<Biome>,RegistryEntry<Biome>> biomes=new java.util.HashMap<>();
            ((MultiNoiseBiomeSource)(Object)this).getBiomes().forEach(b -> b.getKey().ifPresent(k -> biomes.put(k,b)));
            greatsea$oceanBiomes=java.util.Map.copyOf(biomes);
            greatsea$lookedUp=true;
        }
        var key=SeaBiomeContext.biomeKey(GreatSea.CONFIG,seed==null ? 0 : seed,x*4+2,z*4+2,sampler);
        var biome=greatsea$oceanBiomes.get(key);
        if (biome!=null) cir.setReturnValue(biome);
    }
    @Inject(method="getBiome(IIILnet/minecraft/world/biome/source/util/MultiNoiseUtil$MultiNoiseSampler;)Lnet/minecraft/registry/entry/RegistryEntry;",at=@At("RETURN"),cancellable=true)
    private void mist$cold(int x,int y,int z,MultiNoiseUtil.MultiNoiseSampler sampler,CallbackInfoReturnable<RegistryEntry<Biome>> cir){
        if(!SeaBiomeContext.unstable(sampler)||SeaBiomeContext.samplingNative()||FarlandsBiomes.samplingNative())return;
        int bx=x*4+2,bz=z*4+2;if(!dev.greatsea.MistRegion.contains(bx,bz))return;
        Long seed=SeaBiomeContext.seed(sampler);cir.setReturnValue(dev.greatsea.MistBiomes.map((MultiNoiseBiomeSource)(Object)this,seed==null?0:seed,bx,bz,cir.getReturnValue()));
    }

}
