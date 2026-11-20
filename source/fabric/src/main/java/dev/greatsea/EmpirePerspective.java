package dev.greatsea;

import com.google.gson.*;
import net.minecraft.block.BlockState;
import net.minecraft.registry.Registries;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Property;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.world.EmptyBlockView;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.List;
import java.util.stream.IntStream;
import java.util.zip.ZipFile;

/** Development-only views of actual generated FULL chunks, not client screenshots. */
final class EmpirePerspective {
    private static final int MIN_Y=-64, HEIGHT=384, IMAGE_W=1440, IMAGE_H=900;
    private static final String[] DIRECTIONS={"west","east","down","up","north","south"};
    private record Face(int[] pixels,int tint) {}
    private record Material(Box[] boxes,Face[] faces,boolean cube) {}
    private record Camera(String file,String title,double x,double y,double z,double targetX,double targetY,double targetZ,double fov) {}
    private final Map<String,JsonObject> jsons=new HashMap<>();
    private final Map<String,int[]> textures=new HashMap<>();
    private final Set<String> fallbacks=new TreeSet<>();
    private final ZipFile assets;
    private EmpirePerspective(ZipFile assets){this.assets=assets;}

    static void render(ServerWorld world,List<String> report) throws Exception {
        Path directory=Path.of("empire-perspective");Files.createDirectories(directory);
        Path assetJar=Path.of("../gradle-home/caches/fabric-loom/1.21.11/minecraft-client.jar");
        if(!Files.isRegularFile(assetJar))assetJar=Path.of("../gradle-home-115/caches/fabric-loom/1.21.11/minecraft-client.jar");
        try(var zip=new ZipFile(assetJar.toFile())){
            new EmpirePerspective(zip).renderWorld(world,directory,report);
        }
    }

    private void renderWorld(ServerWorld world,Path directory,List<String> report) throws Exception {
        int ox=GreatSea.FARLANDS.entryBoundary+FarlandsEmpire.origin(),oz=FarlandsEmpire.tangentOrigin();
        int sx=Math.floorDiv(ox-64,16)*16,sz=Math.floorDiv(oz-64,16)*16;
        int width=Math.floorDiv(ox+FarlandsEmpire.SAMPLE.width+79-sx,16)*16;
        int depth=Math.floorDiv(oz+FarlandsEmpire.SAMPLE.depth+79-sz,16)*16;
        char[] cells=new char[Math.multiplyExact(Math.multiplyExact(width,depth),HEIGHT)];
        var states=new ArrayList<BlockState>();var ids=new HashMap<BlockState,Character>();
        states.add(net.minecraft.block.Blocks.AIR.getDefaultState());ids.put(states.getFirst(),(char)0);
        var pos=new BlockPos.Mutable();long blocks=0;
        for(int z=0;z<depth;z+=16)for(int x=0;x<width;x+=16){
            var chunk=world.getChunk((sx+x)>>4,(sz+z)>>4);
            for(int y=MIN_Y;y<MIN_Y+HEIGHT;y++)for(int dz=0;dz<16;dz++)for(int dx=0;dx<16;dx++){
                BlockState state=chunk.getBlockState(pos.set(sx+x+dx,y,sz+z+dz));
                if(state.isAir())continue;
                Character id=ids.get(state);
                if(id==null){if(states.size()==65535)throw new IllegalStateException("Too many preview states");id=(char)states.size();ids.put(state,id);states.add(state);}
                cells[((y-MIN_Y)*depth+z+dz)*width+x+dx]=id;blocks++;
            }
            if(x==0 && z%128==0)GreatSea.LOGGER.info("EMPIRE_PREVIEW_CAPTURE {}/{} rows",z,depth);
        }
        Material[] palette=new Material[states.size()];
        for(int i=1;i<palette.length;i++)palette[i]=material(states.get(i));
        double ax=ox-sx,az=oz-sz;
        Camera[] cameras={
            new Camera("01-empire-castle-and-town.png","Castle and town",ax+485,237,az+40,ax+235,62,az+340,72),
            new Camera("02-empire-town-and-repeating-wall.png","Town beneath the Repeating Farlands",ax+65,204,az+178,ax+390,24,az+272,72)
        };
        for(Camera camera:cameras){
            BufferedImage image=raycast(cells,palette,width,depth,camera);
            annotate(image,camera.title(),"Generated block-data view · actual FULL chunks · not a client screenshot");
            ImageIO.write(image,"png",directory.resolve(camera.file()).toFile());
            report.add("Empire perspective "+camera.file()+": "+(width/16*depth/16)+" FULL chunks, "+blocks+" non-air blocks, "+states.size()+" states; camera="+(sx+camera.x())+","+camera.y()+","+(sz+camera.z()));
            GreatSea.LOGGER.info("EMPIRE_PERSPECTIVE_PASS {}",camera.file());
        }
        report.add("Empire preview texture approximations for block-entity renderers: "+fallbacks);
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
                    if(pixels!=null)faces[target]=new Face(pixels,data.has("tintindex")?0x70b049:0xffffff);
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
        return new Material(boxes,faces,cube);
    }

