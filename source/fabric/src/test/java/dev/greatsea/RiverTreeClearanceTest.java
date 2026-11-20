package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class RiverTreeClearanceTest {
    private static final long SEED=4200439302357479658L;
    @Test void completeTreeFootprintsStayDryThroughRiverBendsOnBothFaces(){
        int start=SpokelandsTerrain.start(SpokelandsTerrain.Region.SCULK_FOREST),entry=GreatSea.FARLANDS.entryBoundary,accepted=0,rejected=0;
        for(int q=1800;q<3600;q+=113)for(int tangent=-440;tangent<=440;tangent+=37){
            int depth=start+q,z=-entry-depth;
            boolean clear=SpokelandsTerrain.treeClearOfRiver(SEED,tangent,z,130);
            assertEquals(clear,SpokelandsTerrain.treeClearOfRiver(SEED,z,tangent,130));
            if(!clear){rejected++;continue;}accepted++;
            // Independently sample the entire conservative tree footprint,
            // including upstream/downstream bends, against actual water blocks.
            for(int da=-130;da<=130;da+=6)for(int db=-130;db<=130;db+=6)
                assertNotEquals(8,SpokelandsTerrain.material(SEED,tangent+db,62,z-da),"A permitted crown extends over river water");
        }
        assertTrue(accepted>25,"All inland trees were suppressed");assertTrue(rejected>25,"Riverbank trees were not excluded");
    }
    @Test void dryTrunksAreRejectedWhenTheirCrownsWouldReachWater(){
        int depth=SpokelandsTerrain.start(SpokelandsTerrain.Region.SCULK_FOREST)+23745,z=-GreatSea.FARLANDS.entryBoundary-depth;
        int bank=(int)Math.round(SpokelandsTerrain.riverCenter(SEED,depth))+60;
        assertNotEquals(8,SpokelandsTerrain.material(SEED,bank,62,z));
        assertFalse(SpokelandsTerrain.treeClearOfRiver(SEED,bank,z,130));
        assertTrue(SpokelandsTerrain.treeClearOfRiver(SEED,bank+350,z,130));
    }
    @Test void shallowRiverHasSculkMudAndSingleBlockFroglightSpots(){
        int start=SpokelandsTerrain.start(SpokelandsTerrain.Region.SCULK_FOREST),entry=GreatSea.FARLANDS.entryBoundary;
        int sculk=0,mud=0,lights=0,connected=0;
        for(int q=2000;q<2800;q+=2){
            int depth=start+q,z=-entry-depth,center=(int)Math.round(SpokelandsTerrain.riverCenter(SEED,depth));
            for(int x=center-36;x<=center+36;x+=2){
                if(SpokelandsTerrain.material(SEED,x,62,z)!=8)continue;
                int floor=SpokelandsTerrain.ground(SEED,x,z),bed=SpokelandsTerrain.material(SEED,x,floor,z);
                assertTrue(floor==60||floor==61,"River is deeper than two water blocks");
                if(bed==SpokelandsTerrain.SCULK)sculk++;else if(bed==SpokelandsTerrain.MUD)mud++;else if(bed==SpokelandsTerrain.FROGLIGHT){
                    lights++;
                    if(SpokelandsTerrain.material(SEED,x+1,floor,z)==bed||SpokelandsTerrain.material(SEED,x-1,floor,z)==bed||SpokelandsTerrain.material(SEED,x,floor,z+1)==bed||SpokelandsTerrain.material(SEED,x,floor,z-1)==bed)connected++;
                }else fail("Unexpected riverbed material: "+bed);
            }
        }
        assertTrue(sculk>500&&mud>500&&lights>20);assertTrue(lights<(sculk+mud)/5,"Froglights cover the river instead of forming spots");
        assertEquals(0,connected,"Froglight spots must be single blocks, never connected patches");
    }
}

