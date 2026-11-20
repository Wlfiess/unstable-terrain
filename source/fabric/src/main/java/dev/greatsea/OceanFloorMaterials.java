package dev.greatsea;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;

/** The pre-1.23 sand/gravel bed, retaining the current ocean climate and its soft boundary. */
public final class OceanFloorMaterials {
    public enum Sediment { SAND, GRAVEL }
    private OceanFloorMaterials() {}
    public static boolean gravel(SeaConfig config,long seed,int x,int z) {
        double blend=SeaBiomeContext.coldBlend(config,seed,x,z);
        if (blend<=0) return false;
        if (blend>=1) return true;
        double a=x*.8+z*.6,b=z*.8-x*.6;
        double threshold=(1+.7*SeaTerrain.noise(seed+621,a,b,12)+
                .3*SeaTerrain.noise(seed+622,x,z,5))*.5;
        return blend>threshold;
    }
    public static Sediment sediment(SeaConfig config,long seed,int x,int z) {
        return gravel(config,seed,x,z) ? Sediment.GRAVEL : Sediment.SAND;
    }
    public static BlockState surface(SeaConfig config,long seed,int x,int z,int y) {
        return (gravel(config,seed,x,z) ? Blocks.GRAVEL : Blocks.SAND).getDefaultState();
    }
}
