package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.util.ArrayList;
import java.util.List;

class OceanStructureDensityTest {
    @Test void exactlyOneMonumentCandidateInEachFourByFourRegionIncludingNegativeCoordinates() {
        for (long seed:new long[]{0,1,-1,4200439302357479658L}) {
            for (int rx=-12;rx<=12;rx++) for (int rz=-12;rz<=12;rz++) {
                int accepted=0;
                for (int x=0;x<4;x++) for (int z=0;z<4;z++) {
                    if (OceanStructureDensity.acceptsMonumentCell(seed,rx*4+x,rz*4+z)) {
                        accepted++;
                        assertTrue(x==1 || x==2,"Monument lies against a region border");
                        assertTrue(z==1 || z==2,"Monument lies against a region border");
                    }
                }
                assertEquals(1,accepted,"Missing or clustered monument region "+rx+","+rz);
            }
        }
    }

    @Test void monumentsStayAtLeast1120BlocksApartEvenWithClosestVanillaPlacementJitter() {
        List<int[]> accepted=new ArrayList<>();
        long seed=4200439302357479658L;
        for (int x=-64;x<64;x++) for (int z=-64;z<64;z++)
            if (OceanStructureDensity.acceptsMonumentCell(seed,x,z)) accepted.add(new int[]{x,z});
        for (int i=0;i<accepted.size();i++) for (int j=i+1;j<accepted.size();j++) {
            int[] a=accepted.get(i),b=accepted.get(j);
            // Monument spacing32, separation5: a vanilla offset is within0..26 chunks.
            int closestX=Math.max(0,Math.abs(a[0]-b[0])*32-26);
            int closestZ=Math.max(0,Math.abs(a[1]-b[1])*32-26);
            assertTrue((long)closestX*closestX+(long)closestZ*closestZ>=70L*70,
                    "Two retained native candidates can create nearby monuments");
        }
    }

    @Test void ruinsAndShipwrecksHaveDesiredLowerDensityWithoutDoublingBiomeVariants() {
        int ruins=0,ships=0;
        long seed=4200439302357479658L;
        for (int x=-64;x<64;x++) for (int z=-64;z<64;z++) {
            boolean warm=OceanStructureDensity.accepts(seed,"ocean_ruin_warm",x*20+4,z*20+7);
            assertEquals(warm,OceanStructureDensity.accepts(seed,"ocean_ruin_cold",x*20+4,z*20+7));
            if (warm) ruins++;
            boolean ship=OceanStructureDensity.accepts(seed,"shipwreck",x*24+4,z*24+7);
            assertEquals(ship,OceanStructureDensity.accepts(seed,"shipwreck_beached",x*24+4,z*24+7));
            if (ship) ships++;
        }
        assertTrue(ruins>3900 && ruins<4300,"Ruin retention differs from one quarter: "+ruins);
        assertTrue(ships>7900 && ships<8500,"Shipwreck retention differs from one half: "+ships);
    }

    @Test void decisionsAreStableAcrossCandidateOffsetsAndRegenerationButVaryByWorldSeed() {
        int changed=0;
        for (int x=-20;x<=20;x++) for (int z=-20;z<=20;z++) {
            boolean accepted=OceanStructureDensity.accepts(17,"monument",x*32,z*32);
            for (int offset:new int[]{0,7,26})
                assertEquals(accepted,OceanStructureDensity.accepts(17,"monument",x*32+offset,z*32+offset));
            if (accepted!=OceanStructureDensity.accepts(18,"monument",x*32,z*32)) changed++;
        }
        assertTrue(changed>50,"Sparse layout does not change with world seed");
        assertTrue(OceanStructureDensity.accepts(17,"village_plains",123,456));
    }
}
