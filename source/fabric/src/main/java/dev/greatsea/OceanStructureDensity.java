package dev.greatsea;

/** Stateless thinning of genuine vanilla placement candidates inside the Great Sea.
 * This class deliberately has no Minecraft or world-generation side effects. */
public final class OceanStructureDensity {
    public static final int MONUMENT_CELL_CHUNKS=32;
    public static final int RUIN_CELL_CHUNKS=20;
    public static final int SHIPWRECK_CELL_CHUNKS=24;
    private static final long MONUMENT_SALT=0x56615F7DB353758BL;
    private static final long RUIN_SALT=0x5CC903C04A08C7E1L;
    private static final long SHIPWRECK_SALT=0xB1B5D075053696D5L;
    private OceanStructureDensity() {}

    public static boolean accepts(long seed,String structurePath,int chunkX,int chunkZ) {
        return switch(structurePath) {
            case "monument" -> acceptsMonumentCell(seed,Math.floorDiv(chunkX,MONUMENT_CELL_CHUNKS),
                    Math.floorDiv(chunkZ,MONUMENT_CELL_CHUNKS));
            // Both biome alternatives use one decision, including vanilla retry order.
            case "ocean_ruin_warm", "ocean_ruin_cold" ->
                    (hash(seed^RUIN_SALT,Math.floorDiv(chunkX,RUIN_CELL_CHUNKS),
                            Math.floorDiv(chunkZ,RUIN_CELL_CHUNKS))&3)==0;
            case "shipwreck", "shipwreck_beached" ->
                    (hash(seed^SHIPWRECK_SALT,Math.floorDiv(chunkX,SHIPWRECK_CELL_CHUNKS),
                            Math.floorDiv(chunkZ,SHIPWRECK_CELL_CHUNKS))&1)==0;
            default -> true;
        };
    }

    /** Exactly one of the sixteen native monument cells remains in each 2048-block region.
     * Choosing its interior cell keeps neighbouring regions' monuments at least three
     * native cells apart, even when their vanilla placement offsets point toward each other. */
    public static boolean acceptsMonumentCell(long seed,int cellX,int cellZ) {
        int regionX=Math.floorDiv(cellX,4),regionZ=Math.floorDiv(cellZ,4);
        long choice=hash(seed^MONUMENT_SALT,regionX,regionZ);
        int chosenX=1+(int)(choice&1),chosenZ=1+(int)((choice>>>1)&1);
        return Math.floorMod(cellX,4)==chosenX && Math.floorMod(cellZ,4)==chosenZ;
    }

    private static long hash(long seed,long x,long z) {
        long value=seed^x*0x9E3779B97F4A7C15L^z*0xC2B2AE3D27D4EB4FL;
        value=(value^(value>>>30))*0xBF58476D1CE4E5B9L;
        value=(value^(value>>>27))*0x94D049BB133111EBL;
        return value^(value>>>31);
    }
}
