package dev.greatsea;
import java.util.*;
import net.minecraft.network.packet.s2c.play.GameStateChangeS2CPacket;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.world.LightType;

/** Personal weather and authoritative shelter state; ordinary players keep normal weather. */
public final class MistEffects {
 public record State(float snow,float fog,boolean sheltered,boolean cave){}
 private static final Map<ServerPlayerEntity,State> STATES=new WeakHashMap<>();
 private MistEffects(){}
 public static State state(ServerPlayerEntity p){return STATES.getOrDefault(p,new State(0,0,false,false));}
 public static void update(ServerPlayerEntity player){
  var world=player.getEntityWorld();if(world.getTime()%10!=0)return;
  float strength=MistRegion.active(world)?(float)MistRegion.strength(player.getX(),player.getZ()):0;
  var previous=STATES.get(player);
  if(strength==0&&previous==null)return;
  boolean shelter=strength>0&&MistShelter.inside(world,player.getBlockPos());
  boolean cave=strength>0&&!shelter&&world.getLightLevel(LightType.SKY,player.getBlockPos())<3;
  var state=new State(strength,shelter?0:strength,shelter,cave);
  if(previous==null||!state.equals(previous)||world.getTime()%20==0)MistNetwork.send(player,state);
  if(strength>0){
   if(previous==null||previous.snow()==0)player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.RAIN_STARTED,0));
   if(world.getTime()%20==0||previous==null){
    player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.RAIN_GRADIENT_CHANGED,strength));
    player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.THUNDER_GRADIENT_CHANGED,0));
    int x=player.getBlockX()+world.getRandom().nextInt(49)-24,z=player.getBlockZ()+world.getRandom().nextInt(49)-24;
    if(MistRegion.frost(world.getSeed(),x,z)&&world.isChunkLoaded(x>>4,z>>4)){
     var pos=new net.minecraft.util.math.BlockPos(x,world.getTopY(net.minecraft.world.Heightmap.Type.MOTION_BLOCKING,x,z),z);
     var snow=net.minecraft.block.Blocks.SNOW.getDefaultState();
     if(world.getBlockState(pos).isAir()&&snow.canPlaceAt(world,pos))world.setBlockState(pos,snow,3);
    }
   }
   STATES.put(player,state);
  }else{
   player.networkHandler.sendPacket(new GameStateChangeS2CPacket(world.isRaining()?GameStateChangeS2CPacket.RAIN_STARTED:GameStateChangeS2CPacket.RAIN_STOPPED,0));
   player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.RAIN_GRADIENT_CHANGED,world.getRainGradient(1)));
   player.networkHandler.sendPacket(new GameStateChangeS2CPacket(GameStateChangeS2CPacket.THUNDER_GRADIENT_CHANGED,world.getThunderGradient(1)));
   STATES.remove(player);
  }
 }
}
