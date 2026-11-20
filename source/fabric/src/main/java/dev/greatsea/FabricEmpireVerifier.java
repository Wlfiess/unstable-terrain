package dev.greatsea;

import net.minecraft.block.BlockState;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** Opt-in import checks against actual generated FULL Minecraft chunks. */
public final class FabricEmpireVerifier {
    private FabricEmpireVerifier() {}
    private record Sample(int a,int y,int b,int palette) {}
    private record SourceArea(int x,int z,List<Sample> samples,Map<Integer,Sample> directional) {}

    private static void check(boolean value,String message) {
        if(!value)throw new AssertionError(message);
    }

    public static void run(MinecraftServer server) {
        var report=new ArrayList<String>();
        long began=System.nanoTime();
        try {
            verify(server.getOverworld(),report);
            report.add("PASS empire integration in "+(System.nanoTime()-began)/1e9+" seconds");
        } catch(Throwable error) {
            report.add("FAIL "+error);
            GreatSea.LOGGER.error("Empire verification failed",error);
        }
        try { Files.write(Path.of("empire-verification.txt"),report); }
        catch(Exception error) { throw new RuntimeException(error); }
        GreatSea.LOGGER.info(String.join("\n",report));
        server.stop(false);
    }

    /** Also callable by the remapped Paper verifier using the identical source assets. */
    public static void verify(ServerWorld world,List<String> report) throws Exception {
        check(FarlandsWorld.supported(world),"Empire check needs an Unstable Overworld");
        var schematic=FarlandsEmpire.SAMPLE;
        check(schematic.width==506&&schematic.height==384&&schematic.depth==530,"Empire bounds changed");
        check(schematic.blockEntities==30360,"Empire block entity manifest changed");
        check(schematic.nonAirBlocks==37661777L,"Empire solid manifest changed");
        check(FarlandsEmpire.SOURCE_BOTTOM==68&&FarlandsEmpire.BASE_Y==-132,"Empire elevation changed");
        check(FarlandsEmpire.BASE_Y+FarlandsEmpire.SOURCE_BOTTOM==world.getBottomY(),"Deep source crop is not at world bottom");
        check(FarlandsEmpire.tangentOrigin()+schematic.depth-1==FarlandsEmpire.NEAREST_TANGENT,"Incorrect empire offset");
        check(FarlandsEmpire.NEAREST_TANGENT<=-500,"Empire reaches the zero-axis route");
        check(FarlandsEmpire.origin()>=FarlandsTerrain.start(FarlandsTerrain.Stage.REPEATING),"Empire starts before Repeating Lands");
        check(FarlandsEmpire.origin()+schematic.width<=FarlandsTerrain.end(FarlandsTerrain.Stage.REPEATING),"Empire extends past Repeating Lands");
        verifyZeroRoute(world,report);

        int widthChunks=(schematic.width+15)>>4,depthChunks=(schematic.depth+15)>>4;
        var paletteSamples=new TreeMap<Integer,Sample>();
        var functionalTiles=new LinkedHashMap<String,NbtCompound>();
        var tileTypeCounts=new TreeMap<String,Integer>();
        var richest=new ArrayList<SourceArea>();
        byte[] airByPalette=new byte[65536];
        byte[] directionalByPalette=new byte[65536];
        long sourceSolids=0,retainedSolids=0;
        int areas=0,solidChecks=0,airChecks=0,tileChecks=0,clippedTiles=0,inventorySlots=0,signChecks=0;
        Sample highestSolid=null;

        for(int cz=0;cz<depthChunks;cz++)for(int cx=0;cx<widthChunks;cx++) {
            int maxA=Math.min(schematic.width,(cx+1)*16),maxB=Math.min(schematic.depth,(cz+1)*16);
            Sample firstAir=null,firstSolid=null;
            var directional=new HashMap<Integer,Sample>();
            // Scan the source to select samples and count its real mass. World checks
            // below use FULL chunks, not the generator's state function as actual data.
            for(int y=0;y<schematic.height;y++)for(int b=cz*16;b<maxB;b++)for(int a=cx*16;a<maxA;a++) {
                int id=schematic.index(a,y,b);
                if(airByPalette[id]==0) {
                    var state=schematic.state(a,y,b);
                    airByPalette[id]=(byte)(state.isAir()?1:2);
                    directionalByPalette[id]=(byte)(directional(state)?2:1);
                }
                boolean air=airByPalette[id]==1;
                if(!air)sourceSolids++;
                if(y<FarlandsEmpire.SOURCE_BOTTOM)continue;
                if(!paletteSamples.containsKey(id))paletteSamples.put(id,new Sample(a,y,b,id));
                if(air) {
                    if(firstAir==null)firstAir=new Sample(a,y,b,id);
                } else {
                    retainedSolids++;
                    if(highestSolid==null||y>highestSolid.y())highestSolid=new Sample(a,y,b,id);
                    if(firstSolid==null)firstSolid=new Sample(a,y,b,id);
                    if(directionalByPalette[id]==2&&!directional.containsKey(id))directional.put(id,new Sample(a,y,b,id));
                }
            }
            var samples=new ArrayList<Sample>();
            if(firstAir!=null)samples.add(firstAir);
            if(firstSolid!=null)samples.add(firstSolid);
            for(int i=0;i<8;i++) {
                long hash=SeaTerrain.hash(125031L+i*1009L,cx,cz);
                int a=cx*16+(int)Math.floorMod(hash,maxA-cx*16);
                int b=cz*16+(int)Math.floorMod(hash>>>17,maxB-cz*16);
                int y=FarlandsEmpire.SOURCE_BOTTOM+(int)Math.floorMod(hash>>>33,schematic.height-FarlandsEmpire.SOURCE_BOTTOM);
                samples.add(new Sample(a,y,b,schematic.index(a,y,b)));
            }
            for(var sample:samples) {
                var expected=checkState(world,0,sample);
                if(expected.isAir())airChecks++;else solidChecks++;
            }
            for(var tile:schematic.tiles(cx,cz)) {
                if(tile.getInt("y",0)<FarlandsEmpire.SOURCE_BOTTOM){clippedTiles++;continue;}
                verifyTile(world,0,tile);
                String type=tile.getString("id","");
                functionalTiles.putIfAbsent(type,tile);
                tileTypeCounts.merge(type,1,Integer::sum);
                inventorySlots+=tile.getListOrEmpty("Items").size();
                if(type.contains("sign"))signChecks++;
                tileChecks++;
            }
            richest.add(new SourceArea(cx,cz,List.copyOf(samples),Map.copyOf(directional)));
            richest.sort(Comparator.comparingInt((SourceArea area)->area.directional().size()).reversed());
            if(richest.size()>12)richest.remove(richest.size()-1);
            areas++;
            if(areas%32==0)GreatSea.LOGGER.info("EMPIRE_VERIFY {} / {} source chunk areas; {} block entities checked",areas,widthChunks*depthChunks,tileChecks);
        }
        check(areas==1088,"Not every empire source chunk area was checked: "+areas);
        check(sourceSolids==schematic.nonAirBlocks,"Source non-air total differs: "+sourceSolids);
        check(tileChecks+clippedTiles==schematic.blockEntities,"Not every source block entity was accounted for: "+tileChecks+" retained + "+clippedTiles+" below world bottom");
        check(airChecks>0&&solidChecks>0,"Missing solid/air world samples");
        report.add("PASS +X empire: all "+areas+" source chunk areas loaded as actual FULL chunks; "+solidChecks+" non-air and "+airChecks+" air voxel samples match exact source states");
        report.add("PASS complete source scan: "+sourceSolids+" source non-air blocks; "+retainedSolids+" retained at world Y=-64..251 and "+(sourceSolids-retainedSolids)+" deep underground blocks below the world floor; horizontal footprint remains exact 506 x 530 with no scaled or wrapped import");

        int directionalPalette=0;
        for(var sample:paletteSamples.values()) {
            var expected=checkState(world,0,sample);
            if(directional(expected))directionalPalette++;
        }
        report.add("PASS +X every retained palette state: "+paletteSamples.size()+" exact block-state checks, including "+directionalPalette+" directional states");
        report.add("PASS +X all "+tileChecks+" retained block entities preserve canonical source data; "+clippedTiles+" deep underground source block entities excluded below world Y=-64; "+inventorySlots+" retained source inventory slots and "+signChecks+" signs; types="+tileTypeCounts);

        for(int face=1;face<4;face++) {
            int voxels=0,directions=0;
            for(var area:richest) {
                for(var sample:area.samples()) {checkState(world,face,sample);voxels++;}
                for(var sample:area.directional().values()) {checkState(world,face,sample);voxels++;directions++;}
            }
            for(var tile:functionalTiles.values())verifyTile(world,face,tile);
            report.add("PASS face "+face+": "+richest.size()+" representative source chunk areas, "+voxels+" actual mirrored/rotated voxel checks ("+directions+" directional), and canonical block entity data for all "+functionalTiles.size()+" source types");
        }
        verifyElevation(world,highestSolid,report);
        verifyZeroRoute(world,report);
        if(Boolean.getBoolean("unstableterrain.previewEmpire"))EmpirePerspective.render(world,report);
    }

