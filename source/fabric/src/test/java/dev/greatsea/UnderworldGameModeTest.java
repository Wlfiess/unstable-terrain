package dev.greatsea;
import org.junit.jupiter.api.Test;
import net.minecraft.world.GameMode;
import static org.junit.jupiter.api.Assertions.*;

class UnderworldGameModeTest {
    @Test void survivalEntersAdventureAndAutomaticAdventureReturnsToSurvival(){
        var enter=UnderworldGameMode.transition(true,GameMode.SURVIVAL,false);
        assertEquals(GameMode.ADVENTURE,enter.target());assertTrue(enter.automatic());
        var stay=UnderworldGameMode.transition(true,GameMode.ADVENTURE,true);
        assertNull(stay.target());assertTrue(stay.automatic());
        var leave=UnderworldGameMode.transition(false,GameMode.ADVENTURE,true);
        assertEquals(GameMode.SURVIVAL,leave.target());assertFalse(leave.automatic());
    }
    @Test void preexistingAdventureAndExplicitOtherModesArePreserved(){
        for(boolean inside:new boolean[]{false,true})for(var mode:new GameMode[]{GameMode.ADVENTURE,GameMode.CREATIVE,GameMode.SPECTATOR}){
            var untouched=UnderworldGameMode.transition(inside,mode,false);
            assertNull(untouched.target());assertFalse(untouched.automatic());
        }
        for(var mode:new GameMode[]{GameMode.CREATIVE,GameMode.SPECTATOR}){
            var changed=UnderworldGameMode.transition(true,mode,true);
            assertNull(changed.target());assertFalse(changed.automatic());
            assertNull(UnderworldGameMode.transition(false,mode,changed.automatic()).target());
        }
    }
    @Test void survivalOutsideTheRegionNeverChangesMode(){
        var untouched=UnderworldGameMode.transition(false,GameMode.SURVIVAL,false);
        assertNull(untouched.target());assertFalse(untouched.automatic());
    }
}
