package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeneratedIcePolicyTest {
    private static final String[] ICE={"minecraft:ice","minecraft:packed_ice","minecraft:blue_ice","minecraft:frosted_ice"};
    @Test void frozenOceanIcebergsBecomeWaterBelowTheSeaAndAirAboveIt() {
        for (String ice:ICE) for (int sea:new int[]{63,200}) {
            assertEquals(GeneratedIcePolicy.Replacement.WATER,GeneratedIcePolicy.replacement(ice,true,true,true,false,sea-1,sea));
            assertEquals(GeneratedIcePolicy.Replacement.WATER,GeneratedIcePolicy.replacement(ice,true,true,true,false,-30,sea));
            assertEquals(GeneratedIcePolicy.Replacement.AIR,GeneratedIcePolicy.replacement(ice,true,true,true,false,sea,sea));
            assertEquals(GeneratedIcePolicy.Replacement.AIR,GeneratedIcePolicy.replacement(ice,true,true,true,false,sea+32,sea));
        }
    }
    @Test void highFrozenLakesKeepTheirWaterEvenAboveTheOrdinarySeaLevel() {
        for (String ice:ICE)
            assertEquals(GeneratedIcePolicy.Replacement.WATER,GeneratedIcePolicy.replacement(ice,true,true,false,true,108,63));
    }
    @Test void solidIceTerrainKeepsItsShapeAsRockWhileSnowStaysSnow() {
        for (String ice:ICE) {
            assertEquals(GeneratedIcePolicy.Replacement.STONE,GeneratedIcePolicy.replacement(ice,true,true,false,false,90,63));
            assertEquals(GeneratedIcePolicy.Replacement.DEEPSLATE,GeneratedIcePolicy.replacement(ice,true,true,false,false,-8,63));
        }
        for (String block:new String[]{"minecraft:snow","minecraft:snow_block","minecraft:powder_snow","minecraft:stone","minecraft:water"})
            assertEquals(GeneratedIcePolicy.Replacement.KEEP,GeneratedIcePolicy.replacement(block,true,true,false,false,90,63));
    }
    @Test void playerPlacedIceAndOrdinaryWorldGenerationRemainUntouched() {
        for (String ice:ICE) for (boolean ocean:new boolean[]{false,true}) {
            assertEquals(GeneratedIcePolicy.Replacement.KEEP,GeneratedIcePolicy.replacement(ice,true,false,ocean,false,90,63));
            assertEquals(GeneratedIcePolicy.Replacement.KEEP,GeneratedIcePolicy.replacement(ice,false,true,ocean,false,90,63));
        }
    }
}
