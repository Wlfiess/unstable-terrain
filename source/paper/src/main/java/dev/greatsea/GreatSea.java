package dev.greatsea;
/** Shared terrain configuration; this Paper build has no Fabric initialization dependency. */
public final class GreatSea {
    public static final org.slf4j.Logger LOGGER=org.slf4j.LoggerFactory.getLogger("unstableterrain-paper");
    public static SeaConfig CONFIG=new SeaConfig();
    public static FarlandsConfig FARLANDS=new FarlandsConfig();
    private GreatSea(){}
}
