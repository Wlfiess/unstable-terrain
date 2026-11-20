package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarlandsNativeTerrainTest {
    private static final long SEED=4200439302357479658L;
    private int position(FarlandsTerrain.Stage s) {
        return (GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(s)+1024)/16*16;
    }
    @Test void repeatingChunksIncludeIdenticalBareSourceBlockVolumes() {
        int x=position(FarlandsTerrain.Stage.REPEATING),plants=0,solid=0;
        for(int z=0;z<16;z++)for(int dx=0;dx<16;dx++)for(int y=-64;y<320;y++) {
            int m=FarlandsTerrain.material(SEED,x+dx,y,z);
            assertEquals(m,FarlandsTerrain.material(SEED,x+dx+16,y,z));
            if(m==27 || m==14 || m==15)plants++;
            if(m!=0 && m!=8 && m!=29)solid++;
        }
        assertEquals(0,plants);assertTrue(solid>10000);
    }
    @Test void eachChunkTemplateHasDifferentGeometryAndRetainsNativeVegetation() {
        var fingerprints=new java.util.HashSet<Long>();int foliage=0;
        for(int i=0;i<8;i++) {
            var tile=FarlandsChunkTemplate.load("chunk-"+i);long fingerprint=0;
            for(int y=-64;y<320;y++)for(int z=0;z<16;z++)for(int x=0;x<16;x++) {
                int m=tile.material(x,y,z);fingerprint=31*fingerprint+m;
                if(m==14 || m==15 || m==27)foliage++;
            }
            assertTrue(fingerprints.add(fingerprint),"Chunk geometry must differ, not just biome IDs");
        }
        assertTrue(foliage>1000);
    }
    @Test void skyGridSpacingWidensWithoutCreatingOffLatticeBlocks() {
        int start=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.SKYGRID);
        int width=GreatSea.FARLANDS.stageWidths[FarlandsTerrain.Stage.SKYGRID.ordinal()];
        for(int level=0;level<5;level++) {
            int offset=(int)((level*.18+.01)*width);
            while(FarlandsTerrain.skygridSpacing(start-GreatSea.FARLANDS.entryBoundary+offset)==0)offset++;
            int x=start+offset,spacing=FarlandsTerrain.skygridSpacing(x-GreatSea.FARLANDS.entryBoundary),count=0;
            for(int y=-64;y<320;y++)for(int z=0;z<4096;z++) {
                int m=FarlandsTerrain.material(SEED,x,y,z);
                if(m!=0){count++;assertEquals(0,Math.floorMod(y,spacing));assertEquals(0,Math.floorMod(z,spacing));}
            }
            assertTrue(count>0);
        }
    }
    @Test void hillPeaksAreTallAndDuneCellBoundariesHaveNoAbruptReset() {
        int max=0;
        for(int x=0;x<256;x++)for(int z=0;z<256;z+=4) {
            max=Math.max(max,FarlandsTerrain.hillHeight(SEED,x,z));
            assertTrue(Math.abs(FarlandsTerrain.spikeHeight(SEED,x,z,true)-FarlandsTerrain.spikeHeight(SEED,x+1,z,true))<=24);
        }
        assertTrue(max>240);
    }
}
