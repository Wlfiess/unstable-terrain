package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class SeaBiomeCacheTest {
 @Test void horizontalCacheDoesNotChangeCoastOrOffshoreBiomesAcrossSeedsAndSettings(){
  var c=new SeaConfig();for(long seed:new long[]{0,72,-8647155992503787980L})for(int change=0;change<3;change++){
   c.coastWaviness=change*24;c.seaLevel=200+change;
   for(int x=250450;x<251300;x+=3)for(int z=-22;z<22;z+=4){
    int bx=Math.floorDiv(x,4)*4+2,bz=Math.floorDiv(z,4)*4+2;
    var expected=SeaBiomeContext.uncachedBiomeKey(c,seed,bx,bz,null);
    assertEquals(expected,SeaBiomeContext.biomeKey(c,seed,x,z));assertEquals(expected,SeaBiomeContext.biomeKey(c,seed,x,z));
   }
  }
 }
 @Test void cacheRejectsStaleEntriesAfterConfigMutationAtTheSameCoordinates(){
  var c=new SeaConfig();int x=250550,z=2;var before=SeaBiomeContext.biomeKey(c,72,x,z);c.beachWidth=128;
  var after=SeaBiomeContext.biomeKey(c,72,x,z);assertNotEquals(before,after);assertEquals(SeaBiomeContext.uncachedBiomeKey(c,72,x,z,null),after);
 }
 @Test void sandAboveThePreservationHeightCannotLeaveNativeTerrainAboveIt(){
  var c=new SeaConfig();for(double sand=70;sand<260;sand+=.125)for(int old=-64;old<576;old+=7)
   assertTrue(MainlandJoin.ceiling(c,old,sand)<=sand);
 }
 @Test void samplerBindingCacheSeesSeedAndWorldTypeReregistration(){
  var sampler=new net.minecraft.world.biome.source.util.MultiNoiseUtil.MultiNoiseSampler(null,null,null,null,null,null,java.util.List.of());
  SeaBiomeContext.register(sampler,9313419287L,false);assertFalse(SeaBiomeContext.unstable(sampler));assertEquals(9313419287L,SeaBiomeContext.seed(sampler));
  SeaBiomeContext.register(sampler,9313419288L,true);assertTrue(SeaBiomeContext.unstable(sampler));assertEquals(9313419288L,SeaBiomeContext.seed(sampler));
 }
}
