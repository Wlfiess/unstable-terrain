package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarlandsTerrainTest {
    private static final long SEED=4200439302357479658L;
    @Test void stagesFollowDocumentOnAllFacesAndNormalPositionsRemainOutside() {
        int b=GreatSea.FARLANDS.entryBoundary;
        assertFalse(FarlandsWorld.contains(b-1,0));assertFalse(FarlandsWorld.contains(0,-b+1));
        for(var stage:FarlandsTerrain.Stage.values()) {
            int x=b+FarlandsTerrain.start(stage)+128;
            assertEquals(stage,FarlandsTerrain.stage(x,0));assertEquals(stage,FarlandsTerrain.stage(-x,0));
            assertEquals(stage,FarlandsTerrain.stage(0,x));assertEquals(stage,FarlandsTerrain.stage(0,-x));
        }
    }
    @Test void stoneEventSampleRestoresSoilAndThreeDimensionalVoids() {
        int b=GreatSea.FARLANDS.entryBoundary;
        int air=0,rock=0,grass=0,overhangs=0;
        for(int x=b+100;x<b+164;x+=2)for(int z=0;z<64;z+=2) {
            boolean emptyBelow=false;
            for(int y=-64;y<160;y++) {
                int m=FarlandsTerrain.material(SEED,x,y,z);
                if(m==0){air++;emptyBelow=true;}else {rock++;if(emptyBelow)overhangs++;if(m==4)grass++;}
            }
        }
        assertTrue(air>1000 && rock>1000);assertTrue(grass>100,"Stone sample must have restored grass");
        overhangs=0;
        int floating=b+FarlandsTerrain.start(FarlandsTerrain.Stage.FLOATING)+128;
        for(int x=floating;x<floating+64;x+=2)for(int z=0;z<64;z+=2) {
            boolean emptyBelow=false;
            for(int y=-64;y<160;y++) {
                if(FarlandsTerrain.material(SEED,x,y,z)==0)emptyBelow=true;
                else if(emptyBelow)overhangs++;
            }
        }
        assertTrue(overhangs>100,"Density must retain floating terrain and caves");
    }
    @Test void decayRemovesIndividualBlocksMonotonicallyAndKeepsWater() {
        int b=GreatSea.FARLANDS.entryBoundary;int start=b+FarlandsTerrain.start(FarlandsTerrain.Stage.DECAY);
        int width=GreatSea.FARLANDS.stageWidths[FarlandsTerrain.Stage.DECAY.ordinal()];int early=0,late=0;
        int a=(start+width/10)/16*16,c=(start+width*9/10)/16*16;
        for(int z=0;z<16;z++)for(int dx=0;dx<16;dx++)for(int y=-59;y<320;y++) {
            int first=FarlandsTerrain.material(SEED,a+dx,y,z),last=FarlandsTerrain.material(SEED,c+dx,y,z);
            if(first!=0 && first!=8)early++;
            if(last!=0 && last!=8){late++;assertEquals(first,last,"Eroded blocks must never reappear");}
        }
        assertTrue(late>0 && late<early*.6);
        assertFalse(FarlandsTerrain.missingChunk(SEED,c,0));
        for(int y=-64;y<=-60;y++)assertEquals(8,FarlandsTerrain.material(SEED,c,y,0));
        int last=start+width-1;
        for(int dx=0;dx<16;dx++)for(int z=0;z<16;z++)for(int y=-59;y<320;y++)
            assertEquals(0,FarlandsTerrain.material(SEED,last-dx,y,z),"No residual pools or lava above the basal water");
    }
    @Test void gridIsSpacedAndEventuallyCompletelyEmpty() {
        int b=GreatSea.FARLANDS.entryBoundary;int start=b+FarlandsTerrain.start(FarlandsTerrain.Stage.SKYGRID);
        int x=start+1026;int cells=0;
        for(int z=0;z<64;z++)for(int y=0;y<64;y++) {
            int m=FarlandsTerrain.material(SEED,x,y,z);
            if(m!=0){cells++;assertEquals(0,Math.floorMod(y,6));assertEquals(0,Math.floorMod(z,6));}
        }
        assertEquals(121,cells,"Entrance blocks must have five empty blocks between their faces");
        int after=b+FarlandsTerrain.end(FarlandsTerrain.Stage.SKYGRID)+16;
        for(int y=-64;y<320;y++)assertEquals(0,FarlandsTerrain.material(SEED,after,y,0));
    }
    @Test void neighboringChunkBiomesDifferIncludingNegativePositions() {
        for(int x=-128;x<128;x+=16)for(int z=-128;z<128;z+=16) {
            int a=FarlandsTerrain.chunkBiome(SEED,x,z);
            assertNotEquals(a,FarlandsTerrain.chunkBiome(SEED,x+16,z));assertNotEquals(a,FarlandsTerrain.chunkBiome(SEED,x,z+16));
        }
    }
}
