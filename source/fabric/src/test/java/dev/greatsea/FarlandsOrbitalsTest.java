package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarlandsOrbitalsTest {
    @Test void fullSchematicFitsInsideTheGapWithClearance() {
        int origin=GreatSea.FARLANDS.entryBoundary+FarlandsOrbitals.origin(42),count=0;
        var s=FarlandsOrbitals.SAMPLE;
        int start=GreatSea.FARLANDS.entryBoundary+FarlandsOrbitals.center()-FarlandsOrbitals.length(42)/2;
        assertTrue(origin-start>=8);assertTrue(start+FarlandsOrbitals.length(42)-(origin+s.width)>=8);
        for(int a=0;a<s.width;a++)for(int b=0;b<FarlandsOrbitals.DEPTH;b+=13)for(int y=0;y<s.height;y++) {
            if(s.material(a,y,FarlandsOrbitals.sourceColumn(b))==0)continue;
            int x=origin+a,z=b+FarlandsOrbitals.tangentOrigin();
            assertTrue(FarlandsOrbitals.gap(42,x,z));
            assertTrue(FarlandsOrbitals.hasBlock(42,x,FarlandsOrbitals.BASE_Y+y,z));count++;
        }
        assertTrue(count>10000);
    }
    @Test void orbitalRowHasFiftyShotsOfEachKindAndOneControlPlatform() {
        int expectedCenter=-5000;
        assertEquals(expectedCenter,FarlandsOrbitals.tangentOrigin()+FarlandsOrbitals.SAMPLE.depth/2);
        var plates=new java.util.HashSet<net.minecraft.util.math.BlockPos>();
        int stabs=0;
        var first=FarlandsOrbitals.plate(42,0);
        var lever=FarlandsOrbitals.controlLever(42);
        assertTrue(FarlandsOrbitals.hasBlock(42,lever.getX(),lever.getY(),lever.getZ()));
        for(int silo=0;silo<FarlandsOrbitals.SILOS;silo++){
            var plate=FarlandsOrbitals.plate(42,silo);
            assertTrue(plates.add(plate));
            assertEquals(first.getZ()+100*silo,plate.getZ());
            assertEquals(silo,FarlandsOrbitals.nearestSilo(plate));
            assertTrue(FarlandsOrbitals.hasBlock(42,plate.getX(),plate.getY(),plate.getZ()));
            assertFalse(FarlandsOrbitals.hasBlock(42,lever.getX(),lever.getY(),plate.getZ()));
            if(OrbitalRuntime.Shot.at(silo)==OrbitalRuntime.Shot.STAB)stabs++;
        }
        assertEquals(50,stabs);
        assertEquals(50,plates.size()-stabs);
        for(int b=37;b<FarlandsOrbitals.DEPTH;b++){
            var track=FarlandsOrbitals.position(42,14,FarlandsOrbitals.BASE_Y+81,b);
            assertTrue(FarlandsOrbitals.hasBlock(42,track.getX(),track.getY(),track.getZ()),"Track interrupted at "+track);
        }
    }
    @Test void stageAndOldConfigurationMigrateInOrder(){
        var config=new FarlandsConfig();config.stageWidths=new int[]{12000,10000,12000,10000,12000,10000,12000,12000,14000,14000,14000,18000,18000,36000,2500,48000};config.validate();
        assertEquals(17,config.stageWidths.length);assertEquals(1024,config.stageWidths[12]);assertEquals(18000,config.stageWidths[13]);assertEquals(2500,config.stageWidths[15]);
        assertEquals(FarlandsTerrain.end(FarlandsTerrain.Stage.CORRUPTED),FarlandsTerrain.start(FarlandsTerrain.Stage.ORBITALS));
        assertEquals(FarlandsTerrain.end(FarlandsTerrain.Stage.ORBITALS),FarlandsTerrain.start(FarlandsTerrain.Stage.CHUNKS));
    }
    @Test void gapIsWideEnoughAndSchematicRetainsFullSize(){
        var widths=new java.util.HashSet<Integer>();int border=GreatSea.FARLANDS.entryBoundary;
        for(long seed=0;seed<256;seed++){
            int n=FarlandsOrbitals.length(seed);assertEquals(160,n);widths.add(n);
            int start=border+FarlandsOrbitals.center()-n/2;
            assertFalse(FarlandsOrbitals.gap(seed,start-1,0));assertTrue(FarlandsOrbitals.gap(seed,start,0));assertTrue(FarlandsOrbitals.gap(seed,start+n-1,0));assertFalse(FarlandsOrbitals.gap(seed,start+n,0));
        }
        assertEquals(1,widths.size());assertEquals(142,FarlandsOrbitals.SAMPLE.width);assertEquals(1786,FarlandsOrbitals.SAMPLE.depth);assertEquals(137,FarlandsOrbitals.SAMPLE.height);
    }
    @Test void positiveOrbitalVoidHasNoFloorOrCeilingAndNegativeSpokelandsRetainTerrain(){
        int out=GreatSea.FARLANDS.entryBoundary+FarlandsOrbitals.center();
        for(int[] p:new int[][]{{out,2000},{2000,out}})for(int y=-64;y<320;y++)assertEquals(0,FarlandsTerrain.material(42,p[0],y,p[1]));
        for(int[] p:new int[][]{{-out,2000},{2000,-out}}){
            assertTrue(SpokelandsTerrain.contains(p[0],p[1]));assertFalse(FarlandsOrbitals.gap(42,p[0],p[1]));
            int land=0;
            for(int y=-64;y<320;y++){
                int material=FarlandsTerrain.material(42,p[0],y,p[1]);
                assertEquals(SpokelandsTerrain.material(42,p[0],y,p[1]),material);
                assertFalse(FarlandsOrbitals.contains(42,p[0],y,p[1]));
                if(material!=0&&material!=8)land++;
            }
            assertTrue(land>100,"An inherited orbital corridor erased negative terrain");
        }
    }
    @Test void exactImportedBlockCount(){
        int n=0;var s=FarlandsOrbitals.SAMPLE;
        for(int y=0;y<s.height;y++)for(int z=0;z<s.depth;z++)for(int x=0;x<s.width;x++)if(s.material(x,y,z)!=0)n++;
        assertEquals(205438,n);
    }
}
