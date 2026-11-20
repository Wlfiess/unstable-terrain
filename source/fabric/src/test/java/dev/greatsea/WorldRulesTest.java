package dev.greatsea;
import net.minecraft.entity.SpawnReason;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WorldRulesTest {
    @Test void amplifiedIsBoundedOnAllFacesAndDoesNotOverlapTheSea() {
        for(int[] p:new int[][]{{128000,0},{-128000,0},{0,128000},{0,-128000},{239999,239999}})assertTrue(AmplifiedTerrain.contains(p[0],p[1]));
        for(int[] p:new int[][]{{127999,127999},{240000,0},{0,-240000},{250000,0},{3000000,3000000}})assertFalse(AmplifiedTerrain.contains(p[0],p[1]));
    }
    @Test void rarityAppliesOnlyToNaturalGeneration() {
        for(var reason:SpawnReason.values())assertEquals(reason==SpawnReason.NATURAL || reason==SpawnReason.CHUNK_GENERATION,UnstableMobSpawns.natural(reason));
    }
    @Test void movingTheSeaInwardAlsoLimitsAmplified() {
        var old=GreatSea.CONFIG;
        try {
            var config=new SeaConfig();config.innerBoundary=160_000;GreatSea.CONFIG=config;
            assertTrue(AmplifiedTerrain.contains(159_983,0));
            assertFalse(AmplifiedTerrain.contains(159_984,0));
            assertFalse(AmplifiedTerrain.contains(160_000,0));
            assertTrue(AmplifiedTerrain.contains(159_983,159_983),"The small square-corner smoothing no longer reaches this inland chunk");
            assertFalse(AmplifiedTerrain.contains(159_999,159_999));
        }finally{GreatSea.CONFIG=old;}
    }
}

