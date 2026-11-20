package dev.greatsea;

import java.io.DataInputStream;
import java.io.IOException;
import java.util.Objects;
import java.util.zip.GZIPInputStream;

/** Geometry only from the supplied October terrain samples. No source builds or palettes. */
final class TerrainSamples138 {
    private TerrainSamples138(){}
    private static DataInputStream open(String name)throws IOException{return new DataInputStream(new GZIPInputStream(Objects.requireNonNull(TerrainSamples138.class.getResourceAsStream("/assets/unstableterrain/samples/"+name))));}
    private record Height(int width,int depth,short[] cells){
        static Height load(String name){try(var in=open(name+".height.gz")){if(in.readInt()!=0x55484d31)throw new IOException("Height sample header");int w=in.readInt(),d=in.readInt();short[] a=new short[w*d];for(int i=0;i<a.length;i++)a[i]=in.readShort();return new Height(w,d,a);}catch(IOException e){throw new IllegalStateException(name,e);}}
        int at(int x,int z){return cells[mirror(z,depth)*width+mirror(x,width)];}
        int sample(long seed,int x,int z){double u=x+6*SeaTerrain.noise(seed+13801,x,z,530),v=z+6*SeaTerrain.noise(seed+13802,x,z,470);int dx=(int)(SeaTerrain.hash(seed,138,1)&255),dz=(int)(SeaTerrain.hash(seed,138,2)&255);return at((int)Math.floor(u)+dx,(int)Math.floor(v)+dz);}
    }
    private static final class Cherry{static final Height DATA=Height.load("cherry");}
    private static final class Dunes{static final Height DATA=Height.load("dunes");}
    private static int mirror(int n,int size){int t=Math.floorMod(n,2*size-2);return t<size?t:2*size-2-t;}
    static int cherryHeight(long seed,int depth,int tangent){int h=Cherry.DATA.sample(seed,depth,tangent);return h+(int)Math.round(Math.max(0,h-50)*.30);}
    /** The stone heightmap preserves its ridge/valley topology. Match the earlier
     * captured dunes' vertical distribution without inventing conical mountains. */
    static int dunesHeight(long seed,int depth,int tangent){
        double u=depth/1.75+25*SeaTerrain.noise(seed+13901,depth,tangent,800)+7*SeaTerrain.noise(seed+13902,depth,tangent,240);
        double v=tangent/1.75+25*SeaTerrain.noise(seed+13903,depth,tangent,760)+7*SeaTerrain.noise(seed+13904,depth,tangent,220);
        u+=(int)(SeaTerrain.hash(seed,139,1)&511);v+=(int)(SeaTerrain.hash(seed,139,2)&511);
        int x=(int)Math.floor(u),z=(int)Math.floor(v);double tx=u-x,tz=v-z;
        double near=SeaTerrain.lerp(Dunes.DATA.at(x,z),Dunes.DATA.at(x+1,z),tx);
        double far=SeaTerrain.lerp(Dunes.DATA.at(x,z+1),Dunes.DATA.at(x+1,z+1),tx);
        return duneElevation(SeaTerrain.lerp(near,far,tz));
    }
    private static final double[] DUNE_SOURCE={3,29,51,68,86,103,113,130,177};
    private static final double[] DUNE_WORLD={31,71,117,151,189,223,243,284,317};
    static int duneElevation(double height){
        if(height<=DUNE_SOURCE[0])return 31;
        for(int i=1;i<DUNE_SOURCE.length;i++)if(height<=DUNE_SOURCE[i]){
            double t=(height-DUNE_SOURCE[i-1])/(DUNE_SOURCE[i]-DUNE_SOURCE[i-1]);
            // Monotone cubic Hermite interpolation avoids artificial contour kinks.
            double dx=DUNE_SOURCE[i]-DUNE_SOURCE[i-1],dy=DUNE_WORLD[i]-DUNE_WORLD[i-1];
            double left=i==1?dy/dx:(DUNE_WORLD[i]-DUNE_WORLD[i-2])/(DUNE_SOURCE[i]-DUNE_SOURCE[i-2]);
            double right=i==DUNE_SOURCE.length-1?dy/dx:(DUNE_WORLD[i+1]-DUNE_WORLD[i-1])/(DUNE_SOURCE[i+1]-DUNE_SOURCE[i-1]);
            left=Math.min(left,3*dy/dx);right=Math.min(right,3*dy/dx);
            return Math.max(31,Math.min(317,(int)Math.round((2*t*t*t-3*t*t+1)*DUNE_WORLD[i-1]+(t*t*t-2*t*t+t)*dx*left+(-2*t*t*t+3*t*t)*DUNE_WORLD[i]+(t*t*t-t*t)*dx*right)));
        }
        return 317;
    }
    private static final class CherryColumn {long seed;int depth,tangent,top,lowestNeighbour,slope;boolean valid;}
    private static final ThreadLocal<CherryColumn> CHERRY_COLUMN=ThreadLocal.withInitial(CherryColumn::new);
    static int cherryMaterial(long seed,int depth,int y,int tangent){
        var c=CHERRY_COLUMN.get();
        if(!c.valid||c.seed!=seed||c.depth!=depth||c.tangent!=tangent){
            c.valid=true;c.seed=seed;c.depth=depth;c.tangent=tangent;c.top=cherryHeight(seed,depth,tangent);
            int east=cherryHeight(seed,depth+1,tangent),west=cherryHeight(seed,depth-1,tangent);
            int south=cherryHeight(seed,depth,tangent+1),north=cherryHeight(seed,depth,tangent-1);
            c.lowestNeighbour=Math.min(Math.min(east,west),Math.min(south,north));
            c.slope=Math.max(Math.abs(east-west),Math.abs(south-north));
        }
        if(y>c.top||y< -64)return 0;
        // A grass block itself has dirt on its sides. Keep the edge of every
        // downward step stone, then hide the soil entirely inside the terrace.
        boolean rock=c.slope>3||c.lowestNeighbour<c.top;
        if(y==c.top)return rock?1:4;
        if(y>=c.top-2&&!rock&&y<=c.lowestNeighbour)return 3;
        return y<0?2:1;
    }
    private record Winding(int width,int depth,int height,byte[] cells){static Winding load(){try(var in=open("winding.volume.gz")){if(in.readInt()!=0x55574e31)throw new IOException("Winding sample header");int w=in.readInt(),d=in.readInt(),h=in.readInt();return new Winding(w,d,h,in.readNBytes(w*d*h));}catch(IOException e){throw new IllegalStateException(e);}}}
    private static final class Ribbons{static final Winding DATA=Winding.load();}
    static boolean windingSolid(long seed,int depth,int y,int tangent){var a=Ribbons.DATA;int x=mirror(depth+(int)(SeaTerrain.hash(seed,138,3)&127),a.width),z=mirror(tangent+(int)(SeaTerrain.hash(seed,138,4)&127),a.depth);return a.cells[(z*a.width+x)*a.height+Math.floorMod(y,a.height)]!=0;}
    private static final class Relief {static final Height DATA=Height.load("sculk-relief");}
    static int sculkRelief(long seed,int a,int b){return Relief.DATA.sample(seed,a,b);}
    private static final class MudGround {static final Height DATA=Height.load("warped-mud");}
    static boolean warpedMud(long seed,int depth,int tangent){return MudGround.DATA.at(depth+(int)(SeaTerrain.hash(seed,140,1)&255),tangent+(int)(SeaTerrain.hash(seed,140,2)&255))!=0;}
    private static final class RiverRocks {
        static final int[][][] DATA=load();
        static int[][][] load(){
            try(var in=new java.io.InputStreamReader(new GZIPInputStream(Objects.requireNonNull(TerrainSamples138.class.getResourceAsStream("/assets/unstableterrain/samples/river-rocks.json.gz"))),java.nio.charset.StandardCharsets.UTF_8)){
                var rocks=com.google.gson.JsonParser.parseReader(in).getAsJsonObject().getAsJsonArray("rocks");int[][][] result=new int[rocks.size()][][];
                for(int i=0;i<result.length;i++){var voxels=rocks.get(i).getAsJsonObject().getAsJsonArray("voxels");result[i]=new int[voxels.size()][3];for(int j=0;j<voxels.size();j++)for(int k=0;k<3;k++)result[i][j][k]=voxels.get(j).getAsJsonArray().get(k).getAsInt();}
                return result;
            }catch(IOException e){throw new IllegalStateException("Original riverbank rocks",e);}
        }
    }
    static int[][] riverRock(long hash){return RiverRocks.DATA[(int)Long.remainderUnsigned(hash,RiverRocks.DATA.length)];}
    static final class Tree {
        final int width,depth,height,cx,cz;final byte[] cells;
        Tree(int w,int d,int h,int x,int z,byte[] a){width=w;depth=d;height=h;cx=x;cz=z;cells=a;}
        int at(int x,int y,int z){return x<0||x>=width||z<0||z>=depth||y<0||y>=height?0:cells[(z*width+x)*height+y];}
        static Tree load(int id){try(var in=open("warped-tree-"+id+".volume.gz")){if(in.readInt()!=0x55545231)throw new IOException("Tree sample header");int w=in.readInt(),d=in.readInt(),h=in.readInt(),x=in.readInt(),z=in.readInt();return new Tree(w,d,h,x,z,in.readNBytes(w*d*h));}catch(IOException e){throw new IllegalStateException(e);}}
    }
    private static final class Trees{static final Tree[] DATA={Tree.load(0),Tree.load(1)};}
    static Tree tree(long hash){return Trees.DATA[(int)Math.floorMod(hash,Trees.DATA.length)];}
}
