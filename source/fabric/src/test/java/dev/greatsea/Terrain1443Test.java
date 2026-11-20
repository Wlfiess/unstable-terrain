package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class Terrain1443Test {
 @Test void approvedLowerWavesRemainExactAndTopIsAWholeSampledCrest(){
  int top=0;for(int x=0;x<576;x+=3)for(int z=0;z<928;z+=5){
   for(int q:new int[]{0,17,60,140,250,300,322})assertEquals(TerrainSamples138.windingSolid(73,x,q,z),SpokelandsTerrain.windingWave(73,x,q,z));
   for(int q=323;q<335;q++)assertFalse(SpokelandsTerrain.windingWave(73,x,q,z));
   for(int q=335;q<=366;q++)assertEquals(TerrainSamples138.windingSolid(73,x,q-9,z),SpokelandsTerrain.windingWave(73,x,q,z));
   assertFalse(TerrainSamples138.windingSolid(73,x,358,z),"Sample above the complete crest is not empty");
   assertFalse(SpokelandsTerrain.windingWave(73,x,367,z));if(SpokelandsTerrain.windingWave(73,x,366,z))top++;
  }assertTrue(top>100);
 }
 @Test void riverRocksAreSingleConnectedBouldersWithoutNeighbouringFragments(){
  for(int n=0;n<2;n++){var v=TerrainSamples138.riverRock(n);var cells=new java.util.HashSet<String>();for(var p:v)cells.add(p[0]+","+p[1]+","+p[2]);
   var pending=new java.util.ArrayDeque<int[]>();var seen=new java.util.HashSet<String>();pending.add(v[0]);
   while(!pending.isEmpty()){var p=pending.remove();String key=p[0]+","+p[1]+","+p[2];if(!cells.contains(key)||!seen.add(key))continue;
    for(var d:new int[][]{{1,0,0},{-1,0,0},{0,1,0},{0,-1,0},{0,0,1},{0,0,-1}})pending.add(new int[]{p[0]+d[0],p[1]+d[1],p[2]+d[2]});
   }assertEquals(cells.size(),seen.size());assertTrue(cells.size()>200&&cells.size()<400);
   for(var p:v)if(p[1]==0)assertTrue(cells.contains(p[0]+",1,"+p[2]),"Flat crop strip outside the boulder footprint");
  }
 }
 @Test void riverbedHasMoreMudInCoherentPerlinPatches(){
  int mud=0,count=0,same=0;for(int x=0;x<256;x++)for(int z=0;z<256;z++){int m=SpokelandsTerrain.riverBed(73,x,z);if(m==SpokelandsTerrain.MUD)mud++;assertTrue(m==SpokelandsTerrain.MUD||m==SpokelandsTerrain.SCULK);if(m==SpokelandsTerrain.riverBed(73,x+1,z))same++;count++;}
  assertTrue(mud>count*.55&&mud<count*.90,"Mud coverage "+mud/(double)count);assertTrue(same>count*.85);
 }
}
