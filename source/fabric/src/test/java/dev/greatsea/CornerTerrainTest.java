package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CornerTerrainTest {
    private final SeaConfig c=new SeaConfig();
    @Test void rightAnglesHaveOnlyASmallTangentBlendAtBothSquareBoundaries() {
        assertEquals(16,SeaTerrain.cornerRadius(c));
        for (int boundary:new int[]{c.innerBoundary,c.outerBoundary})
        for (int sx:new int[]{-1,1}) for (int sz:new int[]{-1,1}) {
            assertEquals(0,SeaTerrain.shoreDistance(c,sx*boundary,0),1e-10);
            assertEquals(0,SeaTerrain.shoreDistance(c,0,sz*boundary),1e-10);
            for (int inset:new int[]{32,100,550,1050}) {
                assertEquals(0,SeaTerrain.shoreDistance(c,sx*boundary,sz*(boundary-inset)),1e-10);
                assertEquals(0,SeaTerrain.shoreDistance(c,sx*(boundary-inset),sz*boundary),1e-10);
            }
            assertEquals(0,SeaTerrain.shoreDistance(c,sx*(boundary-4),sz*(boundary-4)),1e-10);
            assertFalse(SeaTerrain.contains(c,sx*(c.innerBoundary-5),sz*(c.innerBoundary-5)));
            assertTrue(SeaTerrain.contains(c,sx*(c.outerBoundary-5),sz*(c.outerBoundary-5)));
        }
    }
    @Test void chunkIntersectionMatchesBlockContainmentAtEveryCorner() {
        for (int boundary:new int[]{c.innerBoundary,c.outerBoundary})
        for (int sx:new int[]{-1,1}) for (int sz:new int[]{-1,1})
        for (int ox=-80;ox<=80;ox+=13) for (int oz=-80;oz<=80;oz+=17) {
            int x=(sx*(boundary+ox))&~15,z=(sz*(boundary+oz))&~15;boolean any=false;
            for (int dx=0;dx<16;dx++) for (int dz=0;dz<16;dz++) any|=SeaTerrain.contains(c,x+dx,z+dz);
            assertEquals(any,SeaTerrain.intersects(c,x,z),"Missed corner chunk "+x+","+z);
        }
    }
    @Test void squareContoursRemainSmoothAcrossTheDiagonalWithoutAFloorCliff() {
        for (long seed:new long[]{0,72,4200439302357479658L})
        for (int boundary:new int[]{c.innerBoundary,c.outerBoundary}) {
            int direction=boundary==c.innerBoundary ? 1 : -1;
            for (int d:new int[]{100,300,400,700,900,1050,1200}) {
                int q=boundary+direction*d;
                double a=SeaTerrain.height(c,seed,q-1,q+1,64,-20);
                double b=SeaTerrain.height(c,seed,q,q,64,-20);
                double e=SeaTerrain.height(c,seed,q+1,q-1,64,-20);
                assertTrue(Math.abs(a-2*b+e)<.3,"Hard diagonal seam at depth "+d+": "+Math.abs(a-2*b+e)+" seed="+seed+" boundary="+boundary);
            }
            double previous=Double.NaN;
            for (int d=600;d<1200;d++) {
                int q=boundary+direction*d;
                double h=SeaTerrain.height(c,seed,q,q,64,-20);
                if (!Double.isNaN(previous)) assertTrue(Math.abs(h-previous)<1.5,"Sharp coast/floor corner join");
                previous=h;
            }
        }
    }
}
