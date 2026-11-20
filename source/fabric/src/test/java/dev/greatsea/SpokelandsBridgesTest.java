package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import com.google.gson.*;
import java.util.*;
class SpokelandsBridgesTest {
 @Test void generatedColumnsMatchIndependentSourceHashes()throws Exception{
  var records=JsonParser.parseReader(new java.io.InputStreamReader(getClass().getResourceAsStream("/bridge-source-1480.json"))).getAsJsonArray();
  for(var e:records){var o=e.getAsJsonObject();int x=o.get("x").getAsInt(),z=o.get("z").getAsInt();byte[] blocks=new byte[384],directions=new byte[384];
   SpokelandsBridges.fillTerrain(blocks,directions,73,SpokelandsTerrain.start(SpokelandsTerrain.Region.BRIDGES)+z,SpokelandsBridges.SOURCE_X+x,false);
   assertEquals(o.get("sha256").getAsString(),HexFormat.of().formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(Arrays.copyOf(blocks,192))));
   for(int y=192;y<384;y++)assertEquals(0,blocks[y],"No invented upper towers");
  }
 }
 @Test void originalElevationAndMaterialsStayUnscaled(){byte[] b=new byte[384],d=new byte[384];int top=SpokelandsBridges.fillTerrain(b,d,73,SpokelandsTerrain.start(SpokelandsTerrain.Region.BRIDGES)+387,29,false);assertTrue(top<=120);for(byte m:b)assertTrue(m==0||m==1||m==60||m>=68&&m<=82);}
 @Test void actualSpruceBridgeSpansFromUnfilteredSaveArePresent(){
  var records=JsonParser.parseReader(new java.io.InputStreamReader(getClass().getResourceAsStream("/bridge-raw-spans-1480.json"))).getAsJsonArray();byte[] b=new byte[384],d=new byte[384];
  for(var e:records){var o=e.getAsJsonObject();int x=o.get("x").getAsInt(),y=o.get("y").getAsInt(),z=o.get("z").getAsInt();SpokelandsBridges.fillTerrain(b,d,73,SpokelandsTerrain.start(SpokelandsTerrain.Region.BRIDGES)+z-SpokelandsBridges.SOURCE_Z,x,false);assertEquals(82,b[y+64],"A real connecting span was removed at "+x+","+y+","+z);}
 }
}
