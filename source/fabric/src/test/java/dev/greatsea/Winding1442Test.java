package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Winding1442Test {
 @Test void lowestAndHighestWavesAreExactly366BlocksApart(){
  int start=SpokelandsTerrain.start(SpokelandsTerrain.Region.WINDING)+2000,entry=GreatSea.FARLANDS.entryBoundary,bottom=10000,top=-10000;
  for(int d=start;d<start+576;d+=3)for(int t=0;t<928;t+=5){int z=-entry-d;
   if(SpokelandsTerrain.material(73,t,160,z)!=0)bottom=160;
   if(SpokelandsTerrain.material(73,t,526,z)!=0)top=526;
   assertEquals(0,SpokelandsTerrain.material(73,t,527,z));
   for(int y:new int[]{160,177,194,355,470,482}){
    boolean source=TerrainSamples138.windingSolid(73,d,y-160,t);
    assertEquals(source,SpokelandsTerrain.material(73,t,y,z)!=0,"Source wave was warped by walking-floor noise");
   }
  }assertEquals(366,top-bottom);
 }
 @Test void sourceContainsNoEntireMissingDepthOrTangentStrip()throws Exception{
  try(var in=new java.io.DataInputStream(new java.util.zip.GZIPInputStream(getClass().getResourceAsStream("/assets/unstableterrain/samples/winding.volume.gz")))){
   in.readInt();int w=in.readInt(),d=in.readInt(),h=in.readInt();byte[] v=in.readAllBytes();
   for(int x=0;x<w;x++){int count=0;for(int z=0;z<d;z++)for(int y=0;y<h;y++)count+=v[(z*w+x)*h+y];assertTrue(count>0,"Empty source X strip "+x);}
   for(int z=0;z<d;z++){int count=0;for(int x=0;x<w;x++)for(int y=0;y<h;y++)count+=v[(z*w+x)*h+y];assertTrue(count>0,"Empty source Z strip "+z);}
  }
 }
}
