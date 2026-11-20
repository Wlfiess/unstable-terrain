package dev.greatsea;
/** Connected, multiscale desert mountain ridges with sharp crests and branching valleys. */
final class OxumsDunes {
    private OxumsDunes(){}
    static int height(long seed,int x,int z){
        return TerrainSamples138.dunesHeight(seed,x,z);
    }
}
