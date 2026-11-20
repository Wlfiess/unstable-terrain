package dev.greatsea;

import net.minecraft.block.*;
import net.minecraft.registry.RegistryKey;
import net.minecraft.registry.RegistryKeys;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.random.Random;
import net.minecraft.world.StructureWorldAccess;
import net.minecraft.world.chunk.Chunk;
import net.minecraft.world.gen.feature.*;
import net.minecraft.world.gen.stateprovider.BlockStateProvider;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Source-shaped vegetation for the independent negative-coordinate sequence.
 * Custom trees are reconstructed in each owning chunk, so a large crown does not
 * depend on whether neighbouring chunks happened to finish decorating first. */
public final class SpokelandsDecoration {
    private static final double TAU=Math.PI*2;
    private record Candidate(int x,int z,long hash,SpokelandsTerrain.Region region){}
    private static final Map<Block,ConfiguredFeature<SimpleBlockFeatureConfig,?>> PLANTS=new ConcurrentHashMap<>();
    private static final class Palette {static final Block[] RAINBOW={Blocks.POPPY,Blocks.DANDELION,Blocks.CORNFLOWER,Blocks.OXEYE_DAISY,Blocks.ALLIUM,Blocks.AZURE_BLUET,Blocks.ORANGE_TULIP,Blocks.PINK_TULIP};}
    private SpokelandsDecoration(){}

