package dev.greatsea;
/** Distance fog with a clear near field, independent of weather's negative start. */
public final class MistFogProfile {
 private MistFogProfile(){}
 public static float start(float original,float strength){float safe=Math.max(MistRegion.FOG_START,original);return safe+(MistRegion.FOG_START-safe)*strength;}
 public static float end(float original,float strength){float safe=Float.isFinite(original)?Math.max(MistRegion.FOG_END,original):1024;return safe+(MistRegion.FOG_END-safe)*strength;}
}
