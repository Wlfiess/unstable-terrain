package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.nio.file.*;
import java.net.*;
class Revision1383Test {
 @Test void windingEntryIsBareThenPlantsIncreaseSmoothlyOnBothNegativeFaces(){
  int start=SpokelandsTerrain.windingStart();
  for(int d=0;d<=1000;d+=10){assertEquals(0,SpokelandsDecoration.windingVegetationAtDepth(start+d));
   int outward=GreatSea.FARLANDS.entryBoundary+start+d;
   assertEquals(0,SpokelandsDecoration.windingVegetation(-outward,30));
   assertEquals(0,SpokelandsDecoration.windingVegetation(30,-outward));}
  double prior=0;
  for(int d=1000;d<=1512;d++){double next=SpokelandsDecoration.windingVegetationAtDepth(start+d);assertTrue(next>=prior&&next<=1);prior=next;}
  assertEquals(.5,SpokelandsDecoration.windingVegetationAtDepth(start+1256),1e-9);
  assertEquals(1,SpokelandsDecoration.windingVegetationAtDepth(start+1512));
 }
 @Test void distributionFlagIsLoadedFromTheActualArtifactResource()throws Exception{
  byte[] code;try(var in=StructurePolicy.class.getResourceAsStream("StructurePolicy.class")){code=in.readAllBytes();}
  for(boolean enabled:new boolean[]{true,false}){
   Path root=Files.createTempDirectory("structure-policy-");Path cls=root.resolve("dev/greatsea/StructurePolicy.class"),resource=root.resolve("assets/unstableterrain/distribution.properties");
   Files.createDirectories(cls.getParent());Files.createDirectories(resource.getParent());Files.write(cls,code);Files.writeString(resource,"structures="+enabled+"\n");
   try(var loader=new URLClassLoader(new URL[]{root.toUri().toURL()},null)){
    assertEquals(enabled,loader.loadClass("dev.greatsea.StructurePolicy").getMethod("enabled").invoke(null));
   }
  }
 }
}