    public static void decorate(Chunk chunk,StructureWorldAccess world){
        int sx=chunk.getPos().getStartX(),sz=chunk.getPos().getStartZ();long seed=world.getSeed();
        // Sampled bridges carry their original terrain only; no procedural vine overlay.
        // Proposals have independent positions, counts and neighbour priorities.
        // They are neither cell-centred nor periodically copied tree schematics.
        var nearby=new ArrayList<Candidate>();
        int search=SpokelandsTerrain.contains(sx+8,sz+8)&&FarlandsWorld.project(sx+8,sz+8).depth()>=SpokelandsTerrain.start(SpokelandsTerrain.Region.SCULK_FOREST)-160?10:3;
        for(int dz=-search;dz<=search;dz++)for(int dx=-search;dx<=search;dx++)for(int index=0;index<4;index++){
            var candidate=candidate(seed,chunk.getPos().x+dx,chunk.getPos().z+dz,index);
            if(candidate==null||!custom(candidate.region())||!selected(candidate)||!separated(seed,candidate))continue;
            double reach=reach(candidate.region());
            if(candidate.x()+reach<sx||candidate.x()-reach>sx+15||candidate.z()+reach<sz||candidate.z()-reach>sz+15)continue;
            nearby.add(candidate);
        }
        nearby.sort((a,b)->Long.compareUnsigned(a.hash(),b.hash()));
        for(var candidate:nearby){
            int y=SpokelandsTerrain.ground(seed,candidate.x(),candidate.z())+1;
            if(candidate.region()==SpokelandsTerrain.Region.SCULK_FOREST){
                var model=TerrainSamples138.tree(candidate.hash());
                int extent=Math.max(Math.max(model.cx,model.width-1-model.cx),Math.max(model.cz,model.depth-1-model.cz))+2;
                if(!SpokelandsTerrain.treeClearOfRiver(seed,candidate.x(),candidate.z(),extent))continue;
            }
            if(!supported(seed,candidate.x(),y,candidate.z(),candidate.region()))continue;
            var writer=new Writer(world,chunk,candidate.region());
            switch(candidate.region()){
                case RED_PLAINS -> ashen(writer,candidate,y);
                case DARK_FOREST -> dark(writer,candidate,y);
                case RAINBOW_FOREST -> rainbow(writer,candidate,y);
                case SCULK_FOREST -> luminous(writer,candidate,y);
                default -> {}
            }
        }
        // Ordinary oaks and birches retain Minecraft's native branch/leaf rules.
        for(int index=0;index<4;index++){
            var candidate=candidate(seed,chunk.getPos().x,chunk.getPos().z,index);
            if(candidate==null||custom(candidate.region())||!selected(candidate)||!separated(seed,candidate))continue;
            var region=candidate.region();if(region!=SpokelandsTerrain.Region.CLASSIC&&region!=SpokelandsTerrain.Region.WINDING)continue;
            if(region==SpokelandsTerrain.Region.WINDING){
                if(unit(candidate.hash(),1117)>=windingVegetation(candidate.x(),candidate.z()))continue;
                // Every stable exposed ribbon is a separate planting surface.
                // Choosing one Y for the entire vertical column starved almost
                // all upper waves while concentrating proposals on the ground.
                int floor=SpokelandsTerrain.ground(seed,candidate.x(),candidate.z());
                for(int y:windingTreeSurfaces(seed,candidate.x(),candidate.z())){
                    long hash=SeaTerrain.hash(candidate.hash(),y,1017);
                    if(unit(hash,1)>=(y>floor+1?.72:.5))continue;
                    var root=new BlockPos(candidate.x(),y,candidate.z());
                    if(!soil(world.getBlockState(root.down()),region)||!replaceable(world.getBlockState(root)))continue;
                    boolean clear=true;for(int dy=1;dy<=9;dy++)if(!replaceable(world.getBlockState(root.up(dy)))){clear=false;break;}
                    if(clear)nativeTree(world,unit(hash,4)<.29?TreeConfiguredFeatures.BIRCH:TreeConfiguredFeatures.OAK,hash,root);
                }
                continue;
            }
            int y=ordinarySurface(world,candidate);
            if(y<=world.getBottomY()||!supported(seed,candidate.x(),y,candidate.z(),region))continue;
            var root=new BlockPos(candidate.x(),y,candidate.z());
            if(!soil(world.getBlockState(root.down()),region)||!replaceable(world.getBlockState(root)))continue;
            boolean birch=unit(candidate.hash(),4)<.29;
            nativeTree(world,birch?TreeConfiguredFeatures.BIRCH:unit(candidate.hash(),5)<.18?TreeConfiguredFeatures.FANCY_OAK:TreeConfiguredFeatures.OAK,candidate.hash(),root);
        }
        // Flowers and roots are placed last, into the actual remaining clear floor.
        // Smooth patch fields retain the distinct grouped colours visible in the footage.
        for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++){
            int x=sx+dx,z=sz+dz;if(!SpokelandsTerrain.contains(x,z))continue;
            var region=SpokelandsTerrain.region(x,z);
            if(region==SpokelandsTerrain.Region.WINDING&&windingVegetation(x,z)==0)continue;
            int y=SpokelandsTerrain.ground(seed,x,z)+1;
            if(y<=world.getBottomY()||y>=world.getTopYInclusive())continue;
            var root=new BlockPos(x,y,z);
            if(replaceable(world.getBlockState(root))&&soil(world.getBlockState(root.down()),region))plantFloor(world,seed,region,root);
            if(region==SpokelandsTerrain.Region.WINDING){
                // Each exposed upper shelf can carry its own native plants; its
                // Y contributes to the patch seed so layers do not duplicate.
                for(int ledge=y+10;ledge<world.getTopYInclusive();ledge++){
                    var at=new BlockPos(x,ledge,z);
                    if(!world.getBlockState(at.down()).isOf(Blocks.GRASS_BLOCK)||!replaceable(world.getBlockState(at)))continue;
                    plantFloor(world,seed,region,at);
                }
            }
        }
    }

    /** First kilometre stays bare; a further 512 blocks gradually introduces plants. */
    static double windingVegetation(int x,int z){
        return windingVegetationAtDepth((int)FarlandsWorld.depth(x,z));
    }
    static double windingVegetationAtDepth(int depth){
        return SeaTerrain.smooth((depth-SpokelandsTerrain.windingStart()-1000)/512.0);
    }
    // Reconstruct source voxels after reload too; only original tree blocks are protected.
    private record TreeChunk(long seed,int x,int z){}
    private static final Map<TreeChunk,Map<Long,BlockState>> STABLE_TREES=Collections.synchronizedMap(new LinkedHashMap<>(){
        protected boolean removeEldestEntry(Map.Entry<TreeChunk,Map<Long,BlockState>> e){return size()>512;}
    });
    public static boolean stableRainbow(long seed,BlockPos pos,BlockState state){
        if(!SpokelandsTerrain.contains(pos.getX(),pos.getZ())||SpokelandsTerrain.region(pos.getX(),pos.getZ())!=SpokelandsTerrain.Region.RAINBOW_FOREST||!SpokelandsRainbowTreeAssets.uses(state))return false;
        var key=new TreeChunk(seed,pos.getX()>>4,pos.getZ()>>4);
        Map<Long,BlockState> voxels;
        synchronized(STABLE_TREES){voxels=STABLE_TREES.computeIfAbsent(key,k->{
            var result=new HashMap<Long,BlockState>();var candidates=new ArrayList<Candidate>();
            for(int dz=-3;dz<=3;dz++)for(int dx=-3;dx<=3;dx++)for(int i=0;i<4;i++){
                var c=candidate(seed,k.x()+dx,k.z()+dz,i);
                if(c==null||c.region()!=SpokelandsTerrain.Region.RAINBOW_FOREST||!selected(c)||!separated(seed,c))continue;
                int y=SpokelandsTerrain.ground(seed,c.x(),c.z())+1;
                if(supported(seed,c.x(),y,c.z(),c.region()))candidates.add(c);
            }
            candidates.sort((a,b)->Long.compareUnsigned(a.hash(),b.hash()));
            for(var c:candidates)SpokelandsRainbowTreeAssets.place(c.hash(),c.x(),SpokelandsTerrain.ground(seed,c.x(),c.z())+1,c.z(),(x,y,z,block)->{
                if((x>>4)==k.x()&&(z>>4)==k.z())result.put(BlockPos.asLong(x,y,z),block);
            });
            // Packed block-position longs cluster badly in MapN linear probing.
            // Keep the already-built hash table; no caller can mutate it.
            return Collections.unmodifiableMap(result);
        });}
        var expected=voxels.get(pos.asLong());return expected!=null&&expected.isOf(state.getBlock());
    }

    private static Candidate candidate(long seed,int cx,int cz,int index){
        long hash=SeaTerrain.hash(seed+0x6F53504F4B45L+index*0x632BE59BD9B4E019L,cx,cz);
        int x=cx*16+(int)(unit(hash,1)*16),z=cz*16+(int)(unit(hash,2)*16);
        return SpokelandsTerrain.contains(x,z)?new Candidate(x,z,hash,SpokelandsTerrain.region(x,z)):null;
    }
    private static boolean custom(SpokelandsTerrain.Region region){return region==SpokelandsTerrain.Region.RED_PLAINS||region==SpokelandsTerrain.Region.DARK_FOREST||region==SpokelandsTerrain.Region.RAINBOW_FOREST||region==SpokelandsTerrain.Region.SCULK_FOREST;}
    private static double proposals(SpokelandsTerrain.Region region){return switch(region){
        case CLASSIC -> 0;case WINDING -> .75;case RED_PLAINS -> .65;
        case DARK_FOREST -> .85;case RAINBOW_FOREST -> .90;case SCULK_FOREST -> .30;
        default -> 0;
    };}
    private static double separation(SpokelandsTerrain.Region region){return switch(region){case RED_PLAINS -> 12;case DARK_FOREST -> 13;case RAINBOW_FOREST -> 12;case SCULK_FOREST -> 90;default -> 4;};}
    private static double reach(SpokelandsTerrain.Region region){return switch(region){case RED_PLAINS -> 14;case DARK_FOREST -> 28;case RAINBOW_FOREST -> 26;case SCULK_FOREST -> 150;default -> 8;};}
    private static boolean selected(Candidate candidate){
        if(SeaTerrain.unit(candidate.hash())>=proposals(candidate.region())/4)return false;
        if(candidate.region()==SpokelandsTerrain.Region.RED_PLAINS){
            double remaining=SpokelandsTerrain.end(candidate.region())-FarlandsWorld.depth(candidate.x(),candidate.z());
            if(unit(candidate.hash(),1019)>=SeaTerrain.smooth(remaining/96.0))return false;
        }
        int extent=(int)Math.ceil(reach(candidate.region()))+2;
        // Reject the complete tree proposal, never trim its crown at a biome line.
        for(int dx:new int[]{-extent,extent})for(int dz:new int[]{-extent,extent})
            if(!SpokelandsTerrain.contains(candidate.x()+dx,candidate.z()+dz)||SpokelandsTerrain.region(candidate.x()+dx,candidate.z()+dz)!=candidate.region())return false;
        return true;
    }
    private static boolean separated(long seed,Candidate candidate){
        int cx=Math.floorDiv(candidate.x(),16),cz=Math.floorDiv(candidate.z(),16),radius=(int)Math.ceil(separation(candidate.region())/16);
        for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++)for(int index=0;index<4;index++){
            var other=candidate(seed,cx+dx,cz+dz,index);
            if(other==null||other.equals(candidate)||other.region()!=candidate.region()||!selected(other))continue;
            long a=other.x()-candidate.x(),b=other.z()-candidate.z();double distance=separation(candidate.region());
            if(a*a+b*b<distance*distance&&Long.compareUnsigned(other.hash(),candidate.hash())<0)return false;
        }
        return true;
    }
    private static double unit(long hash,int channel){return SeaTerrain.unit(SeaTerrain.hash(hash,channel,channel*37L+11));}
    private static boolean soil(BlockState state,SpokelandsTerrain.Region region){
        if(region==SpokelandsTerrain.Region.RED_PLAINS)return state.isOf(Blocks.CRIMSON_NYLIUM);
        if(region==SpokelandsTerrain.Region.SCULK_FOREST)return state.isOf(Blocks.WARPED_NYLIUM)||state.isOf(Blocks.MUD)||state.isOf(Blocks.SCULK);
        if(region==SpokelandsTerrain.Region.SAND_LANDS)return state.isOf(Blocks.RED_SAND)||state.isOf(Blocks.SAND);
        return state.isOf(Blocks.GRASS_BLOCK)||state.isOf(Blocks.DIRT)||state.isOf(Blocks.PODZOL)||state.isOf(Blocks.MOSS_BLOCK);
    }
    private static boolean replaceable(BlockState state){return state.isAir()||state.isOf(Blocks.SHORT_GRASS)||state.isOf(Blocks.FERN)||state.isOf(Blocks.SNOW)||state.isOf(Blocks.CRIMSON_ROOTS)||state.isOf(Blocks.WARPED_ROOTS)||state.isOf(Blocks.NETHER_SPROUTS);}
    private static boolean supported(long seed,int x,int y,int z,SpokelandsTerrain.Region region){
        if(y< -59||y>(region==SpokelandsTerrain.Region.WINDING?309:285)||!soil(SpokelandsTerrain.state(seed,x,y-1,z),region))return false;
        if(SpokelandsTerrain.material(seed,x,y,z)!=0)return false;
        // Keep the whole tree inland. Never carve a river-shaped hole through
        // a completed crown or leave branches hanging above the water.
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
            if(!SpokelandsTerrain.contains(x+dx,z+dz)||SpokelandsTerrain.region(x+dx,z+dz)!=region)return false;
            int ground=SpokelandsTerrain.ground(seed,x+dx,z+dz);
            if(region==SpokelandsTerrain.Region.CLASSIC||region==SpokelandsTerrain.Region.WINDING){
                ground=Integer.MIN_VALUE;
                for(int nearby=y-4;nearby<=y+2;nearby++)if(soil(SpokelandsTerrain.state(seed,x+dx,nearby,z+dz),region)&&SpokelandsTerrain.material(seed,x+dx,nearby+1,z+dz)==0){ground=nearby;break;}
                if(ground==Integer.MIN_VALUE)return false;
            }
            if(Math.abs(ground-(y-1))>3||!soil(SpokelandsTerrain.state(seed,x+dx,ground,z+dz),region))return false;
        }
        return true;
    }
    /** Native oak/birch roots on the measured ribbons, with real crown clearance. */
    static int[] windingTreeSurfaces(long seed,int x,int z){
        var roots=new java.util.ArrayList<Integer>();
        var region=SpokelandsTerrain.Region.WINDING;
        if(!SpokelandsTerrain.contains(x,z)||SpokelandsTerrain.region(x,z)!=region)return new int[0];
        for(int ground=SpokelandsTerrain.ground(seed,x,z);ground<=308;ground++){
            if(SpokelandsTerrain.material(seed,x,ground,z)!=4)continue;
            boolean clear=true;
            for(int dy=1;dy<=10;dy++)if(SpokelandsTerrain.material(seed,x,ground+dy,z)!=0){clear=false;break;}
            if(clear&&windingSurfaceSupported(seed,x,ground,z))roots.add(ground+1);
        }
        return roots.stream().mapToInt(Integer::intValue).toArray();
    }
    private static boolean windingSurfaceSupported(long seed,int x,int ground,int z){
        for(int dz=-1;dz<=1;dz++)for(int dx=-1;dx<=1;dx++){
            if(!SpokelandsTerrain.contains(x+dx,z+dz)||SpokelandsTerrain.region(x+dx,z+dz)!=SpokelandsTerrain.Region.WINDING)return false;
            boolean supported=false;
            for(int nearby=ground-3;nearby<=ground+3;nearby++){
                int material=SpokelandsTerrain.material(seed,x+dx,nearby,z+dz);
                if((material==3||material==4)&&SpokelandsTerrain.material(seed,x+dx,nearby+1,z+dz)==0){supported=true;break;}
            }
            if(!supported)return false;
        }
        return true;
    }
    private static int ordinarySurface(StructureWorldAccess world,Candidate candidate){
        int x=candidate.x(),z=candidate.z();long seed=world.getSeed();
        int ground=SpokelandsTerrain.ground(seed,x,z);
        if(candidate.region()!=SpokelandsTerrain.Region.CLASSIC)return ground+1;
        // Reservoir sampling distributes native trees over exposed ledges, rather
        // than placing an entire forest on the wall's highest roof alone.
        int chosen=world.getBottomY()-1,count=0;
        for(int y=-58;y<world.getTopYInclusive()-16;y++){
            if(!soil(world.getBlockState(new BlockPos(x,y,z)),candidate.region()))continue;
            boolean clear=true;for(int dy=1;dy<=12;dy++)if(!replaceable(world.getBlockState(new BlockPos(x,y+dy,z)))){clear=false;break;}
            if(clear&&unit(SeaTerrain.hash(candidate.hash(),y,3),9)*++count<1)chosen=y+1;
        }
        return chosen;
    }
    private static boolean nativeTree(StructureWorldAccess world,RegistryKey<ConfiguredFeature<?,?>> key,long hash,BlockPos root){
        var feature=world.getRegistryManager().getOrThrow(RegistryKeys.CONFIGURED_FEATURE).getOrThrow(key).value();
        return feature.generate(world,world.toServerWorld().getChunkManager().getChunkGenerator(),Random.create(hash),root);
    }
    private static void plant(StructureWorldAccess world,Block block,long hash,BlockPos root){
        if(!replaceable(world.getBlockState(root))||!block.getDefaultState().canPlaceAt(world,root))return;
        var feature=PLANTS.computeIfAbsent(block,b->new ConfiguredFeature<>(Feature.SIMPLE_BLOCK,new SimpleBlockFeatureConfig(BlockStateProvider.of(b))));
        feature.generate(world,world.toServerWorld().getChunkManager().getChunkGenerator(),Random.create(hash),root);
    }
    private static void plantFloor(StructureWorldAccess world,long seed,SpokelandsTerrain.Region region,BlockPos root){
        int x=root.getX(),z=root.getZ();
        if(region==SpokelandsTerrain.Region.WINDING && SeaTerrain.unit(SeaTerrain.hash(seed+1117,x,z))>=windingVegetation(x,z))return;
        long hash=SeaTerrain.hash(seed+0x504C414E54L+root.getY()*1327L,x,z);double roll=SeaTerrain.unit(hash);
        double patch=SeaTerrain.noise(seed+14411,x,z,17),colour=SeaTerrain.noise(seed+14412,x,z,11);
        switch(region){
            case FLOWER_HILLS -> {
                double mix=unit(hash,602),white=.54+.05*colour,purple=.82+.03*colour;
                if(roll<.95+.025*patch)plant(world,mix<white?Blocks.OXEYE_DAISY:mix<purple?Blocks.ALLIUM:Blocks.DANDELION,hash,root);
                else if(roll>.985)plant(world,Blocks.SHORT_GRASS,hash,root);
            }
            case RED_PLAINS -> {if(roll<.72+.13*patch)plant(world,Blocks.CRIMSON_ROOTS,hash,root);}
            case RAINBOW_FOREST -> {
                // Colour patches contain a local dominant flower, with scattered
                // accents, open grass strips and the occasional mixed patch edge.
                if(patch>-.24&&roll<.72){
                    Block[] palette=Palette.RAINBOW;
                    int index=unit(hash,81)<.88?(int)(unit(hash,82)*4):4+(int)(unit(hash,83)*4);
                    
                    plant(world,palette[index],hash,root);
                }else if(roll<.40)plant(world,Blocks.SHORT_GRASS,hash,root);
            }
            case SCULK_FOREST -> {
                if(roll<.10+.07*patch)plant(world,Blocks.WARPED_ROOTS,hash,root);
                else if(roll<.17+.09*patch)plant(world,unit(hash,12)<.78?Blocks.WARPED_FUNGUS:Blocks.CRIMSON_FUNGUS,hash,root);
                else if(roll>.95)plant(world,Blocks.NETHER_SPROUTS,hash,root);
            }
            // The complete negative red-sand traversal shows bare terrain,
            // without the cacti and dead bushes of the positive dunes region.
            case SAND_LANDS -> {}
            case WINDING -> {
                if(roll<.16)plant(world,Blocks.SHORT_GRASS,hash,root);
                else if(roll<.18)plant(world,unit(hash,18)<.5?Blocks.DANDELION:Blocks.POPPY,hash,root);
            }
            case DARK_FOREST -> {if(roll<.32)plant(world,Blocks.FERN,hash,root);else if(roll<.72)plant(world,Blocks.SHORT_GRASS,hash,root);}
            default -> {}
        }
    }

    /** Every voxel write belongs to this chunk and preserves its original terrain. */
    private static final class Writer {
        final StructureWorldAccess world;final long seed;final int sx,sz,minY,maxY;final SpokelandsTerrain.Region region;
        final BlockPos.Mutable at=new BlockPos.Mutable();
        Writer(StructureWorldAccess world,Chunk chunk,SpokelandsTerrain.Region region){
            this.world=world;seed=world.getSeed();sx=chunk.getPos().getStartX();sz=chunk.getPos().getStartZ();minY=world.getBottomY();maxY=world.getTopYInclusive();this.region=region;
        }
        void put(int x,int y,int z,BlockState state,boolean foliage){
            if(x<sx||x>sx+15||z<sz||z>sz+15||y<minY||y>maxY||!SpokelandsTerrain.contains(x,z)||SpokelandsTerrain.region(x,z)!=region)return;
            at.set(x,y,z);var old=world.getBlockState(at);
            boolean leaf=old.getBlock() instanceof LeavesBlock;
            if(!replaceable(old)&&!leaf)return;
            if(foliage&&!old.isAir()&&!leaf)return;
            // The actual generated rock/soil/overhead mesh was rejected above.
            // Re-evaluating a whole terrain column for each branch voxel would
            // discard the generation engine's efficient consecutive-Y cache.
            world.setBlockState(at,state,2);
        }
        void woodBand(int x,int y,int z,BlockState state){
            if(x<sx||x>sx+15||z<sz||z>sz+15||y<minY||y>maxY)return;
            at.set(x,y,z);
            if(world.getBlockState(at).isOf(Blocks.STRIPPED_ACACIA_WOOD))world.setBlockState(at,state,2);
        }
        void ellipsoid(double cx,double cy,double cz,double rx,double ry,double rz,BlockState state,long hash){
            int ax=Math.max(sx,(int)Math.floor(cx-rx-1)),bx=Math.min(sx+15,(int)Math.ceil(cx+rx+1));
            int az=Math.max(sz,(int)Math.floor(cz-rz-1)),bz=Math.min(sz+15,(int)Math.ceil(cz+rz+1));
            for(int y=Math.max(minY,(int)Math.floor(cy-ry-1));y<=Math.min(maxY,(int)Math.ceil(cy+ry+1));y++)for(int z=az;z<=bz;z++)for(int x=ax;x<=bx;x++){
                double dx=(x-cx)/rx,dy=(y-cy)/ry,dz=(z-cz)/rz;
                double edge=dx*dx+dy*dy+dz*dz,ragged=.11*(SeaTerrain.unit(SeaTerrain.hash(hash+x*13L,y,z))-.5);
                if(edge<=1+ragged)put(x,y,z,state,true);
            }
        }
        void vine(int x,int y,int z,int length){
            if(x<sx||x>sx+15||z<sz||z>sz+15)return;
            int start=Integer.MIN_VALUE;
            for(int atY=y-2;atY<=y+5;atY++)if(world.getBlockState(new BlockPos(x,atY+1,z)).getBlock() instanceof LeavesBlock&&world.getBlockState(new BlockPos(x,atY,z)).isAir()){start=atY;break;}
            if(start==Integer.MIN_VALUE)return;
            var vine=Blocks.VINE.getDefaultState().with(Properties.NORTH,true);
            for(int dy=0;dy<length;dy++){
                if(!world.getBlockState(new BlockPos(x,start-dy,z)).isAir())break;
                put(x,start-dy,z,vine.with(Properties.UP,dy==0),false);
            }
        }
        void line(double ax,double ay,double az,double bx,double by,double bz,double radius,BlockState state){
            double length=Math.sqrt((bx-ax)*(bx-ax)+(by-ay)*(by-ay)+(bz-az)*(bz-az));int steps=Math.max(1,(int)Math.ceil(length*1.5));
            for(int i=0;i<=steps;i++){
                double t=i/(double)steps,x=SeaTerrain.lerp(ax,bx,t),y=SeaTerrain.lerp(ay,by,t),z=SeaTerrain.lerp(az,bz,t),r=Math.max(.49,radius*(1-.40*t));
                if(x+r<sx||x-r>sx+15||z+r<sz||z-r>sz+15)continue;
                put((int)Math.round(x),(int)Math.round(y),(int)Math.round(z),state,false);
                for(int iz=Math.max(sz,(int)Math.floor(z-r));iz<=Math.min(sz+15,(int)Math.ceil(z+r));iz++)for(int ix=Math.max(sx,(int)Math.floor(x-r));ix<=Math.min(sx+15,(int)Math.ceil(x+r));ix++)for(int iy=(int)Math.floor(y-r);iy<=Math.ceil(y+r);iy++)
                    if((ix-x)*(ix-x)+(iy-y)*(iy-y)+(iz-z)*(iz-z)<=r*r+.15)put(ix,iy,iz,state,false);
            }
        }
    }
    private static BlockState leaves(Block block){return block.getDefaultState().with(Properties.PERSISTENT,true).with(Properties.DISTANCE_1_7,1);}

    /** Approved asset with deterministic rotation; no procedural substitute. */
    private static void ashen(Writer writer,Candidate tree,int ground){
        SpokelandsRedTreeAsset.place(tree.hash(),tree.x(),ground,tree.z(),(x,y,z,state)->writer.put(x,y,z,state,false));
    }

    /** Source voxels and source palette; placement varies, models are not resculpted. */
    private static void dark(Writer writer,Candidate tree,int ground){
        SpokelandsTreeAssets.place(tree.hash(),tree.x(),ground,tree.z(),(x,y,z,state)->writer.put(x,y,z,state,false));
    }

    /** Preserve Horace's source blocks and geometry; rotation alone creates orientation variety. */
    private static void rainbow(Writer writer,Candidate tree,int ground){
        SpokelandsRainbowTreeAssets.place(tree.hash(),tree.x(),ground,tree.z(),(x,y,z,state)->writer.put(x,y,z,state,false));
    }

    /** Source-connected specimens with recovered glass veins and separated wood/crowns. */
    private static void luminous(Writer writer,Candidate tree,int ground){
        long hash=tree.hash();var shape=TerrainSamples138.tree(hash);int turn=(int)((hash>>>8)&3);
        for(int z=writer.sz;z<=writer.sz+15;z++)for(int x=writer.sx;x<=writer.sx+15;x++){
            int dx=x-tree.x(),dz=z-tree.z();int u=switch(turn){case 1 -> dz;case 2 -> -dx;case 3 -> -dz;default -> dx;};int v=switch(turn){case 1 -> -dx;case 2 -> -dz;case 3 -> dx;default -> dz;};
            for(int y=-1;y<=shape.height;y++){
                int px=u+shape.cx,pz=v+shape.cz;int kind=shape.at(px,y,pz);boolean cover=glassCover(shape,px,y,pz);if(kind==0&&!cover)continue;
                BlockState state=cover?Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState():treeLight(shape,px,y,pz)?Blocks.VERDANT_FROGLIGHT.getDefaultState():switch(kind){
                    case 2 -> Blocks.WARPED_WART_BLOCK.getDefaultState();
                    case 3 -> Blocks.WARPED_HYPHAE.getDefaultState();
                    case 4 -> Blocks.WARPED_PLANKS.getDefaultState();
                    case 13 -> Blocks.STRIPPED_WARPED_HYPHAE.getDefaultState().with(PillarBlock.AXIS,(turn&1)==0?Direction.Axis.X:Direction.Axis.Z);
                    case 14 -> Blocks.STRIPPED_WARPED_HYPHAE.getDefaultState().with(PillarBlock.AXIS,(turn&1)==0?Direction.Axis.Z:Direction.Axis.X);
                    case 5 -> (treeLight(shape,u+shape.cx,y,v+shape.cz)?Blocks.VERDANT_FROGLIGHT:Blocks.SCULK).getDefaultState();
                    case 6 -> (treeLight(shape,u+shape.cx,y,v+shape.cz)?Blocks.VERDANT_FROGLIGHT:Blocks.WARPED_HYPHAE).getDefaultState();
                    case 7 -> Blocks.LIGHT_BLUE_STAINED_GLASS.getDefaultState();
                    case 15 -> (y<=1?Blocks.WARPED_NYLIUM:Blocks.MUD).getDefaultState();
                    case 8 -> Blocks.MOSSY_COBBLESTONE.getDefaultState();
                    case 9 -> Blocks.PRISMARINE.getDefaultState();
                    case 10 -> Blocks.DARK_PRISMARINE.getDefaultState();
                    case 11 -> Blocks.MOSSY_COBBLESTONE.getDefaultState();
                    default -> Blocks.BROWN_TERRACOTTA.getDefaultState();
                };
                writer.put(x,ground+y,z,state,false);
            }
        }
    }
    static final int[][] LIGHT_DIRS={{-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}};
    static int backingDirection(TerrainSamples138.Tree shape,int x,int y,int z){
        int best=-1,score=-1;
        for(int i=0;i<6;i++){var d=LIGHT_DIRS[i];int px=x+d[0],py=y+d[1],pz=z+d[2],k=shape.at(px,py,pz);if(k==0||k==7)continue;
            int filled=0;for(var n:LIGHT_DIRS)if(shape.at(px+n[0],py+n[1],pz+n[2])!=0)filled++;
            if(filled>score){score=filled;best=i;}
        }
        return best;
    }
    static boolean treeLight(TerrainSamples138.Tree shape,int x,int y,int z){
        int k=shape.at(x,y,z);if(k==0||k==7)return false;
        for(int i=0;i<6;i++){var d=LIGHT_DIRS[i];int gx=x+d[0],gy=y+d[1],gz=z+d[2];if(shape.at(gx,gy,gz)==7&&backingDirection(shape,gx,gy,gz)==(i^1))return true;}
        return false;
    }
    static boolean glassCover(TerrainSamples138.Tree shape,int x,int y,int z){
        if(shape.at(x,y,z)!=0)return false;
        for(var d:LIGHT_DIRS)if(treeLight(shape,x+d[0],y+d[1],z+d[2]))return true;
        return false;
    }
}
