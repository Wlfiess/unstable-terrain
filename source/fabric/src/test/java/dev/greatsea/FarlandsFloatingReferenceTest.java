package dev.greatsea;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FarlandsFloatingReferenceTest {
    private static final long SEED=4200439302357479658L;
    @Test void nativePresetHeightBoundsHaveNoBasementOrImportedVolume(){
        for(int x=-128;x<128;x+=8)for(int z=-128;z<128;z+=8){
            assertFalse(VanillaSkylands.solid(SEED,x,-1,z));
            assertFalse(VanillaSkylands.solid(SEED,x,256,z));
        }
    }    @Test void warpedIslandsRetainLargeOpenSpacesAndHaveNoArtificialFloor() {
        int air=0,solid=0,upper=0,lower=0;
        for(int a=26000;a<26512;a+=8)for(int b=-256;b<256;b+=8) {
            assertEquals(0,FarlandsFloating.material(SEED,a,-64,b));
            for(int y=256;y<320;y+=16)assertEquals(0,FarlandsFloating.material(SEED,a,y,b));
            for(int y=-60;y<205;y+=3) {
                int m=FarlandsFloating.material(SEED,a,y,b);
                if(m==0)air++;else{solid++;if(y>100)upper++;if(y>=0 && y<80)lower++;}
            }
        }
        assertTrue(air>solid*2,"Reference islands must retain open space between the broad masses");
        assertTrue(upper>500 && lower>500);
    }
    @Test void seedWarpDoesNotRepeatExactSourceVolumes() {
        int different=0;
        for(int a=26000;a<26448;a+=8)for(int b=-224;b<224;b+=8)for(int y=-40;y<205;y+=12)
            if(FarlandsFloating.material(SEED,a,y,b)!=FarlandsFloating.material(SEED,a+192,y,b))different++;
        assertTrue(different>500,"Neighboring island fields must not copy the same source layout");
    }
    @Test void noLargeSquareOfMissingTerrainBetweenIslandFields() {
        boolean[][] occupied=new boolean[64][64];
        for(int z=0;z<64;z++)for(int x=0;x<64;x++)for(int y=-40;y<=200;y+=8)
            if(FarlandsFloating.material(SEED,26000+x*8,y,-256+z*8)!=0){occupied[z][x]=true;break;}
        for(int z=0;z<=48;z+=4)for(int x=0;x<=48;x+=4) {
            int land=0;for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++)if(occupied[z+dz][x+dx])land++;
            assertTrue(land>8,"An artificial 128-block empty square remains at "+x+","+z);
        }
    }
    @Test void skyClimateContainsDistinctLandBiomesAcrossTheRegion() {
        var biomes=new java.util.HashSet<net.minecraft.registry.RegistryKey<net.minecraft.world.biome.Biome>>();
        int start=GreatSea.FARLANDS.entryBoundary+FarlandsTerrain.start(FarlandsTerrain.Stage.FLOATING);
        for(int z=-4096;z<4096;z+=128)for(int x=start+256;x<start+8256;x+=128) {
            var key=FarlandsBiomes.key(SEED,x,z);biomes.add(key);
            assertFalse(key.getValue().getPath().contains("ocean"));
            assertFalse(key.getValue().getPath().contains("river"));
        }
        assertTrue(biomes.size()>=9,"Floating lands need broad real ecosystems, not one plains biome");
    }
    @Test void warpedSourceIsContinuousAcrossOldIslandStampBoundaries() {
        int discontinuity=0,regular=0,seamPlanes=0,regularPlanes=0;
        for(int a=26000;a<26512;a++)if(Math.floorMod(a+1,192)==0)seamPlanes++;else if(Math.floorMod(a,8)==0)regularPlanes++;
        for(int b=-256;b<256;b+=4)for(int y=-40;y<205;y+=4) {
            for(int a=26000;a<26512;a++) {
                if(Math.floorMod(a+1,192)!=0 && Math.floorMod(a,8)!=0)continue;
                if(FarlandsFloating.material(SEED,a,y,b)!=FarlandsFloating.material(SEED,a+1,y,b)) {
                    if(Math.floorMod(a+1,192)==0)discontinuity++;else regular++;
                }
            }
        }
        assertTrue(discontinuity/(double)seamPlanes<regular/(double)regularPlanes*3,"Former template-cell seams must not create long sheer walls");
    }
}