    private static void verifyElevation(ServerWorld world,Sample highest,List<String> report) {
        check(highest!=null&&highest.y()==FarlandsEmpire.SAMPLE.height-1,"Source's highest retained solid is not at its original top");
        int empireTop=FarlandsEmpire.BASE_Y+highest.y();
        check(empireTop==251,"Empire roof is not at Y=251: "+empireTop);
        int skyChecks=0,wallChecks=0;
        for(int face=0;face<4;face++) {
            check(!checkState(world,face,highest).isAir(),"Highest empire block missing on face "+face);
            // Every source area supplies a cleared-sky probe. This catches an old
            // native repeating roof left above the lowered imported landmark.
            for(int cz=0;cz<(FarlandsEmpire.SAMPLE.depth+15)/16;cz+=face==0?1:8)for(int cx=0;cx<(FarlandsEmpire.SAMPLE.width+15)/16;cx++) {
                int a=Math.min(cx*16+8,FarlandsEmpire.SAMPLE.width-1),b=Math.min(cz*16+8,FarlandsEmpire.SAMPLE.depth-1);
                var pos=FarlandsEmpire.position(face,a,252,b);
                world.getChunk(pos.getX()>>4,pos.getZ()>>4);
                for(int y:new int[]{252,255,256,319}) {
                    check(world.getBlockState(new BlockPos(pos.getX(),y,pos.getZ())).isAir(),"Native terrain remained above lowered empire on face "+face+" at "+pos.getX()+","+y+","+pos.getZ());
                    skyChecks++;
                }
                // The +X town has already been exhaustively loaded. Other faces
                // use a smaller bounded set rather than generating four empires.
                if(face>0)break;
            }
            int maximumWall=-65;
            for(int a=-16;a<0;a++) {
                var pos=FarlandsEmpire.position(face,a,0,FarlandsEmpire.SAMPLE.depth/2);
                world.getChunk(pos.getX()>>4,pos.getZ()>>4);
                int top=world.getBottomY()-1;
                for(int y=world.getTopYInclusive();y>=world.getBottomY();y--)if(!world.getBlockState(new BlockPos(pos.getX(),y,pos.getZ())).isAir()){top=y;break;}
                maximumWall=Math.max(maximumWall,top);wallChecks++;
            }
            check(maximumWall==255,"Adjacent repeating wall top changed on face "+face+": "+maximumWall);
            check(maximumWall-empireTop==4,"Empire is not four blocks below the repeating wall on face "+face);
        }
        report.add("PASS empire elevation on four faces: highest original source block at Y=251, adjacent repeating wall top Y=255, exact four-block clearance; "+skyChecks+" actual cleared-sky probes and "+wallChecks+" wall-column probes");
    }

