package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OceanFloorMaterialsTest {
    private static final SeaConfig CONFIG=new SeaConfig();
    @Test void restoredBedHasOnlySandAndGravelAndColdInteriorsStayGravel() {
        int warm=0,cold=0,mixedSand=0,mixedGravel=0;
        for (int x=400000;x<900000;x+=257) for (int z=-150000;z<150000;z+=263) {
            var material=OceanFloorMaterials.sediment(CONFIG,72,x,z);
            double blend=SeaBiomeContext.coldBlend(CONFIG,72,x,z);
            if (blend==0) { assertEquals(OceanFloorMaterials.Sediment.SAND,material);warm++; }
            if (blend==1) { assertEquals(OceanFloorMaterials.Sediment.GRAVEL,material);cold++; }
            if (blend>0 && blend<1) {
                if (material==OceanFloorMaterials.Sediment.GRAVEL) mixedGravel++;else mixedSand++;
            }
        }
        assertEquals(2,OceanFloorMaterials.Sediment.values().length,"New dirt/clay/rock skin survived the rollback");
        assertTrue(warm>10000 && cold>10000,"The survey did not exercise pure sandy and gravel beds");
        assertTrue(mixedSand>1000 && mixedGravel>1000,"The retained climate edge lost its two sediment types");
    }
    @Test void coherentDepositsDoNotBecomeBlockConfettiOrDependOnQueryOrder() {
        int pairs=0,changes=0;
        for (int x=450000;x<450512;x++) for (int z=-256;z<256;z++) {
            var material=OceanFloorMaterials.sediment(CONFIG,72,x,z);
            var right=OceanFloorMaterials.sediment(CONFIG,72,x+1,z);
            var forward=OceanFloorMaterials.sediment(CONFIG,72,x,z+1);
            assertEquals(material,OceanFloorMaterials.sediment(CONFIG,72,x,z));
            pairs+=2;if (material!=right) changes++;if (material!=forward) changes++;
        }
        assertTrue(changes/(double)pairs<.12,"Ocean bed turns into independent random blocks");
    }
    @Test void floorMaterialChangesWithWorldSeed() {
        int differences=0;
        // A restored warm bed is uniformly sandy. Survey multiple climate patches,
        // rather than requiring two seeds to differ inside one small warm patch.
        for (int x=400000;x<900000;x+=701) for (int z=-150000;z<150000;z+=911)
            if (OceanFloorMaterials.sediment(CONFIG,72,x,z)!=OceanFloorMaterials.sediment(CONFIG,19,x,z)) differences++;
        assertTrue(differences>1000,"Seafloor distribution is not seeded");
    }
}

