package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PlainsFarlandsProfileTest {
    @Test void capturedLowerGeometryDoesNotDependOnScreenshotFallback() {
        int openings=0,dirt=0;
        for(int z=-192;z<192;z++)for(int y=-60;y<63;y++) {
            double a=PlainsFarlandsProfile.density(72,1024,y,z,-1);
            assertEquals(a,PlainsFarlandsProfile.density(72,1024,y,z,1));
            if(a<=0)openings++;
            if(PlainsFarlandsProfile.material(72,1024,y,z)==3)dirt++;
        }
        assertTrue(openings>10000 && dirt>1000);
        assertEquals(-1,PlainsFarlandsProfile.density(72,1024,270,0,1));
    }
    @Test void adjacentCapturedProfilePeriodsDoNotCopyTheSameOpenings(){
        int differences=0,total=0;
        for(int z=-900;z<900;z+=3)for(int y=16;y<240;y+=4){
            boolean a=PlainsFarlandsProfile.material(4200439302357479658L,1024,y,z)!=0;
            boolean b=PlainsFarlandsProfile.material(4200439302357479658L,1024,y,z+382)!=0;
            if(a!=b)differences++;total++;
        }
        assertTrue(differences>total*.025,"Captured motifs still repeat every 382 blocks");
    }
    @Test void longerPatternIsSharedByPlainsSandAndCorruptedWithoutChangingItsOpenings(){
        int x=GreatSea.FARLANDS.entryBoundary+1024;
        for(int tangent=-1400;tangent<1400;tangent+=13)for(int y=-64;y<=255;y+=5){
            boolean source=PlainsFarlandsProfile.material(72,1024,y,tangent)!=0;
            for(var stage:new FarlandsTerrain.Stage[]{FarlandsTerrain.Stage.PLAINS,FarlandsTerrain.Stage.SAND_FARLANDS,FarlandsTerrain.Stage.CORRUPTED,FarlandsTerrain.Stage.ORBITALS})
                assertEquals(source,FarlandsNatural.material(72,x,y,tangent,stage)!=0,"Changed opening in "+stage);
        }
    }
    private static final long SEED=4200439302357479658L;
    @Test void upperWallHasFrequentOpeningsInsteadOfBroadUnbrokenPanels() {
        int solid=0,total=0,longest=0;
        for(int y=180;y<=230;y+=5) {
            int run=0;
            for(int z=-300;z<=300;z++) {
                boolean stone=PlainsFarlandsProfile.density(SEED,1024,y,z,
                        FarlandsNatural.classicDensity(SEED,1024,y,z))>0;
                total++;if(stone){solid++;longest=Math.max(longest,++run);}else run=0;
            }
        }
        assertTrue(solid<total*.85,"Too much solid upper wall: "+solid+"/"+total);
        assertTrue(longest<250,"Unbroken upper-wall panel is too wide: "+longest);
        System.out.println("Upper-wall solid samples: "+solid+" / "+total+"; longest horizontal stone run: "+longest+" blocks");
    }
    @Test void allThreeHeightBandsRetainOpeningsAndStone() {
        for(int low:new int[]{64,128,192}) {
            int solid=0,air=0;
            for(int z=-240;z<240;z+=2)for(int y=low;y<low+63;y+=2) {
                double d=PlainsFarlandsProfile.density(SEED,1024,y,z,.08);
                assertTrue(Double.isFinite(d));if(d>0)solid++;else air++;
            }
            assertTrue(solid>1000,"Missing supports in height band "+low);
            assertTrue(air>1000,"Filled openings in height band "+low);
        }
    }
    @Test void lowerWallIsNotACopyOfTheUpperWallAndSeedChangesTheProfile() {
        int verticalDifferences=0,seedDifferences=0;
        for(int z=-240;z<240;z++)for(int y=64;y<115;y+=2) {
            boolean a=PlainsFarlandsProfile.density(SEED,1024,y,z,.08)>0;
            if(a!=(PlainsFarlandsProfile.density(SEED,1024,y+128,z,.08)>0))verticalDifferences++;
            if(a!=(PlainsFarlandsProfile.density(SEED+1,1024,y,z,.08)>0))seedDifferences++;
        }
        assertTrue(verticalDifferences>1500);assertTrue(seedDifferences>100);
    }
    @Test void screenshotJoinIsContinuousAndDistantCoordinatesRemainFinite() {
        for(double z:new double[]{-1e7,-602.4,-301.2,301.2,602.4,1e7})
            for(int y=-59;y<256;y+=7) {
                double a=PlainsFarlandsProfile.density(SEED,1024,y,z,.08);
                double b=PlainsFarlandsProfile.density(SEED,1024,y,z+.001,.08);
                assertTrue(Double.isFinite(a));assertTrue(Math.abs(a-b)<.04);
            }
    }
}
