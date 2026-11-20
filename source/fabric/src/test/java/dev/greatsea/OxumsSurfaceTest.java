package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class OxumsSurfaceTest {
    @Test void nativeSurfaceCapsFollowMeasuredSandAndSoilThickness() {
        long seed=4200439302357479658L;int[] sand=new int[4],soil=new int[3];
        for(var stage:new FarlandsTerrain.Stage[]{FarlandsTerrain.Stage.SAND_DUNES,FarlandsTerrain.Stage.PLAINS_HILLS}) {
            int start=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(stage)+1024;
            for(int z=0;z<128;z++)for(int dx=0;dx<128;dx++) {
                int x=start+dx;var p=FarlandsWorld.project(x,z);
                int top=FarlandsRelief.ridge(seed,p.depth(),p.tangent(),stage==FarlandsTerrain.Stage.SAND_DUNES);
                if(stage==FarlandsTerrain.Stage.SAND_DUNES) {
                    int layers=0;for(int k=0;k<4;k++)if(FarlandsTerrain.material(seed,x,top-k,z)==5)layers++;else break;
                    assertTrue(layers>=1 && layers<=3);sand[layers]++;
                    assertEquals(6,FarlandsTerrain.material(seed,x,top-layers,z));
                }else {
                    assertEquals(4,FarlandsTerrain.material(seed,x,top,z));
                    int layers=0;for(int k=1;k<=3;k++)if(FarlandsTerrain.material(seed,x,top-k,z)==3)layers++;else break;
                    assertTrue(layers<=2);soil[layers]++;
                    assertEquals(1,FarlandsTerrain.material(seed,x,top-layers-1,z));
                }
            }
        }
        assertTrue(sand[2]/16384.0>.60 && sand[2]/16384.0<.69,"Source sand cap is predominantly two blocks");
        assertTrue(soil[1]/16384.0>.75 && soil[1]/16384.0<.83,"Source grassy hills predominantly have one dirt layer");
    }
}
