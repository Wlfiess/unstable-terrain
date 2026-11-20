package dev.greatsea;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.WorldGenLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.StructurePiece;
import java.lang.reflect.*;
import java.nio.file.Path;
import java.util.List;

public final class PaperBridge {
    public static Path configDirectory(){return PaperPlugin.instance.getDataFolder().toPath();}
    private static final Field MONUMENT_CHILDREN;
    static {
        try{MONUMENT_CHILDREN=net.minecraft.world.level.levelgen.structure.structures.OceanMonumentPieces.MonumentBuilding.class.getDeclaredField("childPieces");MONUMENT_CHILDREN.setAccessible(true);}
        catch(Exception e){throw new ExceptionInInitializerError(e);}
    }
    @SuppressWarnings("unchecked") public static List<StructurePiece> monumentChildren(Object building){
        try{return (List<StructurePiece>)MONUMENT_CHILDREN.get(building);}catch(Exception e){throw new IllegalStateException(e);}
    }
    static boolean ominous(BlockState state){return state.is(Blocks.VAULT) && state.getValue(VaultBlock.OMINOUS);}
    static WorldGenLevel protectedFeatures(WorldGenLevel delegate,boolean nether){
        return (WorldGenLevel)Proxy.newProxyInstance(PaperBridge.class.getClassLoader(),new Class[]{WorldGenLevel.class},(proxy,method,args)->{
            if(method.getName().equals("getSeaLevel") && (OceanStructures.inStructurePass() || SeaDecoration.inOceanVegetationPass()))return GreatSea.CONFIG.seaLevel;
            if(method.getName().equals("setBlock") && args!=null && args.length>=2 && args[0] instanceof BlockPos pos && args[1] instanceof BlockState state){
                if(!nether&&MistRegion.frost(delegate.getSeed(),pos.getX(),pos.getZ())){args=args.clone();args[1]=MistTerrain.frozen(state);state=(BlockState)args[1];}
                if(GeneratedIcePolicy.isIce(state)&&!( !nether&&MistRegion.contains(pos.getX(),pos.getZ()))){
                    boolean ocean=GeneratedIcePolicy.ocean(delegate.getBiome(pos));
                    int waterLevel=!nether&&SeaTerrain.contains(GreatSea.CONFIG,pos.getX(),pos.getZ())?GreatSea.CONFIG.seaLevel:delegate.getSeaLevel();
                    args=args.clone();args[1]=GeneratedIcePolicy.generated(state,ocean,delegate.getFluidState(pos).is(net.minecraft.tags.FluidTags.WATER),pos.getY(),waterLevel);state=(BlockState)args[1];
                }
                if(state.is(Blocks.ANCIENT_DEBRIS) || ominous(state)&&!( !nether && ForbiddenWorldBlocks.allowsOminous(pos.getX(),pos.getZ()))){args=args.clone();args[1]=Blocks.AIR.defaultBlockState();state=(BlockState)args[1];}
                if(nether && pos.getY()<0)return false;
                int x=pos.getX(),z=pos.getZ();
                if(!nether && FarlandsWorld.contains(x,z)){
                    if(!SpokelandsTerrain.contains(x,z)&&FarlandsEmpire.contains(x,pos.getY(),z))return false;
                    if(FarlandsDecoration.blocksFeatureWrite(delegate.getSeed(),x,z,FarlandsDecoration.isGenerating(),FarlandsStructures.placing()))return false;
                    if(!SpokelandsTerrain.contains(x,z)&&FarlandsTemple.footprint(x,z)&&!state.isAir())return false;
                }
                if(!nether && SeaTerrain.contains(GreatSea.CONFIG,x,z)){
                    if(SeaDecoration.inOceanVegetationPass()){
                        if(!SeaDecoration.permitsOceanVegetation(delegate,pos,state))return false;
                        try{return method.invoke(delegate,args);}catch(InvocationTargetException e){throw e.getCause();}
                    }
                    if(!OceanStructures.inStructurePass() || !SeaTerrain.deepFloor(GreatSea.CONFIG,delegate.getSeed(),x,z)){
                        var chunk=delegate.getChunk(x>>4,z>>4);
                        if(!SeaGeneration.preservesMainland(chunk,x,z))return false;
                        int profile=SeaGeneration.height(delegate.getSeed(),x,z);
                        if(pos.getY()>Math.max(SeaGeneration.retainedCeiling(chunk,x,z),profile) && state.isSolid() && !state.is(BlockTags.LOGS) && !state.is(BlockTags.LEAVES))return false;
                    }
                }
            }
            try{return method.invoke(delegate,args);}catch(InvocationTargetException e){throw e.getCause();}
        });
    }
}