    private static boolean directional(BlockState state) {
        for(var property:state.getProperties()) {
            String name=property.getName();
            if(name.equals("facing")||name.equals("axis")||name.equals("rotation")||name.equals("shape")||name.equals("hinge")||
                    name.equals("north")||name.equals("south")||name.equals("east")||name.equals("west"))return true;
        }
        return false;
    }

    private static BlockState checkState(ServerWorld world,int face,Sample sample) {
        var pos=FarlandsEmpire.position(face,sample.a(),FarlandsEmpire.BASE_Y+sample.y(),sample.b());
        check(FarlandsEmpire.contains(pos.getX(),pos.getY(),pos.getZ()),"Sample falls outside empire: "+pos);
        check(FarlandsOrbitals.face(pos.getX(),pos.getZ())==face,"Projection changed face: "+pos);
        check(FarlandsEmpire.a(pos.getX(),pos.getZ())==sample.a()&&FarlandsEmpire.b(pos.getX(),pos.getZ())==sample.b(),"Projection round trip differs: "+pos);
        var original=FarlandsEmpire.SAMPLE.state(sample.a(),sample.y(),sample.b());
        var expected=FarlandsOrbitals.orient(original,face);
        if(expected.isOf(net.minecraft.block.Blocks.ANCIENT_DEBRIS)||ForbiddenWorldBlocks.ominousVault(expected))expected=net.minecraft.block.Blocks.AIR.getDefaultState();
        expected=GeneratedIcePolicy.generated(expected,false,pos.getY());
        world.getChunk(pos.getX()>>4,pos.getZ()>>4);
        var actual=world.getBlockState(pos);
        check(actual.equals(expected),"Empire block differs on face "+face+" at "+pos+"; expected "+expected+", actual "+actual+", source palette "+sample.palette());
        return expected;
    }

