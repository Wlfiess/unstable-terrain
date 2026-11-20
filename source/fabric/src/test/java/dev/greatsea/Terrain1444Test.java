package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Terrain1444Test {
 @Test void everyRiverWaterFaceTouchesWaterOrSolidBankAndNeverSand(){
  long seed=4200439302357479658L;int start=SpokelandsTerrain.start(SpokelandsTerrain.Region.SCULK_FOREST),b=GreatSea.FARLANDS.entryBoundary,water=0;
  for(int d=start-32;d<start+700;d+=3)for(int t=-280;t<=280;t+=3)for(int y=61;y<=62;y++){
   int z=-b-d,m=SpokelandsTerrain.material(seed,t,y,z);if(d<start)assertNotEquals(8,m,"River starts in sand");if(m!=8)continue;water++;
   for(var q:new int[][]{{1,0},{-1,0},{0,1},{0,-1}})assertNotEquals(0,SpokelandsTerrain.material(seed,t+q[0],y,z+q[1]),"Exposed lateral water at "+t+","+y+","+z);
  }assertTrue(water>100);
 }
 @Test void compactSplitPreservesTheTempleAndLaterBiomeCoordinates(){
  assertEquals(1024,GreatSea.FARLANDS.stageWidths[6]);assertEquals(78000,FarlandsTerrain.end(FarlandsTerrain.Stage.SPLIT));assertEquals(77488,FarlandsTemple.center());
  var c=new FarlandsConfig();c.stageWidths[5]=10000;c.stageWidths[6]=12000;c.validate();assertEquals(20976,c.stageWidths[5]);assertEquals(1024,c.stageWidths[6]);
 }
}