    private static BufferedImage raycast(char[] cells,Material[] palette,int width,int depth,Camera camera){
        int[] pixels=new int[IMAGE_W*IMAGE_H];double vx=camera.targetX()-camera.x(),vy=camera.targetY()-camera.y(),vz=camera.targetZ()-camera.z();
        double yaw=Math.atan2(vz,vx),pitch=Math.atan2(vy,Math.hypot(vx,vz));
        double f=Math.tan(Math.toRadians(camera.fov())/2),aspect=IMAGE_W/(double)IMAGE_H;
        double cp=Math.cos(pitch),sp=Math.sin(pitch),ca=Math.cos(yaw),sa=Math.sin(yaw),ox=camera.x(),oy=camera.y(),oz=camera.z();
        IntStream.range(0,IMAGE_H).parallel().forEach(py->{
            for(int px=0;px<IMAGE_W;px++){
                double u=((px+.5)/IMAGE_W*2-1)*f*aspect,v=(1-(py+.5)/IMAGE_H*2)*f;
                double dx=cp*ca-u*sa-v*sp*ca,dy=sp+v*cp,dz=cp*sa+u*ca-v*sp*sa;
                double length=Math.sqrt(dx*dx+dy*dy+dz*dz);dx/=length;dy/=length;dz/=length;
                int color=0x9bc1ef;
                double near=0,far=2000;
                double[] starts={ox,oy,oz},directions={dx,dy,dz},minimum={0,MIN_Y,0},maximum={width,MIN_Y+HEIGHT,depth};
                for(int axis=0;axis<3;axis++){
                    double t1=(minimum[axis]-starts[axis])/directions[axis],t2=(maximum[axis]-starts[axis])/directions[axis];
                    near=Math.max(near,Math.min(t1,t2));far=Math.min(far,Math.max(t1,t2));
                }
                if(near<far){
                    double distance=near+.000001;
                    int x=(int)Math.floor(ox+dx*distance),y=(int)Math.floor(oy+dy*distance),z=(int)Math.floor(oz+dz*distance);
                    int ax=dx>0?1:-1,ay=dy>0?1:-1,az=dz>0?1:-1;
                    double ix=1/Math.abs(dx),iy=1/Math.abs(dy),iz=1/Math.abs(dz);
                    double tx=((dx>0?x+1:x)-ox)/dx,ty=((dy>0?y+1:y)-oy)/dy,tz=((dz>0?z+1:z)-oz)/dz;
                    for(int step=0;step<1800&&distance<far;step++){
                        if(x<0||x>=width||z<0||z>=depth||y<MIN_Y||y>=MIN_Y+HEIGHT)break;
                        int id=cells[((y-MIN_Y)*depth+z)*width+x];double exit=Math.min(tx,Math.min(ty,tz));
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
                                    int r=rgb>>16&255,g=rgb>>8&255,b=rgb&255;
                                    r=r*(face.tint()>>16&255)/255;g=g*(face.tint()>>8&255)/255;b=b*(face.tint()&255)/255;
                                    double shade=side==3?1:side==2?.55:side<2?.82:.69,fog=Math.min(.25,best/2000);
                                    r=(int)(r*shade*(1-fog)+155*fog);g=(int)(g*shade*(1-fog)+193*fog);b=(int)(b*shade*(1-fog)+239*fog);color=r<<16|g<<8|b;break;
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
    private static void annotate(BufferedImage image,String title,String detail){
        var g=image.createGraphics();g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g.setColor(new Color(12,19,28,205));g.fillRect(0,image.getHeight()-65,image.getWidth(),65);
        g.setColor(Color.WHITE);g.setFont(new Font(Font.SANS_SERIF,Font.BOLD,22));g.drawString(title,22,image.getHeight()-36);
        g.setColor(new Color(207,216,230));g.setFont(new Font(Font.SANS_SERIF,Font.PLAIN,15));g.drawString(detail,22,image.getHeight()-13);g.dispose();
    }
}
