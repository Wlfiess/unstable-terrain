package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SculkTreeLightsTest {
 @Test void thinInwardBackingAndCoveredEdges(){for(long id=0;id<2;id++){var t=TerrainSamples138.tree(id);int lights=0,glass=0;for(int z=0;z<t.depth;z++)for(int x=0;x<t.width;x++)for(int y=0;y<t.height;y++){
 if(SpokelandsDecoration.treeLight(t,x,y,z)){lights++;for(var d:SpokelandsDecoration.LIGHT_DIRS)if(t.at(x+d[0],y+d[1],z+d[2])==0)assertTrue(SpokelandsDecoration.glassCover(t,x+d[0],y+d[1],z+d[2]));}
 if(t.at(x,y,z)==7){glass++;int i=SpokelandsDecoration.backingDirection(t,x,y,z);if(i>=0){var d=SpokelandsDecoration.LIGHT_DIRS[i];assertTrue(SpokelandsDecoration.treeLight(t,x+d[0],y+d[1],z+d[2]));}}
 }assertTrue(lights>1000);assertTrue(lights<=glass,"Backing spread onto multiple sides of the glass strip");}}
}