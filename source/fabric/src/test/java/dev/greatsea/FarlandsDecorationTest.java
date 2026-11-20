package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarlandsDecorationTest {
    @Test void rootCandidatesBelongToTheirCellsAcrossSignedCoordinates() {
        for (int spacing:new int[]{7,8,10,16}) for (long seed:new long[]{0,-731,4200439302357479658L})
            for (int gz=-160;gz<=160;gz+=7) for (int gx=-160;gx<=160;gx+=11) {
                var p=FarlandsDecoration.treeCandidate(seed,gx,gz,spacing);
                assertEquals(gx,Math.floorDiv(p.x(),spacing));
                assertEquals(gz,Math.floorDiv(p.z(),spacing));
                assertEquals(p,FarlandsDecoration.treeCandidate(seed,gx,gz,spacing));
            }
    }
    @Test void denseCherryAndSparseGravelDoNotPlantWaterOrGrid() {
        var cherry=FarlandsChunkTemplate.load("cherry-volume");int leaves=0,logs=0;
        for(int y=40;y<120;y++)for(int z=0;z<256;z++)for(int x=0;x<256;x++) {
            int m=cherry.material(x,y,z);if(m==15)leaves++;if(m==14)logs++;
        }
        assertTrue(leaves>60000 && logs>1000,"Retain the dense native cherry canopy and trunks");
        double density=acceptedDensity(FarlandsTerrain.Stage.CHERRY);
        assertTrue(density>.005 && density<.007,"Proposal density accounts for the sample's 305 roots in 65,536 columns and supported-ground filtering");
        assertEquals(0,acceptedDensity(FarlandsTerrain.Stage.WATER));
        assertEquals(0,acceptedDensity(FarlandsTerrain.Stage.SKYGRID));
        assertEquals(0,acceptedDensity(FarlandsTerrain.Stage.SAND_DUNES));
        assertEquals(0,acceptedDensity(FarlandsTerrain.Stage.SAND_FARLANDS));
    }
    @Test void scatteredRootsUseEntireChunksAndDoNotRepeatInAdjacentChunks() {
        int[] histogramX=new int[16],histogramZ=new int[16];int changed=0;
        for(int cz=-100;cz<100;cz++)for(int cx=0;cx<40;cx++)for(int i=0;i<16;i++) {
            var a=FarlandsDecoration.randomCandidate(78261,cx,cz,i);
            var b=FarlandsDecoration.randomCandidate(78261,cx+1,cz,i);
            histogramX[Math.floorMod(a.x(),16)]++;histogramZ[Math.floorMod(a.z(),16)]++;
            if(Math.floorMod(a.x(),16)!=Math.floorMod(b.x(),16) || Math.floorMod(a.z(),16)!=Math.floorMod(b.z(),16))changed++;
            assertEquals(cx,Math.floorDiv(a.x(),16));assertEquals(cz,Math.floorDiv(a.z(),16));
            assertEquals(a,FarlandsDecoration.randomCandidate(78261,cx,cz,i));
        }
        assertTrue(changed>125000,"Neighbouring chunks must not share a stamped tree layout");
        for(int i=0;i<16;i++){assertTrue(histogramX[i]>7000);assertTrue(histogramZ[i]>7000);}
    }
    private static double acceptedDensity(FarlandsTerrain.Stage stage) {
        int spacing=FarlandsDecoration.treeSpacing(stage),accepted=0;
        for (int gz=-128;gz<128;gz++) for (int gx=-128;gx<128;gx++) {
            var p=FarlandsDecoration.treeCandidate(78261,gx,gz,spacing);
            if (SeaTerrain.unit(p.hash())<FarlandsDecoration.treeChance(stage)) accepted++;
        }
        return accepted/(65536.0*spacing*spacing);
    }
    @Test void duneVegetationIsSparseAndIndependentlyScattered(){
        int cacti=0,bushes=0,empty=0,changed=0;
        for(int z=-50;z<50;z++)for(int x=-50;x<50;x++){
            boolean cactus=FarlandsDecoration.dunePlantSelected(78261,x,z,0);
            boolean bush=FarlandsDecoration.dunePlantSelected(78261,x,z,1);
            boolean secondBush=FarlandsDecoration.dunePlantSelected(78261,x,z,2);
            if(cactus)cacti++;if(bush)bushes++;if(secondBush)bushes++;
            if(!cactus&&!bush&&!secondBush)empty++;
            if(cactus!=FarlandsDecoration.dunePlantSelected(78261,x+1,z,0))changed++;
        }
        assertTrue(cacti>2200&&cacti<2800,"Cacti should average about one per four chunks, before support checks");
        assertTrue(bushes>8400&&bushes<9600,"Bushes remain below one per chunk on average");
        assertTrue(empty>1500&&changed>3000,"Desert plants must be omitted and independently scattered");
    }
    @Test void measuredWindingRibbonsProvideStableTreeRootsAcrossUpperLayers(){
        long seed=4200439302357479658L;
        int depth=SpokelandsTerrain.start(SpokelandsTerrain.Region.WINDING)+GreatSea.FARLANDS.spokelandsWidths[3]/2;
        int distance=GreatSea.FARLANDS.entryBoundary+depth,upper=0,groundRoots=0;
        var layers=new java.util.HashSet<Integer>();
        for(int z=-96;z<=96;z+=12)for(int x=-96;x<=96;x+=12){
            int worldZ=-distance-z,floor=SpokelandsTerrain.ground(seed,x,worldZ);
            for(int root:SpokelandsDecoration.windingTreeSurfaces(seed,x,worldZ)){
                assertEquals(4,SpokelandsTerrain.material(seed,x,root-1,worldZ));
                for(int dy=0;dy<10;dy++)assertEquals(0,SpokelandsTerrain.material(seed,x,root+dy,worldZ),"Native tree crown lacks clearance");
                if(root>floor+10){upper++;layers.add((root-floor)/35);}else groundRoots++;
            }
        }
        assertTrue(upper>30,"The source-shaped upper waves must actually offer tree placements");
        assertTrue(layers.size()>=3,"Tree roots were limited to one ribbon level");
        assertTrue(groundRoots>20,"Walkable ground also retains native trees");
    }
    @Test void cactusProbesReachRareNaturalCrestsWithoutChangingTheDuneHeightfield(){
        long seed=4200439302357479658L;
        int middle=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.SAND_DUNES)+7000;
        int selected=0,supported=0;
        for(int cz=-3;cz<3;cz++)for(int cx=(middle>>4)-3;cx<(middle>>4)+3;cx++){
            if(!FarlandsDecoration.dunePlantSelected(seed,cx,cz,0))continue;
            selected++;var seen=new java.util.HashSet<Long>();boolean crest=false;
            for(int attempt=0;attempt<196;attempt++){
                var candidate=FarlandsDecoration.dunePlantCandidate(seed,cx,cz,0,attempt);
                int x=candidate.x(),z=candidate.z(),top=FarlandsTerrain.column(seed,x,z).top();
                assertTrue(Math.floorMod(x,16)>=1&&Math.floorMod(x,16)<=14);
                assertTrue(Math.floorMod(z,16)>=1&&Math.floorMod(z,16)<=14);
                assertTrue(seen.add(((long)x<<32)^(z&0xffffffffL)),"A cactus probe repeated instead of examining a new column");
                if(FarlandsTerrain.material(seed,x,top,z)==5&&top<319&&FarlandsTerrain.column(seed,x-1,z).top()<=top&&FarlandsTerrain.column(seed,x+1,z).top()<=top&&FarlandsTerrain.column(seed,x,z-1).top()<=top&&FarlandsTerrain.column(seed,x,z+1).top()<=top)crest=true;
            }
            if(crest)supported++;
        }
        assertTrue(selected>=5);assertTrue(supported>=selected/2,"Wider dunes must still provide naturally valid cactus sites");
        assertTrue(supported<=selected,"Cacti must not force a flat pad into an unsupported dune face");
    }
    @Test void cherrySourcePlantsNeverBecomeRepeatedTerrainOrFalseTreeGround() {
        int x=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.CHERRY)+1024;
        long seed=4200439302357479658L;int ground=0;
        for(int dz=-64;dz<64;dz+=3)for(int dx=0;dx<128;dx+=3) {
            var column=FarlandsTerrain.column(seed,x+dx,dz);
            assertFalse(column.voidColumn());
            assertTrue(column.material()==1 || column.material()==3 || column.material()==4);
            for(int y=column.top()+1;y<120;y++)assertEquals(0,FarlandsTerrain.material(seed,x+dx,y,dz),"Trees and petals are generated independently of the source tile");
            if(column.material()==4)ground++;
        }
        assertTrue(ground>1000,"Cherry groves retain their grass-bearing natural surface");
    }
}
