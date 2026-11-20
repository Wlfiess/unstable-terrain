package dev.greatsea;

import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnderworldTerrainTest {
    @Test void infinitelySampledTerrainRetainsReferenceReliefAndSolidThickness() {
        for (long seed:new long[]{0,72,4200439302357479658L}) {
            double min=0,max=-1000,sum=0;int count=0;int[] materials=new int[4];
            for (int x=-2000000;x<2000000;x+=17321) for (int z=-1200000;z<1200000;z+=32173) {
                var c=UnderworldTerrain.column(seed,x,z);double h=UnderworldTerrain.height(seed,x,z);
                min=Math.min(min,h);max=Math.max(max,h);sum+=h;count++;materials[c.material()]++;
                assertNotEquals(3,c.material(),"Coarse dirt remained in the Underworld surface");
                assertTrue(c.bottom()<c.top() && c.bottom()>UnderworldTerrain.MIN_Y);
                assertTrue(c.top()<UnderworldTerrain.TOP_Y-40,"Terrain intrudes into the Nether gap");
                assertTrue(c.top()-c.bottom()+1>=5,"Thin or missing Underworld foundation");
            }
            assertTrue(max-min>25,"Reference hills and valleys were flattened");
            assertTrue(sum/count>-245 && sum/count<-230,"The Underworld was not lowered twenty blocks");
            assertTrue(materials[0]/(double)count>.80 && materials[0]/(double)count<.95,"Mud proportions differ from reference");
            assertTrue(materials[1]>100 && materials[2]>50,"Missing soul terrain patches");
        }
    }
    @Test void soulSandCoverageIncreasesAboutTenPercentRelativeToPreviousRelease() {
        // These exact locations gave 5,185 soul-sand columns in the unmodified
        // 1.22.0 engine. Keep its clean soil-patch layout with a small expansion.
        int sand=0,count=0;
        for (long seed:new long[]{0,72,4200439302357479658L})
        for (int x=-2_000_000;x<2_000_000;x+=11213)
        for (int z=-1_200_000;z<1_200_000;z+=12289) {
            if (UnderworldTerrain.column(seed,x,z).material()==2) sand++;count++;
        }
        assertEquals(209916,count);
        assertTrue(sand/5185.0>=1.08 && sand/5185.0<=1.12,"Soul sand expansion was "+(sand/5185.0-1)*100+"%");
    }
    @Test void authoredCircularEngravingIsRepairedFromSurroundingReferenceRelief() throws Exception {
        var field=UnderworldTerrain.class.getDeclaredField("GRID");field.setAccessible(true);
        Object grid=field.get(null);var accessor=grid.getClass().getDeclaredMethod("heights");accessor.setAccessible(true);
        float[] heights=(float[])accessor.invoke(grid);double curvature=0;int count=0;
        // The downloaded sample's authored disc caused 1.596 RMS Laplacian here.
        // Its internal carving must disappear while the reference outside stays.
        for (int z=2;z<26;z++) for (int x=96;x<144;x++) {
            double dx=x*4-476,dz=z*4-28;if (dx*dx+dz*dz>80*80) continue;
            int i=z*256+x;double lap=heights[i-1]+heights[i+1]+heights[i-256]+heights[i+256]-4*heights[i];
            curvature+=lap*lap;count++;
        }
        assertEquals(830,count);
        assertTrue(Math.sqrt(curvature/count)<.01,"Imported circle engraving is still present");
        assertEquals(-225,UnderworldTerrain.SURFACE_BASE);
    }
    @Test void seededTerrainHasNoRepeatingTileSeamsOrCoordinateCutoffs() {
        long seed=72;double curvature=0;
        for (int x=-12000;x<=12000;x+=31) {
            double a=UnderworldTerrain.height(seed,x-1,337),b=UnderworldTerrain.height(seed,x,337),d=UnderworldTerrain.height(seed,x+1,337);
            assertTrue(Math.abs(a-b)<2 && Math.abs(d-b)<2,"Terrain step at "+x);
            curvature=Math.max(curvature,Math.abs(a-2*b+d));
        }
        assertTrue(curvature<.5,"Sharp tile boundary in the reference height field");
        assertNotEquals(UnderworldTerrain.height(1,100,200),UnderworldTerrain.height(2,100,200));
        assertNotEquals(UnderworldTerrain.height(seed,100,200),UnderworldTerrain.height(seed,2140,200));
        assertTrue(Double.isFinite(UnderworldTerrain.height(seed,29999900,-29999900)));
    }
    @Test void allSixOriginalCrystalsAreBundledWithoutStoneBuilds() {
        var models=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/assets/greatsea/underworld/crystals.json"))).getAsJsonArray();
        assertEquals(6,models.size());
        for (var element:models) {
            var model=element.getAsJsonObject();int low=999,high=-999;
            for (var state:model.getAsJsonArray("palette")) {
                String name=state.getAsJsonObject().get("Name").getAsString();
                assertTrue(name.equals("minecraft:green_stained_glass") || name.equals("minecraft:lime_stained_glass") || name.equals("minecraft:verdant_froglight"));
            }
            for (var block:model.getAsJsonArray("blocks")) {
                var b=block.getAsJsonArray();low=Math.min(low,b.get(1).getAsInt());high=Math.max(high,b.get(1).getAsInt());
                assertTrue(Math.abs(b.get(0).getAsInt())<=14 && Math.abs(b.get(2).getAsInt())<=14,"Crystal crosses placement search bounds");
            }
            assertEquals(0,low);assertTrue(high>=36 && high<=39);
        }
        int[] variants=new int[6];
        for (int x=-100;x<100;x++) for (int z=-100;z<100;z++) {
            var candidate=UnderworldTerrain.crystalCandidate(72,x,z);
            if (SeaTerrain.unit(candidate.hash())<=UnderworldTerrain.CRYSTAL_CHANCE) variants[candidate.variant()]++;
        }
        int total=java.util.Arrays.stream(variants).sum();
        for (int count:variants) assertTrue(count/(double)total>.145 && count/(double)total<.19,"A crystal variant is missing or overrepresented");
    }
}
