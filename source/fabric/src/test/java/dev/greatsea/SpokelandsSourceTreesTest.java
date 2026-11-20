package dev.greatsea;
import com.google.gson.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.GZIPInputStream;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpokelandsSourceTreesTest {
    @Test void allTwentySourceTreesRetainTheirDetailedPaletteAndExcludeTheDisplayPlatform()throws Exception{
        try(var in=new InputStreamReader(new GZIPInputStream(getClass().getResourceAsStream("/assets/unstableterrain/spokelands/dark-trees.json.gz")),StandardCharsets.UTF_8)){
            var asset=JsonParser.parseReader(in).getAsJsonObject();var palette=asset.getAsJsonArray("palette");
            var trees=asset.getAsJsonArray("trees");assertEquals(20,trees.size());int total=0,vines=0,stairs=0;var shapes=new HashSet<String>();
            for(var element:trees){
                var tree=element.getAsJsonObject();shapes.add(tree.getAsJsonArray("blocks").toString());int bottom=1000;
                for(var entry:tree.getAsJsonArray("blocks")){
                    var b=entry.getAsJsonArray();bottom=Math.min(bottom,b.get(1).getAsInt());
                    var state=palette.get(b.get(3).getAsInt()).getAsJsonObject();String name=state.get("Name").getAsString();
                    assertFalse(Set.of("minecraft:air","minecraft:white_concrete","minecraft:white_concrete_powder","minecraft:lime_concrete","minecraft:netherite_block","minecraft:cherry_wood").contains(name));
                    if(name.endsWith("_leaves"))assertEquals("true",state.getAsJsonObject("Properties").get("persistent").getAsString());
                    if(name.equals("minecraft:vine"))vines++;if(name.endsWith("_stairs"))stairs++;total++;
                }
                assertEquals(0,bottom);
            }
            assertEquals(47764,total);assertEquals(20,shapes.size());assertTrue(vines>2000);assertTrue(stairs>1000);
        }
    }
    @Test void rainbowUsesOnlyUnscaledHoraceSourceTreesWithNoDisplayPlatform()throws Exception{
        try(var in=new InputStreamReader(new GZIPInputStream(getClass().getResourceAsStream("/assets/unstableterrain/spokelands/rainbow-trees.json.gz")),StandardCharsets.UTF_8)){
            var asset=JsonParser.parseReader(in).getAsJsonObject();assertTrue(asset.get("source").getAsString().contains("Horace"));
            var trees=asset.getAsJsonArray("trees");assertEquals(20,trees.size());var ids=new HashSet<Integer>();int total=0;
            for(var element:trees){var t=element.getAsJsonObject();assertTrue(ids.add(t.get("sourceCandidate").getAsInt()));
                for(var entry:t.getAsJsonArray("blocks")){var b=entry.getAsJsonArray();assertTrue(Math.abs(b.get(0).getAsInt())<=26&&Math.abs(b.get(2).getAsInt())<=26);assertTrue(b.get(1).getAsInt()>=0&&b.get(1).getAsInt()<70);
                    String name=asset.getAsJsonArray("palette").get(b.get(3).getAsInt()).getAsJsonObject().get("Name").getAsString();
                    assertFalse(Set.of("minecraft:white_concrete","minecraft:lime_concrete","minecraft:netherite_block").contains(name));total++;
                }
            }
            assertEquals(38248,total);
        }
    }
    @Test void approvedRedTreeKeepsCoralSupportedByMossAndContainsNoBirch()throws Exception{
        try(var in=new InputStreamReader(new GZIPInputStream(getClass().getResourceAsStream("/assets/unstableterrain/spokelands/red-tree.json.gz")),StandardCharsets.UTF_8)){
            var asset=JsonParser.parseReader(in).getAsJsonObject();var palette=asset.getAsJsonArray("palette");
            assertEquals(8,asset.getAsJsonArray("trees").size());var blocks=asset.getAsJsonArray("trees").get(0).getAsJsonObject().getAsJsonArray("blocks");
            assertEquals(1609,blocks.size());var occupied=new HashMap<String,String>();int coral=0;boolean paleOak=false;
            for(var entry:blocks){var b=entry.getAsJsonArray();String name=palette.get(b.get(3).getAsInt()).getAsJsonObject().get("Name").getAsString();
                assertFalse(name.contains("birch"));assertNotEquals("minecraft:smooth_stone",name);
                paleOak|=name.equals("minecraft:pale_oak_stairs");occupied.put(b.get(0)+","+b.get(1)+","+b.get(2),name);
            }
            for(var entry:blocks){var b=entry.getAsJsonArray();String name=palette.get(b.get(3).getAsInt()).getAsJsonObject().get("Name").getAsString();
                if(name.equals("minecraft:dead_horn_coral_fan")){coral++;assertEquals("minecraft:pale_moss_block",occupied.get(b.get(0)+","+(b.get(1).getAsInt()-1)+","+b.get(2)));}
            }
            assertEquals(248,coral);assertTrue(paleOak);
        }
    }
}
