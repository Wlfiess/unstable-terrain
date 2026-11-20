package dev.greatsea;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.structure.StructureStart;
import net.minecraft.util.math.*;
import net.minecraft.util.math.random.*;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.*;
import net.minecraft.world.gen.chunk.ChunkGenerator;
import net.minecraft.world.gen.structure.Structure;

/** Native structure starts and loot, placed against the actual sky/Farlands density. */
public final class FarlandsStructures {
    private static final ThreadLocal<Boolean> PLACING=ThreadLocal.withInitial(()->false);
    private FarlandsStructures(){}
    public static boolean placing(){return PLACING.get();}
    public static boolean enabled(int x,int z){
        if(!FarlandsWorld.contains(x,z))return false;
        if(SpokelandsTerrain.contains(x,z))return SpokelandsTerrain.region(x,z)==SpokelandsTerrain.Region.RED_PLAINS;
        return FarlandsTerrain.stage(x,z)==FarlandsTerrain.Stage.FLOATING || FarlandsTerrain.stage(x,z)==FarlandsTerrain.Stage.CORRUPTED;
    }
    public static boolean accepts(RegistryKey<Structure> key,int x,int z){
        if(!enabled(x,z))return false;
        String path=key.getValue().getPath();
        if(SpokelandsTerrain.contains(x,z))return path.equals("mineshaft");
        if(FarlandsTerrain.stage(x,z)!=FarlandsTerrain.Stage.FLOATING)return path.equals("trial_chambers");
        return !path.equals("monument")&&!path.contains("ocean")&&!path.contains("shipwreck")&&!path.contains("buried_treasure")&&
            !path.contains("nether")&&!path.contains("bastion")&&!path.contains("fortress")&&!path.contains("end_city");
    }
    public static boolean trialAllowed(int x,int z){return (MistRegion.contains(x,z)||FarlandsWorld.contains(x,z)&&!SpokelandsTerrain.contains(x,z)&&(FarlandsTerrain.stage(x,z)==FarlandsTerrain.Stage.FLOATING||FarlandsTerrain.stage(x,z)==FarlandsTerrain.Stage.CORRUPTED));}
    /** Retain vanilla mineshaft pieces and loot in the crimson plains biome. */
    public static boolean spokeMineSelected(long seed,int chunkX,int chunkZ){return SeaTerrain.unit(SeaTerrain.hash(seed+18432,chunkX,chunkZ))<.10;}
    public static int spokeMineShift(long seed,int x,int z,int minY,int maxY){
        int target=SpokelandsTerrain.ground(seed,x,z)+140;
        return Math.min(target-(minY+maxY)/2,315-maxY);
    }
    public static boolean createSpokeMines(net.minecraft.structure.StructureSet.WeightedEntry entry,StructureAccessor structures,
            net.minecraft.registry.DynamicRegistryManager registries,net.minecraft.world.gen.noise.NoiseConfig noise,
            net.minecraft.structure.StructureTemplateManager templates,long seed,Chunk chunk,ChunkPos pos,ChunkSectionPos section,
            net.minecraft.registry.RegistryKey<net.minecraft.world.World> dimension,ChunkGenerator generator){
        if(!spokeMineSelected(seed,pos.x,pos.z))return false;
        var structure=entry.structure().value();var previous=structures.getStructureStart(section,structure,chunk);
        var start=structure.createStructureStart(entry.structure(),dimension,registries,generator,generator.getBiomeSource(),noise,templates,seed,pos,previous==null?0:previous.getReferences(),chunk,biome->true);
        if(!start.hasChildren())return false;structures.setStructureStart(section,structure,start,chunk);return true;
    }
    /** Reject every mainland/non-floating trial and move surviving starts wholly into native island stone. */
    public static void prepareTrials(Chunk chunk,long seed,net.minecraft.registry.Registry<Structure> registry){
        for(var entry:new java.util.HashMap<>(chunk.getStructureStarts()).entrySet()){
            var key=registry.getKey(entry.getKey()).orElseThrow();
            var candidate=entry.getValue();var candidateOrigin=candidate.getPos();
            if(candidate.hasChildren()&&SpokelandsTerrain.contains(candidateOrigin.getCenterX(),candidateOrigin.getCenterZ())&&key.getValue().getPath().startsWith("mineshaft")){
                if(!spokeMineSelected(seed,candidateOrigin.x,candidateOrigin.z)){chunk.setStructureStart(entry.getKey(),StructureStart.DEFAULT);continue;}
                var bounds=candidate.getBoundingBox();
                int dy=spokeMineShift(seed,candidateOrigin.getCenterX(),candidateOrigin.getCenterZ(),bounds.getMinY(),bounds.getMaxY());
                for(var piece:candidate.getChildren())piece.translate(0,dy,0);
                chunk.setStructureStart(entry.getKey(),new StructureStart(candidate.getStructure(),candidateOrigin,candidate.getReferences(),new net.minecraft.structure.StructurePiecesList(candidate.getChildren())));
                continue;
            }
            if(!key.getValue().getPath().equals("trial_chambers"))continue;
            var start=entry.getValue();if(!start.hasChildren())continue;
            var origin=start.getPos();
            if(!trialAllowed(origin.getCenterX(),origin.getCenterZ())){chunk.setStructureStart(entry.getKey(),StructureStart.DEFAULT);continue;}
            if(MistRegion.contains(origin.getCenterX(),origin.getCenterZ())||FarlandsTerrain.stage(origin.getCenterX(),origin.getCenterZ())==FarlandsTerrain.Stage.CORRUPTED)continue;
            int shift=Integer.MIN_VALUE;
            for(int bottom=48;bottom<=192;bottom+=8){
                int dy=bottom-start.getBoundingBox().getMinY();boolean fits=true;
                for(var piece:start.getChildren()){
                    var box=piece.getBoundingBox();
                    if(box.getMaxY()+dy+4>=256){fits=false;break;}
                    // Check the entire expanded volume: rooms must be buried, not hanging in air.
                    for(int x=box.getMinX()-4;x<=box.getMaxX()+4&&fits;x+=4)
                        for(int z=box.getMinZ()-4;z<=box.getMaxZ()+4&&fits;z+=4)
                            for(int y=box.getMinY()+dy-4;y<=box.getMaxY()+dy+4;y+=4){
                                var p=FarlandsWorld.project(x,z);
                                if(!trialAllowed(x,z)||!VanillaSkylands.solid(seed,p.depth()-FarlandsTerrain.start(FarlandsTerrain.Stage.FLOATING),y,p.tangent())){fits=false;break;}
                            }
                    if(fits){
                        int lo=box.getMinY()+dy-4,hi=box.getMaxY()+dy+4;
                        for(int x=box.getMinX()-4;x<=box.getMaxX()+4&&fits;x++)for(int z=box.getMinZ()-4;z<=box.getMaxZ()+4&&fits;z++){
                            if(!buried(seed,x,lo,z)||!buried(seed,x,hi,z))fits=false;
                        }
                        for(int y=lo;y<=hi&&fits;y++){
                            for(int x=box.getMinX()-4;x<=box.getMaxX()+4&&fits;x++)if(!buried(seed,x,y,box.getMinZ()-4)||!buried(seed,x,y,box.getMaxZ()+4))fits=false;
                            for(int z=box.getMinZ()-4;z<=box.getMaxZ()+4&&fits;z++)if(!buried(seed,box.getMinX()-4,y,z)||!buried(seed,box.getMaxX()+4,y,z))fits=false;
                        }
                    }
                    if(!fits)break;
                }
                if(fits){shift=dy;break;}
            }
            if(shift==Integer.MIN_VALUE){chunk.setStructureStart(entry.getKey(),StructureStart.DEFAULT);continue;}
            for(var piece:start.getChildren())piece.translate(0,shift,0);
            // Rebuild the start so both references and clipping see the translated bounding box.
            chunk.setStructureStart(entry.getKey(),new StructureStart(start.getStructure(),origin,start.getReferences(),new net.minecraft.structure.StructurePiecesList(start.getChildren())));
        }
    }
    public static boolean buried(long seed,int x,int y,int z){var p=FarlandsWorld.project(x,z);return FarlandsWorld.contains(x,z)&&!SpokelandsTerrain.contains(x,z)&&FarlandsTerrain.stage(x,z)==FarlandsTerrain.Stage.FLOATING&&VanillaSkylands.solid(seed,p.depth()-FarlandsTerrain.start(FarlandsTerrain.Stage.FLOATING),y,p.tangent());}
    /** Supply the underground checks expected by vanilla pieces without adding rock to the sky.
     * All block/entity writes still go directly to the real generation region. */
    private static StructureWorldAccess floatingMineWorld(StructureWorldAccess world,StructureStart start){
        var roofs=start.getChildren().stream().map(piece->piece.getBoundingBox()).toList();
        return (StructureWorldAccess)java.lang.reflect.Proxy.newProxyInstance(StructureWorldAccess.class.getClassLoader(),new Class<?>[]{StructureWorldAccess.class},(proxy,method,args)->{
            if(args!=null&&args.length==3&&args[0] instanceof net.minecraft.world.Heightmap.Type&&method.getReturnType()==int.class)return world.getTopYInclusive()+1;
            try{
                Object result=method.invoke(world,args);
                if(result instanceof net.minecraft.block.BlockState state&&state.isAir()&&args!=null&&args.length==1&&args[0] instanceof BlockPos pos){
                    for(var roof:roofs)if(pos.getY()==roof.getMaxY()+1&&pos.getX()>=roof.getMinX()&&pos.getX()<=roof.getMaxX()&&pos.getZ()>=roof.getMinZ()&&pos.getZ()<=roof.getMaxZ())return net.minecraft.block.Blocks.STONE.getDefaultState();
                }
                return result;
            }catch(java.lang.reflect.InvocationTargetException e){throw e.getCause();}
        });
    }
    public static void generate(ChunkGenerator generator,StructureWorldAccess world,Chunk chunk,StructureAccessor structures){
        if(!structures.shouldGenerateStructures())return;
        var registry=world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);
        var all=registry.stream().sorted(java.util.Comparator.comparing(structure->registry.getKey(structure).orElseThrow().getValue().toString())).toList();var pos=chunk.getPos();
        var section=ChunkSectionPos.from(pos,world.getBottomSectionCoord());
        var random=new ChunkRandom(new Xoroshiro128PlusPlusRandom(0));
        long population=random.setPopulationSeed(world.getSeed(),pos.getStartX(),pos.getStartZ());
        var box=new BlockBox(pos.getStartX(),chunk.getBottomY()+1,pos.getStartZ(),pos.getStartX()+15,chunk.getTopYInclusive(),pos.getStartZ()+15);
        boolean previous=PLACING.get();PLACING.set(true);
        try{
            for(var step:GenerationStep.Feature.values()){
                int index=0;
                for(var structure:all){
                    if(structure.getFeatureGenerationStep()!=step)continue;
                    int decorator=index++;var key=registry.getKey(structure).orElseThrow();
                    random.setDecoratorSeed(population,decorator,step.ordinal());
                    world.setCurrentlyGeneratingStructureName(()->String.valueOf(key.getValue()));
                    for(StructureStart start:structures.getStructureStarts(section,structure)){
                        var origin=start.getPos();
                        if(accepts(key,origin.getCenterX(),origin.getCenterZ())){
                            boolean mine=SpokelandsTerrain.contains(origin.getCenterX(),origin.getCenterZ())&&key.getValue().getPath().startsWith("mineshaft");
                            start.place(mine?floatingMineWorld(world,start):world,structures,generator,random,box,pos);
                        }
                    }
                }
            }
        }finally{world.setCurrentlyGeneratingStructureName(null);PLACING.set(previous);}
    }
}


