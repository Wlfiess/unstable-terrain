package dev.greatsea;

import com.google.gson.JsonParser;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.registry.Registries;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import java.io.DataInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;

/** Continuous, seeded terrain reconstructed from clean Underworld reference samples. */
public final class UnderworldTerrain {
    public static final int MIN_Y=-320,TOP_Y=-128,SURFACE_BASE=-225;
    // The reference's ceiling is Y=300 and the supplied coordinate's ground is Y=95.
    private static final double REFERENCE_ANCHOR=95;
    // Expand existing sand patches into their margins: calibrated to about 10% more
    // surface coverage, rather than adding ten percentage points of soul sand.
    private static final double SOUL_SAND_WEIGHT_BONUS=.175;
    public static final int CRYSTAL_SPACING=256;
    public static final double CRYSTAL_CHANCE=.4284;
    private record Grid(int size,int step,float[] heights,byte[] materials,byte[] thickness) {}
    public record Column(int top,int bottom,int material) {}
    public record CrystalCandidate(int x,int z,int variant,int rotation,long hash) {}
    private record Voxel(int x,int y,int z,BlockState state) {}
    private record Crystal(List<Voxel> blocks,List<Voxel> roots) {}
    private static final Grid GRID=loadGrid();
    private static final ThreadLocal<LinkedHashMap<Long,Column>> COLUMNS=ThreadLocal.withInitial(LinkedHashMap::new);
    private static final ThreadLocal<Long> COLUMN_SEED=ThreadLocal.withInitial(()->Long.MIN_VALUE);
    private static final class Models { private static final List<Crystal> CRYSTALS=loadCrystals(); }
    private UnderworldTerrain() {}

