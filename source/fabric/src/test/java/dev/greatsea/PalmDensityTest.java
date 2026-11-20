package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PalmDensityTest {
    @Test void randomPalmDensityTracksConfiguredAreaAcrossSeeds() {
        SeaConfig config=new SeaConfig();
        for (long seed:new long[]{72,19,4200439302357479658L}) {
            int count=0;
            for (var candidate:PalmLayout.candidates(seed,-1500,-1500,1500,1500,config.palmSpacing))
                if (SeaTerrain.unit(candidate.hash())<=config.palmChance) count++;
            double expected=3001.0*3001/(config.palmSpacing*(double)config.palmSpacing)*config.palmChance;
            assertTrue(count>expected*.78 && count<expected*1.22,"Palm density no longer tracks configuration: "+count+" vs "+expected);
        }
    }
    @Test void randomPalmResultsStayIdenticalAcrossChunkOrderAndNegativeBorders() {
        long seed=72;int spacing=194;
        var complete=new java.util.HashSet<>(PalmLayout.candidates(seed,-256,-256,255,255,spacing));
        var tiled=new java.util.HashSet<PalmLayout.Candidate>();
        for (int z=240;z>=-256;z-=16) for (int x=240;x>=-256;x-=16)
            tiled.addAll(PalmLayout.candidates(seed,x,z,x+15,z+15,spacing));
        assertEquals(complete,tiled,"Palm coordinates depend on chunk generation order");
    }
    @Test void denserPalmsIncreaseAcceptedCandidatesByTwentyPercentAcrossSeeds() {
        SeaConfig config=new SeaConfig();
        for (long seed:new long[]{72,19,4200439302357479658L}) {
            int previous=count(seed,204,.8555680963010203),current=count(seed,config.palmSpacing,config.palmChance);
            double ratio=current/(double)previous;
            assertTrue(ratio>1.19 && ratio<1.21,"Palm density increase: "+ratio);
        }
    }
    private static int count(long seed,int spacing,double chance) {
        int accepted=0,extent=40000,grid=extent/spacing+2;
        for (int x=-grid;x<=grid;x++) for (int z=-grid;z<=grid;z++) {
            var candidate=PalmLayout.candidate(seed,x,z,spacing);
            if (candidate.x()>=-extent && candidate.x()<extent && candidate.z()>=-extent && candidate.z()<extent &&
                    SeaTerrain.unit(candidate.hash())<=chance) accepted++;
        }
        return accepted;
    }
    @Test void previousDefaultsMigrateWhileCustomAndCurrentSettingsStayIntact() {
        SeaConfig migrated=SeaConfig.parse("{\"configVersion\":10,\"palmSpacing\":204,\"palmChance\":0.8555680963010203,\"deadBushSpacing\":28,\"deadBushChance\":0.6}");
        assertEquals(194,migrated.palmSpacing);assertEquals(12,migrated.configVersion);
        assertEquals(28,migrated.deadBushSpacing);assertEquals(.72,migrated.deadBushChance);
        assertEquals(1.2,(migrated.palmChance/(194.0*194))/(.8555680963010203/(204.0*204)),1e-12);
        SeaConfig custom=SeaConfig.parse("{\"configVersion\":10,\"palmSpacing\":300,\"palmChance\":0.5,\"deadBushSpacing\":40,\"deadBushChance\":0.4}");
        assertEquals(300,custom.palmSpacing);assertEquals(.5,custom.palmChance);
        assertEquals(40,custom.deadBushSpacing);assertEquals(.4,custom.deadBushChance);
        SeaConfig current=SeaConfig.parse("{\"configVersion\":11,\"palmSpacing\":204,\"palmChance\":0.8555680963010203,\"deadBushSpacing\":28,\"deadBushChance\":0.6}");
        assertEquals(204,current.palmSpacing);assertEquals(.8555680963010203,current.palmChance);
        assertEquals(28,current.deadBushSpacing);assertEquals(.6,current.deadBushChance);
    }
    @Test void denserDeadBushesIncreaseAcceptedCandidatesAcrossSeeds() {
        SeaConfig config=new SeaConfig();
        for (long seed:new long[]{72,19,4200439302357479658L}) {
            int previous=0,current=0;
            for (int x=-250;x<=250;x++) for (int z=-250;z<=250;z++) {
                double acceptance=SeaTerrain.unit(SeaTerrain.hash(seed+201,x,z));
                if (acceptance<=.6) previous++;
                if (acceptance<=config.deadBushChance) current++;
            }
            double ratio=current/(double)previous;
            assertTrue(ratio>1.19 && ratio<1.21,"Dead bush density increase: "+ratio);
        }
    }
}
