package dev.greatsea;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.Heightmap;
import net.minecraft.world.chunk.Chunk;
import java.util.EnumSet;

public final class FarlandsGeneration {
    private static final BlockState[] MATERIALS={Blocks.AIR.getDefaultState(),Blocks.STONE.getDefaultState(),Blocks.DEEPSLATE.getDefaultState(),
            Blocks.DIRT.getDefaultState(),Blocks.GRASS_BLOCK.getDefaultState(),Blocks.SAND.getDefaultState(),Blocks.SANDSTONE.getDefaultState(),
            Blocks.SNOW_BLOCK.getDefaultState(),Blocks.WATER.getDefaultState(),Blocks.GRAVEL.getDefaultState(),Blocks.BEDROCK.getDefaultState(),
            Blocks.STONE.getDefaultState(),Blocks.DIRT.getDefaultState(),Blocks.GRASS_BLOCK.getDefaultState(),Blocks.OAK_LOG.getDefaultState(),
            Blocks.OAK_LEAVES.getDefaultState(),Blocks.SANDSTONE.getDefaultState(),Blocks.IRON_ORE.getDefaultState(),Blocks.COAL_ORE.getDefaultState(),
            Blocks.COPPER_ORE.getDefaultState(),Blocks.GOLD_ORE.getDefaultState(),Blocks.DIAMOND_ORE.getDefaultState(),Blocks.REDSTONE_ORE.getDefaultState(),
            Blocks.LAPIS_ORE.getDefaultState(),Blocks.MOSS_BLOCK.getDefaultState(),Blocks.CLAY.getDefaultState(),Blocks.OBSIDIAN.getDefaultState(),
            Blocks.SHORT_GRASS.getDefaultState(),Blocks.SNOW.getDefaultState(),Blocks.LAVA.getDefaultState(),Blocks.POPPY.getDefaultState(),Blocks.DANDELION.getDefaultState(),
            Blocks.OAK_PLANKS.getDefaultState(),Blocks.OAK_FENCE.getDefaultState(),Blocks.RAIL.getDefaultState().with(net.minecraft.state.property.Properties.RAIL_SHAPE,net.minecraft.block.enums.RailShape.EAST_WEST),
            Blocks.COBWEB.getDefaultState(),Blocks.IRON_CHAIN.getDefaultState(),Blocks.TERRACOTTA.getDefaultState(),Blocks.RAIL.getDefaultState(),Blocks.MYCELIUM.getDefaultState(),Blocks.PACKED_ICE.getDefaultState()};
    private FarlandsGeneration() {}
    public static boolean intersects(Chunk chunk) {
        int x=chunk.getPos().getStartX(),z=chunk.getPos().getStartZ();
        return FarlandsWorld.contains(x,z)||FarlandsWorld.contains(x+15,z)||FarlandsWorld.contains(x,z+15)||FarlandsWorld.contains(x+15,z+15);
    }
    public static boolean fullyManaged(Chunk chunk) {
        int x=chunk.getPos().getStartX(),z=chunk.getPos().getStartZ();
        return FarlandsWorld.contains(x,z)&&FarlandsWorld.contains(x+15,z)&&FarlandsWorld.contains(x,z+15)&&FarlandsWorld.contains(x+15,z+15);
    }
    public static BlockState state(long seed,int x,int y,int z) {
        return GeneratedIcePolicy.generated(rawState(seed,x,y,z),false,y);
    }
    private static BlockState rawState(long seed,int x,int y,int z) {
        if(FarlandsMargins.active(x,z))return FarlandsMargins.state(seed,x,y,z);
        return unblendedState(seed,x,y,z);
    }
    static BlockState unblendedState(long seed,int x,int y,int z) {
        if(SpokelandsTerrain.contains(x,z))return SpokelandsTerrain.state(seed,x,y,z);
        var stage=FarlandsTerrain.stage(x,z);
        if(stage==FarlandsTerrain.Stage.REPEATING) {
            if(FarlandsEmpire.backdrop(x,z))return FarlandsSamples.backdropState(x,y,z);
            if(FarlandsEmpire.contains(x,y,z))return FarlandsEmpire.state(x,y,z);
        }
        if(stage==FarlandsTerrain.Stage.ORBITALS && (FarlandsOrbitals.gap(seed,x,z) || FarlandsOrbitals.hasBlock(seed,x,y,z)))return FarlandsOrbitals.state(seed,x,y,z);
        if(stage==FarlandsTerrain.Stage.SPLIT && FarlandsTemple.contains(x,y,z))return FarlandsTemple.state(x,y,z);
        int material=FarlandsTerrain.rawMaterial(seed,x,y,z);
        if(FarlandsWorld.contains(x,z) && FarlandsTerrain.nativeStage(stage)) {
            if(material==0)return Blocks.AIR.getDefaultState();
            if(stage==FarlandsTerrain.Stage.REPEATING && FarlandsRepeatingContents.changed(material))return FarlandsRepeatingContents.state(material,x,z);
            if(FarlandsSamples.basalWater(stage) && y>=-64 && y<=-60)return Blocks.WATER.getDefaultState();
            return FarlandsSamples.state(seed,x,y,z);
        }
        if(stage==FarlandsTerrain.Stage.FLOATING && (material==1 || material==2))return FarlandsFloating.state(seed,x,y,z);
        BlockState state=MATERIALS[material];
        if((material==34 || material==38) && Math.abs((long)z)>Math.abs((long)x))state=MATERIALS[material==34?38:34];
        if(material==37)state=switch(Math.floorMod(y,12)) {
            case 0,1 -> Blocks.RED_TERRACOTTA.getDefaultState();
            case 4 -> Blocks.WHITE_TERRACOTTA.getDefaultState();
            case 5 -> Blocks.YELLOW_TERRACOTTA.getDefaultState();
            case 8,9 -> Blocks.BROWN_TERRACOTTA.getDefaultState();
            default -> Blocks.ORANGE_TERRACOTTA.getDefaultState();
        };

        if(material==7 && stage!=FarlandsTerrain.Stage.SPIKES)state=Blocks.GRASS_BLOCK.getDefaultState();
        // Support sand/gravel ledges over the open void without gravity collapsing
        // imported shelves as soon as a nearby block is touched.
        if((material==5 || material==9) && FarlandsTerrain.rawMaterial(seed,x,y-1,z)==0)
            return (material==5 ? Blocks.SANDSTONE:Blocks.STONE).getDefaultState();
        return state;
    }
    public static Chunk fill(Chunk chunk,long seed) {
        int startX=chunk.getPos().getStartX(),startZ=chunk.getPos().getStartZ();
        BlockPos.Mutable p=new BlockPos.Mutable();
        Heightmap.populateHeightmaps(chunk,EnumSet.of(Heightmap.Type.OCEAN_FLOOR_WG,Heightmap.Type.WORLD_SURFACE_WG));
        for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++) {
            int x=startX+dx,z=startZ+dz;
            if(!FarlandsWorld.contains(x,z))continue;
            for(int y=chunk.getBottomY();y<=chunk.getTopYInclusive();y++) {
                BlockState state=state(seed,x,y,z);
                if(!state.equals(chunk.getBlockState(p.set(x,y,z))))chunk.setBlockState(p,state,0);
            }
        }
        BridgeVillages.populate(chunk);
        if(!SpokelandsTerrain.contains(startX+8,startZ+8)){
            FarlandsOrbitals.populate(chunk,seed);
            FarlandsTemple.populate(chunk);
            FarlandsRepeatingContents.populate(chunk,seed);
            FarlandsEmpire.populate(chunk);
        }
        return chunk;
    }
}
