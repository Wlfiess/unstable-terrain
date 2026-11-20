package dev.greatsea;

import java.util.ArrayList;
import java.util.List;

/** A seeded spatial point process: cells, rows and chunk borders do not select tree positions. */
public final class PalmLayout {
    public record Candidate(int x,int z,long hash) {}
    private PalmLayout() {}
    private static int separation(int spacing) { return Math.min(32,Math.max(10,spacing/4)); }
    private static double chance(int spacing) {
        double area=Math.PI*separation(spacing)*separation(spacing);
        // Correct for neighbour thinning so changing placement does not hollow out the beach.
        return -Math.log1p(-Math.min(.9,area/(spacing*(double)spacing)))/area;
    }
    private static boolean potential(long seed,int x,int z,double chance) {
        return SeaTerrain.unit(SeaTerrain.hash(seed+317,x,z))<chance;
    }
    public static List<Candidate> candidates(long seed,int minX,int minZ,int maxX,int maxZ,int spacing) {
        List<Candidate> result=new ArrayList<>();double chance=chance(spacing);int radius=separation(spacing);
        for (int z=minZ;z<=maxZ;z++) for (int x=minX;x<=maxX;x++) {
            if (!potential(seed,x,z,chance)) continue;
            long priority=SeaTerrain.hash(seed+318,x,z);boolean dominated=false;
            for (int dz=-radius;dz<=radius && !dominated;dz++) for (int dx=-radius;dx<=radius;dx++) {
                if ((dx==0 && dz==0) || dx*dx+dz*dz>=radius*radius) continue;
                int nx=x+dx,nz=z+dz;
                if (potential(seed,nx,nz,chance) && Long.compareUnsigned(SeaTerrain.hash(seed+318,nx,nz),priority)<0) {
                    dominated=true;break;
                }
            }
            if (!dominated) result.add(new Candidate(x,z,SeaTerrain.hash(seed+301,x,z)));
        }
        return List.copyOf(result);
    }
    /** Kept for old analysis callers; actual generation uses the spatial candidates above. */
    public static Candidate candidate(long seed,int gx,int gz,int spacing) {
        long h=SeaTerrain.hash(seed+301,gx,gz);
        return new Candidate(gx*spacing+(int)(SeaTerrain.unit(SeaTerrain.hash(h,1,0))*spacing),
                gz*spacing+(int)(SeaTerrain.unit(SeaTerrain.hash(h,0,1))*spacing),h);
    }
}
