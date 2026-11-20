package dev.greatsea;



import net.minecraft.block.BlockState;

import net.minecraft.block.Blocks;



/** Finishes hills/dune relief before its straight biome boundary.
 * Adjacent generators are never sampled and material palettes never cross the line. */
final class FarlandsMargins {

    private static final int WIDTH=256;

    private static final class Column {

        long seed;int x,z,stamp,boundary;boolean valid,right;

        int lx,lz,rx,rz;final byte[] left=new byte[384],other=new byte[384],blocks=new byte[384];

        final double[] ld=new double[384],rd=new double[384];

    }

    private static final ThreadLocal<Column> CACHE=ThreadLocal.withInitial(Column::new);

    private static final class Boundary {int x,z,entry,result;int[] widths;boolean valid;}

    private static final ThreadLocal<Boundary> EDGE=ThreadLocal.withInitial(Boundary::new);

    private FarlandsMargins(){}

    static int boundary(int x,int z){

        var edge=EDGE.get();

        if(edge.valid&&edge.x==x&&edge.z==z&&edge.entry==GreatSea.FARLANDS.entryBoundary&&edge.widths==GreatSea.FARLANDS.stageWidths)return edge.result;

        edge.valid=true;edge.x=x;edge.z=z;edge.entry=GreatSea.FARLANDS.entryBoundary;edge.widths=GreatSea.FARLANDS.stageWidths;

        edge.result=findBoundary(x,z);return edge.result;

    }

    private static int findBoundary(int x,int z){

        if(!FarlandsWorld.contains(x,z)||SpokelandsTerrain.contains(x,z))return -1;

        int depth=FarlandsWorld.project(x,z).depth();

        // Only the two joins adjoining Plains Hills and Desert Dunes retain
        // the newer relief margins. Every other positive stage keeps its
        // original generator right through the boundary.
        for(int edge:new int[]{FarlandsTerrain.start(FarlandsTerrain.Stage.SAND_DUNES)}){
            if(Math.abs(depth-edge)<WIDTH)return edge;

        }

        return -1;

    }

    static int floor(long seed,int depth,int tangent){return 144+(int)Math.round(3*SeaTerrain.noise(seed+18905,depth,tangent,180)+SeaTerrain.noise(seed+18906,depth,tangent,45));}

    static boolean active(int x,int z){return boundary(x,z)>=0;}

    private static Column column(long seed,int x,int z){

        var c=CACHE.get();int stamp=31*java.util.Arrays.hashCode(GreatSea.FARLANDS.stageWidths)+GreatSea.FARLANDS.entryBoundary;

        if(c.valid&&c.seed==seed&&c.x==x&&c.z==z&&c.stamp==stamp)return c;

        c.valid=true;c.seed=seed;c.x=x;c.z=z;c.stamp=stamp;c.boundary=boundary(x,z);

        var point=FarlandsWorld.project(x,z);int depth=point.depth();

        var own=FarlandsTerrain.stage(x,z);

        int distance=Math.min(own==FarlandsTerrain.Stage.PLAINS?Integer.MAX_VALUE:depth-FarlandsTerrain.start(own),FarlandsTerrain.end(own)-1-depth);

        c.lx=c.rx=x;c.lz=c.rz=z;c.right=false;

        for(int i=0;i<384;i++)c.left[i]=(byte)FarlandsTerrain.rawMaterial(seed,x,i-64,z);

        int level=floor(seed,depth,point.tangent());

        int surface=switch(own){case SAND_DUNES,SAND_FARLANDS->5;case SPIKES->7;case GRAVEL,GRAVEL_AFTER_SPIKES->9;default->4;};

        int under=surface==5?6:surface==9?9:surface==7?1:3;

        for(int i=0;i<384;i++)c.other[i]=(byte)(i-64>level?0:i-64==level?surface:i-64>level-3?under:i<5?8:i==5?10:i<64?2:1);
        double t=1-SeaTerrain.smooth(Math.max(0,Math.min(1,distance/(double)WIDTH)));

        TerrainMorph.blend(c.left,c.other,c.ld,c.rd,t,false,c.blocks);

        var stage=FarlandsTerrain.stage(c.right?c.rx:c.lx,c.right?c.rz:c.lz);

        int cap=switch(stage){case SAND_DUNES,SAND_FARLANDS->5;case SPIKES->7;case GRAVEL,GRAVEL_AFTER_SPIKES->9;default->4;};

        int soil=cap==5?6:cap==9?9:cap==7?1:3;

        for(int i=383;i>=0;i--)if(TerrainMorph.solid(c.blocks[i]&255)&&(i==383||c.blocks[i+1]==0)){

            int old=c.blocks[i]&255;if(old>13)continue;

            c.blocks[i]=(byte)(cap==5&&(i==0||c.blocks[i-1]==0)?6:cap);

            for(int k=1;k<=2&&i-k>=0&&TerrainMorph.solid(c.blocks[i-k]&255);k++)if((c.blocks[i-k]&255)<=13)c.blocks[i-k]=(byte)soil;

        }

        return c;

    }

    static int material(long seed,int x,int y,int z){return y< -64||y>319?0:column(seed,x,z).blocks[y+64]&255;}

    static BlockState state(long seed,int x,int y,int z){

        if(y< -64||y>319)return Blocks.AIR.getDefaultState();

        var c=column(seed,x,z);int i=y+64,m=c.blocks[i]&255;

        if(m==0)return Blocks.AIR.getDefaultState();if(m==8)return Blocks.WATER.getDefaultState();

        byte[] source=c.right?c.other:c.left;

        int at=TerrainMorph.solid(source[i]&255)?i:TerrainMorph.nearestSolid(source,i);

        if(at<0)return switch(m){case 2->Blocks.DEEPSLATE.getDefaultState();case 3->Blocks.DIRT.getDefaultState();case 4->Blocks.GRASS_BLOCK.getDefaultState();case 5->Blocks.SAND.getDefaultState();case 6->Blocks.SANDSTONE.getDefaultState();case 7->Blocks.SNOW_BLOCK.getDefaultState();case 9->Blocks.GRAVEL.getDefaultState();case 10->Blocks.BEDROCK.getDefaultState();default->Blocks.STONE.getDefaultState();};
        var state=FarlandsGeneration.unblendedState(seed,c.right?c.rx:c.lx,at-64,c.right?c.rz:c.lz);

        if(m!=(source[at]&255))return switch(m){
            case 1->Blocks.STONE.getDefaultState();case 2->Blocks.DEEPSLATE.getDefaultState();
            case 3->Blocks.DIRT.getDefaultState();case 4->Blocks.GRASS_BLOCK.getDefaultState();

            case 5->Blocks.SAND.getDefaultState();case 6->Blocks.SANDSTONE.getDefaultState();

            case 7->Blocks.SNOW_BLOCK.getDefaultState();case 9->Blocks.GRAVEL.getDefaultState();default->state;

        };

        return state;

    }

}

