package dev.greatsea;

import org.junit.jupiter.api.Test;
import java.util.HashSet;
import static org.junit.jupiter.api.Assertions.*;
class UnderworldCoordinateTextTest {
    @Test void onlyExtendedNetherUnderworldMasksCoordinates(){
        assertTrue(UnderworldCoordinateText.hidden(true,-320,-128.01));
        assertFalse(UnderworldCoordinateText.hidden(true,-320,-128));
        assertFalse(UnderworldCoordinateText.hidden(true,0,-200));
        assertFalse(UnderworldCoordinateText.hidden(false,-320,-200));
    }
    @Test void positionsAreReplacedAndNumbersNeverSeedTheMask(){
        String a=UnderworldCoordinateText.maskLine("XYZ: -12345.000 / -220.00000 / 98765.000",71);
        assertEquals(a,UnderworldCoordinateText.maskLine("XYZ: 1 / 2 / 3",71));
        assertFalse(a.chars().anyMatch(Character::isDigit));
        for(String prefix:new String[]{"Block:","Chunk:","Section-relative:","Chunk-relative:","Targeted Block:","Targeted Fluid:"}){
            String masked=UnderworldCoordinateText.maskLine(prefix+" 123 -456 789 in r.2.-3.mca",71);
            assertTrue(masked.startsWith(prefix));assertFalse(masked.chars().anyMatch(Character::isDigit));assertFalse(masked.contains(".mca"));
        }
    }
    @Test void formattedTargetHeadersAlsoHidePositions(){
        String line=UnderworldCoordinateText.maskLine("§nTargeted Block: 12550821, -220, 4",71);
        assertTrue(line.startsWith("§nTargeted Block:"));assertFalse(line.chars().anyMatch(Character::isDigit));
        assertEquals("§n"+UnderworldCoordinateText.maskLine("Targeted Block: 1, 2, 3",71),line);
    }
    @Test void animationChangesQuicklyWithoutInvalidFormattingOrControlCharacters(){
        assertEquals(0,UnderworldCoordinateText.animationFrame(49_999_999));
        assertEquals(1,UnderworldCoordinateText.animationFrame(50_000_000));
        var frames=new HashSet<String>();
        for(int i=0;i<200;i++){
            String frame=UnderworldCoordinateText.actionBar(i);assertTrue(frames.add(frame));
            assertFalse(frame.chars().anyMatch(Character::isISOControl));assertFalse(frame.contains("§"));assertFalse(frame.chars().anyMatch(Character::isDigit));
        }
    }
    @Test void unrelatedDiagnosticsRemainExact(){
        for(String line:new String[]{"Facing: west (Towards negative X)","Client Chunk Cache: 729, 441", "Local Difficulty: 3.17 // 0.59 (Day 14)","minecraft:the_nether FC: 0", "Java: 21.0.10"})
            assertEquals(line,UnderworldCoordinateText.maskLine(line,7));
    }
}
