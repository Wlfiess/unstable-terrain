package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Terrain1440Test {
 @Test void windingContinuesAboveTheOldCeilingWithoutStone(){
  int d=SpokelandsTerrain.start(SpokelandsTerrain.Region.WINDING)+2000,z=-GreatSea.FARLANDS.entryBoundary-d,upper=0;
  for(int x=-128;x<128;x+=5){int g=SpokelandsTerrain.ground(73,x,z);
   for(int y=160;y<=526;y++){int m=SpokelandsTerrain.material(73,x,y,z);assertTrue(m==0||m==3||m==4);if(y>319&&m!=0)upper++;}
   assertEquals(0,SpokelandsTerrain.material(73,x,527,z));
  }assertTrue(upper>100);
 }
 @Test void measuredSculkReliefRetainsBothLowGroundAndBroadRises(){
  int lo=100,hi=-100;for(int x=0;x<384;x+=3)for(int z=0;z<384;z+=3){int h=TerrainSamples138.sculkRelief(73,x,z);lo=Math.min(lo,h);hi=Math.max(hi,h);}assertTrue(lo<0);assertTrue(hi>4);assertTrue(hi<16);assertTrue(hi-lo<50);
 }
}
