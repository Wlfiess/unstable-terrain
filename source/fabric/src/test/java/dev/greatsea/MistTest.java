package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import net.minecraft.world.biome.BiomeKeys;

class MistTest {
 @Test void correctedCenterAndReferenceLocationsAreInside(){
  assertEquals(1,MistRegion.strength(739,-105599));
  assertTrue(MistRegion.core(11840,-108251));
  assertTrue(MistRegion.core(-3244,-107986));
  assertFalse(MistRegion.contains(739,-10559));
  assertFalse(MistRegion.contains(0,0));
 }
 @Test void fringeIsContinuousAndNeverAmplified(){
  double last=1;
  for(int d=0;d<=2300;d++){
   double value=MistRegion.strength(739,-105599-16000-d);
   assertTrue(value<=last+1e-12);last=value;
  }
  assertEquals(0,last);
  for(int z=-125000;z<-85000;z+=128)for(int x=-23000;x<24000;x+=128)
   if(MistRegion.contains(x,z))assertEquals(0,AmplifiedTerrain.weight(x,z));
 }
 @Test void warmBiomesBecomeColdWithoutErasingWaterGeography(){
  assertTrue(MistBiomes.cold(MistBiomes.key(73,739,-105599,BiomeKeys.DESERT)));
  assertTrue(MistBiomes.cold(MistBiomes.key(73,739,-105599,BiomeKeys.LUSH_CAVES)));
  assertEquals(BiomeKeys.FROZEN_RIVER,MistBiomes.key(73,739,-105599,BiomeKeys.RIVER));
  assertEquals(MistBiomes.DEEP_OCEAN,MistBiomes.key(73,739,-105599,BiomeKeys.DEEP_LUKEWARM_OCEAN));
  assertEquals(BiomeKeys.DESERT,MistBiomes.key(73,0,0,BiomeKeys.DESERT));
 }
 @Test void cavesKeepMistButConstructedInteriorsDoNot(){
  assertFalse(MistShelter.sheltered(new MistShelter.Evidence(false,true,0,0,4,false,false,1)));
  assertFalse(MistShelter.sheltered(new MistShelter.Evidence(false,false,0,0,0,false,false,0)));
  assertTrue(MistShelter.sheltered(new MistShelter.Evidence(true,true,0,0,0,false,false,0)));
  assertTrue(MistShelter.sheltered(new MistShelter.Evidence(false,true,9,0,2,false,true,0)));
  assertTrue(MistShelter.sheltered(new MistShelter.Evidence(false,true,0,3,4,true,true,4)));
  assertTrue(MistShelter.sheltered(new MistShelter.Evidence(false,true,0,0,4,true,true,4)));
 }
}