    private static double mirror(double v) {
        double period=2.0*(GRID.size-1);v-=Math.floor(v/period)*period;
        return v<GRID.size-1 ? v : period-v;
    }
    private static int index(int x,int z) {
        return reflect(z,GRID.size)*GRID.size+reflect(x,GRID.size);
    }
    private static int reflect(int value,int size) {
        int period=2*(size-1);value=Math.floorMod(value,period);
        return value>=size ? period-value : value;
    }
    private static double cubic(double a,double b,double c,double d,double t) {
        return b+.5*t*(c-a+t*(2*a-5*b+4*c-d+t*(3*(b-c)+d-a)));
    }
    private static double sample(double x,double z,boolean thickness) {
        x=mirror(x/GRID.step);z=mirror(z/GRID.step);
        int ix=(int)x,iz=(int)z;double[] row=new double[4];
        for (int k=-1;k<=2;k++) {
            double[] v=new double[4];
            for (int j=-1;j<=2;j++) {
                int index=index(ix+j,iz+k);
                v[j+1]=thickness ? Byte.toUnsignedInt(GRID.thickness[index]) : GRID.heights[index];
            }
            row[k+1]=cubic(v[0],v[1],v[2],v[3],x-ix);
        }
        return cubic(row[0],row[1],row[2],row[3],z-iz);
    }
    private record Point(double x,double z) {}
    private static Point coordinates(long seed,int x,int z) {
        double dx=140*SeaTerrain.noise(seed+8101,x,z,640),dz=140*SeaTerrain.noise(seed+8102,x,z,640);
        return new Point(x*.8+z*.6+dx+SeaTerrain.unit(SeaTerrain.hash(seed,81,13))*8192,
                z*.8-x*.6+dz+SeaTerrain.unit(SeaTerrain.hash(seed,17,83))*8192);
    }
    private static double profile(long seed,int x,int z,Point p) {
        double primary=sample(p.x,p.z,false),secondary=sample(-p.z+347,p.x+631,false);
        return SURFACE_BASE+(.86*primary+.14*secondary-REFERENCE_ANCHOR)+
                4*SeaTerrain.noise(seed+8110,x,z,512)+1.3*SeaTerrain.noise(seed+8111,x,z,96);
    }
    public static double height(long seed,int x,int z) { return profile(seed,x,z,coordinates(seed,x,z)); }
    public static Column column(long seed,int x,int z) {
        var cache=COLUMNS.get();
        if (COLUMN_SEED.get()!=seed) { cache.clear();COLUMN_SEED.set(seed); }
        long key=((long)x<<32)^(z&0xffffffffL);Column existing=cache.get(key);
        if (existing!=null) return existing;
        Point p=coordinates(seed,x,z);
        int top=(int)Math.round(profile(seed,x,z,p));
        double depth=.86*sample(p.x,p.z,true)+.14*sample(-p.z+347,p.x+631,true);
        int bottom=top-Math.max(5,(int)Math.round(depth))+1;
        double gx=mirror(p.x/GRID.step),gz=mirror(p.z/GRID.step);
        int ix=(int)gx,iz=(int)gz,material=0;double largest=0;
        for (int type=1;type<=2;type++) {
            double a=GRID.materials[index(ix,iz)]==type ? 1:0,b=GRID.materials[index(ix+1,iz)]==type ? 1:0;
            double d=GRID.materials[index(ix,iz+1)]==type ? 1:0,e=GRID.materials[index(ix+1,iz+1)]==type ? 1:0;
            double weight=SeaTerrain.lerp(SeaTerrain.lerp(a,b,gx-ix),SeaTerrain.lerp(d,e,gx-ix),gz-iz);
            if (type==2 && weight>0) weight+=SOUL_SAND_WEIGHT_BONUS;
            if (weight>.45 && weight>largest) { material=type;largest=weight; }
        }
        Column result=new Column(top,bottom,material);
        if (cache.size()>=4096) cache.clear();cache.put(key,result);
        return result;
    }
    public static BlockState state(long seed,int x,int y,int z) {
        if (y>=0) throw new IllegalArgumentException("Underworld state is only valid below the Nether");
        if (y>=TOP_Y || y<MIN_Y) return Blocks.AIR.getDefaultState();
        Column c=column(seed,x,z);
        if (y>c.top || y<c.bottom) return Blocks.AIR.getDefaultState();
        return surface(c.material);
    }
    private static BlockState surface(int material) {
        return switch(material) {
            case 1 -> Blocks.SOUL_SOIL.getDefaultState();
            case 2 -> Blocks.SOUL_SAND.getDefaultState();
            default -> Blocks.MUD.getDefaultState();
        };
    }
    public static Chunk fill(Chunk chunk,long seed) {
        BlockPos.Mutable p=new BlockPos.Mutable();
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();
        for (int dz=0;dz<16;dz++) for (int dx=0;dx<16;dx++) {
            int x=sx+dx,z=sz+dz;Column c=column(seed,x,z);BlockState soil=surface(c.material);
            for (int y=chunk.getBottomY();y<0;y++) {
                BlockState block=y>=c.bottom && y<=c.top ? soil : Blocks.AIR.getDefaultState();
                if (!chunk.getBlockState(p.set(x,y,z)).equals(block)) chunk.setBlockState(p,block,0);
            }
        }
        return chunk;
    }
    public static CrystalCandidate crystalCandidate(long seed,int gx,int gz) {
        long h=SeaTerrain.hash(seed+8301,gx,gz);
        int x=gx*CRYSTAL_SPACING+CRYSTAL_SPACING/2+(int)Math.floor((SeaTerrain.unit(SeaTerrain.hash(h,1,0))-.5)*176);
        int z=gz*CRYSTAL_SPACING+CRYSTAL_SPACING/2+(int)Math.floor((SeaTerrain.unit(SeaTerrain.hash(h,0,1))-.5)*176);
        return new CrystalCandidate(x,z,(int)((h>>>3)%6),(int)((h>>>7)&3),h);
    }
    private static int[] rotate(int x,int z,int rotation) {
        for (int i=0;i<rotation;i++) { int old=x;x=-z;z=old; }return new int[]{x,z};
    }
    public static void decorate(Chunk chunk,StructureWorldAccess world) {
        long seed=world.getSeed();int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();
        BlockPos.Mutable p=new BlockPos.Mutable();
        for (int gz=Math.floorDiv(sz-14,CRYSTAL_SPACING);gz<=Math.floorDiv(sz+29,CRYSTAL_SPACING);gz++)
        for (int gx=Math.floorDiv(sx-14,CRYSTAL_SPACING);gx<=Math.floorDiv(sx+29,CRYSTAL_SPACING);gx++) {
            var candidate=crystalCandidate(seed,gx,gz);
            if (SeaTerrain.unit(candidate.hash)>CRYSTAL_CHANCE) continue;
            Crystal model=Models.CRYSTALS.get(candidate.variant);
            int base=column(seed,candidate.x,candidate.z).top-2;
            for (Voxel root:model.roots) {
                int[] r=rotate(root.x,root.z,candidate.rotation);int x=candidate.x+r[0],z=candidate.z+r[1];
                if (x<sx || x>=sx+16 || z<sz || z>=sz+16) continue;
                for (int y=column(seed,x,z).top+1;y<base;y++) chunk.setBlockState(p.set(x,y,z),root.state,0);
            }
            for (Voxel v:model.blocks) {
                int[] r=rotate(v.x,v.z,candidate.rotation);int x=candidate.x+r[0],z=candidate.z+r[1],y=base+v.y;
                if (x>=sx && x<sx+16 && z>=sz && z<sz+16 && y<TOP_Y && y>=chunk.getBottomY())
                    chunk.setBlockState(p.set(x,y,z),v.state,0);
            }
        }
    }
    private static Grid loadGrid() {
        try (var in=new DataInputStream(Objects.requireNonNull(UnderworldTerrain.class.getResourceAsStream("/assets/greatsea/underworld/terrain.bin")))) {
            if (in.readInt()!=0x554e4452) throw new IllegalArgumentException("Invalid Underworld terrain data");
            int size=in.readInt(),step=in.readInt();in.readInt();in.readInt();
            if (size!=256 || step!=4) throw new IllegalArgumentException("Unexpected Underworld grid size");
            float[] h=new float[size*size];byte[] m=new byte[h.length],d=new byte[h.length];
            for (int i=0;i<h.length;i++) {
                h[i]=in.readShort();m[i]=in.readByte();d[i]=in.readByte();
                if (m[i]==3) m[i]=0;
            }
            repairAuthoredCircle(h,d,size,step);
            // Remove four-block sampling/inpainting edges while retaining the reference's
            // broad hills. Filter depth equally so the shelf underside stays coherent.
            float[] filtered=new float[h.length];byte[] depths=new byte[d.length];
            for (int z=0;z<size;z++) for (int x=0;x<size;x++) {
                double sum=0,depth=0;
                for (int dz=-1;dz<=1;dz++) for (int dx=-1;dx<=1;dx++) {
                    int weight=(dx==0 ? 2:1)*(dz==0 ? 2:1),i=reflect(z+dz,size)*size+reflect(x+dx,size);
                    sum+=h[i]*weight;depth+=Byte.toUnsignedInt(d[i])*weight;
                }
                filtered[z*size+x]=(float)(sum/16);depths[z*size+x]=(byte)Math.round(depth/16);
            }
            return new Grid(size,step,filtered,m,depths);
        } catch (Exception e) { throw new ExceptionInInitializerError(e); }
    }
    private static void repairAuthoredCircle(float[] heights,byte[] thickness,int size,int step) {
        // The source contains a built circular depression at local X=476,Z=28.
        // Its mud blocks passed the natural-material filter, leaving the circular
        // engraving in every warped copy. Reconstruct just that footprint from
        // its clean boundary; all other source hills and valleys stay intact.
        boolean[] repair=new boolean[heights.length];float[] depth=new float[heights.length];
        for (int z=0;z<size;z++) for (int x=0;x<size;x++) {
            int i=z*size+x;double dx=x*step-476,dz=z*step-28;
            repair[i]=dx*dx+dz*dz<=104*104;depth[i]=Byte.toUnsignedInt(thickness[i]);
            if (repair[i]) { heights[i]=81;depth[i]=37; }
        }
        // Harmonic interpolation uses the surrounding unmodified surface, including
        // a reflected edge where the source download ends at Z=0.
        for (int pass=0;pass<1800;pass++) for (int z=0;z<size;z++) for (int x=0;x<size;x++) {
            int i=z*size+x;if (!repair[i]) continue;
            int a=z*size+reflect(x-1,size),b=z*size+reflect(x+1,size),
                    c=reflect(z-1,size)*size+x,e=reflect(z+1,size)*size+x;
            heights[i]=(heights[a]+heights[b]+heights[c]+heights[e])*.25f;
            depth[i]=(depth[a]+depth[b]+depth[c]+depth[e])*.25f;
        }
        for (int i=0;i<thickness.length;i++) if (repair[i]) thickness[i]=(byte)Math.round(depth[i]);
    }
    private static List<Crystal> loadCrystals() {
        var json=JsonParser.parseReader(new InputStreamReader(Objects.requireNonNull(UnderworldTerrain.class.getResourceAsStream("/assets/greatsea/underworld/crystals.json")))).getAsJsonArray();
        var result=new ArrayList<Crystal>();
        for (var element:json) {
            var tree=element.getAsJsonObject();var palette=new ArrayList<BlockState>();
            for (var elementState:tree.getAsJsonArray("palette")) {
                var def=elementState.getAsJsonObject();var nbt=new NbtCompound();nbt.putString("Name",def.get("Name").getAsString());
                if (def.has("Properties")) {
                    var properties=new NbtCompound();
                    for (var entry:def.getAsJsonObject("Properties").entrySet()) properties.putString(entry.getKey(),entry.getValue().getAsString());
                    nbt.put("Properties",properties);
                }
                palette.add(NbtHelper.toBlockState(Registries.BLOCK,nbt));
            }
            var voxels=new ArrayList<Voxel>();var roots=new ArrayList<Voxel>();
            for (var block:tree.getAsJsonArray("blocks")) {
                var a=block.getAsJsonArray();var v=new Voxel(a.get(0).getAsInt(),a.get(1).getAsInt(),a.get(2).getAsInt(),palette.get(a.get(3).getAsInt()));
                voxels.add(v);if (v.y==0) roots.add(v);
            }
            result.add(new Crystal(voxels,roots));
        }
        return result;
    }
}
