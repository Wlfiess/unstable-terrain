package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SpikeFloorTest {
    @Test void snowFloorHasSmallContinuousSeededRelief() {
        int min=0,max=-64,different=0;
        for(int z=-256;z<256;z+=3)for(int x=-256;x<256;x+=3) {
            int h=FarlandsRelief.spikeFloor(72,x,z);min=Math.min(min,h);max=Math.max(max,h);
            assertTrue(h>=-62 && h<=-57);
            assertTrue(Math.abs(h-FarlandsRelief.spikeFloor(72,x+1,z))<=1);
            assertTrue(Math.abs(h-FarlandsRelief.spikeFloor(72,x,z+1))<=1);
            if(h!=FarlandsRelief.spikeFloor(73,x,z))different++;
        }
        assertTrue(max-min>=4 && different>10000);
    }
    @Test void openValleysKeepTheRaisedSnowFloorWithoutBuryingTheSpires() {
        int valleys=0;
        for(int x=0;x<128;x++)for(int z=0;z<128;z++) {
            int floor=FarlandsRelief.spikeFloor(72,x,z),top=FarlandsRelief.spire(72,x,z);
            assertTrue(top>=floor);
            if(top==floor)valleys++;
        }
        assertTrue(valleys>5000);
    }
}
