package dev.greatsea;
import org.junit.jupiter.api.Test;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.security.MessageDigest;
import java.util.HexFormat;
import static org.junit.jupiter.api.Assertions.*;

class FabricTerrainParityTest {
    @Test void allNineNegativeRegionsMatchFabricOnBothNegativeFaces() throws Exception {
        var source=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/fabric-1.50.1-spoke-fixture.json"))).getAsJsonObject();
        for(var region:SpokelandsTerrain.Region.values()) {
            int distance=GreatSea.FARLANDS.entryBoundary+(SpokelandsTerrain.start(region)+SpokelandsTerrain.end(region))/2;
            var digest=MessageDigest.getInstance("SHA-256");
            for(int face=0;face<2;face++)for(int t=-96;t<96;t+=3)for(int d=0;d<96;d+=3)for(int y=-64;y<320;y+=3) {
                int x=face==0?-distance-d:t,z=face==0?t:-distance-d;
                digest.update((byte)FarlandsTerrain.material(source.get("seed").getAsLong(),x,y,z));
            }
            assertEquals(source.getAsJsonArray("digests").get(region.ordinal()).getAsString(),HexFormat.of().formatHex(digest.digest()),"Negative route parity: "+region);
        }
    }
    @Test void allSeventeenTerrainRegionsMatchTheFabric1260ReferenceOnAllFourFaces() throws Exception {
        var source=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/fabric-1.50.1-material-fixture.json"))).getAsJsonObject();
        var expected=source.getAsJsonArray("digests");
        long seed=source.get("seed").getAsLong();int border=source.get("entry").getAsInt();
        for(var stage:FarlandsTerrain.Stage.values()){
            int distance=border+FarlandsTerrain.start(stage)+GreatSea.FARLANDS.stageWidths[stage.ordinal()]/2;
            var digest=MessageDigest.getInstance("SHA-256");
            for(int face=0;face<4;face++)for(int z=-64;z<64;z+=4)for(int dx=0;dx<128;dx+=4)for(int y=-64;y<320;y+=4){
                int outward=distance+dx,x=face<2?(face==0?outward:-outward):z,wz=face<2?z:(face==2?outward:-outward);
                digest.update((byte)FarlandsTerrain.material(seed,x,y,wz));
            }
            assertEquals(expected.get(stage.ordinal()).getAsString(),HexFormat.of().formatHex(digest.digest()),"Paper terrain differs from Fabric in "+stage);
        }
    }
    @Test void earlyGrassPatchesAndLateSkygridMatchFabricAtSingleBlockResolution() throws Exception {
        var source=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/fabric-1.50.1-material-fixture.json"))).getAsJsonObject();
        long seed=source.get("seed").getAsLong();int grid=source.get("entry").getAsInt()+FarlandsTerrain.start(FarlandsTerrain.Stage.SKYGRID);
        for(int phase=0;phase<2;phase++){
            int distance=grid+(int)(GreatSea.FARLANDS.stageWidths[16]*(phase==0?.05:.83));var digest=MessageDigest.getInstance("SHA-256");
            for(int face=0;face<4;face++)for(int z=-16;z<16;z++)for(int dx=0;dx<64;dx++)for(int y=64;y<96;y++){
                int outward=distance+dx,x=face<2?(face==0?outward:-outward):z,wz=face<2?z:(face==2?outward:-outward);
                digest.update((byte)FarlandsTerrain.material(seed,x,y,wz));
            }
            assertEquals(source.getAsJsonArray("skygridFineDigests").get(phase).getAsString(),HexFormat.of().formatHex(digest.digest()));
        }
    }
}
