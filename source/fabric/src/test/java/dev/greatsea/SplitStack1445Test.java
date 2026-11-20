package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SplitStack1445Test {
 @Test void sampledShelvesContinueEvery51BlocksTowardVoidWithoutBuildings(){
  int c=FarlandsTemple.center(),checked=0;
  for(int d=-200;d<=200;d+=7)for(int t=-200;t<=200;t+=7){if(Math.hypot(d,t)<82)continue;
   for(int y=-64;y<=145;y+=3){assertEquals(FarlandsTerrain.splitReferenceMaterial(c+d,y+51,t),FarlandsTerrain.splitReferenceMaterial(c+d,y,t));if(FarlandsTerrain.splitReferenceMaterial(c+d,y,t)!=0)checked++;}
  }assertTrue(checked>10000);
  for(int y=-64;y<169;y++)assertFalse(FarlandsTemple.contains(GreatSea.FARLANDS.entryBoundary+c,y,0));
  assertEquals(0,FarlandsTerrain.splitReferenceMaterial(c+100,-65,100));
 }
 @Test void importHasNoDetachedTinyTerrainFragments()throws Exception{
  try(var in=new java.io.DataInputStream(new java.util.zip.GZIPInputStream(getClass().getResourceAsStream("/assets/unstableterrain/farlands/split-reference.bin.gz")))){
   assertEquals(0x55465232,in.readInt());int w=in.readInt(),d=in.readInt(),bottom=in.readInt(),h=in.readInt(),n=in.readInt();for(int i=0;i<n;i++){in.readByte();in.skipNBytes(in.readInt());}
   int[] v=new int[w*d*h];for(int i=0;i<v.length;i++)v[i]=in.readUnsignedShort();
   for(int y=1;y<h-1;y++)for(int z=1;z<d-1;z++)for(int x=1;x<w-1;x++){
    int i=(y*d+z)*w+x;if(v[i]==0||v[i+w*d]!=0||v[i-w*d]==0)continue;
    boolean isolated=v[i-1]==0&&v[i+1]==0&&v[i-w]==0&&v[i+w]==0;
    int below=i-w*d,neighbors=(v[below-1]>0?1:0)+(v[below+1]>0?1:0)+(v[below-w]>0?1:0)+(v[below+w]>0?1:0);
    assertFalse(isolated&&neighbors>=3,"Single block bump remains");
   }
  }
 }
}
