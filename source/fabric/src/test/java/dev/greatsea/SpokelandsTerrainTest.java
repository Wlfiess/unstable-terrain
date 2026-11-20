package dev.greatsea;

import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.util.Identifier;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

/** Independent invariants for negative terrain, its transitions and legacy isolation. */
class SpokelandsTerrainTest {
    private static final long SEED=4200439302357479658L;
    private static int outward(SpokelandsTerrain.Region region,int offset){return GreatSea.FARLANDS.entryBoundary+SpokelandsTerrain.start(region)+offset;}
    private static int middle(SpokelandsTerrain.Region region){return outward(region,GreatSea.FARLANDS.spokelandsWidths[region.ordinal()]/2);}
    private static int[] position(int outward,int tangent,boolean west){return west?new int[]{-outward,tangent}:new int[]{tangent,-outward};}
    private static boolean solid(int material){return material!=0&&material!=8;}

    @Test void dominantNegativeFacePreservesPositiveMixedCornersAndDiagonalTie(){
        int boundary=GreatSea.FARLANDS.entryBoundary;
        assertFalse(SpokelandsTerrain.contains(-boundary+1,0));assertFalse(SpokelandsTerrain.contains(0,-boundary+1));
        assertTrue(SpokelandsTerrain.contains(-boundary,0));assertTrue(SpokelandsTerrain.contains(0,-boundary));
        assertFalse(SpokelandsTerrain.contains(boundary,0));assertFalse(SpokelandsTerrain.contains(0,boundary));
        assertFalse(SpokelandsTerrain.contains(boundary+16,-boundary));
        assertTrue(SpokelandsTerrain.contains(boundary,-boundary-16));
        assertTrue(SpokelandsTerrain.contains(-boundary-16,boundary));
        assertFalse(SpokelandsTerrain.contains(-boundary,boundary+16));
        assertFalse(SpokelandsTerrain.contains(boundary,-boundary),"Positive X wins an exact diagonal tie");
        assertTrue(SpokelandsTerrain.contains(-boundary,boundary),"Negative X wins an exact diagonal tie");
        assertTrue(SpokelandsTerrain.contains(-boundary,-boundary));
        assertFalse(SpokelandsTerrain.contains(boundary,boundary));
    }

    @Test void allNineRegionsBeginAndEndInTheSameNorthWestOrderWithNamedNavigation(){
        String[] names={"plains","flower_hills","red_plains","winding","dark_forest","bridges","rainbow_forest","sand_lands","sculk_forest"};
        var regions=SpokelandsTerrain.Region.values();assertEquals(names.length,regions.length);
        int cumulative=0;
        for(int index=0;index<regions.length;index++){
            var region=regions[index];int width=GreatSea.FARLANDS.spokelandsWidths[index];
            assertTrue(width>=256);assertEquals(cumulative,SpokelandsTerrain.start(region));assertEquals(cumulative+width,SpokelandsTerrain.end(region));
            assertEquals(names[index],SpokelandsCommand.name(region));
            for(boolean west:new boolean[]{false,true})for(int offset:new int[]{0,1,width/2,width-1}){
                var p=position(outward(region,offset),23,west);
                assertTrue(SpokelandsTerrain.contains(p[0],p[1]));assertEquals(region,SpokelandsTerrain.region(p[0],p[1]));
            }
            if(index+1<regions.length)for(boolean west:new boolean[]{false,true}){
                var p=position(outward(region,width),-29,west);assertEquals(regions[index+1],SpokelandsTerrain.region(p[0],p[1]));
            }
            cumulative+=width;
        }
        assertEquals(SpokelandsTerrain.Region.SCULK_FOREST,SpokelandsTerrain.region(0,-GreatSea.FARLANDS.entryBoundary-cumulative-1024));
    }

