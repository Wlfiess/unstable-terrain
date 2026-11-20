package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SandFarlandsPatternTest {
    @Test void sandAndPlainsHaveIdenticalSolidVolumesWithTheirOwnPalettes() {
        int border=GreatSea.FARLANDS.entryBoundary;
        int plains=border+FarlandsTerrain.start(FarlandsTerrain.Stage.PLAINS);
        int sand=border+FarlandsTerrain.start(FarlandsTerrain.Stage.SAND_FARLANDS);
        for(long seed:new long[]{4200439302357479658L,73L})for(int face=0;face<2;face++)for(int offset:new int[]{0,1024,6000,11999})for(int t=-256;t<=256;t+=3)for(int y=-64;y<=319;y++) {
            int px=face==0?plains+offset:t,pz=face==0?t:plains+offset;
            int sx=face==0?sand+offset:t,sz=face==0?t:sand+offset;
            int original=FarlandsTerrain.material(seed,px,y,pz),recolored=FarlandsTerrain.material(seed,sx,y,sz);
            assertEquals(original!=0,recolored!=0,"Sand Farlands must preserve the Plains block pattern");
            assertTrue(recolored==0||recolored==5||recolored==6,"Only the sand/sandstone palette belongs here");
        }
    }
}
