package dev.greatsea;

import com.google.gson.*;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.Perspective;
import net.minecraft.client.util.ScreenshotRecorder;
import java.nio.file.*;
import java.util.*;

/** Opt-in developer-only real client capture; never enabled in normal play. */
final class SpokelandsClientCapture {
    private static final Path ROOT=Path.of("../test-server/spokelands-perspective");
    private static final Path OUT=Path.of("native-client-captures-1320");
    private static final List<JsonObject> SHOTS=new ArrayList<>();
    private static final JsonArray RESULTS=new JsonArray();
    private static final boolean DAYLIGHT=Boolean.getBoolean("unstableterrain.captureSpokelandsDaylight");
    private static final boolean FLOWERS=Boolean.getBoolean("unstableterrain.captureSpokelandsFlowerOnly");
    private static int index=-1,ticks,stable,lastBuilt=-1;
    private static boolean requested,configured,finished;
    static void initialize(){ClientTickEvents.END_CLIENT_TICK.register(SpokelandsClientCapture::tick);}
    private static void tick(MinecraftClient client){
        try{
            if(finished||client.player==null||client.world==null)return;
            if(!configured){
                configured=true;Files.createDirectories(OUT);
                var source=JsonParser.parseString(Files.readString(ROOT.resolve("provenance.json"))).getAsJsonObject();
                for(var frame:source.getAsJsonArray("frames")){
                    var shot=frame.getAsJsonObject().deepCopy();
                    if(FLOWERS&&!shot.get("region").getAsString().equals("FLOWER_HILLS"))continue;
                    if(DAYLIGHT){
                        if(!shot.get("region").getAsString().equals("DARK_FOREST"))continue;
                        shot.addProperty("file",shot.get("file").getAsString().replace(".png","-daylight.png"));
                    }
                    SHOTS.add(shot);
                }
                client.options.hudHidden=true;client.options.pauseOnLostFocus=false;
                client.options.setPerspective(Perspective.FIRST_PERSON);client.options.getFov().setValue(76);
                client.options.getCloudRenderMode().setValue(net.minecraft.client.option.CloudRenderMode.OFF);
                client.options.getViewDistance().setValue(12);client.options.getGamma().setValue(.5);
                client.getWindow().setWindowedSize(1920,1080);
                org.lwjgl.glfw.GLFW.glfwHideWindow(client.getWindow().getHandle());
                command(client,"gamemode spectator");command(client,"weather clear");command(client,"gamerule minecraft:advance_time false");
                advance(client);return;
            }
            ticks++;
            if(index>=SHOTS.size())return;
            if(ticks>2400)throw new IllegalStateException("Client capture timed out for "+SHOTS.get(index).get("file"));
            var shot=SHOTS.get(index);var camera=shot.getAsJsonArray("cameraWorld");
            if(client.player.squaredDistanceTo(camera.get(0).getAsDouble(),camera.get(1).getAsDouble(),camera.get(2).getAsDouble())>9)return;
            int built=client.worldRenderer.getCompletedChunkCount();
            if(built==lastBuilt)stable++;else{stable=0;lastBuilt=built;}
            if(requested){
                if(Files.isRegularFile(OUT.resolve("screenshots").resolve(shot.get("file").getAsString()))){
                    var result=shot.deepCopy();result.addProperty("plannedFovDegrees",result.get("fovDegrees").getAsDouble());result.addProperty("fovDegrees",76);result.addProperty("completedClientChunks",built);
                    result.addProperty("waitTicks",ticks);result.addProperty("kind","Actual Minecraft 1.21.11 client framebuffer");
                    result.addProperty("terrainRenderComplete",client.worldRenderer.isTerrainRenderComplete());RESULTS.add(result);
                    Files.writeString(OUT.resolve(DAYLIGHT?"daylight-provenance.json":FLOWERS?"flower-provenance.json":"provenance.json"),new GsonBuilder().setPrettyPrinting().create().toJson(RESULTS));
                    GreatSea.LOGGER.info("SPOKELANDS_REAL_CLIENT_PASS {}",shot.get("file"));advance(client);
                }
                return;
            }
            int cx=client.player.getBlockX()>>4,cz=client.player.getBlockZ()>>4;boolean loaded=true;
            for(int dz=-5;dz<=5;dz++)for(int dx=-5;dx<=5;dx++)loaded&=client.world.isChunkLoaded(cx+dx,cz+dz);
            if(ticks>=200&&loaded&&built>64&&(stable>=40||client.worldRenderer.isTerrainRenderComplete())){
                requested=true;client.setScreen(null);
                ScreenshotRecorder.saveScreenshot(OUT.toFile(),shot.get("file").getAsString(),client.getFramebuffer(),1,text->GreatSea.LOGGER.info("Native screenshot: {}",text.getString()));
            }
            if(ticks>2400)throw new IllegalStateException("Client terrain did not settle for "+shot.get("file")+": "+built+" chunks, "+client.worldRenderer.getChunksDebugString());
        }catch(Throwable error){
            finished=true;GreatSea.LOGGER.error("Spokelands real-client capture failed",error);
            try{Files.createDirectories(OUT);Files.writeString(OUT.resolve("FAIL.txt"),error.toString());}catch(Exception ignored){}
            client.scheduleStop();
        }
    }
    private static void command(MinecraftClient client,String command){client.player.networkHandler.sendChatCommand(command);}
    private static void advance(MinecraftClient client){
        index++;ticks=0;stable=0;lastBuilt=-1;requested=false;
        if(index>=SHOTS.size()){
            finished=true;command(client,"stop");client.scheduleStop();return;
        }
        var shot=SHOTS.get(index);var c=shot.getAsJsonArray("cameraWorld");var t=shot.getAsJsonArray("targetWorld");
        double dx=t.get(0).getAsDouble()-c.get(0).getAsDouble(),dy=t.get(1).getAsDouble()-c.get(1).getAsDouble(),dz=t.get(2).getAsDouble()-c.get(2).getAsDouble();
        double yaw=Math.toDegrees(Math.atan2(-dx,dz)),pitch=-Math.toDegrees(Math.atan2(dy,Math.hypot(dx,dz)));
        command(client,String.format(Locale.ROOT,"tp @s %.4f %.4f %.4f %.4f %.4f",c.get(0).getAsDouble(),c.get(1).getAsDouble(),c.get(2).getAsDouble(),yaw,pitch));
        boolean dark=shot.get("region").getAsString().equals("DARK_FOREST");
        boolean red=shot.get("region").getAsString().equals("RED_PLAINS");
        command(client,"time set "+(dark&&!DAYLIGHT?13500:6000));
        if((dark&&!DAYLIGHT)||red)command(client,"effect clear @s");else command(client,"effect give @s minecraft:night_vision 1000000 0 true");
    }
}

