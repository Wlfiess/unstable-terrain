package dev.greatsea;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import java.util.HashSet;
import java.util.Set;
import java.util.ArrayDeque;
import static org.junit.jupiter.api.Assertions.*;

class RegressionTest {
    private record P(int x,int y,int z) {}
    @Test void suppliedPalmsHaveRealGroundRootsAndNoFloatingWood() {
        var trees=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream("/assets/greatsea/palms.json"))).getAsJsonArray();
        int[] counts={229,215,155,151,157},heights={25,20,22,19,22},roots={1,4,3,1,3};
        assertEquals(5,trees.size(),"All five supplied palm models must be bundled");
        for (int i=0;i<trees.size();i++) {
            var tree=trees.get(i).getAsJsonObject();
            var palette=tree.getAsJsonArray("palette");
            Set<P> wood=new HashSet<>();int maxY=0;
            for (var block:tree.getAsJsonArray("blocks")) {
                var a=block.getAsJsonArray();
                P p=new P(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt());
                maxY=Math.max(maxY,p.y);
                if (palette.get(a.get(3).getAsInt()).getAsJsonObject().get("Name").getAsString().contains("wood")) wood.add(p);
            }
            assertEquals(counts[i],tree.getAsJsonArray("blocks").size());
            assertEquals(heights[i],maxY+1);
            assertEquals(roots[i],wood.stream().filter(p -> p.y==0).count());
            assertTrue(wood.stream().allMatch(p -> p.y>=0),"Trunk must start at the actual ground anchor");
            for (var state:palette) {
                var definition=state.getAsJsonObject();
                if (definition.get("Name").getAsString().endsWith("_leaves"))
                    assertEquals("true",definition.getAsJsonObject("Properties").get("persistent").getAsString(),"Palm leaves must not decay");
            }
            assertTrue(wood.contains(new P(0,0,0)),"True root must be at ground anchor");
            Set<P> visited=new HashSet<>();ArrayDeque<P> queue=new ArrayDeque<>();queue.add(new P(0,0,0));
            while (!queue.isEmpty()) {
                P p=queue.remove();if (!visited.add(p)) continue;
                for (int dx=-1;dx<=1;dx++) for (int dy=-1;dy<=1;dy++) for (int dz=-1;dz<=1;dz++) {
                    P n=new P(p.x+dx,p.y+dy,p.z+dz);
                    if (wood.contains(n) && !visited.contains(n)) queue.add(n);
                }
            }
            assertEquals(wood,visited,"Every wood block must connect to the grounded trunk");
        }
    }
    @Test void everySuppliedPalmModelIsSelectedAcrossCandidates() {
        int[] selected=new int[5];
        for (int i=0;i<20000;i++) selected[SeaDecoration.palmVariant(SeaTerrain.hash(72,i,0),selected.length)]++;
        for (int count:selected) assertTrue(count>3600 && count<4400,"Palm variants should all appear at similar rates");
    }
    @Test void oldDefaultConfigIsUpgradedWhenReplacingJar() {
        SeaConfig c=SeaConfig.parse("{\"beachWidth\":2048,\"underwaterSlopeWidth\":2048,\"palmSpacing\":96,\"minFloorY\":0,\"maxFloorY\":30}");
        assertEquals(550,c.beachWidth); assertEquals(500,c.underwaterSlopeWidth);
        assertEquals(194,c.palmSpacing);assertEquals(-30,c.minFloorY);assertEquals(-10,c.maxFloorY);
        assertEquals(210,c.beachPeakY);assertEquals(18,c.kelpMaxHeight);assertEquals(31,c.coldKelpMaxHeight);
        assertEquals(20000,c.coldOceanInset);assertEquals(70,c.mainlandPreserveTopY);assertEquals(12,c.configVersion);
    }
    @Test void version110ConfigUpgradesTheOceanSlopeTo500() {
        SeaConfig c=SeaConfig.parse("{\"configVersion\":3,\"underwaterSlopeWidth\":875}");
        assertEquals(500,c.underwaterSlopeWidth);assertEquals(550,c.beachWidth);
        assertEquals(12,c.configVersion);
    }
    @Test void explicitCustomConfigValuesArePreserved() {
        SeaConfig c=SeaConfig.parse("{\"beachWidth\":700,\"palmSpacing\":300}");
        assertEquals(700,c.beachWidth);assertEquals(300,c.palmSpacing);
    }
    @Test void version130MigratesKelpToBedRelativeHeightsAndDistantColdPatches() {
        SeaConfig c=SeaConfig.parse("{\"configVersion\":6,\"coldKelpTopY\":120,\"coldOceanInset\":20000,\"palmChance\":0.7475}");
        assertEquals(18,c.kelpMaxHeight);assertEquals(31,c.coldKelpMaxHeight);
        assertEquals(20000,c.coldOceanInset);assertEquals(70,c.mainlandPreserveTopY);
        assertEquals(.9284936814413263,c.palmChance,1e-12);assertEquals(12,c.configVersion);
        SeaConfig custom=SeaConfig.parse("{\"configVersion\":6,\"coldOceanInset\":4000,\"kelpMaxHeight\":12,\"mainlandPreserveTopY\":80}");
        assertEquals(4000,custom.coldOceanInset);assertEquals(12,custom.kelpMaxHeight);assertEquals(80,custom.mainlandPreserveTopY);
    }
    @Test void version140DefaultsUpgradeWhileExplicitChoicesRemain() {
        SeaConfig defaults=SeaConfig.parse("{\"configVersion\":7,\"coldOceanInset\":1000,\"kelpMaxHeight\":24,\"coldKelpMaxHeight\":32,\"palmChance\":0.7475}");
        assertEquals(20000,defaults.coldOceanInset);assertEquals(18,defaults.kelpMaxHeight);assertEquals(31,defaults.coldKelpMaxHeight);
        assertEquals(.9284936814413263,defaults.palmChance,1e-12);assertEquals(194,defaults.palmSpacing);assertEquals(12,defaults.configVersion);
        SeaConfig custom=SeaConfig.parse("{\"configVersion\":7,\"coldOceanInset\":30000,\"kelpMaxHeight\":16,\"coldKelpMaxHeight\":22,\"palmChance\":0.5}");
        assertEquals(30000,custom.coldOceanInset);assertEquals(16,custom.kelpMaxHeight);assertEquals(22,custom.coldKelpMaxHeight);
        assertEquals(.5,custom.palmChance,1e-12);
        SeaConfig current=SeaConfig.parse("{\"configVersion\":8,\"coldOceanInset\":1000,\"kelpMaxHeight\":24,\"coldKelpMaxHeight\":32,\"palmChance\":0.7475}");
        assertEquals(1000,current.coldOceanInset);assertEquals(24,current.kelpMaxHeight);assertEquals(32,current.coldKelpMaxHeight);
        assertEquals(.7475,current.palmChance,1e-12);
    }
    @Test void version150ColdKelpDefaultGetsTwentyPercentGrowthAndCustomLimitsRemain() {
        SeaConfig defaults=SeaConfig.parse("{\"configVersion\":8,\"coldKelpMaxHeight\":26,\"kelpMaxHeight\":18}");
        assertEquals(31,defaults.coldKelpMaxHeight);assertEquals(18,defaults.kelpMaxHeight);assertEquals(12,defaults.configVersion);
        SeaConfig custom=SeaConfig.parse("{\"configVersion\":8,\"coldKelpMaxHeight\":22,\"kelpMaxHeight\":16}");
        assertEquals(22,custom.coldKelpMaxHeight);assertEquals(16,custom.kelpMaxHeight);
        SeaConfig current=SeaConfig.parse("{\"configVersion\":9,\"coldKelpMaxHeight\":26}");
        assertEquals(26,current.coldKelpMaxHeight);
    }
    @Test void coldKelpGrowsTwentyPercentAcrossTheDistributionWhileWarmKelpIsUnchanged() {
        SeaConfig config=new SeaConfig();long oldColdSum=0,newColdSum=0;
        for (int i=0;i<200000;i++) {
            long hash=SeaTerrain.hash(4200439302357479658L+93,i,0);
            double height=.2+.8*SeaTerrain.unit(SeaTerrain.hash(hash,7,9))*SeaTerrain.unit(SeaTerrain.hash(hash,13,5));
            int oldCold=2+(int)Math.round(height*24),oldWarm=2+(int)Math.round(height*16);
            int cold=SeaDecoration.kelpLength(config,true,hash),warm=SeaDecoration.kelpLength(config,false,hash);
            oldColdSum+=oldCold;newColdSum+=cold;
            assertEquals(oldWarm,warm,"Warm kelp was changed by the cold-only height adjustment");
            assertTrue(cold<=31 && cold>=8,"Cold kelp escaped the realistic stalk range");
        }
        double increase=newColdSum/(double)oldColdSum;
        assertTrue(increase>1.19 && increase<1.21,"The whole cold-kelp height distribution must rise by 20%: "+increase);
        config.coldKelpMaxHeight=22;
        for (int i=0;i<10000;i++) assertTrue(SeaDecoration.kelpLength(config,true,SeaTerrain.hash(72,i,0))<=22);
    }
}

