package dev.greatsea;
import org.junit.jupiter.api.Test;
import java.io.*;import java.util.zip.GZIPInputStream;import java.security.MessageDigest;import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;
class WindingContinuityTest {
 @Test void capturedGeometryIsPreservedAndMissingCaptureBandIsReconstructed()throws Exception{
  try(var in=new DataInputStream(new GZIPInputStream(getClass().getResourceAsStream("/assets/unstableterrain/samples/winding.volume.gz")))){
   assertEquals(0x55574e31,in.readInt());int w=in.readInt(),d=in.readInt(),h=in.readInt();assertEquals(288,w);assertEquals(464,d);assertEquals(35,h);byte[] a=in.readAllBytes();var digest=MessageDigest.getInstance("SHA-256");
   for(int z=0;z<d;z++)digest.update(a,z*w*h,196*h);
   assertEquals("c1c4eab2b6e2520779a14e720be426cf255c93ee8a54f76fd7b4c5c8f6fdf699",HexFormat.of().formatHex(digest.digest()));
   for(int x=196;x<w;x++){int solid=0;for(int z=0;z<d;z++)for(int y=0;y<h;y++)solid+=a[(z*w+x)*h+y];assertTrue(solid>0,"Missing download band at "+x);}
  }
 }
 @Test void originalThirtyFiveBlockCycleIsRetained(){for(int x=-200;x<200;x+=3)for(int z=-80;z<80;z+=3)for(int y=0;y<35;y++)assertEquals(TerrainSamples138.windingSolid(73,x,y,z),TerrainSamples138.windingSolid(73,x,y+35,z));}
 @Test void fiveHundredBareGrassColumnsStopAtTheOriginalWaves(){
  int start=SpokelandsTerrain.end(SpokelandsTerrain.Region.RED_PLAINS),waves=SpokelandsTerrain.windingStart();
  assertEquals(500,waves-start);
  int edge=GreatSea.FARLANDS.entryBoundary+start;
  int firstUpperRibbons=0;
  for(int tangent=-192;tangent<=192;tangent+=19){
   int redY=SpokelandsTerrain.ground(73,tangent,1-edge);
   int firstY=SpokelandsTerrain.ground(73,tangent,-edge);
   assertTrue(Math.abs(redY-firstY)<=1);
   assertEquals(1,SpokelandsTerrain.material(73,tangent,0,-edge-500));
   for(int d=0;d<500;d++){
    int radial=-edge-d,ground=SpokelandsTerrain.ground(73,tangent,radial);
    assertEquals(4,SpokelandsTerrain.material(73,tangent,ground,radial));
    assertEquals(4,SpokelandsTerrain.material(73,radial,ground,tangent));
    assertEquals(0,SpokelandsTerrain.material(73,tangent,ground-41,radial));
    assertEquals(0,SpokelandsDecoration.windingVegetationAtDepth(start+d));
    for(int y=ground+1;y<=526;y++)assertEquals(0,SpokelandsTerrain.material(73,tangent,y,radial));
   }
   for(int offset:new int[]{0,1,48,191,192})for(int y=160;y<=527;y++){
    int z=-edge-500-offset,q=y-160,depth=waves+offset;
    boolean occupied=SpokelandsTerrain.windingWave(73,depth,q,tangent);
    int expected=!occupied?0:SpokelandsTerrain.windingWave(73,depth,q+1,tangent)?3:4;
    assertEquals(expected,SpokelandsTerrain.material(73,tangent,y,z));
    assertEquals(expected,SpokelandsTerrain.material(73,z,y,tangent));
    if(offset==0&&y>450&&occupied)firstUpperRibbons++;
   }
  }
  assertTrue(firstUpperRibbons>0);
 }
 @Test void windingWavesContainNoStoneEvenInsideTheirThickerParts(){
  int entry=GreatSea.FARLANDS.entryBoundary,start=SpokelandsTerrain.start(SpokelandsTerrain.Region.WINDING),checked=0;
  for(int d=600;d<1100;d+=13)for(int x=-160;x<160;x+=11){int z=-entry-start-d,ground=SpokelandsTerrain.ground(73,x,z);
   for(int y=ground+10;y<319;y++){int m=SpokelandsTerrain.material(73,x,y,z);assertTrue(m==0||m==3||m==4,"Stone inside a winding ribbon");if(m!=0)checked++;}
  }
  assertTrue(checked>1000);
 }
 @Test void completeSourceRibbonsFinishAtNaturalCycleAirGaps(){
  for(int depth=-200;depth<200;depth+=3)for(int tangent=-160;tangent<160;tangent+=3)
   assertEquals(TerrainSamples138.windingSolid(73,depth,0,tangent),TerrainSamples138.windingSolid(73,depth,35,tangent),"Sample cycle is discontinuous");
 }
 @Test void everyExposedWindingTopIsGrass(){
  int entry=GreatSea.FARLANDS.entryBoundary,start=SpokelandsTerrain.start(SpokelandsTerrain.Region.WINDING),checked=0;
  for(int d=600;d<1100;d+=11)for(int x=-160;x<160;x+=9){int z=-entry-start-d,ground=SpokelandsTerrain.ground(73,x,z);
   for(int y=ground+10;y<319;y++)if(SpokelandsTerrain.material(73,x,y,z)!=0&&SpokelandsTerrain.material(73,x,y+1,z)==0){assertEquals(4,SpokelandsTerrain.material(73,x,y,z));checked++;}
  }assertTrue(checked>1000);
 }
}
