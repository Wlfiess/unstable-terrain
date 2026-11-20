package dev.greatsea;

/** A continuous trimming ceiling lets the buried sand embankment meet native mainland. */
public final class MainlandJoin {
    private MainlandJoin() {}

    public static double ceiling(SeaConfig config,double nativeSurfaceTop,double sandHeight) {
        double start=config.mainlandJoinY,end=config.mainlandPreserveTopY;
        double original=Math.max(nativeSurfaceTop,end);
        // Equal join/preservation settings have no taper interval. Preserve the exact
        // native boundary, then use the configured ceiling as soon as the sand rises.
        if (end<=start) return sandHeight<=start ? original : end;
        double t=Math.max(0,Math.min(1,(sandHeight-start)/(end-start)));
        double weight=t*t*t*(t*(t*6-15)+10);
        return original+(end-original)*weight;
    }
}
