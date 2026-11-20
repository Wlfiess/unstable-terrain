package dev.greatsea;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
/** Mojang floating_islands terrain, with land biomes and native structures. */
final class FarlandsFloating {
    static int material(long seed,int depth,int y,int tangent){
        return VanillaSkylands.solid(seed,depth-FarlandsTerrain.start(FarlandsTerrain.Stage.FLOATING),y,tangent)?1:0;
    }
    static BlockState state(long seed,int x,int y,int z){
        var p=FarlandsWorld.project(x,z);
        return material(seed,p.depth(),y,p.tangent())==0?Blocks.AIR.getDefaultState():Blocks.STONE.getDefaultState();
    }
}