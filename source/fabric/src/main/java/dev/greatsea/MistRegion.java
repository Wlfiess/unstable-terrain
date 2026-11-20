package dev.greatsea;

/** The Mist occupies normal mainland in the northern sector, on every Unstable seed. */
public final class MistRegion {
 public static final int CENTER_X=739,CENTER_Z=-105_599;
 public static final int RADIUS_X=20_000,RADIUS_Z=16_000,TRANSITION=2048;
 public static final float FOG_START=6,FOG_END=42;
 private MistRegion(){}
 public static boolean active(net.minecraft.server.world.ServerWorld world){return world.getRegistryKey().equals(net.minecraft.world.World.OVERWORLD)&&UnstableWorldGen.isUnstable(world.getChunkManager().getChunkGenerator());}
 public static double strength(double x,double z){
  double dx=(x-CENTER_X)/RADIUS_X,dz=(z-CENTER_Z)/RADIUS_Z;
  double distance=Math.sqrt(dx*dx+dz*dz);
  double margin=TRANSITION/(double)Math.min(RADIUS_X,RADIUS_Z);
  double t=Math.max(0,Math.min(1,(1+margin-distance)/margin));
  return t*t*t*(t*(t*6-15)+10);
 }
 public static boolean intersects(int x,int z){
  double dx=Math.max(0,Math.max(x-CENTER_X,CENTER_X-(x+15)))/(double)RADIUS_X;
  double dz=Math.max(0,Math.max(z-CENTER_Z,CENTER_Z-(z+15)))/(double)RADIUS_Z;
  double edge=1+TRANSITION/(double)Math.min(RADIUS_X,RADIUS_Z);return dx*dx+dz*dz<edge*edge;
 }
 public static boolean contains(int x,int z){return strength(x,z)>0;}
 public static boolean core(int x,int z){return strength(x,z)>=1;}
 public static boolean frost(long seed,int x,int z){
  double s=strength(x,z);return s>=1||s>0&&(.5+.5*SeaTerrain.noise(seed+14701,x,z,128))<s;
 }
}
