package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class BridgeWalkabilityTest {
 @Test void mirroredTileJoinsHaveIdenticalAdjacentColumns(){
  byte[] a=new byte[384],b=new byte[384],d=new byte[384];int start=SpokelandsTerrain.start(SpokelandsTerrain.Region.BRIDGES);
  for(int tangent=-256;tangent<768;tangent+=7)for(int edge:new int[]{0,1024,2048,3072}){
   SpokelandsBridges.fill(a,d,73,start+edge-1,tangent,false);SpokelandsBridges.fill(b,d,73,start+edge,tangent,false);assertArrayEquals(a,b,"Gap across depth tile join");
   SpokelandsBridges.fill(a,d,73,start+tangent+256,-256+edge-1,false);SpokelandsBridges.fill(b,d,73,start+tangent+256,-256+edge,false);assertArrayEquals(a,b,"Gap across tangent tile join");
  }
 }
 @Test void projectionDoesNotWarpOrRandomlyScaleTheSample(){for(int i=-4096;i<4096;i++){int a=SpokelandsBridges.fold(i,1024),b=SpokelandsBridges.fold(i+1,1024);assertTrue(a>=0&&a<1024);assertTrue(Math.abs(a-b)<=1);}}
}
