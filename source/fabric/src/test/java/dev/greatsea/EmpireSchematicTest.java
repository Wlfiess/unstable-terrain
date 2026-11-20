package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EmpireSchematicTest {
    @Test void entireMassiveSchematicIsRetainedWithoutWrapping(){
        var source=FarlandsEmpire.SAMPLE;
        assertEquals(506,source.width);assertEquals(530,source.depth);assertEquals(384,source.height);
        assertEquals(37_661_645L,source.nonAirBlocks);assertEquals(30_360,source.blockEntities);
        assertEquals(0,source.index(0,383,0));
        assertEquals(732,source.index(354,372,509),"Keep the yellow wool built into the castle");
        assertEquals(source.nonAirBlocks,source.countBlocks(),"Every source shard must survive the import");
        assertEquals(0,source.index(-1,0,0));assertEquals(0,source.index(506,0,0));
        assertEquals(0,source.index(0,384,0));assertEquals(0,source.index(0,0,530));
        assertNotEquals(0,source.index(0,0,0));assertNotEquals(0,source.index(505,0,529));
    }
    @Test void landmarkFitsPositiveRepeatingFacesAndLeavesNegativeSpokelandsClear(){
        int first=GreatSea.FARLANDS.entryBoundary+FarlandsEmpire.origin(),last=first+505;
        assertEquals(-1029,FarlandsEmpire.tangentOrigin());
        for(int face:new int[]{0}){
            for(int a:new int[]{0,505})for(int b:new int[]{0,529}){
                var p=FarlandsEmpire.position(face,a,100,b);
                assertEquals(face==0,FarlandsEmpire.contains(p.getX(),100,p.getZ()));
                assertEquals(a,FarlandsEmpire.a(p.getX(),p.getZ()));assertEquals(b,FarlandsEmpire.b(p.getX(),p.getZ()));
                assertEquals(FarlandsTerrain.Stage.REPEATING,FarlandsTerrain.stage(p.getX(),p.getZ()));
            }
            for(int outward:new int[]{first,(first+last)/2,last}){
                int x=face<2?(face==0?outward:-outward):0,z=face<2?0:(face==2?outward:-outward);
                assertFalse(FarlandsEmpire.footprint(x,z),"No empire or carve on the straight-out axis");
            }
        }
        for(int face:new int[]{1,3})for(int a:new int[]{0,505})for(int b:new int[]{0,529}){
            var p=FarlandsEmpire.position(face,a,100,b);
            assertTrue(SpokelandsTerrain.contains(p.getX(),p.getZ()));
            assertFalse(FarlandsEmpire.contains(p.getX(),100,p.getZ()),"The positive empire must not appear in Spokelands");
            assertFalse(FarlandsEmpire.footprint(p.getX(),p.getZ()));
        }
        for(int face:new int[]{1,3})for(int outward:new int[]{first,(first+last)/2,last}){
            int x=face==1?-outward:0,z=face==3?-outward:0;
            assertFalse(FarlandsEmpire.footprint(x,z),"No empire or carve on the negative straight-out axis");
        }
    }
    @Test void schematicAirCarvesOnlyItsBoundedLandmark(){
        var air=FarlandsEmpire.position(0,450,319,30);
        assertEquals(0,FarlandsEmpire.SAMPLE.index(450,383,30));
        assertEquals(0,FarlandsTerrain.material(42,air.getX(),air.getY(),air.getZ()));
        for(int a:new int[]{-1,506}){
            var p=FarlandsEmpire.position(0,a,100,265);assertFalse(FarlandsEmpire.contains(p.getX(),100,p.getZ()));
        }
        var below=FarlandsEmpire.position(0,0,-65,0);assertFalse(FarlandsEmpire.contains(below.getX(),-65,below.getZ()));
    }
    @Test void castleFitsBelowRepeatingWallAndBottomFoundationIsClipped(){
        assertEquals(251,FarlandsEmpire.BASE_Y+FarlandsEmpire.SAMPLE.height-1);
        assertEquals(68,FarlandsEmpire.SOURCE_BOTTOM);
        var above=FarlandsEmpire.position(0,250,255,250);
        assertTrue(FarlandsEmpire.contains(above.getX(),255,above.getZ()));
        assertEquals(0,FarlandsTerrain.material(42,above.getX(),255,above.getZ()),"Keep sky above the lowered castle open");
        var below=FarlandsEmpire.position(0,250,-65,250);
        assertFalse(FarlandsEmpire.contains(below.getX(),-65,below.getZ()));
    }
}
