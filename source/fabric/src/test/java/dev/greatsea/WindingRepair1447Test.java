package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class WindingRepair1447Test {
 @Test void everyMeasuredRibbonContinuesAcrossTheTileAndHealthyInteriorIsUnchanged()throws Exception{
  try(var in=new java.io.DataInputStream(new java.util.zip.GZIPInputStream(getClass().getResourceAsStream("/assets/unstableterrain/samples/winding.volume.gz")))){
   assertEquals(0x55574e31,in.readInt());int w=in.readInt(),d=in.readInt(),h=in.readInt();byte[] v=in.readAllBytes();
   var digest=java.security.MessageDigest.getInstance("SHA-256");digest.update(v,112*w*h,(432-112)*w*h);
   assertEquals("588c389cd3e7a0863945f342ff9519d6c2eb8bfc4497cf51c2bbde9d00ae14c9",java.util.HexFormat.of().formatHex(digest.digest()));
   int vertical=0,horizontal=0;
   for(int x=0;x<w;x++){int count=0;for(int z=112;z<432;z++)for(int y=0;y<h;y++)count+=v[(z*w+x)*h+y];if(count/320.0<=1.7)continue;vertical++;
    for(int z=0;z<d-1;z++){boolean connected=false;for(int y=0;y<h;y++)connected|=v[(z*w+x)*h+y]!=0&&v[((z+1)*w+x)*h+y]!=0;assertTrue(connected,"Broken longitudinal ribbon x="+x+", z="+z);}
   }
   for(int z=0;z<d;z++){int count=0;for(int x=0;x<220;x++)for(int y=0;y<h;y++)count+=v[(z*w+x)*h+y];if(count/220.0<=1.7)continue;horizontal++;
    for(int x=0;x<w-1;x++){boolean connected=false;for(int y=0;y<h;y++)connected|=v[(z*w+x)*h+y]!=0&&v[(z*w+x+1)*h+y]!=0;assertTrue(connected,"Broken transverse ribbon x="+x+", z="+z);}
   }assertTrue(vertical>70&&horizontal>100);
  }
 }
}
