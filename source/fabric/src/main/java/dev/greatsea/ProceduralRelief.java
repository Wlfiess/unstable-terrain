package dev.greatsea;

/** Infinite seeded landforms, calibrated to the samples without reusing their layouts. */
final class ProceduralRelief {
    private static double unit(long h,int key){return SeaTerrain.unit(SeaTerrain.hash(h,key,key+19));}
    private static double point(long seed,int x,int z,int axis,int cell) {
        long h=SeaTerrain.hash(seed,x,z);
        return (axis==0?x:z)*cell+cell*.5+cell*.92*(unit(h,axis+1)-.5);
    }
    static int spire(long seed,int x,int z) {
        int cell=23,cx=Math.floorDiv(x,cell),cz=Math.floorDiv(z,cell);
        double top=FarlandsRelief.spikeFloor(seed,x,z);long salt=seed+833;
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++) {
            int gx=cx+dx,gz=cz+dz;long h=SeaTerrain.hash(salt,gx,gz);
            if(unit(h,8)<.17)continue;
            double px=point(salt,gx,gz,0,cell),pz=point(salt,gx,gz,1,cell);boolean accepted=true;
            for(int nz=-1;nz<=1;nz++)for(int nx=-1;nx<=1;nx++) {
                if(nx==0&&nz==0)continue;
                long other=SeaTerrain.hash(salt,gx+nx,gz+nz);
                if(unit(other,8)<.17 || SeaTerrain.unit(other)>=SeaTerrain.unit(h))continue;
                if(Math.hypot(px-point(salt,gx+nx,gz+nz,0,cell),pz-point(salt,gx+nx,gz+nz,1,cell))<17)accepted=false;
            }
            if(!accepted)continue;
            double angle=unit(h,4)*Math.PI*2,ux=(x-px)*Math.cos(angle)+(z-pz)*Math.sin(angle),uz=(z-pz)*Math.cos(angle)-(x-px)*Math.sin(angle);
            double width=5.5+5*unit(h,2),aspect=.65+.7*unit(h,3);
            double distance=Math.hypot(ux/width,uz/(width*aspect));
            double amplitude=unit(h,9)<.25?45+145*unit(h,10):330+57*unit(h,10);
            double taper=.9+1.1*unit(h,11);
            top=Math.max(top,-60+amplitude*Math.pow(Math.max(0,1-distance),taper));
        }
        // The complete stone reference retains tips up to Y=317, not a Y=309 cap.
        return Math.min(317,(int)Math.round(top));
    }
    static int dune(long seed,int x,int z) {
        int cell=72,cx=Math.floorDiv(x,cell),cz=Math.floorDiv(z,cell);
        double base=-21+4*SeaTerrain.noise(seed+8317,x,z,230),relief=0;
        for(int dz=-2;dz<=2;dz++)for(int dx=-2;dx<=2;dx++) {
            int gx=cx+dx,gz=cz+dz;long h=SeaTerrain.hash(seed+831,gx,gz);
            if(unit(h,8)<.07)continue;
            double px=point(seed+831,gx,gz,0,cell),pz=point(seed+831,gx,gz,1,cell);
            double angle=.85*SeaTerrain.noise(seed+8318,px,pz,460)+(unit(h,4)-.5)*2.7;
            double wx=x-px+7*SeaTerrain.noise(seed+8319,x,z,115),wz=z-pz+7*SeaTerrain.noise(seed+8320,x,z,115);
            double ux=wx*Math.cos(angle)+wz*Math.sin(angle),uz=wz*Math.cos(angle)-wx*Math.sin(angle);
            double halfCrest=36+47*unit(h,2),endSlope=24+35*unit(h,7),width=41+31*unit(h,3);
            width*=uz>0?.7+.28*unit(h,5):1.03+.36*unit(h,6);
            // Each formation is a raised crest segment with broad planar sides.
            // Their overlapping side planes form the sample's creases and saddles.
            double along=Math.max(0,(Math.abs(ux)-halfCrest)/endSlope);
            double distance=Math.max(along,Math.abs(uz)/width);
            double amplitude=92+86*unit(h,10);
            double crestTilt=1+(unit(h,11)-.5)*.2*ux/(halfCrest+endSlope);
            relief=Math.max(relief,amplitude*Math.max(0,1-distance)*crestTilt);
        }
        return Math.min(157,(int)Math.round(base+relief));
    }
}
