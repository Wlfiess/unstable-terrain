package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarlandsReferenceShapeTest {
    private static final long SEED=4200439302357479658L;
    private int start(FarlandsTerrain.Stage stage) {return GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(stage);}

    @Test void repeatingCrossSectionDoesNotCollapseIntoSixteenBlockSquareTiles() {
        int x=start(FarlandsTerrain.Stage.REPEATING)+1024,different=0;
        for(int y=64;y<256;y++)for(int z=0;z<256;z++) {
            int m=FarlandsTerrain.material(SEED,x,y,z);
            assertEquals(m,FarlandsTerrain.material(SEED,x+16,y,z));
            if(m!=FarlandsTerrain.material(SEED,x,y,z+16))different++;
        }
        assertTrue(different>5000,"Different longitudinal strips must retain their different wall profiles");
    }
    @Test void earlyDecayHasRepeatedCanyonsOverOneHundredBlocksDeep() {
        int x=start(FarlandsTerrain.Stage.DECAY)+1024,low=320,high=-64;
        for(int z=0;z<128;z++) {
            int h=FarlandsTerrain.column(SEED,x,z).top();low=Math.min(low,h);high=Math.max(high,h);
            for(int y=-59;y<300;y++)assertEquals(FarlandsTerrain.material(SEED,x,y,z),FarlandsTerrain.material(SEED,x+16,y,z));
        }
        assertTrue(high-low>150);assertTrue(low<85);assertTrue(high>230);
    }
    @Test void spiresRetainDeepOpenValleysAndNarrowTallPeaksWithoutMissingChunks() {
        int low=0,high=0,max=-64;
        for(int z=0;z<128;z++)for(int x=0;x<128;x++) {
            int h=FarlandsRelief.spire(SEED,x,z);if(h<0)low++;if(h>170)high++;max=Math.max(max,h);
        }
        assertTrue(low>4000,"Do not bury the lower half of the spires beneath a Y=63 slab");
        assertTrue(high>80 && high<1500,"Spaced tall-spire column count: "+high);assertTrue(max>280,"Tallest spire: "+max);
    }
    @Test void importedCherryVolumeKeepsTreesAndSurfaceTogether() {
        var tile=FarlandsChunkTemplate.load("cherry-volume");int rooted=0;
        for(int z=0;z<256;z++)for(int x=0;x<256;x++)for(int y=40;y<100;y++) {
            int below=tile.material(x,y-1,z);
            if(tile.material(x,y,z)==14 && (below==3 || below==4))rooted++;
        }
        assertTrue(rooted>300,"Retain actual rooted trees throughout the complete sample");
    }
}

