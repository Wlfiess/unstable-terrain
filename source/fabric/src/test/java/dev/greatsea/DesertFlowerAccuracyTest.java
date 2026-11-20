package dev.greatsea;
import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;
class DesertFlowerAccuracyTest {
 static final long SEED=4200439302357479658L;
 @Test void flowerValleysOccupyMoreAreaThanHighCrestsAndExposeGrayCliffs(){
  int center=GreatSea.FARLANDS.entryBoundary+SpokelandsTerrain.start(SpokelandsTerrain.Region.FLOWER_HILLS)+9000;
  int count=0,high=0,cliff=0,meadow=0;
  for(int dz=-384;dz<384;dz+=6)for(int x=-384;x<384;x+=6){
   int z=-center-dz,y=SpokelandsTerrain.ground(SEED,x,z),m=SpokelandsTerrain.material(SEED,x,y,z);count++;assertTrue(y>=139&&y<=179,"Flower relief exceeds forty blocks");if(y>163)high++;
   assertEquals(SpokelandsTerrain.MOSS,m,"Stone visible from top down");if(m==SpokelandsTerrain.MOSS)meadow++;
   int adjacent=Math.min(Math.min(SpokelandsTerrain.ground(SEED,x-1,z),SpokelandsTerrain.ground(SEED,x+1,z)),Math.min(SpokelandsTerrain.ground(SEED,x,z-1),SpokelandsTerrain.ground(SEED,x,z+1)));
   for(int h=y-6;h<=y-3;h++){int side=SpokelandsTerrain.material(SEED,x,h,z);if(side==1||side==SpokelandsTerrain.ANDESITE||side==SpokelandsTerrain.MOSSY_ROCK||side==SpokelandsTerrain.SMOOTH_ROCK)cliff++;}
   for(int h=y-2;h<y;h++)if(SpokelandsTerrain.material(SEED,x,h,z)==3){
    assertTrue(SpokelandsTerrain.ground(SEED,x-1,z)>=h&&SpokelandsTerrain.ground(SEED,x+1,z)>=h&&SpokelandsTerrain.ground(SEED,x,z-1)>=h&&SpokelandsTerrain.ground(SEED,x,z+1)>=h,"Exposed dirt pixel on the gray mountain face");
   }
  }
  assertTrue(high<count*.4&&high>0);assertEquals(count,meadow);assertTrue(cliff>100);
 }
 @Test void redSandRetainsOpenSkyAndSubstantialConnectedOverhangs(){
  int center=GreatSea.FARLANDS.entryBoundary+SpokelandsTerrain.start(SpokelandsTerrain.Region.SAND_LANDS)+9000;
  int count=0,roofs=0,open=0;
  for(int dz=-384;dz<384;dz+=8)for(int x=-384;x<384;x+=8){
   int z=-center-dz,ground=SpokelandsTerrain.ground(SEED,x,z);count++;
   // The 46:23 reference shows patterned sandstone risers among the grainy sand basins.
   int surface=SpokelandsTerrain.material(SEED,x,ground,z);
   assertTrue(surface==SpokelandsTerrain.RED_SAND||surface==SpokelandsTerrain.RED_SANDSTONE,"The walking floor must retain the reference sand/sandstone palette");
   boolean roof=false;for(int y=ground+20;y<320;y++)if(SpokelandsTerrain.material(SEED,x,y,z)!=0){roof=true;break;}
   if(roof)roofs++;else open++;
  }
  assertTrue(roofs>count*.15&&roofs<count*.65,"Sand roofs must not form a nearly closed blanket");assertTrue(open>count*.35);
 }
 @Test void duneHeightCalibrationIsMonotoneAndRetainsTheMeasuredVerticalRange(){
  int previous=31;
  for(double h=3;h<=177;h+=.05){int y=TerrainSamples138.duneElevation(h);assertTrue(y>=previous&&y<=317);previous=y;}
  assertEquals(31,TerrainSamples138.duneElevation(3));assertEquals(151,TerrainSamples138.duneElevation(68));assertEquals(317,TerrainSamples138.duneElevation(177));
 }
}
