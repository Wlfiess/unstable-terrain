package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class AmplifiedTransitionTest {
 @Test void smoothSymmetricEntryAndExit(){
  for(int d=0;d<=AmplifiedTerrain.TRANSITION;d++){
   int r=AmplifiedTerrain.START+d;
   double w=AmplifiedTerrain.weight(r,0);
   assertEquals(w,AmplifiedTerrain.weight(-r,0));
   assertEquals(w,AmplifiedTerrain.weight(0,r));
   assertEquals(w,AmplifiedTerrain.weight(0,-r));
   assertEquals(w,AmplifiedTerrain.weight(AmplifiedTerrain.END-d,0),1e-12);
   if(d>0)assertTrue(w>=AmplifiedTerrain.weight(r-1,0));
  }
  assertEquals(0,AmplifiedTerrain.weight(AmplifiedTerrain.START-1,0));
  assertEquals(0,AmplifiedTerrain.weight(AmplifiedTerrain.START,0));
  assertEquals(1,AmplifiedTerrain.weight(AmplifiedTerrain.START+AmplifiedTerrain.TRANSITION,0));
  assertEquals(1,AmplifiedTerrain.weight(160000,160000));
  assertEquals(0,AmplifiedTerrain.weight(240000,0));
  assertTrue(AmplifiedTerrain.weight(AmplifiedTerrain.START+1,0)<1e-8);
  assertTrue(1-AmplifiedTerrain.weight(AmplifiedTerrain.START+AmplifiedTerrain.TRANSITION-1,0)<1e-8);
 }
}
