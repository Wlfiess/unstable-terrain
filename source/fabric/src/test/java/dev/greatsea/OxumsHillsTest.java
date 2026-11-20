package dev.greatsea;

import java.util.Arrays;
import java.util.HashSet;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OxumsHillsTest {
    private static final long SEED=4200439302357479658L;

    @Test void heightsAndSlopesFollowTheCredibleOxumsInterior() {
        int width=512;
        int[] heights=new int[width*width],steps=new int[2*width*(width-1)];
        int n=0;double sum=0,squares=0;
        for(int z=0;z<width;z++)for(int x=0;x<width;x++) {
            int h=OxumsHills.height(SEED,x,z);heights[z*width+x]=h;sum+=h;squares+=(double)h*h;
            assertTrue(h>=30 && h<=311);
            if(x>0)steps[n++]=Math.abs(h-heights[z*width+x-1]);
            if(z>0)steps[n++]=Math.abs(h-heights[(z-1)*width+x]);
        }
        double mean=sum/heights.length,sd=Math.sqrt(squares/heights.length-mean*mean);
        Arrays.sort(heights);Arrays.sort(steps);
        // Source p10/p50/p90 = 78/172/269, sd=68.88; sampling varies by seed.
        assertTrue(heights[heights.length/10]>=65 && heights[heights.length/10]<=90);
        assertTrue(heights[heights.length/2]>=160 && heights[heights.length/2]<=185);
        assertTrue(heights[heights.length*9/10]>=255 && heights[heights.length*9/10]<=283);
        assertTrue(mean>160 && mean<185 && sd>61 && sd<77);
        assertEquals(3,steps[steps.length/2]);
        assertTrue(steps[steps.length*9/10]>=7 && steps[steps.length*9/10]<=9);
        assertTrue(steps[steps.length*99/100]>=10 && steps[steps.length*99/100]<=14);
        assertTrue(steps[steps.length-1]<=26,"A discontinuous copied edge or artificial wall appeared");
    }

    @Test void neighboringCrestsHaveTheMeasuredWidthWithoutRepeatingSamples() {
        // At 16 blocks source autocorrelation is .61, at 32 it is .076.
        assertTrue(correlation(16,0)>.45 && correlation(16,0)<.75);
        assertTrue(Math.abs(correlation(32,0))<.25);
        for(int period:new int[]{128,254,256,512}) {
            assertTrue(Math.abs(correlation(period,0))<.20);
            assertTrue(Math.abs(correlation(0,period))<.20);
        }
    }

    @Test void distantAndNegativePatchesVaryDeterministicallyWithSeed() {
        var fingerprints=new HashSet<Long>();int changed=0;
        for(int p=-12;p<=12;p++) {
            long f=1;
            for(int z=-32;z<32;z+=4)for(int x=0;x<64;x+=4) {
                int h=OxumsHills.height(SEED,p*257+x,z);
                assertEquals(h,OxumsHills.height(SEED,p*257+x,z));
                f=31*f+h;
            }
            assertTrue(fingerprints.add(f),"Copied hill patch "+p);
        }
        for(int z=-128;z<128;z+=7)for(int x=-128;x<128;x+=7)
            if(OxumsHills.height(SEED,x,z)!=OxumsHills.height(SEED+1,x,z))changed++;
        assertTrue(changed>1300);
    }

    private static double correlation(int dx,int dz) {
        double a=0,b=0,aa=0,bb=0,ab=0;int n=0;
        for(int z=-512;z<512;z+=7)for(int x=-512;x<512;x+=7) {
            double v=OxumsHills.height(SEED,x,z),w=OxumsHills.height(SEED,x+dx,z+dz);
            a+=v;b+=w;aa+=v*v;bb+=w*w;ab+=v*w;n++;
        }
        return (ab-a*b/n)/Math.sqrt((aa-a*a/n)*(bb-b*b/n));
    }
}