    private static void verifyTile(ServerWorld world,int face,NbtCompound source) {
        int a=source.getInt("x",0),y=source.getInt("y",0),b=source.getInt("z",0);
        var pos=FarlandsEmpire.position(face,a,FarlandsEmpire.BASE_Y+y,b);
        check(FarlandsEmpire.contains(pos.getX(),pos.getY(),pos.getZ()),"Source block entity outside import at "+pos);
        checkState(world,face,new Sample(a,y,b,FarlandsEmpire.SAMPLE.index(a,y,b)));
        var nbt=source.copy();nbt.putInt("x",pos.getX());nbt.putInt("y",pos.getY());nbt.putInt("z",pos.getZ());
        var actual=world.getBlockEntity(pos);
        var expected=BlockEntity.createFromNbt(pos,world.getBlockState(pos),nbt,world.getRegistryManager());
        check(actual!=null&&expected!=null,"Missing empire block entity on face "+face+" at "+pos+" / "+source.getString("id",""));
        var actualNbt=actual.createNbtWithIdentifyingData(world.getRegistryManager());
        var expectedNbt=expected.createNbtWithIdentifyingData(world.getRegistryManager());
        check(actualNbt.equals(expectedNbt),"Empire block entity source data differs on face "+face+" at "+pos+" / "+source.getString("id","")+"; source keys "+expectedNbt.getKeys()+", actual keys "+actualNbt.getKeys());
    }

    private static void verifyZeroRoute(ServerWorld world,List<String> report) {
        int probes=0;
        for(int face=0;face<4;face++)for(int a:new int[]{-1,0,FarlandsEmpire.SAMPLE.width/2,FarlandsEmpire.SAMPLE.width-1,FarlandsEmpire.SAMPLE.width}) {
            int outward=GreatSea.FARLANDS.entryBoundary+FarlandsEmpire.origin()+a;
            int x=face<2?(face==1?-outward:outward):0,z=face<2?0:(face==3?-outward:outward);
            check(!FarlandsEmpire.footprint(x,z),"Empire overlaps tangent zero on face "+face);
            world.getChunk(x>>4,z>>4);
            for(int y:new int[]{-64,-20,0,64,128,192,256,319}) {
                var pos=new BlockPos(x,y,z);
                var expected=FarlandsGeneration.state(world.getSeed(),x,y,z);
                check(world.getBlockState(pos).equals(expected),"Straight repeating route changed at "+pos);
                probes++;
            }
        }
        report.add("PASS straight zero-axis route untouched: "+probes+" actual block checks on four faces; nearest empire edge tangent="+FarlandsEmpire.NEAREST_TANGENT+", opposite edge="+FarlandsEmpire.tangentOrigin());
    }
}
