package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeasuredTerrainTest {
    @Test void lowRandomChunksHaveAirAboveTheirMeasuredSurface() {
        long seed=4200439302357479658L;
        int start=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.CHUNKS)+1024;
        int low=0;
        for(int dx=0;dx<256;dx+=8)for(int z=-128;z<128;z+=8) {
            int x=start+dx,top=RandomChunkReference.height(seed,x,z);
            if(top>=63)continue;
            low++;
            for(int y=top+1;y<=63;y++)assertEquals(0,FarlandsTerrain.material(seed,x,y,z),"Water above a low chunk surface");
            assertNotEquals(0,FarlandsTerrain.material(seed,x,top,z));
        }
        assertTrue(low>200,"Survey must cover low profiles");
    }
    @Test void beachSurfaceMatchesMeasuredBroadCrestAndContinuousEndpoints() {
        for(int along=0;along<2040;along+=13) {
            assertEquals(0,BeachReference.rise(0,along),1e-9);assertEquals(1,BeachReference.rise(1,along),1e-9);
            assertTrue(BeachReference.measured(1,along)>210 && BeachReference.measured(1,along)<230);
            double last=0;
            for(int step=1;step<=550;step++) {
                double h=BeachReference.rise(step/550.0,along);
                assertTrue(h>=0 && h<=1);assertTrue(Math.abs(h-last)<.009,"Spurious cliff in measured surface");last=h;
            }
        }
    }
    @Test void randomChunkHeightDistributionRetainsShortFieldsAndRareTallHills() {
        int low=0,tall=0;double sum=0;
        for(int cx=0;cx<100;cx++)for(int cz=0;cz<100;cz++) {
            int h=RandomChunkReference.height(4200439302357479658L,cx*16+8,cz*16+8);
            assertTrue(h>=-58 && h<=178);if(h<110)low++;if(h>130)tall++;sum+=h;
        }
        assertTrue(low>9000 && tall>0 && tall<500);assertTrue(sum/10000>55 && sum/10000<80);
    }
    @Test void randomProfilesKeepLocalReliefAndSeedVariation() {
        int changing=0,seedDifference=0;
        for(int cx=0;cx<40;cx++)for(int cz=0;cz<40;cz++) {
            int x=cx*16,z=cz*16;
            if(RandomChunkReference.height(72,x,z)!=RandomChunkReference.height(72,x+15,z+15))changing++;
            if(RandomChunkReference.height(72,x,z)!=RandomChunkReference.height(73,x,z))seedDifference++;
        }
        assertTrue(changing>1200 && seedDifference>1400);
    }
}
