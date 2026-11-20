package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarlandsRevisedSpecTest {
    @Test void oldWidthsMigrateWithoutLosingOtherRegions() {
        var config=new FarlandsConfig();
        config.stageWidths=new int[]{12000,10000,12000,10000,12000,12000,12000,14000,14000,14000,18000,18000,36000,48000,48000};
        config.validate();
        assertEquals(17,config.stageWidths.length);
        assertEquals(FarlandsTerrain.Stage.GRAVEL_AFTER_SPIKES,FarlandsTerrain.Stage.values()[5]);
        assertEquals(20976,config.stageWidths[5]);assertEquals(1024,config.stageWidths[6]);
        assertEquals(1024,config.stageWidths[12]);assertEquals(2500,config.stageWidths[15]);assertEquals(48000,config.stageWidths[16]);
    }
    @Test void waterIsFiveBlocksOverVoidAndGridContainsOnlyGrass() {
        long seed=4200439302357479658L;int border=GreatSea.FARLANDS.entryBoundary;
        int water=border+FarlandsTerrain.start(FarlandsTerrain.Stage.WATER)+100;
        for(int y=-64;y<320;y++)assertEquals(y>=-64 && y<=-60 ? 8:0,FarlandsTerrain.material(seed,water,y,0));
        int grid=border+FarlandsTerrain.start(FarlandsTerrain.Stage.SKYGRID)+1026;
        int grass=0;
        for(int z=0;z<64;z++)for(int y=0;y<64;y++) {
            int m=FarlandsTerrain.material(seed,grid,y,z);assertTrue(m==0 || m==4);if(m==4)grass++;
        }
        assertEquals(121,grass);
    }
    @Test void spikesHaveContinuousStoneBaseWithoutDeepslate() {
        long seed=4200439302357479658L;
        int x=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.SPIKES)+300;
        for(int dz=0;dz<64;dz+=4)for(int dx=0;dx<64;dx+=4) {
            assertEquals(1,FarlandsTerrain.material(seed,x+dx,-64,dz));
            assertNotEquals(0,FarlandsTerrain.material(seed,x+dx,-62,dz));
        }
    }
}
