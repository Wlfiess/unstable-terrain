package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TerrainJoinTest {
    private static final long SEED=4200439302357479658L;
    private int top(long seed,int x,int z){for(int y=319;y>=-64;y--)if(TerrainMorph.solid(FarlandsTerrain.material(seed,x,y,z)))return y;return -65;}
    @Test void onlyCherryHillsAndHillsDunesUsePositiveMargins(){
        for(var stage:FarlandsTerrain.Stage.values()){
            if(stage==FarlandsTerrain.Stage.PLAINS)continue;
            int edge=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(stage);
            for(int delta:new int[]{-255,-64,-1,0,1,64,255})for(int tangent:new int[]{-321,0,473}){
                int x=edge+delta,z=tangent;
                boolean softened=stage==FarlandsTerrain.Stage.SAND_DUNES;
                assertEquals(softened,FarlandsMargins.active(x,z));
                assertEquals(softened,FarlandsMargins.active(z,x));
                if(!softened)for(int y=-64;y<=319;y+=7){
                    assertEquals(FarlandsTerrain.rawMaterial(SEED,x,y,z),FarlandsTerrain.material(SEED,x,y,z));
                    assertEquals(FarlandsTerrain.rawMaterial(SEED,z,y,x),FarlandsTerrain.material(SEED,z,y,x));
                }
            }
        }
    }
    @Test void cherryEndsTwentyBlocksAboveTheHillLanding(){
        int depth=FarlandsTerrain.start(FarlandsTerrain.Stage.PLAINS_HILLS),edge=GreatSea.FARLANDS.entryBoundary+depth;
        for(long seed:new long[]{SEED,SEED+1337})for(int tangent=-512;tangent<=512;tangent+=29){
            int cherry=top(seed,edge-1,tangent);
            assertEquals(cherry-20,top(seed,edge,tangent));
            assertEquals(cherry-20,top(seed,edge+15,tangent));
            assertEquals(top(seed,edge,tangent),top(seed,tangent,edge));
            assertFalse(FarlandsMargins.active(edge-1,tangent));
        }
    }
    @Test void cherryTerraceInteriorsKeepOneElevation(){
        int begin=FarlandsTerrain.start(FarlandsTerrain.Stage.CHERRY);
        var levels=new java.util.HashMap<Integer,Integer>();int low=100,high=0;
        for(int a=begin;a<begin+512;a+=3)for(int b=-256;b<256;b+=5){
            int original=FarlandsSamples.cherrySourceGround(SEED,a,b),lift=FarlandsSamples.cherryLift(SEED,a,b);
            Integer previous=levels.putIfAbsent(original,lift);
            if(previous!=null)assertEquals(previous.intValue(),lift,"One terrace was tilted");
            assertTrue(lift>=0&&lift<=34);low=Math.min(low,lift);high=Math.max(high,lift);
        }
        assertTrue(high-low>=20);
        var sorted=new java.util.TreeMap<Integer,Integer>(levels);int previous=-1;
        for(int lift:sorted.values()){assertTrue(lift>=previous,"Terrace order was reversed");previous=lift;}
    }
    @Test void negativeClassicKeepsItsOriginalProfileRightUpToFlowerBoundary(){
        int end=SpokelandsTerrain.end(SpokelandsTerrain.Region.CLASSIC);
        int base=GreatSea.FARLANDS.entryBoundary;
        for(int delta:new int[]{-255,-64,-1})for(int tangent:new int[]{-321,0,473})for(int y=-64;y<=255;y++){
            int actual=SpokelandsTerrain.material(SEED,tangent,y,-base-end-delta);
            int raw=PlainsFarlandsProfile.material(SEED,end+delta,y,tangent);
            assertEquals(TerrainMorph.solid(raw),TerrainMorph.solid(actual));
        }
    }
    @Test void warpedRiverBendsReverseDirectionAndKeepAWaterFilledBed(){
        int start=SpokelandsTerrain.start(SpokelandsTerrain.Region.SCULK_FOREST),base=GreatSea.FARLANDS.entryBoundary;
        double minimum=1e9,maximum=-1e9,lastSlope=0;int reversals=0;
        for(int d=start+1024;d<start+4096;d+=16){
            double center=SpokelandsTerrain.riverCenter(SEED,d),slope=SpokelandsTerrain.riverCenter(SEED,d+1)-center;
            if(slope*lastSlope<0)reversals++;lastSlope=slope;minimum=Math.min(minimum,center);maximum=Math.max(maximum,center);
            int x=(int)Math.round(center),z=-base-d;
            assertEquals(8,SpokelandsTerrain.material(SEED,x,62,z),"A river bend lost its water");
            assertEquals(60,SpokelandsTerrain.ground(SEED,x,z));
            int bed=SpokelandsTerrain.material(SEED,x,60,z);
            assertTrue(bed==SpokelandsTerrain.SCULK||bed==SpokelandsTerrain.MUD||bed==SpokelandsTerrain.FROGLIGHT);
        }
        assertTrue(maximum-minimum>300);assertTrue(reversals>=3,"River does not visibly meander");
    }
    @Test void flowerHillsHaveMossTopsOverGrayInteriors(){
        int middle=GreatSea.FARLANDS.entryBoundary+SpokelandsTerrain.start(SpokelandsTerrain.Region.FLOWER_HILLS)+9000;
        int rock=0,moss=0,low=320,high=-64;
        for(int a=-400;a<=400;a+=4)for(int b=-400;b<=400;b+=4){
            int y=SpokelandsTerrain.ground(SEED,b,-middle-a),m=SpokelandsTerrain.material(SEED,b,y,-middle-a);
            assertEquals(SpokelandsTerrain.MOSS,m,"Top-down stone patch");if(m==SpokelandsTerrain.MOSS)moss++;
            int neighbour=SpokelandsTerrain.ground(SEED,b+1,-middle-a);
            // Gray interiors remain available on taller vertical faces, below the moss cap.
            for(int face=y-6;face<=y-3;face++){
                int side=SpokelandsTerrain.material(SEED,b,face,-middle-a);
                if(side==1||side==SpokelandsTerrain.ANDESITE||side==SpokelandsTerrain.MOSSY_ROCK||side==SpokelandsTerrain.SMOOTH_ROCK)rock++;
            }
            low=Math.min(low,y);high=Math.max(high,y);
        }
        assertTrue(rock>1000,"Gray mountain interior palette is missing");assertTrue(moss>1000);assertTrue(high-low>10&&high-low<=40);
    }
    @Test void flowerAndRedPalettesStopAtAnExactStraightLine(){
        int edge=GreatSea.FARLANDS.entryBoundary+SpokelandsTerrain.start(SpokelandsTerrain.Region.RED_PLAINS);
        for(int tangent=-512;tangent<=512;tangent+=7)for(int delta:new int[]{-128,-16,-1,0,16,128}){
            int z=-edge-delta,ground=SpokelandsTerrain.ground(SEED,tangent,z);
            int material=SpokelandsTerrain.material(SEED,tangent,ground,z);
            if(delta<0)assertTrue(material==SpokelandsTerrain.MOSS||material==1||material==SpokelandsTerrain.ANDESITE||material==SpokelandsTerrain.MOSSY_ROCK||material==SpokelandsTerrain.SMOOTH_ROCK,"Red palette leaked onto a flower meadow or gray cliff");
            else assertEquals(SpokelandsTerrain.CRIMSON,material,"Flower palette leaked across the straight biome line");
        }
    }
}