    @Test void negativeRouteRejectsLegacyMissingChunksLandmarksAndTrialChambers(){
        var trial=RegistryKey.of(RegistryKeys.STRUCTURE,Identifier.ofVanilla("trial_chambers"));
        var mine=RegistryKey.of(RegistryKeys.STRUCTURE,Identifier.ofVanilla("mineshaft"));
        for(var region:SpokelandsTerrain.Region.values())for(boolean west:new boolean[]{false,true}){
            int distance=middle(region);
            for(int tangent=-96;tangent<=96;tangent+=16){
                var p=position(distance,tangent,west);
                assertFalse(FarlandsTerrain.missingChunk(SEED,p[0],p[1]),"Legacy corrupted mask leaked into "+region);
                assertFalse(FarlandsStructures.trialAllowed(p[0],p[1]));assertFalse(FarlandsStructures.accepts(trial,p[0],p[1]));
                assertEquals(region==SpokelandsTerrain.Region.RED_PLAINS,FarlandsStructures.accepts(mine,p[0],p[1]));
            }
        }
        int oldCorrupted=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.CORRUPTED);
        for(int depth=16;depth<16000;depth+=256)assertFalse(FarlandsTerrain.missingChunk(SEED,-oldCorrupted-depth,0));
        int empire=GreatSea.FARLANDS.entryBoundary+FarlandsEmpire.origin()+64;
        assertFalse(FarlandsEmpire.footprint(-empire,FarlandsEmpire.tangentOrigin()+64));
        assertFalse(FarlandsEmpire.backdrop(FarlandsEmpire.NEAREST_TANGENT+1,-empire));
        int temple=GreatSea.FARLANDS.entryBoundary+FarlandsTemple.center();
        assertFalse(FarlandsTemple.contains(-temple,164,0));assertFalse(FarlandsTemple.footprint(0,-temple));
        int orbit=GreatSea.FARLANDS.entryBoundary+FarlandsOrbitals.center();
        assertFalse(FarlandsOrbitals.gap(SEED,-orbit,0));assertFalse(FarlandsOrbitals.contains(SEED,0,160,-orbit));
    }

    @Test void everyRegionHasSubstantialTerrainInFreshChunksAndSharedRoutingMatches(){
        for(var region:SpokelandsTerrain.Region.values())for(boolean west:new boolean[]{false,true})for(int chunkOffset:new int[]{-32,0,32}){
            int distance=middle(region)+chunkOffset,solid=0,columns=0;
            for(int tangent=-16;tangent<16;tangent+=4)for(int advance=0;advance<16;advance+=4){
                var p=position(distance+advance,tangent,west);boolean land=false;
                for(int y=-64;y<320;y+=4){
                    int material=SpokelandsTerrain.material(SEED,p[0],y,p[1]);
                    assertEquals(material,FarlandsTerrain.material(SEED,p[0],y,p[1]),"Shared route differs in "+region);
                    assertTrue(material>=0&&material<=SpokelandsTerrain.BRIDGE_VILLAGE);
                    if(solid(material)){solid++;land=true;}
                }
                if(land)columns++;
            }
            assertTrue(solid>24,region+" contains an empty or fragment-only generated chunk");
            assertTrue(columns>=24,region+" has broad missing terrain rather than natural local openings");
        }
    }

    @Test void windingGroundRemainsWalkableBelowMultipleSeparatedRibbonLayers(){
        int distance=middle(SpokelandsTerrain.Region.WINDING),columnsWithMesh=0,columnsWithMultipleLayers=0,gapBlocks=0;
        for(int tangent=-160;tangent<=160;tangent+=8)for(int advance=-160;advance<=160;advance+=8){
            int x=tangent,z=-distance-advance,ground=SpokelandsTerrain.ground(SEED,x,z);
            assertEquals(4,SpokelandsTerrain.material(SEED,x,ground,z));assertEquals(0,SpokelandsTerrain.material(SEED,x,ground+1,z));
            int layers=0;boolean previous=false;
            for(int y=ground+2;y<320;y++){
                boolean current=solid(SpokelandsTerrain.material(SEED,x,y,z));
                if(current&&!previous)layers++;if(!current)gapBlocks++;previous=current;
            }
            if(layers>0)columnsWithMesh++;if(layers>1)columnsWithMultipleLayers++;
        }
        assertTrue(columnsWithMesh>200,"Winding ceiling is absent or too sparse");
        assertTrue(columnsWithMultipleLayers>20,"Winding ceiling collapsed into one heightmap layer");
        assertTrue(gapBlocks>50000,"Walking clearance and separated upper layers were lost");
    }

    @Test void smoothFloorsCrossPhysicalChunkSeamsWithoutArtificialCliffs(){
        var regions=new SpokelandsTerrain.Region[]{SpokelandsTerrain.Region.FLOWER_HILLS,SpokelandsTerrain.Region.RED_PLAINS,SpokelandsTerrain.Region.WINDING,SpokelandsTerrain.Region.DARK_FOREST,SpokelandsTerrain.Region.RAINBOW_FOREST,SpokelandsTerrain.Region.SCULK_FOREST};
        for(var region:regions){
            int aligned=(middle(region)/16)*16;
            for(int offset=-128;offset<=128;offset+=16)for(int tangent=-128;tangent<=128;tangent+=16){
                int z=-aligned-offset,x=tangent;
                int a=SpokelandsTerrain.ground(SEED,x,z),b=SpokelandsTerrain.ground(SEED,x,z-1),c=SpokelandsTerrain.ground(SEED,x-1,z);
                int naturalStep=region==SpokelandsTerrain.Region.FLOWER_HILLS?8:5;
                assertTrue(Math.abs(a-b)<=naturalStep,"Depth chunk seam cuts off "+region+" at "+x+","+z+": "+a+" / "+b);
                assertTrue(Math.abs(a-c)<=naturalStep,"Tangent chunk seam cuts off "+region+" at "+x+","+z+": "+a+" / "+c);
                assertTrue(solid(SpokelandsTerrain.material(SEED,x,a,z)));
            }
        }
    }

    @Test void luminousRiverHasContinuousWaterSupportedByRealTerrainAndDryWoodlandBanks(){
        int distance=middle(SpokelandsTerrain.Region.SCULK_FOREST),wet=0,dry=0;var wetCenters=new HashSet<Integer>();
        for(int advance=-256;advance<=256;advance+=32){
            int minimum=10000,maximum=-10000;
            for(int tangent=-320;tangent<=320;tangent+=8){
                int x=tangent,z=-distance-advance,ground=SpokelandsTerrain.ground(SEED,x,z),floor=SpokelandsTerrain.material(SEED,x,ground,z);
                if(SpokelandsTerrain.material(SEED,x,62,z)==8){
                    wet++;minimum=Math.min(minimum,tangent);maximum=Math.max(maximum,tangent);
                    assertTrue(solid(floor),"Water floats over a cut-off column");
                    for(int y=ground+1;y<=62;y++)assertEquals(8,SpokelandsTerrain.material(SEED,x,y,z),"River column contains a broken air interval");
                    for(int y=ground-4;y<=ground;y++)assertTrue(solid(SpokelandsTerrain.material(SEED,x,y,z)),"Riverbed has no supporting roof");
                }else if(floor==SpokelandsTerrain.WARPED||floor==SpokelandsTerrain.SCULK){
                    dry++;int above=SpokelandsTerrain.material(SEED,x,ground+1,z);assertTrue(above==0||above==1||above==SpokelandsTerrain.ANDESITE,"Unexpected block over dry ground");
                }
            }
            if(maximum>=minimum)wetCenters.add((minimum+maximum)/2);
        }
        assertTrue(wet>60,"Broad river is absent");assertTrue(dry>600,"Forest floor is flooded or absent");
        assertTrue(wetCenters.size()>3,"River became a straight fixed-width channel");
    }

    @Test void redSandHasOpenWalkingGroundAndCoherentHighArchesInsteadOfDenseFins(){
        int distance=middle(SpokelandsTerrain.Region.SAND_LANDS),openGround=0,arches=0,tallSupports=0;
        for(int advance=-320;advance<=320;advance+=8)for(int tangent=-320;tangent<=320;tangent+=8){
            int x=tangent,z=-distance-advance;
            boolean lowAir=SpokelandsTerrain.material(SEED,x,172,z)==0;
            if(lowAir)openGround++;
            boolean roof=false;for(int y=240;y<=311;y+=4)if(solid(SpokelandsTerrain.material(SEED,x,y,z))){roof=true;break;}
            if(lowAir&&roof)arches++;
            if(!lowAir&&roof)tallSupports++;
            assertTrue(solid(SpokelandsTerrain.material(SEED,x,120,z)),"Sand floor contains a missing download column");
        }
        assertTrue(openGround>3000,"Sand traversal became a dense solid maze");
        assertTrue(arches>150,"Connected high sandstone arches are absent");
        // Slender supports should occupy at least 1.5% of this broad traversal sample.
        assertTrue(tallSupports>100,"High arches lack substantial natural supports: "+tallSupports);
    }

    @Test void redSandFoothillsHaveNoMissingMaterialUnderReportedWalkingSurface(){
        int middle=middle(SpokelandsTerrain.Region.SAND_LANDS);
        for(long seed:new long[]{SEED,SEED+1337})for(int tangent=-256;tangent<=256;tangent+=11)for(int advance=-256;advance<=256;advance+=11){
            int x=tangent,z=-middle-advance,ground=SpokelandsTerrain.ground(seed,x,z);
            // A former support assignment replaced the skirt union and left air
            // below its reported walking level. Check the entire load-bearing skirt.
            for(int y=125;y<=ground;y++)assertTrue(solid(SpokelandsTerrain.material(seed,x,y,z)),"Air in sand foothill at "+x+","+y+","+z);
        }
    }

    @Test void classicPlainsMatchesTheExistingPositiveRouteWithoutReseedingOrAddedTerrain(){
        int span=Math.min(GreatSea.FARLANDS.stageWidths[0],GreatSea.FARLANDS.spokelandsWidths[0]);
        for(int depth:new int[]{0,17,span/3,span-400})for(int tangent=-96;tangent<=96;tangent+=13){
            int outward=GreatSea.FARLANDS.entryBoundary+depth;
            for(int y=-64;y<320;y++){
                int positive=FarlandsTerrain.material(SEED,tangent,y,outward);
                assertEquals(positive,SpokelandsTerrain.material(SEED,tangent,y,-outward));
                assertEquals(positive,SpokelandsTerrain.material(SEED,-outward,y,tangent));
            }
        }
    }

    @Test void seededTerrainIsRepeatableVariesWithSeedAndSharesBothNegativeFaceGeometry(){
        for(var region:SpokelandsTerrain.Region.values()){
            int distance=middle(region),different=0;long first=0,second=0;
            for(int tangent=-96;tangent<=96;tangent+=8)for(int advance=0;advance<64;advance+=8){
                int x=tangent,z=-distance-advance;
                int[] original=sample(SEED,x,z),other=sample(SEED+1337,x,z),mirror=sample(SEED,z,x),again=sample(SEED,x,z);
                for(int index=0;index<original.length;index++){
                    int a=original[index],b=other[index];
                    first=first*31+a;second=second*31+b;if(a!=b)different++;
                    assertEquals(a,mirror[index],"North/west profiles differ in "+region);
                    assertEquals(a,again[index],"Seed cache leaked between queries in "+region);
                }
            }
            if(region==SpokelandsTerrain.Region.BRIDGES){assertEquals(0,different,"Exact bridge sample must retain its source geometry across seeds");assertEquals(first,second);}else{assertTrue(different>25,"Terrain ignores the world seed in "+region);assertNotEquals(first,second);}
        }
    }

    private static int[] sample(long seed,int x,int z){
        int[] result=new int[30];for(int index=0;index<result.length;index++)result[index]=SpokelandsTerrain.material(seed,x,64+index*8,z);return result;
    }

    @Test void widthChangesInvalidateCachedColumnsAndRestoreTheOriginalResult(){
        int[] original=GreatSea.FARLANDS.spokelandsWidths.clone();
        int distance=outward(SpokelandsTerrain.Region.RED_PLAINS,128),x=37,z=-distance;
        int[] before=new int[384];for(int y=-64;y<320;y++)before[y+64]=SpokelandsTerrain.material(SEED,x,y,z);
        try{
            var changed=original.clone();changed[0]+=32000;GreatSea.FARLANDS.spokelandsWidths=changed;
            assertEquals(SpokelandsTerrain.Region.CLASSIC,SpokelandsTerrain.region(x,z));
            int[] after=new int[384];for(int y=-64;y<320;y++)after[y+64]=SpokelandsTerrain.material(SEED,x,y,z);
            assertFalse(Arrays.equals(before,after),"Old regional column survived a changed width configuration");
        }finally{GreatSea.FARLANDS.spokelandsWidths=original;}
        for(int y=-64;y<320;y++)assertEquals(before[y+64],SpokelandsTerrain.material(SEED,x,y,z),"Restored widths retained a stale column");
    }
    @Test void redPlainsOuterChunksStayAtTheNaturalFloorWithoutLiftedChunkPillars(){
        int end=GreatSea.FARLANDS.entryBoundary+SpokelandsTerrain.end(SpokelandsTerrain.Region.RED_PLAINS);
        for(boolean west:new boolean[]{false,true})for(int depth=end-720;depth<end-16;depth+=8){
            int previous=-1;
            for(int tangent=-256;tangent<=256;tangent+=8){
                var p=position(depth,tangent,west);int ground=SpokelandsTerrain.ground(SEED,p[0],p[1]);
                assertTrue(ground>=140&&ground<=172,"A Red Plains chunk was lifted above the rolling floor");
                if(previous!=-1)assertTrue(Math.abs(ground-previous)<8,"A chunk boundary became a raised wall");previous=ground;
            }
        }
    }
}

