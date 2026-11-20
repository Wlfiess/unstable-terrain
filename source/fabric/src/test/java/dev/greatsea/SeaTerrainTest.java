package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SeaTerrainTest {
    private final SeaConfig c=new SeaConfig();
    @Test void roundedSquareRingIncludesEveryCardinalAndCorners() {
        assertFalse(SeaTerrain.contains(c,249999,0));
        assertTrue(SeaTerrain.contains(c,249999,249999));
        for (int s:new int[]{-1,1}) {
            assertTrue(SeaTerrain.contains(c,s*250000,0));
            assertTrue(SeaTerrain.contains(c,0,s*250000));
            assertTrue(SeaTerrain.contains(c,s*3000000,0));
            assertFalse(SeaTerrain.contains(c,s*3000001,0));
        }
        assertTrue(SeaTerrain.contains(c,260000,260000));
    }
    @Test void intersectionsHandleMixedAndNegativeChunks() {
        for (int x:new int[]{-3000016,-3000000,-250016,-250000,249984,250000,3000000}) {
            boolean any=false;
            for (int dx=0;dx<16;dx++) for (int dz=0;dz<16;dz++) any|=SeaTerrain.contains(c,x+dx,dz);
            assertEquals(any,SeaTerrain.intersects(c,x,0));
        }
    }
    @Test void shelfJoinsShoreAndDeepFloorWithoutCliffs() {
        for (long seed:new long[]{0,1,-91,4200439302357479658L}) for (int side:new int[]{0,1,2,3}) {
            double previous=Double.NaN;
            for (int d=180;d<c.beachWidth+c.underwaterSlopeWidth+128;d++) {
                int r=c.innerBoundary+d;
                int x=side<2 ? (side==0?r:-r) : 37;
                int z=side>=2 ? (side==2?r:-r) : 37;
                double h=SeaTerrain.height(c,seed,x,z,64);
                assertTrue(h<280,"Mound too high");
                if (!Double.isNaN(previous)) assertTrue(Math.abs(h-previous)<1.5,"Abrupt shore at distance "+d+": "+h+" vs "+previous);
                if (SeaTerrain.shoreDistance(c,seed,x,z)>=c.beachWidth+c.underwaterSlopeWidth+128)
                    assertEquals(SeaTerrain.floorHeight(c,seed,x,z),h,1e-10);
                previous=h;
            }
        }
    }
    @Test void floorIsSeededNaturalAndStaysInRequestedRange() {
        double min=100,max=-100;
        for (int x=-12000;x<12000;x+=67) for (int z=-3000;z<3000;z+=193) {
            double h=SeaTerrain.floorHeight(c,72,x,z);
            min=Math.min(min,h);max=Math.max(max,h);
            assertTrue(h>=-30 && h<=-10);
        }
        assertTrue(max-min>10,"Floor is too flat");
        assertNotEquals(SeaTerrain.floorHeight(c,1,500,500),SeaTerrain.floorHeight(c,2,500,500));
    }
    @Test void outerShoreUsesSameContinuousProfile() {
        for (long seed:new long[]{0,19,4200439302357479658L}) {
            double width=SeaTerrain.localBeachWidth(c,seed,c.outerBoundary-550,0);
            double crest=SeaTerrain.ridgeHeight(c,seed,c.outerBoundary-550,0);
            for (int d:new int[]{1,300,700,1050}) {
                assertEquals(width,SeaTerrain.localBeachWidth(c,seed,c.outerBoundary-d,0),1e-8);
                assertEquals(crest,SeaTerrain.ridgeHeight(c,seed,c.outerBoundary-d,0),1e-8);
            }
            int end=c.outerBoundary-(int)Math.ceil(width)-500;
            assertEquals(-20,SeaTerrain.height(c,seed,end,0,64,-22),.001);
        }
        for (int d=0;d<4200;d+=17) {
            double h=SeaTerrain.height(c,19,c.outerBoundary-d,0,64);
            assertTrue(h>=-30 && h<280);
        }
        int crest=c.outerBoundary-(int)Math.round(SeaTerrain.localBeachWidth(c,19,c.outerBoundary-550,0));
        assertEquals(SeaTerrain.ridgeHeight(c,19,crest,0),SeaTerrain.height(c,19,crest,0,64),.02);
    }
    @Test void sandEmbankmentStartsUndergroundAndHasANaturalMound() {
        assertEquals(550,c.beachWidth);
        for (double original:new double[]{42,64,97,146,245}) {
            assertEquals(30,SeaTerrain.height(c,19,c.innerBoundary,0,original),1e-8);
            int crest=c.innerBoundary+(int)Math.round(SeaTerrain.localBeachWidth(c,19,c.innerBoundary+550,0));
            assertEquals(SeaTerrain.ridgeHeight(c,19,crest,0),SeaTerrain.height(c,19,crest,0,original),.02);
        }
        double halfway=0;
        for (int z=-4000;z<=4000;z+=40) halfway+=SeaTerrain.height(c,19,c.innerBoundary+275,z,64);
        assertTrue(halfway/201>115,"The beach does not climb quickly enough on average");
        assertEquals(500,c.underwaterSlopeWidth);
        int width=(int)Math.ceil(SeaTerrain.localBeachWidth(c,19,c.innerBoundary+550,0));
        int outerWidth=(int)Math.ceil(SeaTerrain.localBeachWidth(c,19,c.outerBoundary-550,0));
        for (int x:new int[]{c.innerBoundary+width+500,c.outerBoundary-outerWidth-500})
            assertEquals(-20,SeaTerrain.height(c,19,x,0,64),.001,
                    "Both smooth ocean slopes must reach their baseline exactly 500 blocks after the ridge");
    }
    @Test void coldPatchesCannotReachEitherShore() {
        assertFalse(SeaTerrain.coldEligible(c,c.innerBoundary+c.coldOceanInset-1,0));
        assertFalse(SeaTerrain.coldEligible(c,c.outerBoundary-c.coldOceanInset+1,0));
        assertTrue(SeaTerrain.coldEligible(c,c.innerBoundary+c.coldOceanInset,0));
        assertTrue(SeaTerrain.coldEligible(c,c.outerBoundary-c.coldOceanInset,0));
        assertFalse(SeaTerrain.deepFloor(c,19,c.innerBoundary+c.beachWidth+10,0));
        assertTrue(SeaTerrain.deepFloor(c,19,c.innerBoundary+c.beachWidth+c.underwaterSlopeWidth+48,0));
    }
    @Test void shorelineWindsAndKeepsTheFiveHundredBlockSlope() {
        double min=999,max=-999;
        for (int z=-4000;z<=4000;z+=13) {
            double width=SeaTerrain.localBeachWidth(c,72,c.innerBoundary+550,z);
            assertTrue(width>=502 && width<=598);
            min=Math.min(min,width);max=Math.max(max,width);
            int x=c.innerBoundary+(int)Math.ceil(width)+500;
            assertEquals(-20,SeaTerrain.height(c,72,x,z,64),.001);
        }
        assertTrue(max-min>40,"Coastline still looks ruler-straight");
    }
    @Test void beachRejectsFineVanillaReliefAndHasRoundedContours() {
        double maxCurvature=0;
        for (int x=c.innerBoundary+110;x<c.innerBoundary+490;x++) for (int z=0;z<600;z+=7) {
            double h=SeaTerrain.height(c,72,x,z,40);
            assertEquals(h,SeaTerrain.height(c,72,x,z,200),1e-8,"Raw hills leak into the dune field");
            double a=SeaTerrain.height(c,72,x,z-1,64),b=SeaTerrain.height(c,72,x,z+1,64);
            maxCurvature=Math.max(maxCurvature,Math.abs(a-2*h+b));
        }
        assertTrue(maxCurvature<.08,"Sharp ridges in the beach height field: "+maxCurvature);
    }
    @Test void ridgeVariesNaturallyAboveTheOldHeightCap() {
        double min=999,max=-999;
        for (int z=-4000;z<=4000;z+=31) {
            double h=SeaTerrain.ridgeHeight(c,72,c.innerBoundary+550,z);
            min=Math.min(min,h);max=Math.max(max,h);
            assertTrue(h>c.seaLevel);
        }
        assertTrue(max>220 && max-min>3 && max-min<10,"Ridge must follow the sample's gently varied crest");
        assertEquals(.9284936814413263,c.palmChance,1e-12);
    }
    @Test void slopeBaseHasNoEndpointCutoff() {
        long seed=19;int z=37;
        int end=c.innerBoundary+(int)Math.ceil(SeaTerrain.localBeachWidth(c,seed,c.innerBoundary+550,z))+500;
        double a=SeaTerrain.height(c,seed,end-1,z,64,-22);
        double b=SeaTerrain.height(c,seed,end,z,64,-22);
        double d=SeaTerrain.height(c,seed,end+1,z,64,-22);
        assertEquals(-20,b,.001);assertEquals(b,d,.001);
        assertTrue(Math.abs(a-b)<.001,"Slope cuts off at the floor");
    }
    @Test void vegetationUsesDepthWithAGradualEdge() {
        assertEquals(0,SeaTerrain.vegetationDensity(-14));
        assertEquals(0,SeaTerrain.vegetationDensity(0));
        assertTrue(SeaTerrain.vegetationDensity(-15)>0 && SeaTerrain.vegetationDensity(-15)<.1);
        assertTrue(SeaTerrain.vegetationDensity(-18)>SeaTerrain.vegetationDensity(-15));
        assertEquals(1,SeaTerrain.vegetationDensity(-22));
    }
    @Test void nativeFloorReliefCannotRoughenTheUnderwaterSlope() {
        long seed=72;
        for (int z=-1200;z<=1200;z+=79) {
            int crest=c.innerBoundary+(int)Math.round(SeaTerrain.localBeachWidth(c,seed,c.innerBoundary+550,z));
            double previous=Double.NaN;
            for (int x=crest+10;x<crest+490;x++) {
                double low=SeaTerrain.height(c,seed,x,z,64,-56),high=SeaTerrain.height(c,seed,x,z,64,3);
                assertEquals(low,high,1e-10,"Native sea-floor relief leaked into the descent");
                if (!Double.isNaN(previous)) assertTrue(low<=previous+.001,"Smooth ocean slope climbs back upward");
                double a=SeaTerrain.height(c,seed,x-1,z,64,-56),b=SeaTerrain.height(c,seed,x+1,z,64,-56);
                assertTrue(Math.abs(a-2*low+b)<.02,"Fine roughness remains in the ocean slope at "+x+","+z+": "+Math.abs(a-2*low+b));
                previous=low;
            }
        }
    }
    @Test void nativeOceanFamilyAndBeachClassificationShareQuartCells() {
        for (int x=480000;x<620000;x+=1024) for (int z=-180000;z<180000;z+=2048) {
            int bx=Math.floorDiv(x,4)*4,bz=Math.floorDiv(z,4)*4;
            var expected=SeaBiomeContext.biomeKey(c,72,bx+2,bz+2);
            for (int dx=0;dx<4;dx++) for (int dz=0;dz<4;dz++)
                assertEquals(expected,SeaBiomeContext.biomeKey(c,72,bx+dx,bz+dz));
        }
    }
    @Test void allNineOceanVariantsOccurInSeededSeaClimate() {
        for (long seed:new long[]{0,1,72,4200439302357479658L}) {
            var kinds=new java.util.HashSet<net.minecraft.registry.RegistryKey<net.minecraft.world.biome.Biome>>();
            for (int x=400000;x<1800000;x+=8192) for (int z=-600000;z<600000;z+=8192)
                kinds.add(SeaBiomeContext.biomeKey(c,seed,x,z));
            assertEquals(9,kinds.size(),"Missing ocean temperature/depth variants: "+kinds);
            assertTrue(kinds.stream().allMatch(SeaBiomeContext::isOcean));
        }
    }
    @Test void everyDrySeaShoreIsABeachInAllDirectionsAndBothBoundaries() {
        for (long seed:new long[]{0,72,4200439302357479658L})
        for (int boundary:new int[]{c.innerBoundary,c.outerBoundary}) for (int sign:new int[]{-1,1}) {
            int inward=boundary==c.innerBoundary ? 1 : -1;
            for (int d=4;d<c.beachWidth-60;d+=19) for (int along:new int[]{-180000,-431,0,731,180000}) {
                assertEquals(net.minecraft.world.biome.BiomeKeys.BEACH,SeaBiomeContext.biomeKey(c,seed,sign*(boundary+inward*d),along));
                assertEquals(net.minecraft.world.biome.BiomeKeys.BEACH,SeaBiomeContext.biomeKey(c,seed,along,sign*(boundary+inward*d)));
            }
        }
    }
    @Test void crestCapIsEightToThirteenBlocksWideWithoutABroadFlatShelf() {
        for (long seed:new long[]{0,19,72,4200439302357479658L}) for (int along=-1600;along<=1600;along+=173)
        for (int boundary:new int[]{c.innerBoundary,c.outerBoundary}) {
            int inward=boundary==c.innerBoundary ? 1 : -1;
            int crest=boundary+inward*(int)Math.round(SeaTerrain.localBeachWidth(c,seed,boundary+inward*c.beachWidth,along));
            double peak=SeaTerrain.ridgeHeight(c,seed,crest,along);
            int nearPeak=0;
            for (int offset=-100;offset<=100;offset++) {
                double height=SeaTerrain.height(c,seed,crest+inward*offset,along,64);
                if (height>=peak-.5) nearPeak++;
            }
            assertTrue(nearPeak>=7 && nearPeak<=14,"Over-wide beach cap at "+crest+","+along+": "+nearPeak);
            assertTrue(SeaTerrain.crestWidth(c,seed,crest,along)>=8 && SeaTerrain.crestWidth(c,seed,crest,along)<=13);
        }
    }
    @Test void palmsUseRandomSpatialPositionsWithSafeCrownSeparation() {
        var trees=PalmLayout.candidates(72,-600,-600,600,600,c.palmSpacing);
        assertTrue(trees.size()>15 && trees.size()<70,"Unexpected random palm density: "+trees.size());
        int nearCells=0;
        for (int i=0;i<trees.size();i++) {
            var a=trees.get(i);
            for (int j=i+1;j<trees.size();j++) {
                var b=trees.get(j);double distance=Math.hypot(a.x()-b.x(),a.z()-b.z());
                assertTrue(distance>=32,"Overlapping palm crowns");
                if (distance<c.palmSpacing*.7) nearCells++;
            }
        }
        assertTrue(nearCells>2,"Palm spacing still forms a regular grid");
    }
    @Test void coldWaterRibbonStaysNearThirtyTwoBlocksWideOnBothShores(){
        for(long seed:new long[]{0,72,-8647155992503787980L})for(int boundary:new int[]{c.innerBoundary,c.outerBoundary})for(int sign:new int[]{-1,1})for(int along=-1024;along<1024;along+=257)for(boolean swap:new boolean[]{false,true}){
            int inward=boundary==c.innerBoundary?1:-1,first=-1;
            for(int d=c.beachWidth;d<c.beachWidth+300;d++){
                int radial=sign*(boundary+inward*d),x=swap?along:radial,z=swap?radial:along;
                if(Math.round(SeaTerrain.height(c,seed,x,z,64))<c.seaLevel-1){first=d;break;}
            }
            assertTrue(first>c.beachWidth);
            for(int step=0;step<=40;step++){
                int radial=sign*(boundary+inward*(first+step)),x=swap?along:radial,z=swap?radial:along;
                if(step<30)assertTrue(SeaBiomeContext.coldShore(c,seed,x,z),"Missing near-shore cold ocean at "+step);
                // Radial sampling crosses a curving shoreline at an angle.
                if(step>=39)assertFalse(SeaBiomeContext.coldShore(c,seed,x,z),"Cold band extends too far at "+step);
            }
            int radial=sign*(boundary+inward*(first-1)),x=swap?along:radial,z=swap?radial:along;
            assertTrue(SeaBiomeContext.beach(c,seed,x,z));assertFalse(SeaBiomeContext.coldShore(c,seed,x,z));
        }
    }
}
