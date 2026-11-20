package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SpokeNativeMinesTest {
 @Test void rareNativeSelection(){int n=0;for(int x=-100;x<100;x++)for(int z=-100;z<100;z++)if(FarlandsStructures.spokeMineSelected(42,x,z))n++;assertTrue(n>3600&&n<4400,n+" retained starts");}
 @Test void wholeNativePieceSetStaysWithinHeightLimit(){int z=-GreatSea.FARLANDS.entryBoundary-SpokelandsTerrain.start(SpokelandsTerrain.Region.RED_PLAINS)-4000;for(int height:new int[]{10,30,55,80}){int shift=FarlandsStructures.spokeMineShift(42,80,z,0,height);assertTrue(height+shift<=315);assertEquals(Math.min(SpokelandsTerrain.ground(42,80,z)+140-height/2,315-height),shift);}}
}
