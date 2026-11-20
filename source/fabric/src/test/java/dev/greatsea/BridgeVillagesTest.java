package dev.greatsea;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import static org.junit.jupiter.api.Assertions.*;
class BridgeVillagesTest {
 @Test void originalFurnitureAndBuildingPropertiesSurviveExtraction(){
  var source=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/bridge-village-source.json"))).getAsJsonArray();byte[] blocks=new byte[384];
  for(var e:source){var o=e.getAsJsonObject();int x=o.get("x").getAsInt(),y=o.get("y").getAsInt(),d=SpokelandsTerrain.start(SpokelandsTerrain.Region.BRIDGES)+o.get("z").getAsInt()-12580480;
   assertEquals(o.get("state"),JsonParser.parseString(BridgeVillages.description(d,x,y)));BridgeVillages.fill(blocks,d,x);assertEquals(SpokelandsTerrain.BRIDGE_VILLAGE,blocks[y+64]);}
 }
}
