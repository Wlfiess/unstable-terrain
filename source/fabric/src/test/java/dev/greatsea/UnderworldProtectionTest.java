package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class UnderworldProtectionTest {
    @Test void survivalBuildingIsRestrictedOnlyInsideTheUnderworld() {
        assertTrue(UnderworldProtection.restricted(true,true,-205));
        assertTrue(UnderworldProtection.restricted(true,true,-128.01));
        assertFalse(UnderworldProtection.restricted(true,true,-128));
        assertFalse(UnderworldProtection.restricted(true,true,64));
    }
    @Test void creativeAndSpectatorRulesAreUnchanged() {
        assertFalse(UnderworldProtection.restricted(true,false,-205));
    }
    @Test void normalNetherAndOtherWorldsAreUnchanged() {
        assertFalse(UnderworldProtection.restricted(false,true,-205));
        assertFalse(UnderworldProtection.restricted(false,false,-205));
    }
}
