package dev.greatsea;

import com.google.gson.*;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.world.LightType;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.biome.GrassColors;
import net.minecraft.world.biome.FoliageColors;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.stream.IntStream;
import java.util.zip.ZipFile;

/** Development render of native FULL chunk states with captured native light levels.
 * Block models, their native textures and ray/outline intersections follow the
 * Empire renderer. This is an annotated data view, never a client screenshot. */
public final class SpokelandsPerspective {
    private static final int MIN_Y=-64,HEIGHT=384,SIZE=256,IMAGE_W=1920,IMAGE_H=1080;
    private static final String[] DIRECTIONS={"west","east","down","up","north","south"};
    private static final int[][] NORMALS={{-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}};
    private record Face(int[] pixels,int tint){}
    private record Material(Box[] boxes,Face[] faces,boolean cube,String name,int emission){}
    private record Camera(String file,String title,double x,double y,double z,double targetX,double targetY,double targetZ,double fov){}
    private record Volume(int sx,int sz,char[] cells,byte[] light,Material[] materials,List<BlockState> states,long[] counts,
                          long blocks,int[] grass,int[] foliage,int[] water,int sky,int fog,JsonObject metadata){}
    private final Map<String,JsonObject> jsons=new HashMap<>();
    private final Map<String,int[]> textures=new HashMap<>();
    private final Set<String> fallbacks=new TreeSet<>();
    private final JsonObject colorMaps=new JsonObject();
    private final ZipFile assets;
    private SpokelandsPerspective(ZipFile assets)throws Exception{
        this.assets=assets;
        // A dedicated server never runs the client's colormap resource reload.
        // Initialise the same vanilla samplers from the actual client assets so
        // native biome overrides and grass-color modifiers remain authoritative.
        GrassColors.setColorMap(colorMap("grass"));
        FoliageColors.setColorMap(colorMap("foliage"));
    }
    private int[] colorMap(String name)throws Exception{
        String path="assets/minecraft/textures/colormap/"+name+".png";
        var entry=assets.getEntry(path);if(entry==null)throw new IllegalStateException("Missing native colormap "+path);
        byte[] bytes;try(var stream=assets.getInputStream(entry)){bytes=stream.readAllBytes();}
        var image=ImageIO.read(new java.io.ByteArrayInputStream(bytes));
        if(image==null||image.getWidth()!=256||image.getHeight()!=256)throw new IllegalStateException("Unexpected native colormap dimensions: "+path);
        var metadata=new JsonObject();metadata.addProperty("asset",path);
        metadata.addProperty("sha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        colorMaps.add(name,metadata);return image.getRGB(0,0,256,256,null,0,256);
    }

    public static void render(ServerWorld world,List<String> report)throws Exception{
        Path directory=Path.of("spokelands-perspective");Files.createDirectories(directory);
        Path assetJar=Path.of("../gradle-home/caches/fabric-loom/1.21.11/minecraft-client.jar");
        if(!Files.isRegularFile(assetJar))assetJar=Path.of("../gradle-home-115/caches/fabric-loom/1.21.11/minecraft-client.jar");
        try(var zip=new ZipFile(assetJar.toFile())){new SpokelandsPerspective(zip).renderWorld(world,directory,report,assetJar);}
    }
    private void renderWorld(ServerWorld world,Path directory,List<String> report,Path assetJar)throws Exception{
        var provenance=new JsonObject();provenance.addProperty("kind","Native FULL chunk block-data renderer");
        provenance.addProperty("sourceAssetJar",assetJar.toAbsolutePath().normalize().toString());
        provenance.addProperty("seed",world.getSeed());provenance.addProperty("entryBoundary",GreatSea.FARLANDS.entryBoundary);
        provenance.addProperty("minecraft","1.21.11");provenance.addProperty("imageWidth",IMAGE_W);provenance.addProperty("imageHeight",IMAGE_H);
        provenance.addProperty("lighting","Actual SKY/BLOCK levels sampled from the native world. Face shading, gamma, fog, sky background and cutout/translucency compositing are approximations, not the client lightmap or shaders.");
        provenance.addProperty("label","Generated block-data view; lighting approximation, not client screenshot");
        provenance.add("nativeColorMaps",colorMaps);
        provenance.addProperty("tintMethod","Native Biome.getGrassColorAt/getFoliageColor after loading the vanilla client grass/foliage colormaps; native biome overrides/modifiers and constant birch/spruce foliage colors are retained.");
        var frames=new JsonArray();var volumes=new JsonArray();
        String selected=System.getProperty("unstableterrain.spokelandsPreviewRegions","");
        for(var region:SpokelandsTerrain.Region.values()){
            if(!selected.isEmpty()&&!Arrays.asList(selected.split(",")).contains(region.name()))continue;
            int radial=GreatSea.FARLANDS.entryBoundary+SpokelandsTerrain.start(region)+GreatSea.FARLANDS.spokelandsWidths[region.ordinal()]/2;
            int centerZ=-radial;String stem=String.format(Locale.ROOT,"%02d-%s",region.ordinal()+1,SpokelandsCommand.name(region).replace('_','-'));
            Volume volume=capture(world,0,centerZ,region,"middle");volumes.add(volume.metadata());
            overview(volume,region,directory,stem);
            Camera panoramic=panorama(world,volume,region,centerZ,stem+"-02-panorama.png");
            if(region==SpokelandsTerrain.Region.CLASSIC){
                // The interior survey stays at this region's midpoint, while the
                // separate frontal capture crosses the actual entrance boundary.
                save(volume,panoramic,region,directory,frames,report);
                Volume front=capture(world,0,-GreatSea.FARLANDS.entryBoundary,region,"entrance-front");volumes.add(front.metadata());
                var camera=new Camera(stem+"-01-front-wall.png","Spokelands Plains: entrance wall",0,145,-GreatSea.FARLANDS.entryBoundary+70,
                    0,170,-GreatSea.FARLANDS.entryBoundary-30,74);
                save(front,camera,region,directory,frames,report);
            }else{
                save(volume,walkup(world,volume,region,centerZ,stem+"-01-walkup.png"),region,directory,frames,report);
                save(volume,panoramic,region,directory,frames,report);
            }
        }
        provenance.add("volumes",volumes);provenance.add("frames",frames);provenance.add("textureFallbacks",new Gson().toJsonTree(fallbacks));
        provenance.addProperty("textureSource","Minecraft client JAR blockstates and model inheritance; first native animation/weighted-model frame; native outline boxes for slabs, stairs, fences and plant bounds.");
        Files.writeString(directory.resolve("provenance.json"),new GsonBuilder().setPrettyPrinting().create().toJson(provenance)+"\n");
        report.add("Spokelands native perspectives: "+frames.size()+" annotated 1920x1080 images; texture fallback block types="+fallbacks);
    }
    private Volume capture(ServerWorld world,int centerX,int centerZ,SpokelandsTerrain.Region region,String purpose)throws Exception{
        int sx=Math.floorDiv(centerX-SIZE/2,16)*16,sz=Math.floorDiv(centerZ-SIZE/2,16)*16;
        char[] cells=new char[SIZE*SIZE*HEIGHT];byte[] light=new byte[cells.length];
        int[] grass=new int[SIZE*SIZE],foliage=new int[SIZE*SIZE],water=new int[SIZE*SIZE];
        var states=new ArrayList<BlockState>();var ids=new HashMap<BlockState,Character>();long[] counts=new long[65536];
        states.add(Blocks.AIR.getDefaultState());ids.put(states.getFirst(),(char)0);long blocks=0;var position=new BlockPos.Mutable();
        var skyView=world.getLightingProvider().get(LightType.SKY);var blockView=world.getLightingProvider().get(LightType.BLOCK);
        for(int z=0;z<SIZE;z+=16)for(int x=0;x<SIZE;x+=16){
            var chunk=world.getChunk((sx+x)>>4,(sz+z)>>4);
            for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++){
                int bx=sx+x+dx,bz=sz+z+dz,column=(z+dz)*SIZE+x+dx;
                var biome=world.getBiome(position.set(bx,80,bz)).value();grass[column]=biome.getGrassColorAt(bx,bz);foliage[column]=biome.getFoliageColor();water[column]=biome.getWaterColor();
            }
            for(int y=MIN_Y;y<MIN_Y+HEIGHT;y++)for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++){
                position.set(sx+x+dx,y,sz+z+dz);int index=((y-MIN_Y)*SIZE+z+dz)*SIZE+x+dx;
                light[index]=(byte)(skyView.getLightLevel(position)|(blockView.getLightLevel(position)<<4));
                var state=chunk.getBlockState(position);Character id=ids.get(state);
                if(id==null){if(states.size()>=65535)throw new IllegalStateException("Too many preview block states");id=(char)states.size();ids.put(state,id);states.add(state);}
                cells[index]=id;counts[id]++;if(!state.isAir())blocks++;
            }
            if(x==0&&z%64==0)GreatSea.LOGGER.info("SPOKELANDS_PREVIEW_CAPTURE {} {} {}/{} rows",region,purpose,z,SIZE);
        }
        Material[] palette=new Material[states.size()];for(int i=1;i<palette.length;i++)palette[i]=material(states.get(i));
        var biome=world.getBiome(new BlockPos(centerX,170,centerZ)).value();
        var skyEntry=biome.getEnvironmentAttributes().getEntry(EnvironmentAttributes.SKY_COLOR_VISUAL);
        var fogEntry=biome.getEnvironmentAttributes().getEntry(EnvironmentAttributes.FOG_COLOR_VISUAL);
        int sky=skyEntry==null?EnvironmentAttributes.SKY_COLOR_VISUAL.getDefaultValue():skyEntry.apply(EnvironmentAttributes.SKY_COLOR_VISUAL.getDefaultValue());
        int fog=fogEntry==null?EnvironmentAttributes.FOG_COLOR_VISUAL.getDefaultValue():fogEntry.apply(EnvironmentAttributes.FOG_COLOR_VISUAL.getDefaultValue());
        int nativeSky=sky;String skyMode="Native biome sky-color attribute; client atmosphere and time-of-day rendering are approximations";
        if((sky&0xffffff)==0)skyMode+="; biome supplies a black sky and the data view preserves that value";
        var metadata=new JsonObject();metadata.addProperty("region",region.name());metadata.addProperty("purpose",purpose);
        metadata.addProperty("startX",sx);metadata.addProperty("startZ",sz);metadata.addProperty("width",SIZE);metadata.addProperty("depth",SIZE);
        metadata.addProperty("minimumY",MIN_Y);metadata.addProperty("height",HEIGHT);metadata.addProperty("fullChunks",SIZE/16*SIZE/16);
        metadata.addProperty("nonAirBlocks",blocks);metadata.addProperty("uniqueStates",states.size());metadata.addProperty("stateVolumeSha256",digest(cells));
        metadata.addProperty("nativePackedLightSha256",HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(light)));
        metadata.addProperty("nativeBiomeSkyRgb",String.format(Locale.ROOT,"#%06x",nativeSky&0xffffff));metadata.addProperty("skyBackgroundMode",skyMode);
        metadata.addProperty("approximateSkyRgb",String.format(Locale.ROOT,"#%06x",sky&0xffffff));metadata.addProperty("biomeFogRgb",String.format(Locale.ROOT,"#%06x",fog&0xffffff));
        metadata.addProperty("grassTintColors",Arrays.stream(grass).distinct().count());metadata.addProperty("foliageTintColors",Arrays.stream(foliage).distinct().count());
        metadata.addProperty("sampleGrassTintRgb",String.format(Locale.ROOT,"#%06x",grass[SIZE*SIZE/2]&0xffffff));metadata.addProperty("sampleFoliageTintRgb",String.format(Locale.ROOT,"#%06x",foliage[SIZE*SIZE/2]&0xffffff));
        var blockCounts=new TreeMap<String,Long>();for(int i=0;i<states.size();i++)blockCounts.merge(Registries.BLOCK.getId(states.get(i).getBlock()).toString(),counts[i],Long::sum);
        metadata.add("blockCounts",new Gson().toJsonTree(blockCounts));
        return new Volume(sx,sz,cells,light,palette,List.copyOf(states),counts,blocks,grass,foliage,water,sky,fog,metadata);
    }
    private static String digest(char[] cells)throws Exception{
        var digest=MessageDigest.getInstance("SHA-256");byte[] buffer=new byte[8192];int used=0;
        for(char cell:cells){buffer[used++]=(byte)cell;buffer[used++]=(byte)(cell>>>8);if(used==buffer.length){digest.update(buffer);used=0;}}
        if(used>0)digest.update(buffer,0,used);return HexFormat.of().formatHex(digest.digest());
    }
    private static int id(Volume v,int x,int y,int z){
        int ax=x-v.sx(),az=z-v.sz();return ax<0||ax>=SIZE||az<0||az>=SIZE||y<MIN_Y||y>=MIN_Y+HEIGHT?0:v.cells()[((y-MIN_Y)*SIZE+az)*SIZE+ax];
    }
    private static boolean clear(Volume v,int x,int y,int z){return id(v,x,y,z)==0&&id(v,x,y+1,z)==0;}
    private static boolean inside(Volume v,double x,double z,int margin){return x>=v.sx()+margin&&x<v.sx()+SIZE-margin&&z>=v.sz()+margin&&z<v.sz()+SIZE-margin;}
    private static boolean obstructed(Volume v,double x,double y,double z){
        int bx=(int)Math.floor(x),by=(int)Math.floor(y),bz=(int)Math.floor(z),id=id(v,bx,by,bz);
        if(id==0)return false;Material material=v.materials()[id];
        // Camera surveys ignore the narrow crossed planes of grass/flowers,
        // but use actual outline boxes for trunks, canopy, rails and terrain.
        if(material.name().contains("grass")&&!material.name().equals("grass_block")||material.name().endsWith("_roots")||material.name().equals("crimson_fungus")||material.name().equals("warped_fungus"))return false;
        double lx=x-bx,ly=y-by,lz=z-bz;
        for(Box box:material.boxes())if(lx>=box.minX&&lx<=box.maxX&&ly>=box.minY&&ly<=box.maxY&&lz>=box.minZ&&lz<=box.maxZ)return true;
        return false;
    }
    private static int mapColor(Volume volume,int state,int column){
        if(state==0)return 0x172333;
        var material=volume.materials()[state];var face=material.faces()[3];
        if(face==null)for(var candidate:material.faces())if(candidate!=null){face=candidate;break;}
        if(face==null)return 0x80756b;
        long r=0,g=0,b=0,count=0;for(int rgb:face.pixels())if((rgb>>>24)>100){r+=rgb>>16&255;g+=rgb>>8&255;b+=rgb&255;count++;}
        if(count==0)return 0x172333;int tint=face.tint()==0x70b049?blockTint(material.name(),volume,column):face.tint();
        return ((int)(r/count)*(tint>>16&255)/255<<16)|((int)(g/count)*(tint>>8&255)/255<<8)|((int)(b/count)*(tint&255)/255);
    }
    private static void overview(Volume volume,SpokelandsTerrain.Region region,Path directory,String stem)throws Exception{
        var top=new BufferedImage(SIZE*3,SIZE*3,BufferedImage.TYPE_INT_RGB);
        var section=new BufferedImage(SIZE*3,HEIGHT*3,BufferedImage.TYPE_INT_RGB);
        for(int z=0;z<SIZE;z++)for(int x=0;x<SIZE;x++){
            int y=319;while(y>=MIN_Y&&volume.states().get(id(volume,volume.sx()+x,y,volume.sz()+z)).isAir())y--;
            int color=mapColor(volume,id(volume,volume.sx()+x,y,volume.sz()+z),z*SIZE+x);
            double shade=.74+.26*(y-MIN_Y)/(double)HEIGHT;
            color=((int)((color>>16&255)*shade)<<16)|((int)((color>>8&255)*shade)<<8)|(int)((color&255)*shade);
            for(int dy=0;dy<3;dy++)for(int dx=0;dx<3;dx++)top.setRGB(x*3+dx,z*3+dy,color);
        }
        for(int y=MIN_Y;y<320;y++)for(int x=0;x<SIZE;x++){
            int color=mapColor(volume,id(volume,volume.sx()+x,y,volume.sz()+SIZE/2),SIZE/2*SIZE+x);
            for(int dy=0;dy<3;dy++)for(int dx=0;dx<3;dx++)section.setRGB(x*3+dx,(319-y)*3+dy,color);
        }
        annotate(top,SpokelandsCommand.label(region)+": native block overview","256 x 256 blocks; three pixels per block; height shading");
        annotate(section,SpokelandsCommand.label(region)+": exact middle section","Y=-64..319; each 3 x 3 square is one generated block; technical cutaway");
        ImageIO.write(top,"png",directory.resolve(stem+"-03-topdown.png").toFile());
        ImageIO.write(section,"png",directory.resolve(stem+"-04-section.png").toFile());
    }
    private static boolean cameraBodyClear(Volume v,int x,int floor,int z){
        if(!inside(v,x,z,12)||!clear(v,x,floor+2,z))return false;
        for(double dx:new double[]{-.35,.35})for(double dz:new double[]{-.35,.35})
            if(obstructed(v,x+.5+dx,floor+1.2,z+.5+dz)||obstructed(v,x+.5+dx,floor+2.6,z+.5+dz))return false;
        return true;
    }
    private static double rayClearance(Volume v,double x,double y,double z,double yaw,double pitch){
        double cp=Math.cos(pitch),dx=cp*Math.cos(yaw),dy=Math.sin(pitch),dz=cp*Math.sin(yaw);
        for(double distance=1;distance<=42;distance+=.75){
            double sx=x+dx*distance,sy=y+dy*distance,sz=z+dz*distance;
            if(!inside(v,sx,sz,2)||sy<MIN_Y||sy>=MIN_Y+HEIGHT)return distance;
            if(obstructed(v,sx,sy,sz))return distance;
        }
        return 42;
    }
    private static double[] viewClearance(Volume volume,Camera camera){
        double dx=camera.targetX()-camera.x(),dz=camera.targetZ()-camera.z();
        double yaw=Math.atan2(dz,dx),pitch=Math.atan2(camera.targetY()-camera.y(),Math.hypot(dx,dz));
        double minimum=42,total=0;int rays=0;
        for(int horizontal:new int[]{-20,-10,0,10,20})for(int vertical:new int[]{-7,0,9}){
            double distance=rayClearance(volume,camera.x(),camera.y(),camera.z(),yaw+Math.toRadians(horizontal),pitch+Math.toRadians(vertical));
            minimum=Math.min(minimum,distance);total+=distance;rays++;
        }
        return new double[]{minimum,total/rays};
    }
    private static Camera walkingCamera(ServerWorld world,Volume volume,SpokelandsTerrain.Region region,int centerZ,String file){
        int wantedX=0,wantedZ=centerZ+76;Camera best=null;double bestScore=-Double.MAX_VALUE;
        for(int dz=-64;dz<=24;dz+=4)for(int dx=-64;dx<=64;dx+=4){
            int x=wantedX+dx,z=wantedZ+dz,ground=SpokelandsTerrain.ground(world.getSeed(),x,z);
            if(region==SpokelandsTerrain.Region.SAND_LANDS){
                // The highest surface can be the roof of a huge natural arch.
                // Show the supported walking floor beneath it, as in the film.
                ground=-65;
                for(int y=118;y<=180;y++)if(id(volume,x,y,z)!=0&&cameraBodyClear(volume,x,y,z)){
                    boolean headroom=true;for(int dy=1;dy<=12;dy++)if(id(volume,x,y+dy,z)!=0){headroom=false;break;}
                    if(headroom){ground=y;break;}
                }
                if(ground==-65)continue;
            }
            if(!cameraBodyClear(volume,x,ground,z)||id(volume,x,ground,z)==0)continue;
            double eyeY=ground+2.62;
            for(int turn:new int[]{0,-22,22,-44,44}){
                double yaw=Math.toRadians(-90+turn),targetX=x+.5+120*Math.cos(yaw),targetZ=z+.5+120*Math.sin(yaw),targetY=ground+6;
                if(region==SpokelandsTerrain.Region.WINDING)targetY=eyeY+65;
                if(region==SpokelandsTerrain.Region.SCULK_FOREST)targetY=eyeY+45;
                if(region==SpokelandsTerrain.Region.FLOWER_HILLS)targetY=ground+1;
                if(region==SpokelandsTerrain.Region.SAND_LANDS)targetY=eyeY+48;
                var candidate=new Camera(file,SpokelandsCommand.label(region)+": generated walkup",x+.5,eyeY,z+.5,targetX,targetY,targetZ,76);
                double[] open=viewClearance(volume,candidate);
                double score=open[0]*1.8+open[1]-.075*Math.hypot(dx,dz)-.05*Math.abs(turn);
                if(score>bestScore){bestScore=score;best=candidate;}
            }
        }
        if(best==null)throw new IllegalStateException("No supported native walkup camera with clear body in "+region);
        return best;
    }
    private static Camera walkup(ServerWorld world,Volume volume,SpokelandsTerrain.Region region,int centerZ,String file){
        if(region==SpokelandsTerrain.Region.BRIDGES){
            Camera deck=findDeck(volume,centerZ,file);if(deck!=null)return deck;
            // Explicitly labelled fallback if this volume happens not to include
            // an accessible bridge deck, rather than standing inside its pier.
            return new Camera(file,SpokelandsCommand.label(region)+": elevated survey (no accessible deck)",0,310,centerZ+90,0,162,centerZ-40,76);
        }
        return walkingCamera(world,volume,region,centerZ,file);
    }
    private static boolean plank(Volume volume,int x,int y,int z){int block=id(volume,x,y,z);return block!=0&&volume.states().get(block).isOf(Blocks.OAK_PLANKS);}
    private static Camera findDeck(Volume volume,int centerZ,String file){
        Camera best=null;double bestScore=-Double.MAX_VALUE;
        for(int z=centerZ-24;z<=centerZ+96;z+=2)for(int x=-80;x<=80;x+=2){
            if(!inside(volume,x,z,12))continue;
            for(int y=310;y>=175;y--){
                if(!plank(volume,x,y,z)||!cameraBodyClear(volume,x,y,z))continue;
                boolean alongX=plank(volume,x,y,z-1)&&plank(volume,x,y,z+1),alongZ=plank(volume,x-1,y,z)&&plank(volume,x+1,y,z);
                if(!alongX&&!alongZ)continue;
                for(int turn:new int[]{-90,0,90,180}){
                    if(turn%180==0&&!alongX||turn%180!=0&&!alongZ)continue;
                    double yaw=Math.toRadians(turn),targetX=x+.5+120*Math.cos(yaw),targetZ=z+.5+120*Math.sin(yaw);
                    var candidate=new Camera(file,"Spokelands Bridges: supported bridge-deck walkup",x+.5,y+2.62,z+.5,targetX,y-13,targetZ,76);
                    double[] open=viewClearance(volume,candidate);
                    double score=open[0]*1.8+open[1]-.04*Math.hypot(x,z-centerZ-76);
                    if(score>bestScore){bestScore=score;best=candidate;}
                }
                break;
            }
        }
        return best;
    }
    private static Camera panorama(ServerWorld world,Volume volume,SpokelandsTerrain.Region region,int centerZ,String file){
        int ground=SpokelandsTerrain.ground(world.getSeed(),-100,centerZ+100);
        double y=ground+80,target=SpokelandsTerrain.ground(world.getSeed(),20,centerZ-20)+14;
        if(region==SpokelandsTerrain.Region.CLASSIC){y=304;target=177;}
        if(region==SpokelandsTerrain.Region.BRIDGES){y=308;target=162;}
        if(region==SpokelandsTerrain.Region.SCULK_FOREST){y=Math.min(315,ground+127);target=ground+57;}
        if(region==SpokelandsTerrain.Region.WINDING){y=ground+116;target=ground+71;}
        if(region==SpokelandsTerrain.Region.SAND_LANDS){y=334;target=180;}
        // Do not place the observer inside an overhanging canopy or terrain.
        while(y<MIN_Y+HEIGHT+12&&obstructed(volume,-103.5,y,centerZ+104.5))y+=4;
        return new Camera(file,SpokelandsCommand.label(region)+": oblique panorama",-104,y,centerZ+104,25,target,centerZ-35,78);
    }
    private void save(Volume volume,Camera camera,SpokelandsTerrain.Region region,Path directory,JsonArray frames,List<String> report)throws Exception{
        var image=raycast(volume,camera);annotate(image,camera.title(),"Generated block-data view; lighting approximation, not client screenshot");
        ImageIO.write(image,"png",directory.resolve(camera.file()).toFile());
        var frame=new JsonObject();frame.addProperty("file",camera.file());frame.addProperty("title",camera.title());frame.addProperty("region",region.name());
        frame.addProperty("capturePurpose",volume.metadata().get("purpose").getAsString());frame.add("cameraWorld",new Gson().toJsonTree(new double[]{camera.x(),camera.y(),camera.z()}));
        frame.add("targetWorld",new Gson().toJsonTree(new double[]{camera.targetX(),camera.targetY(),camera.targetZ()}));frame.addProperty("fovDegrees",camera.fov());
        double[] open=viewClearance(volume,camera);frame.addProperty("minimumForwardViewClearance",open[0]);frame.addProperty("meanForwardViewClearance",open[1]);
        frame.addProperty("nativeFullChunks",SIZE/16*SIZE/16);frame.addProperty("stateVolumeSha256",volume.metadata().get("stateVolumeSha256").getAsString());frames.add(frame);
        report.add("Spokelands perspective "+camera.file()+": native FULL256x256x384 volume, camera="+camera.x()+","+camera.y()+","+camera.z());
        GreatSea.LOGGER.info("SPOKELANDS_PERSPECTIVE_PASS {}",camera.file());
    }
    private JsonObject json(String path) throws Exception {
        JsonObject cached=jsons.get(path);if(cached!=null)return cached;
        var entry=assets.getEntry(path);if(entry==null)return new JsonObject();
        try(var reader=new InputStreamReader(assets.getInputStream(entry),StandardCharsets.UTF_8)){
            var result=JsonParser.parseReader(reader).getAsJsonObject();jsons.put(path,result);return result;
        }
    }
    private JsonObject model(String name) throws Exception {
        String path=resource(name,"models/")+".json";
        var source=json(path);var result=new JsonObject();
        if(source.has("parent")&&!source.get("parent").getAsString().startsWith("builtin/"))result=model(source.get("parent").getAsString()).deepCopy();
        for(var entry:source.entrySet()){
            if(entry.getKey().equals("textures")){
                JsonObject merged=result.has("textures")?result.getAsJsonObject("textures"):new JsonObject();
                for(var texture:entry.getValue().getAsJsonObject().entrySet())merged.add(texture.getKey(),texture.getValue());
                result.add("textures",merged);
            }else result.add(entry.getKey(),entry.getValue());
        }
        return result;
    }
    private static String resource(String name,String prefix){
        int colon=name.indexOf(':');String ns=colon<0?"minecraft":name.substring(0,colon),path=colon<0?name:name.substring(colon+1);
        return "assets/"+ns+"/"+prefix+path;
    }
    private int[] texture(String name) throws Exception {
        int[] cached=textures.get(name);if(cached!=null)return cached;
        var entry=assets.getEntry(resource(name,"textures/")+".png");
        if(entry==null)return null;
        var image=ImageIO.read(assets.getInputStream(entry));int[] pixels=new int[256];
        // Animated textures use their first native 16x16 frame.
        image.getRGB(0,0,16,16,pixels,0,16);textures.put(name,pixels);return pixels;
    }
    private static String resolve(String ref,JsonObject textures){
        for(int i=0;i<32&&ref.startsWith("#");i++){
            String key=ref.substring(1);if(!textures.has(key))return "";ref=textures.get(key).getAsString();
        }
        return ref.startsWith("#")?"":ref;
    }
    private static String property(BlockState state,String name){
        for(var entry:state.getEntries().entrySet())if(entry.getKey().getName().equals(name))return value(entry.getKey(),entry.getValue());
        return "";
    }
    @SuppressWarnings({"unchecked","rawtypes"})
    private static String value(Property property,Comparable value){return property.name(value);}
    private static boolean condition(BlockState state,JsonObject condition){
        for(var entry:condition.entrySet()){
            if(entry.getKey().equals("OR")){boolean any=false;for(var term:entry.getValue().getAsJsonArray())any|=condition(state,term.getAsJsonObject());if(!any)return false;}
            else if(entry.getKey().equals("AND")){for(var term:entry.getValue().getAsJsonArray())if(!condition(state,term.getAsJsonObject()))return false;}
            else if(!Arrays.asList(entry.getValue().getAsString().split("\\|")).contains(property(state,entry.getKey())))return false;
        }
        return true;
    }
    private static boolean variant(BlockState state,String variant){
        if(variant.isEmpty())return true;
        for(String test:variant.split(",")){String[] pair=test.split("=",2);if(pair.length!=2||!pair[1].equals(property(state,pair[0])))return false;}
        return true;
    }
    private static JsonObject first(JsonElement element){return element.isJsonArray()?element.getAsJsonArray().get(0).getAsJsonObject():element.getAsJsonObject();}
    private static int rotatedDirection(int side,int x,int y){
        int[][] vectors={{-1,0,0},{1,0,0},{0,-1,0},{0,1,0},{0,0,-1},{0,0,1}};
        int vx=vectors[side][0],vy=vectors[side][1],vz=vectors[side][2];
        for(int i=0;i<Math.floorMod(x,360)/90;i++){int old=vy;vy=vz;vz=-old;}
        for(int i=0;i<Math.floorMod(y,360)/90;i++){int old=vx;vx=-vz;vz=old;}
        for(int i=0;i<vectors.length;i++)if(vx==vectors[i][0]&&vy==vectors[i][1]&&vz==vectors[i][2])return i;
        return side;
    }
    private Material material(BlockState state) throws Exception {
        String name=Registries.BLOCK.getId(state.getBlock()).getPath();
        Face[] faces=new Face[6];var blockstate=json("assets/minecraft/blockstates/"+name+".json");var choices=new ArrayList<JsonObject>();
        if(blockstate.has("variants"))for(var entry:blockstate.getAsJsonObject("variants").entrySet())if(variant(state,entry.getKey()))choices.add(first(entry.getValue()));
        if(blockstate.has("multipart"))for(var part:blockstate.getAsJsonArray("multipart")){
            var term=part.getAsJsonObject();if(!term.has("when")||condition(state,term.getAsJsonObject("when")))choices.add(first(term.get("apply")));
        }
        for(var choice:choices){
            var model=model(choice.get("model").getAsString());var refs=model.has("textures")?model.getAsJsonObject("textures"):new JsonObject();
            int rx=choice.has("x")?choice.get("x").getAsInt():0,ry=choice.has("y")?choice.get("y").getAsInt():0;
            if(model.has("elements"))for(var element:model.getAsJsonArray("elements")){
                var box=element.getAsJsonObject();if(!box.has("faces"))continue;
                for(var face:box.getAsJsonObject("faces").entrySet()){
                    int side=Arrays.asList(DIRECTIONS).indexOf(face.getKey());if(side<0)continue;
                    int target=rotatedDirection(side,rx,ry);if(faces[target]!=null)continue;
                    var data=face.getValue().getAsJsonObject();var pixels=texture(resolve(data.get("texture").getAsString(),refs));
                    boolean tinted=data.has("tintindex");
                    if((name.equals("pink_petals")||name.equals("wildflowers"))&&tinted&&data.get("tintindex").getAsInt()==0)tinted=false;
                    if(pixels!=null)faces[target]=new Face(pixels,tinted?0x70b049:0xffffff);
                }
            }
            if(refs.has("particle")){
                int[] pixels=texture(resolve(refs.get("particle").getAsString(),refs));
                if(pixels!=null)for(int i=0;i<6;i++)if(faces[i]==null)faces[i]=new Face(pixels,name.endsWith("_leaves")?0x70b049:0xffffff);
            }
        }
        boolean water=name.equals("water")||name.equals("bubble_column"),lava=name.equals("lava");
        if(water||lava){var pixels=texture("minecraft:block/"+(water?"water_still":"lava_still"));Arrays.fill(faces,new Face(pixels,water?0x3f76e4:0xffffff));}
        Face fallback=null;for(Face face:faces)if(face!=null){fallback=face;break;}
        if(fallback==null){
            fallbacks.add(name);
            String wood=name.startsWith("dark_oak")?"dark_oak":name.startsWith("spruce")?"spruce":name.startsWith("birch")?"birch":"oak";
            String fallbackName=name.equals("chest")||name.equals("trapped_chest")?"oak_planks":name.contains("sign")?wood+"_planks":name.contains("bed")?name.replace("_bed","_wool"):name.contains("banner")?name.replace("_wall_banner","_wool").replace("_banner","_wool"):name.equals("light")?"glass":name;
            int[] pixels=texture("minecraft:block/"+fallbackName);
            if(pixels==null){pixels=new int[256];Arrays.fill(pixels,0xffca36cb);}
            fallback=new Face(pixels,0xffffff);
        }
        for(int i=0;i<6;i++)if(faces[i]==null)faces[i]=fallback;
        Box[] boxes=state.getOutlineShape(EmptyBlockView.INSTANCE,BlockPos.ORIGIN).getBoundingBoxes().toArray(Box[]::new);
        if(name.equals("light"))boxes=new Box[0];
        if((water||lava)&&boxes.length==0)boxes=new Box[]{new Box(0,0,0,1,1,1)};
        boolean cube=boxes.length==1&&boxes[0].minX==0&&boxes[0].minY==0&&boxes[0].minZ==0&&boxes[0].maxX==1&&boxes[0].maxY==1&&boxes[0].maxZ==1;
        return new Material(boxes,faces,cube,name,state.getLuminance());
    }


    private static BufferedImage raycast(Volume volume,Camera camera){
        char[] cells=volume.cells();Material[] palette=volume.materials();byte[] light=volume.light();
        int[] pixels=new int[IMAGE_W*IMAGE_H];double vx=camera.targetX()-camera.x(),vy=camera.targetY()-camera.y(),vz=camera.targetZ()-camera.z();
        double yaw=Math.atan2(vz,vx),pitch=Math.atan2(vy,Math.hypot(vx,vz));
        double f=Math.tan(Math.toRadians(camera.fov())/2),aspect=IMAGE_W/(double)IMAGE_H;
        double cp=Math.cos(pitch),sp=Math.sin(pitch),ca=Math.cos(yaw),sa=Math.sin(yaw),ox=camera.x()-volume.sx(),oy=camera.y(),oz=camera.z()-volume.sz();
        IntStream.range(0,IMAGE_H).parallel().forEach(py->{
            for(int px=0;px<IMAGE_W;px++){
                double u=((px+.5)/IMAGE_W*2-1)*f*aspect,v=(1-(py+.5)/IMAGE_H*2)*f;
                double dx=cp*ca-u*sa-v*sp*ca,dy=sp+v*cp,dz=cp*sa+u*ca-v*sp*sa;
                double length=Math.sqrt(dx*dx+dy*dy+dz*dz);dx/=length;dy/=length;dz/=length;
                int color=volume.sky();double near=0,far=700;
                double[] starts={ox,oy,oz},directions={dx,dy,dz},minimum={0,MIN_Y,0},maximum={SIZE,MIN_Y+HEIGHT,SIZE};
                for(int axis=0;axis<3;axis++){
                    if(Math.abs(directions[axis])<1e-12){if(starts[axis]<minimum[axis]||starts[axis]>=maximum[axis]){far=-1;break;}continue;}
                    double t1=(minimum[axis]-starts[axis])/directions[axis],t2=(maximum[axis]-starts[axis])/directions[axis];
                    near=Math.max(near,Math.min(t1,t2));far=Math.min(far,Math.max(t1,t2));
                }
                if(near<far){
                    double distance=near+.000001;
                    int x=(int)Math.floor(ox+dx*distance),y=(int)Math.floor(oy+dy*distance),z=(int)Math.floor(oz+dz*distance);
                    int ax=dx>0?1:-1,ay=dy>0?1:-1,az=dz>0?1:-1;
                    double ix=1/Math.abs(dx),iy=1/Math.abs(dy),iz=1/Math.abs(dz);
                    double tx=((dx>0?x+1:x)-ox)/dx,ty=((dy>0?y+1:y)-oy)/dy,tz=((dz>0?z+1:z)-oz)/dz;
                    for(int step=0;step<1500&&distance<far;step++){
                        if(x<0||x>=SIZE||z<0||z>=SIZE||y<MIN_Y||y>=MIN_Y+HEIGHT)break;
                        int index=((y-MIN_Y)*SIZE+z)*SIZE+x,id=cells[index];double exit=Math.min(tx,Math.min(ty,tz));
                        if(id!=0){
                            Material material=palette[id];double best=Double.POSITIVE_INFINITY;int side=-1;
                            for(Box box:material.boxes()){
                                double a=(x+box.minX-ox)/dx,b=(x+box.maxX-ox)/dx,c=(y+box.minY-oy)/dy,d=(y+box.maxY-oy)/dy,e=(z+box.minZ-oz)/dz,g=(z+box.maxZ-oz)/dz;
                                double nx=Math.min(a,b),ny=Math.min(c,d),nz=Math.min(e,g),t=Math.max(nx,Math.max(ny,nz)),last=Math.min(Math.max(a,b),Math.min(Math.max(c,d),Math.max(e,g)));
                                if(t>=distance-.0001&&t<=exit+.0001&&t<=last+.0001&&t<best){best=t;side=nx>=ny&&nx>=nz?(dx>0?0:1):ny>=nz?(dy>0?2:3):(dz>0?4:5);}
                            }
                            if(side>=0){
                                Face face=material.faces()[side];double hx=ox+dx*best,hy=oy+dy*best,hz=oz+dz*best;
                                int ta=(int)Math.floor((side<2?hz:hx)*16)&15,tb=(int)Math.floor((side==2||side==3?hz:-hy)*16)&15;
                                int rgb=face.pixels()[tb*16+ta];
                                if((rgb>>>24)>80){
                                    int tint=face.tint(),column=z*SIZE+x;
                                    if(tint==0x70b049)tint=blockTint(material.name(),volume,column);
                                    if(material.name().equals("water")||material.name().equals("bubble_column"))tint=volume.water()[column];
                                    int r=(rgb>>16&255)*(tint>>16&255)/255,g=(rgb>>8&255)*(tint>>8&255)/255,b=(rgb&255)*(tint&255)/255;
                                    int lx=x+NORMALS[side][0],ly=y+NORMALS[side][1],lz=z+NORMALS[side][2];
                                    int packed=light[index]&255;
                                    if(lx>=0&&lx<SIZE&&lz>=0&&lz<SIZE&&ly>=MIN_Y&&ly<MIN_Y+HEIGHT)packed=light[((ly-MIN_Y)*SIZE+lz)*SIZE+lx]&255;
                                    int level=Math.max(packed&15,Math.max(packed>>>4,material.emission()));
                                    double normalized=level/15.0,brightness=.075+.925*normalized/(4-3*normalized);
                                    double faceShade=side==3?1:side==2?.55:side<2?.82:.70;
                                    if(material.emission()>=14){brightness=1;faceShade=.95;}
                                    double shade=brightness*faceShade,fog=Math.min(.16,best/2200.0);
                                    int fogColor=volume.fog();r=(int)(r*shade*(1-fog)+(fogColor>>16&255)*fog);g=(int)(g*shade*(1-fog)+(fogColor>>8&255)*fog);b=(int)(b*shade*(1-fog)+(fogColor&255)*fog);
                                    color=r<<16|g<<8|b;break;
                                }
                            }
                        }
                        if(tx<ty&&tx<tz){distance=tx;tx+=ix;x+=ax;}
                        else if(ty<tz){distance=ty;ty+=iy;y+=ay;}
                        else{distance=tz;tz+=iz;z+=az;}
                    }
                }
                pixels[py*IMAGE_W+px]=color;
            }
        });
        var result=new BufferedImage(IMAGE_W,IMAGE_H,BufferedImage.TYPE_INT_RGB);result.setRGB(0,0,IMAGE_W,IMAGE_H,pixels,0,IMAGE_W);return result;
    }
    private static int blockTint(String name,Volume volume,int column){
        // These leaf types use fixed BlockColors handlers in the native client;
        // pale/cherry/azalea leaves have precoloured textures and no handler.
        return switch(name){
            case "spruce_leaves"->FoliageColors.SPRUCE;
            case "birch_leaves"->FoliageColors.BIRCH;
            case "pale_oak_leaves","cherry_leaves","azalea_leaves","flowering_azalea_leaves"->0xffffff;
            default->name.endsWith("_leaves")?volume.foliage()[column]:volume.grass()[column];
        };
    }
    private static void annotate(BufferedImage image,String title,String detail){
        var graphics=image.createGraphics();graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setColor(new Color(12,19,28,205));graphics.fillRect(0,image.getHeight()-72,image.getWidth(),72);
        graphics.setColor(Color.WHITE);graphics.setFont(new Font(Font.SANS_SERIF,Font.BOLD,25));graphics.drawString(title,24,image.getHeight()-40);
        graphics.setColor(new Color(207,216,230));graphics.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,17));graphics.drawString(detail,24,image.getHeight()-15);graphics.dispose();
    }
}
