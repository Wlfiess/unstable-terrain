package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ProceduralReliefTest {
    private static double height(boolean spikes,long seed,int x,int z){return spikes?FarlandsRelief.spire(seed,x,z):FarlandsRelief.ridge(seed,x,z,true);}
    @Test void proceduralSpikesDoNotRepeatTheFormerSamplePeriod() {
        for(int axis=0;axis<2;axis++) {
            int period=158;double sumA=0,sumB=0,aa=0,bb=0,ab=0;int count=0;
            for(int z=-256;z<256;z+=11)for(int x=-256;x<256;x+=11) {
                double a=height(true,72,x,z),b=height(true,72,x+(axis==0?period:0),z+(axis==1?period:0));
                sumA+=a;sumB+=b;aa+=a*a;bb+=b*b;ab+=a*b;count++;
            }
            double correlation=(ab-sumA*sumB/count)/Math.sqrt((aa-sumA*sumA/count)*(bb-sumB*sumB/count));
            System.out.println("Spikes axis "+axis+" former-period correlation="+correlation);
            assertTrue(Math.abs(correlation)<.25,"Repeated sample layout remains: "+correlation);
        }
    }
    @Test void distantPatchesAndDifferentSeedsHaveDistinctProfiles() {
        for(boolean spikes:new boolean[]{true,false}) {
            var patches=new java.util.HashSet<Long>();int changed=0;
            for(int patch=0;patch<25;patch++) {
                long fingerprint=1;
                for(int z=0;z<64;z+=4)for(int x=0;x<64;x+=4)fingerprint=31*fingerprint+(long)height(spikes,72,patch*257+x,z);
                assertTrue(patches.add(fingerprint));
            }
            for(int z=-128;z<128;z+=7)for(int x=-128;x<128;x+=7)if(height(spikes,72,x,z)!=height(spikes,73,x,z))changed++;
            assertTrue(changed>900);
        }
    }
}
