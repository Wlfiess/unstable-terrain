package dev.greatsea;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.io.InputStreamReader;
import static org.junit.jupiter.api.Assertions.*;

class UnderworldIntegrationTest {
    @Test void entryDropIsProtectedButOrdinaryUnderworldFallsAreNot() {
        assertTrue(UnderworldEntry.protectsLanding(-205,206));
        assertTrue(UnderworldEntry.protectsLanding(-185,190));
        assertTrue(UnderworldEntry.protectsLanding(-225,230));
        assertFalse(UnderworldEntry.protectsLanding(30,100));
        assertFalse(UnderworldEntry.protectsLanding(-205,15));
        assertFalse(UnderworldEntry.protectsLanding(-225,40));
        assertFalse(UnderworldEntry.protectsLanding(-310,130));
    }
    @Test void unstablePresetExtendsOnlyItsNetherAndKeepsNativeNoiseRange() {
        var preset=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream(
                "/data/greatsea/worldgen/world_preset/unstable.json"))).getAsJsonObject();
        var dimensions=preset.getAsJsonObject("dimensions");
        assertEquals("minecraft:overworld",dimensions.getAsJsonObject("minecraft:overworld").get("type").getAsString());
        assertEquals("minecraft:the_end",dimensions.getAsJsonObject("minecraft:the_end").get("type").getAsString());
        var nether=dimensions.getAsJsonObject("minecraft:the_nether");
        assertEquals("greatsea:unstable_nether",nether.get("type").getAsString());
        assertEquals("greatsea:unstable_nether",nether.getAsJsonObject("generator").get("settings").getAsString());
        var type=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream(
                "/data/greatsea/dimension_type/unstable_nether.json"))).getAsJsonObject();
        assertEquals(-320,type.get("min_y").getAsInt());
        assertEquals(256,type.get("min_y").getAsInt()+type.get("height").getAsInt());
        assertEquals(128,type.get("min_y").getAsInt()+type.get("logical_height").getAsInt());
        var settings=JsonParser.parseReader(new InputStreamReader(getClass().getResourceAsStream(
                "/data/greatsea/worldgen/noise_settings/unstable_nether.json"))).getAsJsonObject();
        assertEquals(0,settings.getAsJsonObject("noise").get("min_y").getAsInt());
        assertEquals(128,settings.getAsJsonObject("noise").get("height").getAsInt());
        assertEquals(32,settings.get("sea_level").getAsInt());
    }
}
