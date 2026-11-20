package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SpokelandsRegression1439Test {
 private static final long SEED=4200439302357479658L;
 @Test void negativeRainbowDecorationSurvivesThePositiveCorruptedBoundary(){
  int entry=GreatSea.FARLANDS.entryBoundary;
  for(int depth=131997;depth<140000-32;depth+=97)for(int tangent:new int[]{-12000,183,45000}){
   int z=-entry-depth;
   assertEquals(SpokelandsTerrain.Region.RAINBOW_FOREST,SpokelandsTerrain.region(tangent,z));
   assertFalse(FarlandsDecoration.blocksFeatureWrite(SEED,tangent,z,true,false));
   assertFalse(FarlandsDecoration.blocksFeatureWrite(SEED,z,tangent,true,false));
   assertTrue(FarlandsDecoration.blocksFeatureWrite(SEED,tangent,z,false,false));
  }
  int positive=entry+FarlandsTerrain.start(FarlandsTerrain.Stage.CORRUPTED)+100;
  assertTrue(FarlandsDecoration.blocksFeatureWrite(SEED,positive,183,true,false));
  assertFalse(FarlandsDecoration.blocksFeatureWrite(SEED,positive,183,true,true));
 }
 @Test void sandAndForestMeetAtIdenticalWalkingElevationsOnBothFaces(){
  int edge=GreatSea.FARLANDS.entryBoundary+SpokelandsTerrain.start(SpokelandsTerrain.Region.SCULK_FOREST),wet=0,dry=0;
  for(long seed:new long[]{SEED,SEED+1337})for(int t=-600;t<=600;t+=7){
   int sand=SpokelandsTerrain.ground(seed,t,-edge+1),forest=SpokelandsTerrain.ground(seed,t,-edge);
   assertEquals(forest,sand,"An entrance cliff remains at tangent "+t);
   assertEquals(sand,SpokelandsTerrain.ground(seed,-edge+1,t));
   assertTrue(TerrainMorph.solid(SpokelandsTerrain.material(seed,t,sand,-edge+1)));
   assertTrue(Math.abs(sand-SpokelandsTerrain.ground(seed,t,-edge+17))<=1,"Last sand strip drops abruptly");
   if(SpokelandsTerrain.material(seed,t,62,-edge)==8){wet++;assertEquals(8,SpokelandsTerrain.material(seed,t,62,-edge+1));}else dry++;
  }
  assertEquals(0,wet,"River must not cross the desert boundary");assertTrue(dry>100);
 }
 @Test void plainsProfilesAndDerivedWallsHaveNoTravelAxisJogs(){
  for(long seed:new long[]{SEED,SEED+1})for(int t=-280;t<=280;t+=7)for(int y=-50;y<250;y+=7){
   int reference=PlainsFarlandsProfile.material(seed,1024,y,t),orbital=PlainsFarlandsProfile.orbitalMaterial(seed,1024,y,t);
   double natural=FarlandsNatural.classicDensity(seed,1024,y,t);
   for(int depth:new int[]{0,17,511,4096,130017,500003}){
    assertEquals(reference,PlainsFarlandsProfile.material(seed,depth,y,t));
    assertEquals(orbital,PlainsFarlandsProfile.orbitalMaterial(seed,depth,y,t));
    assertEquals(natural,FarlandsNatural.classicDensity(seed,depth,y,t));
   }
  }
 }
}
