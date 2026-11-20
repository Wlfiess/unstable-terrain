package dev.greatsea;
import java.security.*;import java.util.*;
class SeaProfileEquivalenceTest {
 static String digest()throws Exception{
  var hash=MessageDigest.getInstance("SHA-256");var configs=new SeaConfig[]{new SeaConfig(),new SeaConfig(),new SeaConfig()};configs[1].beachWidth=128;configs[1].underwaterSlopeWidth=128;configs[1].coastWaviness=0;configs[1].minFloorY=-42;configs[1].maxFloorY=-8;configs[2].beachWidth=1000;configs[2].coastWaviness=333;configs[2].underwaterSlopeWidth=800;
  for(var c:configs)for(long seed:new long[]{0,1,-91,4200439302357479658L})for(int boundary:new int[]{c.innerBoundary,c.outerBoundary})for(int sign:new int[]{-1,1})for(int d=-96;d<c.beachWidth+c.underwaterSlopeWidth+c.coastWaviness+320;d+=3)for(int tangent:new int[]{0,boundary-16,boundary,boundary+16}){
   int x=sign*(boundary+(boundary==c.innerBoundary?d:-d)),z=tangent;double floor=-55+70*SeaTerrain.unit(SeaTerrain.hash(seed+9191,x,z));double h=SeaTerrain.height(c,seed,x,z,64,floor);long bits=Double.doubleToLongBits(h);for(int s=0;s<64;s+=8)hash.update((byte)(bits>>>s));hash.update((byte)(SeaTerrain.deepFloor(c,seed,x,z)?1:0));
  }return HexFormat.of().formatHex(hash.digest());
 }
 @org.junit.jupiter.api.Test void coastlineHeightsAndDeepFloorRemainBitIdenticalTo1490()throws Exception{org.junit.jupiter.api.Assertions.assertEquals("23ec34aa8dfbd6dcdc43a54f19fe9ce1d74f72eceb8ffd0c973b03e3b965c23a",digest());}
}
