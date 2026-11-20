package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class FarlandsFeedbackTest {
    private static final long SEED=4200439302357479658L;
    private int start(FarlandsTerrain.Stage s){return GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(s);}
    @Test void classicFamiliesHaveStripedRoofsAndIrregularInteriorCavities(){
        for(var s:new FarlandsTerrain.Stage[]{FarlandsTerrain.Stage.PLAINS,FarlandsTerrain.Stage.SAND_FARLANDS,FarlandsTerrain.Stage.CORRUPTED}) {
            int solid=0,air=0;
            for(int dx=0;dx<64;dx+=3)for(int z=0;z<64;z+=3){int x=start(s)+1000+dx;
                if(FarlandsTerrain.missingChunk(SEED,x,z))continue;
                int roof=FarlandsTerrain.material(SEED,x,255,z);
                assertTrue(roof==0 || roof==(s==FarlandsTerrain.Stage.SAND_FARLANDS?5:4));
                assertEquals(0,FarlandsTerrain.material(SEED,x,256,z));
                for(int y=40;y<220;y+=3)if(FarlandsTerrain.material(SEED,x,y,z)==0)air++;else solid++;
            }
            assertTrue(air>1000 && solid>1000,"A perforated volume, not solid walls or copied top divots");
        }
    }
    @Test void roofOpeningsComeFromTheWallDensityRatherThanRepeatingThirtyTwoBlockLanes(){
        int x=start(FarlandsTerrain.Stage.PLAINS)+1000,solid=0,air=0,different=0;
        for(int z=-256;z<256;z++) {
            int m=FarlandsTerrain.material(SEED,x,255,z);
            if(m==0)air++;else solid++;
            if(m!=FarlandsTerrain.material(SEED,x,255,z+32))different++;
        }
        assertTrue(solid>100 && air>50 && different>100);
    }
    @Test void raisedSplitTerrainTouchesTheTempleFoundationOutsideItsRoundWall(){
        int c=FarlandsTemple.center();assertEquals(169,FarlandsTemple.BASE_Y);
        assertTrue(FarlandsTerrain.splitTop(c+76,32)>=170);
        assertEquals(FarlandsTemple.OFFSET_X,FarlandsTemple.a(GreatSea.FARLANDS.entryBoundary+c,0));
        assertEquals(512,c-FarlandsTerrain.start(FarlandsTerrain.Stage.SPLIT));
    }
    @Test void splitApproachesRetainTheMeasuredSeparatedShelves(){
        int c=FarlandsTemple.center(),x=GreatSea.FARLANDS.entryBoundary+c+6,z=100;
        assertEquals(4,FarlandsTerrain.material(SEED,x,173,z));
        assertEquals(4,FarlandsTerrain.material(SEED,x,196,z));
        for(int y=174;y<184;y++)assertEquals(0,FarlandsTerrain.material(SEED,x,y,z),"Stacked shelf gap was filled");
        assertEquals(FarlandsTerrain.material(SEED,x,171,z),FarlandsTerrain.material(SEED,x,120,z),"Lower stack does not continue at the measured period");
    }
    @Test void floatingDensityDoesNotMirrorEvery256BlocksOrImportMineshaftOutlines(){
        int x=start(FarlandsTerrain.Stage.FLOATING)+1000,diff=0,decks=0,rails=0;
        for(int dz=0;dz<512;dz+=2)for(int dx=0;dx<128;dx+=2) {
            for(int y=0;y<145;y+=4)if(FarlandsTerrain.material(SEED,x+dx,y,dz)!=FarlandsTerrain.material(SEED,x+dx,y,511-dz))diff++;
            for(int y=25;y<69;y++){int m=FarlandsTerrain.material(SEED,x+dx,y,dz);if(m==32)decks++;if(m==34 || m==38){rails++;assertEquals(32,FarlandsTerrain.material(SEED,x+dx,y-1,dz));}}
        }
        assertTrue(diff>5000);assertEquals(0,decks);assertEquals(0,rails,"Native structure pieces now supply real mineshafts");
    }
    @Test void gravelIsHigherThanFloatingAndNarrowerWithOnlyTreeDecoration(){
        int gravelX=start(FarlandsTerrain.Stage.GRAVEL)+1024;
        for(int z=0;z<256;z+=4) {
            int gravelY=FarlandsTerrain.column(SEED,gravelX,z).top();
            assertTrue(gravelY>=172);
        }
        int gravel=0;for(int z=0;z<32;z++)if(FarlandsTerrain.material(SEED,gravelX,172,z)==9)gravel++;
        assertEquals(18,gravel);assertTrue(FarlandsDecoration.treeChance(FarlandsTerrain.Stage.GRAVEL)>0);
    }
    @Test void splitCorridorsConvergeOnTheTempleWithBareGrass(){
        int c=FarlandsTemple.center(),x=GreatSea.FARLANDS.entryBoundary+c;
        assertTrue(FarlandsTerrain.bareSplit(x,0));assertEquals(4,FarlandsTerrain.material(SEED,x,168,0));
        var heights=new java.util.HashSet<Integer>();
        for(int ray=0;ray<16;ray++){double a=ray*Math.PI/8;int d=c+(int)(120*Math.cos(a)),t=(int)(120*Math.sin(a));int top=FarlandsTerrain.splitTop(d,t);assertTrue(top>=170&&top<=204);heights.add(top);}
        assertTrue(heights.size()>=4,"Measured stacked sectors collapsed to a flat approach");
    }
    @Test void chunkPaletteIncludesSixteenDistinctBiomesAndLateralDecayDoesNotTileEvery128(){
        var kinds=new java.util.HashSet<Integer>();int diff=0,x=start(FarlandsTerrain.Stage.CHUNKS)+1024,decay=start(FarlandsTerrain.Stage.DECAY)+1024;
        for(int z=0;z<512;z+=16)for(int dx=0;dx<128;dx+=16)kinds.add(FarlandsTerrain.chunkBiome(SEED,x+dx,z));
        assertEquals(16,kinds.size());
        for(int z=0;z<128;z+=3)for(int y=40;y<290;y+=3)if(FarlandsTerrain.material(SEED,decay,y,z)!=FarlandsTerrain.material(SEED,decay,y,z+128))diff++;
        assertTrue(diff>500);
    }
    @Test void lateSkygridHasSparseIsolatedBlocksAndEndsInVoid(){
        int depth=FarlandsTerrain.start(FarlandsTerrain.Stage.SKYGRID)+(int)(GreatSea.FARLANDS.stageWidths[FarlandsTerrain.Stage.SKYGRID.ordinal()]*.83);
        while(FarlandsTerrain.skygridSpacing(depth)==0)depth++;
        int spacing=FarlandsTerrain.skygridSpacing(depth),x=GreatSea.FARLANDS.entryBoundary+depth,solid=0;
        for(int z=0;z<256;z++)for(int y=0;y<256;y++)if(FarlandsTerrain.material(SEED,x,y,z)!=0){solid++;assertEquals(0,y%spacing);assertEquals(0,z%spacing);}
        assertTrue(solid<4,"Late candidate planes should be mostly empty");
        int cleared=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.SKYGRID)+(int)(GreatSea.FARLANDS.stageWidths[FarlandsTerrain.Stage.SKYGRID.ordinal()]*.91);
        for(int z=0;z<128;z+=4)for(int y=-64;y<320;y+=4)assertEquals(0,FarlandsTerrain.material(SEED,cleared,y,z),"SkyGrid must reach open void before the old endpoint");
    }
    @Test void reliefCacheKeepsSeedCoordinatesAndTerrainKindsSeparate(){
        int hills=FarlandsRelief.ridge(SEED,-91,33,false),dunes=FarlandsRelief.ridge(SEED,-91,33,true),spires=FarlandsRelief.spire(SEED,-91,33);
        FarlandsRelief.spire(SEED+1,180,-33);
        assertEquals(hills,FarlandsRelief.ridge(SEED,-91,33,false));assertEquals(dunes,FarlandsRelief.ridge(SEED,-91,33,true));assertEquals(spires,FarlandsRelief.spire(SEED,-91,33));
    }
    @Test void dunesUseTheTallOxumsReferenceHeightRange(){
        int min=400,max=-64;
        for(int x=-2048;x<2048;x+=8)for(int z=-2048;z<2048;z+=8) {
            int h=FarlandsRelief.ridge(SEED,x,z,true);min=Math.min(min,h);max=Math.max(max,h);
        }
        assertTrue(max>270 && max<=319);assertTrue(min>=-60 && max-min>160);
    }
    @Test void classicGalleriesContinueDeepIntoTheWall(){
        int x=start(FarlandsTerrain.Stage.PLAINS)+1024,same=0,total=0;
        for(int z=-64;z<64;z+=2)for(int y=0;y<252;y+=2) {
            if((FarlandsTerrain.material(SEED,x,y,z)==0)==(FarlandsTerrain.material(SEED,x+64,y,z)==0))same++;
            total++;
        }
        assertTrue(same>total*.92,"Wall galleries must stretch through depth rather than isolated pockets");
    }
}
