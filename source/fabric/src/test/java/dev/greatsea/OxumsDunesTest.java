package dev.greatsea;
import java.util.Arrays;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OxumsDunesTest {
    private static final long SEED=4200439302357479658L;
    @Test void duneHeightsAndFaceSlopesFollowTheTallSource() {
        int[] heights=new int[512*512],steps=new int[heights.length];int i=0;
        for(int z=0;z<1024;z+=2)for(int x=0;x<1024;x+=2) {
            int h=OxumsDunes.height(SEED,x,z);heights[i]=h;
            steps[i++]=Math.abs(h-OxumsDunes.height(SEED,x+1,z));
            assertTrue(h>=-60 && h<=319);
        }
        Arrays.sort(heights);Arrays.sort(steps);
        // Source p25/p50/p75/p95 = 117/151/189/243. Regional elevations vary.
        assertTrue(heights[heights.length/4]>70 && heights[heights.length/4]<170);
        assertTrue(heights[heights.length/2]>110 && heights[heights.length/2]<205);
        assertTrue(heights[heights.length*3/4]>145 && heights[heights.length*3/4]<245);
        assertTrue(heights[heights.length*95/100]>200 && heights[heights.length*95/100]<315);
        assertTrue(steps[steps.length/2]>=1 && steps[steps.length/2]<=3);
        assertTrue(steps[steps.length*95/100]>=2 && steps[steps.length*95/100]<=6);
        assertTrue(steps[steps.length*99/100]<=6);
        assertTrue(steps[steps.length-1]<=10,"Downloaded cutoff or abrupt tile join");
    }
    @Test void chunkLinesHaveTheSameContinuityAsOrdinaryFaces() {
        double edge=0,ordinary=0;int edges=0,others=0,different=0;
        for(int z=-512;z<512;z+=7)for(int x=-512;x<512;x++) {
            int h=OxumsDunes.height(SEED,x,z);
            int delta=Math.abs(h-OxumsDunes.height(SEED,x+1,z));
            assertTrue(delta<=10);
            if(Math.floorMod(x,16)==15){edge+=delta;edges++;}else{ordinary+=delta;others++;}
            if(h!=OxumsDunes.height(SEED,x+254,z))different++;
        }
        assertTrue(Math.abs(edge/edges-ordinary/others)<.5,"Chunk edges change the facet slope");
        assertTrue(different>others*.95,"The former measured height tile is still being copied");
    }
}
