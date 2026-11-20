package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SpokelandsDarknessTest {
 @Test void blindnessIsConfinedToBothNegativeDarkForestFaces(){
  int d=GreatSea.FARLANDS.entryBoundary+SpokelandsTerrain.start(SpokelandsTerrain.Region.DARK_FOREST)+100;
  assertTrue(SpokelandsDarkness.inside(true,0,-d));assertTrue(SpokelandsDarkness.inside(true,-d,0));
  assertFalse(SpokelandsDarkness.inside(true,0,d));assertFalse(SpokelandsDarkness.inside(true,d,0));assertFalse(SpokelandsDarkness.inside(false,0,-d));
  int entrance=GreatSea.FARLANDS.entryBoundary+SpokelandsTerrain.start(SpokelandsTerrain.Region.DARK_FOREST);
  int exit=GreatSea.FARLANDS.entryBoundary+SpokelandsTerrain.end(SpokelandsTerrain.Region.DARK_FOREST);
  assertFalse(SpokelandsDarkness.inside(true,0,-entrance+1));assertTrue(SpokelandsDarkness.inside(true,0,-entrance));assertTrue(SpokelandsDarkness.inside(true,0,-exit+1));assertFalse(SpokelandsDarkness.inside(true,0,-exit));
 }
}
