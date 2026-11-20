package dev.greatsea;

import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.zip.ZipFile;
import java.util.stream.IntStream;
import com.google.gson.*;
import net.minecraft.world.EmptyBlockView;
import net.minecraft.util.math.Box;

/** Development-only, textured block-data views. These are not client screenshots. */
final class FarlandsPerspective {
    private static int W=256;
    private static int D=256;
    private static final int H=384,IW=960,IH=540;
    private record Texture(int[] side,int[] top,int[] bottom,boolean grass,boolean tint,boolean water,List<Box> boxes) {}
    private static final Map<String,Map<String,String>> MODELS=new HashMap<>();
    private static JsonObject json(String path) throws Exception {
        var entry=assets.getEntry("assets/minecraft/"+path+".json");
        if(entry==null)return new JsonObject();
        try(var reader=new java.io.InputStreamReader(assets.getInputStream(entry),java.nio.charset.StandardCharsets.UTF_8)) {return JsonParser.parseReader(reader).getAsJsonObject();}
    }
    private static Map<String,String> model(String id) throws Exception {
        id=id.replace("minecraft:","");if(MODELS.containsKey(id))return MODELS.get(id);
        var data=json("models/"+id);Map<String,String> result=new HashMap<>();
        if(data.has("parent")&&!data.get("parent").getAsString().startsWith("builtin/"))result.putAll(model(data.get("parent").getAsString()));
        if(data.has("textures"))for(var e:data.getAsJsonObject("textures").entrySet())result.put(e.getKey(),e.getValue().getAsString());
        MODELS.put(id,result);return result;
    }
    private static String resolved(Map<String,String> textures,String fallback,String... keys) {
        String value=null;for(String key:keys)if(textures.containsKey(key)){value=textures.get(key);break;}
        for(int i=0;value!=null&&value.startsWith("#")&&i<16;i++)value=textures.get(value.substring(1));
        return value==null?fallback:value.replace("minecraft:block/","").replace("block/","");
    }
    private static final Map<String,int[]> TEXTURES=new HashMap<>();
    private static ZipFile assets;
    private static int[] texture(String name) throws Exception {
        if(TEXTURES.containsKey(name))return TEXTURES.get(name);
        var e=assets.getEntry("assets/minecraft/textures/block/"+name+".png");
        if(e==null)e=assets.getEntry("assets/minecraft/textures/block/stone.png");
        var img=ImageIO.read(assets.getInputStream(e));int[] rgb=new int[256];
        img.getRGB(0,0,16,16,rgb,0,16);TEXTURES.put(name,rgb);return rgb;
    }
    private static Texture material(BlockState state) throws Exception {
        String name=Registries.BLOCK.getId(state.getBlock()).getPath(),side=name,top=name,bottom=name;
        var blockstate=json("blockstates/"+name);JsonElement choice=null;
        if(blockstate.has("variants"))for(var variant:blockstate.getAsJsonObject("variants").entrySet()) {
            boolean matches=true;
            for(String term:variant.getKey().split(","))if(!term.isEmpty()) {
                String[] pair=term.split("=");boolean found=false;
                for(var property:state.getEntries().entrySet())if(property.getKey().getName().equals(pair[0])&&property.getValue().toString().equals(pair[1]))found=true;
                if(!found)matches=false;
            }
            if(matches){choice=variant.getValue();break;}
        }
        if(choice==null&&blockstate.has("multipart"))choice=blockstate.getAsJsonArray("multipart").get(0).getAsJsonObject().get("apply");
        if(choice!=null){if(choice.isJsonArray())choice=choice.getAsJsonArray().get(0);var object=choice.getAsJsonObject();if(object.has("model")) {
            var textures=model(object.get("model").getAsString());
            side=resolved(textures,name,"side","all","texture","particle","cross","flowerbed");
            top=resolved(textures,side,"top","all","texture","particle","cross");bottom=resolved(textures,side,"bottom","all","texture","particle");
        }}
        if(name.equals("grass_block")){side="grass_block_side";top="grass_block_top";bottom="dirt";}
        if(name.equals("snow_block") || name.equals("snow"))side=top=bottom="snow";
        if(name.equals("water"))side=top=bottom="water_still";
        if(name.equals("lava"))side=top=bottom="lava_still";
        if(name.endsWith("_log")){top=bottom=name+"_top";}
        if(name.equals("sandstone")){side="sandstone";top="sandstone_top";bottom="sandstone_bottom";}
        if(name.equals("cactus")){side="cactus_side";top="cactus_top";bottom="cactus_bottom";}
        if(name.equals("snowy_grass_block"))side="grass_block_snow";
        boolean tint=name.endsWith("_leaves") && !name.equals("cherry_leaves") || name.equals("short_grass") || name.equals("fern") || name.equals("water");
        List<Box> boxes=state.getOutlineShape(EmptyBlockView.INSTANCE,BlockPos.ORIGIN).getBoundingBoxes();
        if(boxes.isEmpty())boxes=List.of(new Box(0,0,0,1,1,1));
        return new Texture(texture(side),texture(top),texture(bottom),name.equals("grass_block"),tint,name.equals("water"),boxes);
    }
    private static int islandView;
    private static boolean roofView,edgeView,farView,beachView,templeClose;
    static void renderAll(ServerWorld world,List<String> report) throws Exception {
        Path directory=Path.of("farlands-perspective");Files.createDirectories(directory);
        try(var zip=new ZipFile(Path.of("../gradle-home/caches/fabric-loom/1.21.11/minecraft-client.jar").toFile())) {
            assets=zip;
            for(var stage:FarlandsTerrain.Stage.values()) {
                String selected=System.getProperty("unstableterrain.previewStage","");
                if(!selected.isEmpty()){if(!java.util.Arrays.asList(selected.split(",")).contains(stage.name()))continue;}
                else if(stage==FarlandsTerrain.Stage.REPEATING || stage==FarlandsTerrain.Stage.WATER || stage==FarlandsTerrain.Stage.GRAVEL_AFTER_SPIKES || stage==FarlandsTerrain.Stage.CORRUPTED || stage==FarlandsTerrain.Stage.SAND_FARLANDS)continue;
                render(world,stage,directory,report);
                if(stage==FarlandsTerrain.Stage.FLOATING){for(islandView=1;islandView<=3;islandView++)render(world,stage,directory,report);islandView=0;}
                if(stage==FarlandsTerrain.Stage.PLAINS){roofView=true;render(world,stage,directory,report);roofView=false;edgeView=true;render(world,stage,directory,report);edgeView=false;farView=true;render(world,stage,directory,report);farView=false;}
                if(stage==FarlandsTerrain.Stage.SPLIT){farView=true;render(world,stage,directory,report);farView=false;templeClose=true;render(world,stage,directory,report);templeClose=false;}
                if(stage==FarlandsTerrain.Stage.SKYGRID){farView=true;render(world,stage,directory,report);farView=false;}
                if(stage==FarlandsTerrain.Stage.SPIKES){farView=true;render(world,stage,directory,report);farView=false;}
            }
            if(java.util.Arrays.asList(System.getProperty("unstableterrain.previewStage","").split(",")).contains("BEACH")) {
                beachView=true;render(world,FarlandsTerrain.Stage.CHUNKS,directory,report);beachView=false;
            }
        }
    }
    private static void render(ServerWorld world,FarlandsTerrain.Stage stage,Path directory,List<String> report) throws Exception {
        W=stage==FarlandsTerrain.Stage.ORBITALS?512:farView?512:256;
        D=farView && stage==FarlandsTerrain.Stage.PLAINS?768:256;
        if(stage==FarlandsTerrain.Stage.SAND_DUNES){W=640;D=512;}
        if(stage==FarlandsTerrain.Stage.FLOATING){W=512;D=512;}
        if(stage==FarlandsTerrain.Stage.SPLIT)W=512;
        if(beachView){W=768;D=512;}
        int border=GreatSea.FARLANDS.entryBoundary;
        int sx=(border+FarlandsTerrain.start(stage)+GreatSea.FARLANDS.stageWidths[stage.ordinal()]/2)/16*16-32,sz=-128;
        if(stage==FarlandsTerrain.Stage.PLAINS)sx=edgeView?(border/16)*16-32:roofView?(border+1024)/16*16:(border/16)*16-128;
        if(stage==FarlandsTerrain.Stage.PLAINS && farView){sx=(border/16)*16-240;sz=-D/2;}
        if(stage==FarlandsTerrain.Stage.SKYGRID)sx=(border+FarlandsTerrain.start(stage)+(int)(GreatSea.FARLANDS.stageWidths[stage.ordinal()]*(farView?.83:.05)))/16*16;
        if(stage==FarlandsTerrain.Stage.ORBITALS)sx=(border+FarlandsOrbitals.center()-256)/16*16;
        if(stage==FarlandsTerrain.Stage.SPLIT)sx=(border+FarlandsTemple.center()-(farView?320:192))/16*16;
        // Decay begins intact, as in the reference canyon. Later erosion gets its own view.
        if(stage==FarlandsTerrain.Stage.DECAY)sx=(border+FarlandsTerrain.start(stage)+1024)/16*16;
        if(beachView){sx=GreatSea.CONFIG.innerBoundary;sz=0;}
        char[] cells=new char[W*D*H];var states=new ArrayList<BlockState>();var lookup=new HashMap<BlockState,Character>();
        states.add(net.minecraft.block.Blocks.AIR.getDefaultState());lookup.put(states.get(0),(char)0);
        long waterBlocks=0,lavaBlocks=0;
        BlockPos.Mutable pos=new BlockPos.Mutable();
        for(int z=0;z<D;z+=16)for(int x=0;x<W;x+=16) {
            var chunk=world.getChunk((sx+x)>>4,(sz+z)>>4);
            for(int y=-64;y<320;y++)for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++) {
                var state=chunk.getBlockState(pos.set(sx+x+dx,y,sz+z+dz));if(state.isAir())continue;
                if(state.isOf(net.minecraft.block.Blocks.WATER))waterBlocks++;if(state.isOf(net.minecraft.block.Blocks.LAVA))lavaBlocks++;
                Character id=lookup.get(state);
                if(id==null){id=(char)states.size();lookup.put(state,id);states.add(state);}
                cells[((y+64)*D+z+dz)*W+x+dx]=id;
            }
        }
        Texture[] palette=new Texture[states.size()];for(int i=1;i<palette.length;i++)palette[i]=material(states.get(i));
        double cx=14.5,cz=128.5,cy=110,pitch=-.18,yaw=.015;
        switch(stage) {
            case PLAINS -> {cx=7.5;cz=D/2.0+.5;cy=farView?100:edgeView?265:roofView?276:145;pitch=farView?0:edgeView?-.25:roofView?-.18:.12;}
            case ORBITALS -> {cx=256;cz=12;cy=265;pitch=-.27;yaw=Math.PI/2;}
            case REPEATING -> {cy=259;pitch=-.13;}
            case FLOATING -> {cy=290;cz=256;pitch=-.48; if(islandView==1){cx=490;cy=250;pitch=-.30;yaw=Math.PI;} if(islandView==2){cx=256;cz=14;cy=240;pitch=-.35;yaw=Math.PI/2;} if(islandView==3){cx=14;cz=256;cy=-35;pitch=.40;}}
            case GRAVEL,GRAVEL_AFTER_SPIKES -> {cy=178;pitch=-.015;cz=128;}
            case SPIKES -> {cy=farView?-55:138;pitch=farView?.08:.36;}
            case SPLIT -> {cy=farView?145.62:224;cx=10.5;cz=128;pitch=farView?.20:-.18;if(templeClose){cx=border+FarlandsTemple.center()-126.5-sx;cy=145.62;pitch=.20;yaw=0;}}
            case CHERRY -> {cy=88;pitch=-.24;}
            case PLAINS_HILLS,SAND_DUNES -> {
                int best=320;
                for(int z=44;z<88;z++)for(int x=8;x<48;x++) {
                    int top=top(cells,x,z);
                    if(top<best){best=top;cx=x+.5;cz=z+.5;}
                }
                cy=stage==FarlandsTerrain.Stage.SAND_DUNES?280:158;pitch=stage==FarlandsTerrain.Stage.SAND_DUNES?-.52:.12;
            }
            case SAND_FARLANDS,CORRUPTED -> {cy=265;pitch=-.35;}
            case CHUNKS -> {cy=170;pitch=-.42;}
            case DECAY -> {cy=280;pitch=-.45;cz=128;}
            case WATER -> {cy=-49;pitch=-.14;}
            case SKYGRID -> {cy=145;pitch=-.15;}
        }
        if(beachView){cx=80.5;cy=270;cz=256.5;pitch=-.55;}
        if(stage==FarlandsTerrain.Stage.PLAINS_HILLS){cx=16.5;cz=128.5;cy=316;pitch=-.52;}
        if(stage==FarlandsTerrain.Stage.SPIKES || stage==FarlandsTerrain.Stage.SAND_DUNES) {
            double best=-1;
            for(int z=96;z<160;z+=2)for(int x=10;x<50;x+=2) {
                if(solid(cells,x,(int)cy,z))continue;
                double score=0;
                for(int a=-2;a<=2;a++) {
                    int distance=1;
                    while(distance<100 && z+a*distance/8>1 && z+a*distance/8<D-1 && !solid(cells,x+distance,(int)(cy+pitch*distance),z+a*distance/8))distance++;
                    score+=Math.min(distance,80);
                }
                if(score>best){best=score;cx=x+.5;cz=z+.5;}
            }
        }
        // Move a camera accidentally inside a narrow pillar into nearby open air.
        if(solid(cells,(int)cx,(int)cy,(int)cz)) {
            outer:for(int radius=1;radius<24;radius++)for(int dz=-radius;dz<=radius;dz++)for(int dx=-radius;dx<=radius;dx++) {
                int x=(int)cx+dx,z=(int)cz+dz;
                if(x>2 && x<W-2 && z>2 && z<D-2 && !solid(cells,x,(int)cy,z) && !solid(cells,x+1,(int)cy,z)) {cx=x+.5;cz=z+.5;break outer;}
            }
        }
        BufferedImage img=raycast(cells,palette,cx,cy,cz,pitch,yaw);
        ImageIO.write(img,"png",directory.resolve(beachView?"greatsea-beach.png":String.format("%02d-%s.png",stage.ordinal()+1,stage.name().toLowerCase()+(stage==FarlandsTerrain.Stage.FLOATING && islandView>0?"-view-"+islandView:"")+(templeClose?"-walkup":farView?"-approach":edgeView?"-edge":roofView?"-roof":""))).toFile());
        report.add("Perspective "+(beachView?"BEACH":stage)+": "+(W/16*D/16)+" FULL chunks, "+states.size()+" block states, camera="+(sx+cx)+","+cy+","+(sz+cz)+", water="+waterBlocks+", lava="+lavaBlocks);
        GreatSea.LOGGER.info("FARLANDS_PERSPECTIVE_PASS {}",stage);
    }
    private static boolean solid(char[] cells,int x,int y,int z) {return x>=0 && x<W && z>=0 && z<D && y>=-64 && y<320 && cells[((y+64)*D+z)*W+x]!=0;}
    private static int top(char[] cells,int x,int z) {for(int y=319;y>=-64;y--)if(solid(cells,x,y,z))return y;return -64;}
    private static BufferedImage raycast(char[] cells,Texture[] palette,double ox,double oy,double oz,double pitch,double yaw) {
        int[] pixels=new int[IW*IH];double f=Math.tan(Math.toRadians(beachView?40:72)*.5),aspect=IW/(double)IH;
        double cp=Math.cos(pitch),sp=Math.sin(pitch),ca=Math.cos(yaw),sa=Math.sin(yaw);
        IntStream.range(0,IH).parallel().forEach(py->{
            for(int px=0;px<IW;px++) {
                double u=((px+.5)/IW*2-1)*f*aspect,v=(1-(py+.5)/IH*2)*f;
                double dx=cp*ca-u*sa-v*sp*ca,dy=sp+v*cp,dz=cp*sa+u*ca-v*sp*sa;
                double len=Math.sqrt(dx*dx+dy*dy+dz*dz);dx/=len;dy/=len;dz/=len;
                int x=(int)Math.floor(ox),y=(int)Math.floor(oy),z=(int)Math.floor(oz),ax=dx>0?1:-1,ay=dy>0?1:-1,az=dz>0?1:-1;
                double tx=(dx>0?x+1-ox:ox-x)/Math.abs(dx),ty=(dy>0?y+1-oy:oy-y)/Math.abs(dy),tz=(dz>0?z+1-oz:oz-z)/Math.abs(dz);
                double ix=1/Math.abs(dx),iy=1/Math.abs(dy),iz=1/Math.abs(dz),distance=0;int axis=0,color=0x91b8ef;
                for(int step=0;step<640;step++) {
                    if(x<0 || x>=W || z<0 || z>=D || y<-64 || y>=320)break;
                    int id=cells[((y+64)*D+z)*W+x];
                    if(id!=0) {
                        Texture t=palette[id];double hit=Double.POSITIVE_INFINITY;int hitAxis=axis;
                        for(Box box:t.boxes) {
                            double nx=((dx>0?box.minX:box.maxX)+x-ox)/dx,fx=((dx>0?box.maxX:box.minX)+x-ox)/dx;
                            double ny=((dy>0?box.minY:box.maxY)+y-oy)/dy,fy=((dy>0?box.maxY:box.minY)+y-oy)/dy;
                            double nz=((dz>0?box.minZ:box.maxZ)+z-oz)/dz,fz=((dz>0?box.maxZ:box.minZ)+z-oz)/dz;
                            double near=Math.max(nx,Math.max(ny,nz)),end=Math.min(fx,Math.min(fy,fz));
                            if(near<=end&&end>=distance-1e-7&&near<=Math.min(tx,Math.min(ty,tz))+1e-7&&Math.max(near,distance)<hit){hit=Math.max(near,distance);hitAxis=nx>=ny&&nx>=nz?0:ny>=nz?1:2;}
                        }
                        if(Double.isFinite(hit)) {
                        double hx=ox+dx*hit,hy=oy+dy*hit,hz=oz+dz*hit;
                        axis=hitAxis;
                        int a=(int)Math.floor((axis==0?hz:hx)*16)&15,b=(int)Math.floor((axis==1?hz:-hy)*16)&15;
                        int rgb=(axis==1?(dy<0?t.top:t.bottom):t.side)[b*16+a];
                        if((rgb>>>24)>70) {
                            double shade=axis==1?(dy<0?1:.5):axis==0?.78:.65;
                            int r=rgb>>16&255,g=rgb>>8&255,blue=rgb&255;
                            if(t.water){r=r*63/255;g=g*118/255;blue=blue*228/255;}else if(t.tint || t.grass && axis==1 && dy<0){r=r*112/255;g=g*168/255;blue=blue*68/255;}
                            double fog=Math.min(.50,hit/650);
                            r=(int)(r*shade*(1-fog)+145*fog);g=(int)(g*shade*(1-fog)+184*fog);blue=(int)(blue*shade*(1-fog)+239*fog);
                            color=r<<16|g<<8|blue;break;
                        }
                        }
                    }
                    if(tx<ty && tx<tz){distance=tx;tx+=ix;x+=ax;axis=0;}
                    else if(ty<tz){distance=ty;ty+=iy;y+=ay;axis=1;}
                    else{distance=tz;tz+=iz;z+=az;axis=2;}
                }
                pixels[py*IW+px]=color;
            }
        });
        BufferedImage image=new BufferedImage(IW,IH,BufferedImage.TYPE_INT_RGB);image.setRGB(0,0,IW,IH,pixels,0,IW);return image;
    }
}






