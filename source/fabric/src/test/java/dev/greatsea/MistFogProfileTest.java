package dev.greatsea;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class MistFogProfileTest {
 @Test void weatherCannotWhitenHandOrNearbyBody(){for(float s:new float[]{.01f,.5f,1}){float start=MistFogProfile.start(-160,s),end=MistFogProfile.end(1024,s);for(float distance:new float[]{0,.5f,1,4,5.9f})assertTrue(distance<=start);assertTrue(end>start);}}
 @Test void fogBecomesOpaqueNearbyAndBlendsContinuously(){assertEquals(6,MistFogProfile.start(-160,1));assertEquals(42,MistFogProfile.end(1024,1));assertTrue((24-6f)/(42-6f)>.49f);float last=1024;for(int i=0;i<=100;i++){float end=MistFogProfile.end(1024,i/100f);assertTrue(end<=last);last=end;}assertTrue(Float.isFinite(MistFogProfile.end(Float.POSITIVE_INFINITY,1)));}
}
