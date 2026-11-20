package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/** Measurements of the tall Oxums interior, excluding its downloaded crop. */
class OxumsRepeatingTest {
    private static final long SEED=4200439302357479658L;
    private static final FarlandsChunkTemplate SAMPLE=FarlandsChunkTemplate.load("repeat-oxums");

    private static int worldX() {
        return GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.REPEATING)+1024;
    }

    @Test void observedTravelPeriodIsSixteenAndDoesNotCollapseTheTransverseProfile() {
        assertEquals(16,SAMPLE.width);assertEquals(416,SAMPLE.depth);
        int x=worldX(),halfPeriodDifferences=0,transverseDifferences=0;
        for(int z=-208;z<208;z+=4)for(int dx=0;dx<16;dx++)for(int y=-64;y<256;y+=4) {
            int m=FarlandsTerrain.material(SEED,x+dx,y,z);
            assertEquals(m,FarlandsTerrain.material(SEED,x+dx+16,y,z));
            if(m!=FarlandsTerrain.material(SEED,x+dx+8,y,z))halfPeriodDifferences++;
            if(m!=FarlandsTerrain.material(SEED,x+dx,y,z+16))transverseDifferences++;
        }
        assertTrue(halfPeriodDifferences>15000,"The measured shelves have a 16-block travel period");
        assertTrue(transverseDifferences>15000,"Keep the varied transverse wall profile");
    }

    @Test void cavitiesAndGrassShelvesPersistThroughTheFullTallInterior() {
        int repeatedlyShelvedColumns=0,supportedGrass=0;
        for(int z=0;z<SAMPLE.depth;z++)for(int x=0;x<16;x++) {
            int shelves=0;
            for(int y=-48;y<160;y++) {
                int m=SAMPLE.material(x,y,z);
                assertEquals(m,SAMPLE.material(x,y+16,z),"Measured vertical shelf period");
            }
            for(int y=-48;y<176;y++)
                if(SAMPLE.material(x,y,z)==4 && SAMPLE.material(x,y+1,z)==0 && SAMPLE.material(x,y-1,z)==3) {
                    shelves++;supportedGrass++;
                }
            if(shelves>=10)repeatedlyShelvedColumns++;
        }
        assertTrue(repeatedlyShelvedColumns>3000,"Retain numerous deep shelves, not only a grass roof");
        assertTrue(supportedGrass>50000,"Native dirt-backed grass shelves must face real air cavities");
        for(int y=-48;y<176;y++) {
            int solid=0;
            for(int z=0;z<SAMPLE.depth;z++)for(int x=0;x<16;x++)if(SAMPLE.material(x,y,z)!=0)solid++;
            double fraction=solid/(double)(16*SAMPLE.depth);
            assertTrue(fraction>.62 && fraction<.91,"No flat slab or empty downloaded section at Y="+y);
        }
    }

    @Test void onlyBareTallTerrainWasImportedWithoutSuperflatGroundOrCropGaps() {
        int bottomSolid=0,totalSolid=0;
        for(int z=0;z<SAMPLE.depth;z++)for(int x=0;x<16;x++) {
            int top=-65;
            for(int y=-64;y<320;y++) {
                int m=SAMPLE.material(x,y,z);
                assertTrue(m==0 || m==1 || m==3 || m==4,"Unexpected bedrock, foliage, fluid or build material: "+m);
                if(m!=0){top=y;totalSolid++;if(y==-64)bottomSolid++;}
            }
            assertTrue(top>=186 && top<=255,"A superflat or missing source column entered the template");
            assertEquals(0,SAMPLE.material(x,-65,z));assertEquals(0,SAMPLE.material(x,256,z));
        }
        assertTrue(bottomSolid>4000 && bottomSolid<4500,"Do not import a solid superflat basal layer");
        double fraction=totalSolid/(double)(16*SAMPLE.depth*384);
        assertTrue(fraction>.64 && fraction<.67,"Preserve the measured solid/cavity balance");
    }

    @Test void mirroredContinuationMeetsItsVerifiedEndpointsWithoutArtificialSeamWalls() {
        int x=worldX();
        for(int dx=0;dx<16;dx++)for(int y=-64;y<320;y++) {
            assertEquals(FarlandsTerrain.material(SEED,x+dx,y,207),FarlandsTerrain.material(SEED,x+dx,y,208));
            assertEquals(FarlandsTerrain.material(SEED,x+dx,y,-208),FarlandsTerrain.material(SEED,x+dx,y,-209));
            for(int d=1;d<=8;d++) {
                assertEquals(FarlandsTerrain.material(SEED,x+dx,y,207-d),FarlandsTerrain.material(SEED,x+dx,y,208+d));
                assertEquals(FarlandsTerrain.material(SEED,x+dx,y,-208+d),FarlandsTerrain.material(SEED,x+dx,y,-209-d));
            }
        }
        for(int z:new int[]{-217,-209,-208,207,208,216})for(int dx=0;dx<16;dx++) {
            var column=FarlandsTerrain.column(SEED,x+dx,z);
            assertTrue(column.top()>=186 && column.top()<=255,"Downloaded cutoff became a seam gap");
        }
    }
}
