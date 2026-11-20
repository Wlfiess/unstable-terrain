package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

final class FarlandsCoordinateTextTest {
    @Test void entryOriginsMatchEveryBorderAndRetainTheTangentAxis() {
        int border=12_550_821;
        assertEquals(new FarlandsCoordinates.Origin(border,-10),FarlandsCoordinates.entryOrigin(border+.25,-9.25,border));
        assertEquals(new FarlandsCoordinates.Origin(-border,40),FarlandsCoordinates.entryOrigin(-border-.25,40.5,border));
        assertEquals(new FarlandsCoordinates.Origin(8,border),FarlandsCoordinates.entryOrigin(8.25,border+.25,border));
        assertEquals(new FarlandsCoordinates.Origin(-41,-border),FarlandsCoordinates.entryOrigin(-40.5,-border-.25,border));
    }
    @Test void positionKeepsYPrecisionAndResetsOnlyHorizontalCoordinates() {
        assertEquals("XYZ: 0.375 / 249.12500 / 0.625",FarlandsCoordinateText.relative(
                "XYZ: 12550821.375 / 249.12500 / 9.625",12550821,9));
    }
    @Test void westAndNorthPreserveNegativeAxisDirections() {
        assertEquals("XYZ: -19.250 / 80.00000 / -8.500",FarlandsCoordinateText.relative(
                "XYZ: -12550840.250 / 80.00000 / -101.500",-12550821,-93));
        assertEquals("Block: -1 28 -20",FarlandsCoordinateText.relative("Block: -41 28 -12550841",-40,-12550821));
    }
    @Test void nativeChunkFilesAndOtherDiagnosticsAreUnchanged() {
        String chunk="Chunk: 784426 15 0 [10 0 in r.24513.0.mca]";
        assertEquals(chunk,FarlandsCoordinateText.relative(chunk,12550821,9));
        assertEquals("Facing: west (Towards negative X)",FarlandsCoordinateText.relative("Facing: west (Towards negative X)",12550821,9));
        assertEquals("Section-relative: 08 09 02",FarlandsCoordinateText.relative("Section-relative: 08 09 02",12550821,9));
    }
}
