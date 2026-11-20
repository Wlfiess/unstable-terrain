package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MainlandJoinTest {
    private final SeaConfig config=new SeaConfig();

    @Test void nativeHighlandSurvivesTheExactBoundaryRatherThanDroppingStraightToSeventy() {
        for (double height:new double[]{71,100,145,220,300}) {
            assertEquals(height,MainlandJoin.ceiling(config,height,30),1e-12);
            assertEquals(height,MainlandJoin.ceiling(config,height,29),1e-12);
            double justInside=MainlandJoin.ceiling(config,height,30.01);
            assertTrue(height-justInside<.00001,"A native hill acquires a wall at the boundary");
        }
    }

    @Test void lowTerrainKeepsTheWholeRetentionBandIncludingOriginalRiverWater() {
        for (double nativeTop:new double[]{30,42,50,63,66,70})
            for (double sandHeight:new double[]{0,30,35,50,63,69.9,70,120})
                assertEquals(70,MainlandJoin.ceiling(config,nativeTop,sandHeight),1e-12,
                        "The ceiling drops below retained river water or low mainland");
    }

    @Test void highHillCeilingLowersContinuouslyUntilTheSandEmergesWithoutAFlatCutBench() {
        double previous=MainlandJoin.ceiling(config,150,30);
        for (int sample=1;sample<400;sample++) {
            double sandHeight=30+sample*.1;
            double ceiling=MainlandJoin.ceiling(config,150,sandHeight);
            assertTrue(ceiling<previous,"High mainland forms a plateau before the beach emerges");
            assertTrue(ceiling>70,"Native hillside reaches the flat cutoff too early");
            assertTrue(previous-ceiling<.4,"Mainland taper contains an abrupt step");
            previous=ceiling;
        }
        assertEquals(70,MainlandJoin.ceiling(config,150,70),1e-12);
        assertEquals(70,MainlandJoin.ceiling(config,150,70.01),1e-12);
        assertEquals(70,MainlandJoin.ceiling(config,150,210),1e-12);
        assertTrue(MainlandJoin.ceiling(config,150,69.99)-70<.00001,"Taper has a ledge where sand takes over");
    }

    @Test void customJoinAndRetentionHeightsRemainBoundedAndMeetBothEnds() {
        config.mainlandJoinY=15;config.mainlandPreserveTopY=90;
        assertEquals(160,MainlandJoin.ceiling(config,160,15),1e-12);
        assertEquals(125,MainlandJoin.ceiling(config,160,52.5),1e-12);
        assertEquals(90,MainlandJoin.ceiling(config,160,90),1e-12);
        assertEquals(90,MainlandJoin.ceiling(config,63,52.5),1e-12);
        for (double sand=0;sand<200;sand+=.5) {
            double cap=MainlandJoin.ceiling(config,160,sand);
            assertTrue(cap>=90 && cap<=160,"Custom taper escapes its native/configured limits");
        }
    }

    @Test void equalJoinAndRetentionHeightsHaveAnExplicitFiniteZeroWidthPolicy() {
        config.mainlandJoinY=70;config.mainlandPreserveTopY=70;
        assertEquals(140,MainlandJoin.ceiling(config,140,70),1e-12);
        assertEquals(70,MainlandJoin.ceiling(config,140,70.01),1e-12);
        assertEquals(70,MainlandJoin.ceiling(config,50,70),1e-12);
    }
    @Test void nativeBiomeQueriesRestoreTheirScopeAfterNestedQueriesAndFailure() {
        assertFalse(SeaBiomeContext.samplingNative());
        assertEquals("native",SeaBiomeContext.nativeBiomeQuery(()->{
            assertTrue(SeaBiomeContext.samplingNative());
            SeaBiomeContext.nativeBiomeQuery(()->{
                assertTrue(SeaBiomeContext.samplingNative());return 1;
            });
            assertTrue(SeaBiomeContext.samplingNative());return "native";
        }));
        assertFalse(SeaBiomeContext.samplingNative());
        assertThrows(IllegalStateException.class,()->SeaBiomeContext.nativeBiomeQuery(()->{throw new IllegalStateException("test");}));
        assertFalse(SeaBiomeContext.samplingNative(),"A failed surface pass changed later stored beach biome queries");
    }
    @Test void beachClassificationStopsAtThePhysicalRingOnBothSidesInEveryDirection() {
        long seed=72;
        for (int sign:new int[]{-1,1}) for (int boundary:new int[]{config.innerBoundary,config.outerBoundary}) {
            int outside=boundary+(boundary==config.innerBoundary ? -4 : 4);
            for (int along:new int[]{-190000,0,190000}) {
                assertFalse(SeaBiomeContext.beach(config,seed,sign*outside,along));
                assertFalse(SeaBiomeContext.beach(config,seed,along,sign*outside));
            }
        }
    }

}
