package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class TerrainSamples138Test {
 @Test void completeWarpedTreesRetainSourceDimensionsAndStayInsideTheirBounds(){
  for(long i=0;i<2;i++){var t=TerrainSamples138.tree(i);assertTrue(t.height>=170&&t.height<=230);assertTrue(t.width>=150&&t.width<=280);int wood=0,crown=0,glass=0,lamps=0,mud=0;for(int z=0;z<t.depth;z++)for(int x=0;x<t.width;x++)for(int y=0;y<t.height;y++){int k=t.at(x,y,z);if(k>0&&k!=2&&k!=7)wood++;if(k==2)crown++;if(k==7)glass++;if(k==6)lamps++;if(k==15)mud++;if(x==0||z==0||x==t.width-1||z==t.depth-1)assertEquals(0,k,"A downloaded tree boundary was retained");}assertTrue(wood>100000&&crown>100000);assertTrue(glass>3000,"Original source glass was lost");assertEquals(0,lamps,"Invented backing lights remain on the branches");assertTrue(mud>500,"Original source mud was lost");assertEquals(0,t.at(-1,50,10));}
 }
 @Test void treeFeetHaveNoCircularGroundSkirtAndBranchesUseOrientedWood(){
  for(long index=0;index<2;index++){var t=TerrainSamples138.tree(index);int branches=0;
   for(int z=0;z<t.depth;z++)for(int x=0;x<t.width;x++)for(int y=0;y<t.height;y++){int k=t.at(x,y,z);assertNotEquals(4,k,"Plank strip on branch or foot");assertNotEquals(8,k,"Mossy pedestal remains");if(k==13||k==14)branches++;if(y<15&&k!=0&&k!=7&&k!=15)assertNotEquals(0,t.at(x,15,z),"Circular ground skirt extends beyond the stem profile");}
   assertTrue(branches>1000,"Horizontal fork texture must follow its direction");
  }
 }
 @Test void cherryContainsNoFloatingDirtOrSourceBuildMaterials(){
  long seed=4200439302357479658L;int low=500,high=-500;
  for(int z=-512;z<512;z+=7)for(int x=0;x<1024;x+=7){int h=TerrainSamples138.cherryHeight(seed,x,z);low=Math.min(low,h);high=Math.max(high,h);assertTrue(h>=17&&h<=87);assertTrue(TerrainSamples138.cherryMaterial(seed,x,h,z)==1||TerrainSamples138.cherryMaterial(seed,x,h,z)==4);assertEquals(0,TerrainSamples138.cherryMaterial(seed,x,h+1,z));}
  assertTrue(high-low>50);
 }
 @Test void windingRetainsTheMeasuredThirtyFiveBlockVerticalCycle(){
  int solid=0,air=0;for(int x=-200;x<200;x+=3)for(int z=-200;z<200;z+=3)for(int y=0;y<35;y++){boolean k=TerrainSamples138.windingSolid(73,x,y,z);assertEquals(k,TerrainSamples138.windingSolid(73,x,y+35,z));if(k)solid++;else air++;}assertTrue(solid>5000&&air>solid*3);
 }
 @Test void cherryCliffFacesAreStoneWhileFlatInteriorsRetainGrassAndBuriedSoil(){
  long seed=4200439302357479658L;int exposed=0,grass=0,dirt=0;
  for(int z=-192;z<192;z+=3)for(int x=0;x<384;x+=3){
   int top=TerrainSamples138.cherryHeight(seed,x,z);
   int minimum=Math.min(Math.min(TerrainSamples138.cherryHeight(seed,x-1,z),TerrainSamples138.cherryHeight(seed,x+1,z)),Math.min(TerrainSamples138.cherryHeight(seed,x,z-1),TerrainSamples138.cherryHeight(seed,x,z+1)));
   for(int y=top-2;y<=top;y++){
    int material=TerrainSamples138.cherryMaterial(seed,x,y,z);
    if(minimum<y){exposed++;assertEquals(1,material,"Exposed terrace face retained a dirt or grass-block side");}
    if(material==4)grass++;if(material==3){dirt++;assertTrue(y<=minimum);}
   }
  }
  assertTrue(exposed>1000);assertTrue(grass>1000);assertTrue(dirt>1000);
 }
}
