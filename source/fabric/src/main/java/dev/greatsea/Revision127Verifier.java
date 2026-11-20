package dev.greatsea;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.world.gen.structure.StructureKeys;
import net.minecraft.world.gen.chunk.placement.RandomSpreadStructurePlacement;
import net.minecraft.util.math.BlockPos;
import java.util.*;
public final class Revision127Verifier {
 public static void run(MinecraftServer server){var report=new ArrayList<String>();try{if(!Boolean.getBoolean("unstableterrain.previewOnly")){verify(server.getOverworld(),report);if(!Boolean.getBoolean("unstableterrain.skipEmpirePreview"))EmpirePerspective.render(server.getOverworld(),report);}if(Boolean.getBoolean("unstableterrain.fullTerrainAudit"))FarlandsPerspective.renderAll(server.getOverworld(),report);report.add("PASS revision 1.27 integration");}catch(Throwable e){report.add("FAIL "+e);GreatSea.LOGGER.error("Revision127",e);}try{java.nio.file.Files.write(java.nio.file.Path.of(Boolean.getBoolean("unstableterrain.previewOnly")?"revision-127-preview-verification.txt":"revision-127-verification.txt"),report);}catch(Exception e){throw new RuntimeException(e);}GreatSea.LOGGER.info(String.join("\n",report));server.stop(false);}
 public static void verify(ServerWorld world,List<String> report){
  CherryGroveVerifier.verify(world,report);FarlandsTempleVerifier.verify(world,report);long seed=world.getSeed();var registry=world.getRegistryManager().getOrThrow(RegistryKeys.STRUCTURE);var trial=registry.getValueOrThrow(StructureKeys.TRIAL_CHAMBERS);var calculator=world.getChunkManager().getStructurePlacementCalculator();int base=GreatSea.FARLANDS.entryBoundary;
  int prohibited=0,found=0;var seen=new HashSet<Long>();
  for(var placement:calculator.getPlacements(registry.getEntry(trial))){if(!(placement instanceof RandomSpreadStructurePlacement spread))continue;
   for(var stage:FarlandsTerrain.Stage.values()){if(stage==FarlandsTerrain.Stage.FLOATING||stage==FarlandsTerrain.Stage.CORRUPTED)continue;int r=base+FarlandsTerrain.start(stage)+GreatSea.FARLANDS.stageWidths[stage.ordinal()]/2;var pos=spread.getStartChunk(calculator.getStructureSeed(),r>>4,0);if(FarlandsStructures.trialAllowed(pos.getCenterX(),pos.getCenterZ()))continue;var start=world.getChunk(pos.x,pos.z).getStructureStart(trial);if(start!=null&&start.hasChildren())throw new AssertionError("Trial outside Floating Lands "+stage);prohibited++;}
   for(int r:new int[]{0,62000,250000,3000000}){var pos=spread.getStartChunk(calculator.getStructureSeed(),r>>4,0);var start=world.getChunk(pos.x,pos.z).getStructureStart(trial);if(start!=null&&start.hasChildren())throw new AssertionError("Mainland trial at "+pos);prohibited++;}
   int corrupt=base+FarlandsTerrain.start(FarlandsTerrain.Stage.CORRUPTED)+2048;boolean intact=false;
   for(int i=0;i<12&&!intact;i++){
    var pos=spread.getStartChunk(calculator.getStructureSeed(),(corrupt+i*512)>>4,0);
    if(FarlandsTerrain.stage(pos.getCenterX(),pos.getCenterZ())!=FarlandsTerrain.Stage.CORRUPTED)continue;
    var start=world.getChunk(pos.x,pos.z).getStructureStart(trial);if(start==null||!start.hasChildren())continue;
    int spawners=0,gap=0;var box=start.getBoundingBox();
    for(int cx=box.getMinX()>>4;cx<=box.getMaxX()>>4;cx++)for(int cz=box.getMinZ()>>4;cz<=box.getMaxZ()>>4;cz++)for(var be:world.getChunk(cx,cz).getBlockEntities().values())if(be.getCachedState().isOf(net.minecraft.block.Blocks.TRIAL_SPAWNER)&&box.contains(be.getPos())){spawners++;if(FarlandsTerrain.missingChunk(seed,be.getPos().getX(),be.getPos().getZ()))gap++;}
    if(spawners>0&&gap>0){report.add("PASS intact corrupted trial: spawners="+spawners+", spawners across missing-chunk gaps="+gap);intact=true;}
   }
   if(!intact)throw new AssertionError("No complete corrupted trial crossing chunk gaps found");
   int floating=base+FarlandsTerrain.start(FarlandsTerrain.Stage.FLOATING)+1000;
   for(int i=0;i<60&&found<2;i++){
    var pos=spread.getStartChunk(calculator.getStructureSeed(),(floating+(i%20)*400)>>4,((i/20-1)*600)>>4);if(!FarlandsStructures.trialAllowed(pos.getCenterX(),pos.getCenterZ())||!seen.add(pos.toLong()))continue;
    var start=world.getChunk(pos.x,pos.z).getStructureStart(trial);if(start==null||!start.hasChildren())continue;
    for(var piece:start.getChildren()){var box=piece.getBoundingBox();for(int x=box.getMinX()-4;x<=box.getMaxX()+4;x++)for(int z=box.getMinZ()-4;z<=box.getMaxZ()+4;z++)for(int y:new int[]{box.getMinY()-4,box.getMaxY()+4})if(!FarlandsStructures.buried(seed,x,y,z))throw new AssertionError("Unencased trial at "+new BlockPos(x,y,z));}
    int spawners=0;var total=start.getBoundingBox();for(int cx=total.getMinX()>>4;cx<=total.getMaxX()>>4;cx++)for(int cz=total.getMinZ()>>4;cz<=total.getMaxZ()>>4;cz++)for(var be:world.getChunk(cx,cz).getBlockEntities().values())if(be.getCachedState().isOf(net.minecraft.block.Blocks.TRIAL_SPAWNER)&&total.contains(be.getPos()))spawners++;
    if(spawners==0)throw new AssertionError("Buried trial has no actual trial spawners");
    report.add("PASS buried native trial spawners="+spawners+" at "+pos+" bounds "+start.getBoundingBox()+" pieces="+start.getChildren().size());found++;
   }
  }
  report.add("PASS "+prohibited+" native trial placement candidates outside Floating and Corrupted Lands rejected; buried native trials="+found);
  int patterned=0,air=0,solid=0;
  for(int face=0;face<4;face++)for(int a=0;a<FarlandsEmpire.SAMPLE.width;a+=3)for(int y=16;y<248;y+=3){var pos=FarlandsEmpire.position(face,a,y,FarlandsEmpire.SAMPLE.depth);var actual=world.getBlockState(pos);var expected=FarlandsSamples.backdropState(pos.getX(),y,pos.getZ());if(!actual.equals(expected))throw new AssertionError("Empire backdrop mismatch "+pos);patterned++;if(actual.isAir())air++;else solid++;}
  if(air<100||solid<100)throw new AssertionError("Empire backdrop remains flat");report.add("PASS four-face empire backdrop "+patterned+" native repeating profile samples; air="+air+",solid="+solid);
 }
}

