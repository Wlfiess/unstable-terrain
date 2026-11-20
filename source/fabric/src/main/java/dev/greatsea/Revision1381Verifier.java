package dev.greatsea;
import java.nio.file.*;
import java.util.*;
import net.minecraft.block.*;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.math.BlockPos;
/** Opt-in real FULL-chunk inspection, leaving the preview server running. */
public final class Revision1381Verifier {
 private static void check(boolean ok,String message){if(!ok)throw new AssertionError(message);}
 public static void run(MinecraftServer server){
  var result=new LinkedHashMap<String,Object>();var world=server.getOverworld();long seed=world.getSeed();var at=new BlockPos.Mutable();
  try{
   int base=GreatSea.FARLANDS.entryBoundary,winding=base+SpokelandsTerrain.start(SpokelandsTerrain.Region.WINDING)+GreatSea.FARLANDS.spokelandsWidths[3]/2;
   int upperLogs=0,upperLeaves=0,plants=0,upperRoots=0;var bands=new HashSet<Integer>();
   for(int cz=-3;cz<3;cz++)for(int cx=-3;cx<3;cx++){
    var chunk=world.getChunk(cx,(-winding>>4)+cz);
    for(int z=chunk.getPos().getStartZ();z<chunk.getPos().getStartZ()+16;z++)for(int x=chunk.getPos().getStartX();x<chunk.getPos().getStartX()+16;x++){
     int ground=SpokelandsTerrain.ground(seed,x,z);
     for(int y=ground+11;y<=319;y++){var s=chunk.getBlockState(at.set(x,y,z));if(s.isOf(Blocks.OAK_LOG)||s.isOf(Blocks.BIRCH_LOG)){upperLogs++;if(SpokelandsTerrain.material(seed,x,y-1,z)==4){var below=world.getBlockState(at.down());if(below.isOf(Blocks.DIRT)||below.isOf(Blocks.GRASS_BLOCK)){upperRoots++;bands.add((y-ground-10)/35);}}}if(s.getBlock() instanceof LeavesBlock)upperLeaves++;if(s.isOf(Blocks.SHORT_GRASS)||s.isOf(Blocks.POPPY)||s.isOf(Blocks.DANDELION))plants++;}
    }
   }
   result.put("windingUpperLogs",upperLogs);result.put("windingUpperLeaves",upperLeaves);result.put("windingUpperPlants",plants);result.put("windingTreeBands",bands);result.put("windingUpperRoots",upperRoots);check(upperRoots>=2&&upperLogs>10&&upperLeaves>100&&plants>100&&bands.size()>=2,"Missing actual vegetation on upper winding shelves: "+result);
   int dunes=base+FarlandsTerrain.start(FarlandsTerrain.Stage.SAND_DUNES)+7000,cacti=0,bushes=0;
   for(int cz=-3;cz<3;cz++)for(int cx=-3;cx<3;cx++){
    var chunk=world.getChunk((dunes>>4)+cx,cz);for(int z=chunk.getPos().getStartZ();z<chunk.getPos().getStartZ()+16;z++)for(int x=chunk.getPos().getStartX();x<chunk.getPos().getStartX()+16;x++)for(int y=110;y<=319;y++){var s=chunk.getBlockState(at.set(x,y,z));if(s.isOf(Blocks.CACTUS)){cacti++;check(s.canPlaceAt(world,at),"Unsupported cactus "+at);}if(s.isOf(Blocks.DEAD_BUSH)){bushes++;check(s.canPlaceAt(world,at),"Unsupported bush "+at);}}
   }
   result.put("duneCactusBlocks",cacti);result.put("duneDeadBushes",bushes);check(cacti>0&&cacti<100&&bushes>0&&bushes<=72,"Dune plants missing or excessive: "+result);
   int cherry=base+FarlandsTerrain.start(FarlandsTerrain.Stage.CHERRY)+6000,exposed=0;var exposedExamples=new ArrayList<String>();
   for(int cz=-2;cz<2;cz++)for(int cx=-2;cx<2;cx++){
    var chunk=world.getChunk((cherry>>4)+cx,cz);for(int z=chunk.getPos().getStartZ();z<chunk.getPos().getStartZ()+16;z++)for(int x=chunk.getPos().getStartX();x<chunk.getPos().getStartX()+16;x++)for(int y=1;y<=100;y++){
     var s=chunk.getBlockState(at.set(x,y,z));if(!s.isOf(Blocks.DIRT)&&!s.isOf(Blocks.GRASS_BLOCK))continue;
     for(var d:net.minecraft.util.math.Direction.Type.HORIZONTAL)if(!world.getBlockState(at.offset(d)).isOpaqueFullCube()){exposed++;if(exposedExamples.size()<8)exposedExamples.add(at.toShortString()+":"+s);break;}
    }
   }
   result.put("cherryExposedDirtFaces",exposed);result.put("cherryExposedExamples",exposedExamples);check(exposed==0,"Exposed Cherry dirt sides remain: "+exposed);result.put("passed",true);
  }catch(Throwable e){result.put("passed",false);result.put("failure",e.toString());e.printStackTrace();}
  try{Path p=Path.of("../revision1381/native-verification.json");Files.createDirectories(p.getParent());Files.writeString(p,new com.google.gson.GsonBuilder().setPrettyPrinting().create().toJson(result));}catch(Exception e){throw new RuntimeException(e);}
  System.out.println("REVISION_1381_NATIVE "+result);
 }
}
