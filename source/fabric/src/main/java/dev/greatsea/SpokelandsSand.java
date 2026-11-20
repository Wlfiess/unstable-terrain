package dev.greatsea;

/** Red desert reconstruction guided by Spoke's visible sand-basin and roof views.
 * The footage does not disclose an original heightmap or hidden block volume. */
final class SpokelandsSand {
    private record Pier(double a,double b,double radius,int top,long hash) {}
    private record Span(Pier one,Pier two,double dx,double dz,double lengthSquared,
                        double width,double phase,double curveExponent,double opening) {}
    private static final class Cell {
        long seed;int a,b;boolean valid;final Pier[] piers=new Pier[9];
        final Span[] spans=new Span[48];int spanCount;
        final byte[] colors=new byte[384];
    }
    private static final ThreadLocal<Cell> CACHE=ThreadLocal.withInitial(Cell::new);
    private SpokelandsSand() {}

    private static Pier pier(long seed,int a,int b) {
        long h=SeaTerrain.hash(seed+18165,a,b);
        return new Pier(a*180+24+132*SeaTerrain.unit(SeaTerrain.hash(h,1,2)),
                b*180+24+132*SeaTerrain.unit(SeaTerrain.hash(h,2,1)),
                17+14*SeaTerrain.unit(SeaTerrain.hash(h,3,4)),
                235+(int)(82*SeaTerrain.unit(SeaTerrain.hash(h,4,3))),h);
    }
    private static Cell cell(long seed,int a,int b) {
        Cell c=CACHE.get();if(c.valid&&c.seed==seed&&c.a==a&&c.b==b)return c;
        c.valid=true;c.seed=seed;c.a=a;c.b=b;c.spanCount=0;
        // Candidate locations and graph edges are shared by every column in a cell.
        // The extra negative halo retains a span's lens wing when its far
        // endpoint lies just behind a cell boundary.
        Pier[][] grid=new Pier[5][5];
        for(int i=0;i<5;i++)for(int j=0;j<5;j++)grid[i][j]=pier(seed,a+i-2,b+j-2);
        for(int i=0;i<3;i++)for(int j=0;j<3;j++)c.piers[i*3+j]=grid[i+1][j+1];
        for(int i=0;i<4;i++)for(int j=0;j<4;j++) {
            Pier one=grid[i][j];
            for(int axis=0;axis<3;axis++) {
                if(SeaTerrain.unit(SeaTerrain.hash(one.hash,axis,19))<(axis==2?.94:.49))continue;
                Pier two=grid[i+(axis!=1?1:0)][j+(axis!=0?1:0)];
                double dx=two.a-one.a,dz=two.b-one.b;
                c.spans[c.spanCount++]=new Span(one,two,dx,dz,dx*dx+dz*dz,
                        27+17*SeaTerrain.unit(SeaTerrain.hash(one.hash,axis,29)),
                        (one.hash&7)*.7,.55+.45*SeaTerrain.unit(SeaTerrain.hash(one.hash,axis,31)),
                        68+20*SeaTerrain.unit(SeaTerrain.hash(one.hash,axis,32)));
            }
        }
        return c;
    }
    private static double noise(long seed,int salt,double a,double b,double scale) {
        return SeaTerrain.noise(seed+salt,a,b,scale);
    }
    /** Fills one column and returns the low walking floor, excluding overhead roofs. */
    static int fill(byte[] blocks,long seed,double depth,double tangent,int baseFloor) {
        java.util.Arrays.fill(blocks,(byte)0);
        double a=depth+34*noise(seed,18163,depth,tangent,170)+5*noise(seed,18168,depth,tangent,28);
        double b=tangent+34*noise(seed,18164,depth,tangent,170)+5*noise(seed,18169,depth,tangent,28);
        Cell c=cell(seed,(int)Math.floor(a/180),(int)Math.floor(b/180));
        double highestFoot=baseFloor,footSlope=0;
        for(Pier p:c.piers) {
            double distance=Math.hypot(a-p.a,b-p.b),extent=p.radius+80;
            double u=Math.max(0,1-distance/extent),height=baseFloor+48*u*u;
            if(height>highestFoot){highestFoot=height;footSlope=96*u/extent;}
        }
        int foot=(int)Math.floor(highestFoot);
        boolean stonyFoot=footSlope>.30;
        // Red sandstone's grainy top and patterned riser faces are visible in the
        // low dunes at 46:23. Broad flat walking areas retain red sand.
        for(int y=-64;y<=Math.min(319,foot);y++)blocks[y+64]=(byte)(y<=-60?8:y==-59?10:
                y==foot&&!stonyFoot?SpokelandsTerrain.RED_SAND:SpokelandsTerrain.RED_SANDSTONE);

        double patch=FarlandsNatural.gradient2(seed+18167,a/42,b/43)
                +.18*FarlandsNatural.gradient2(seed+18176,a/17,b/17);
        double patchPhase=a/420.0+b/390.0;
        for(int y=Math.max(-59,foot+1);y<320;y++)c.colors[y+64]=(byte)(patch+.12*Math.sin((y-baseFloor)/80.0+patchPhase)>-.08
                ?SpokelandsTerrain.ORANGE_CLAY:SpokelandsTerrain.RED_SANDSTONE);
        // Union all relevant shafts. Selecting just the nearest center sliced
        // overlapping feet and upper shoulders along Voronoi boundaries.
        for(Pier p:c.piers) {
            double da=a-p.a,db=b-p.b,distance=Math.hypot(da,db);
            if(distance>p.radius*1.9+36)continue;
            double angle=Math.atan2(db,da),phase=p.hash&31;
            double lobes=1+.13*Math.sin(angle*3+phase)+.06*Math.sin(angle*5+phase*.7);
            boolean pinnacle=SeaTerrain.unit(SeaTerrain.hash(p.hash,51,17))<.43;
            for(int y=Math.max(foot+1,baseFloor+1);y<=Math.min(319,p.top);y++) {
                double dy=y-baseFloor,t=dy/Math.max(1,p.top-baseFloor);
                double width=p.radius*(pinnacle?1.65*Math.pow(1-t,.70)+.08:.50+1.30*t*t*t)
                        +29*Math.exp(-dy/30.0);
                double cap=Math.max(0,(y-(p.top-30))/30.0);
                width*=Math.sqrt(Math.max(0,1-cap*cap))*lobes;
                double shiftA=4*Math.sin(dy/70.0+phase),shiftB=3*Math.sin(dy/90.0+phase*.7);
                if(Math.hypot(da-shiftA,db-shiftB)<=width)blocks[y+64]=c.colors[y+64];
            }
        }
        double rough=3*noise(seed,18170,a,b,32)+1.2*noise(seed,18166,a,b,11);
        for(int i=0;i<c.spanCount;i++) {
            Span span=c.spans[i];
            double t=((a-span.one.a)*span.dx+(b-span.one.b)*span.dz)/span.lengthSquared;
            if(t<0||t>1)continue;
            double distance=Math.hypot(a-span.one.a-t*span.dx,b-span.one.b-t*span.dz);
            double width=span.width*(1+.10*Math.sin(t*Math.PI*2+span.phase));
            if(distance>=width)continue;
            double cross=distance/width,curve=Math.pow(Math.sin(Math.PI*t),span.curveExponent);
            double top=SeaTerrain.lerp(span.one.top,span.two.top,t)-5+12*curve-29*Math.pow(cross,1.4)+rough;
            // A soft crown stays curved below the old Y317 clipping plane.
            if(top>305)top=305+(top-305)/(1+(top-305)/12.0);
            double bottom=Math.min(top-28,baseFloor+42+span.opening*curve+18*cross*cross+rough);
            double middle=(top+bottom)*.5,half=(top-bottom)*.5*Math.sqrt(Math.max(0,1-cross*cross));
            int low=Math.max(foot+1,(int)Math.ceil(middle-half)),high=Math.min(319,(int)Math.floor(middle+half));
            for(int y=Math.max(-59,low);y<=high;y++)blocks[y+64]=c.colors[y+64];
        }
        // Stable surface veneers: a one-block-wide roof edge must not turn into
        // an unsupported falling-sand block as chunks become active.
        for(int i=383;i>4;i--)if(blocks[i]!=0&&blocks[i]!=8&&(i==383||blocks[i+1]==0)) {
            if(i==foot+64&&stonyFoot)continue;
            blocks[i]=(byte)(blocks[i-1]!=0&&blocks[i-1]!=8?SpokelandsTerrain.RED_SAND:SpokelandsTerrain.RED_SANDSTONE);
        }
        return foot;
    }
}
