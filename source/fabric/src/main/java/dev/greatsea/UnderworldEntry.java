package dev.greatsea;

/** A Nether-floor drop is protected; falls starting inside the Underworld retain normal damage. */
public final class UnderworldEntry {
    private UnderworldEntry() {}
    public static boolean protectsLanding(double landingY,double fallDistance) {
        // Fall distance reconstructs the height where this uninterrupted descent began.
        // A small allowance accounts for entering through the underside of the bedrock.
        return landingY<UnstableNether.UNDERWORLD_TOP && fallDistance>100 &&
                landingY+fallDistance>=-8;
    }
}
