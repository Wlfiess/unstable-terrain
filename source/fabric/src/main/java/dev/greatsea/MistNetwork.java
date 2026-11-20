package dev.greatsea;
import net.fabricmc.fabric.api.networking.v1.*;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
public final class MistNetwork {
 public record Payload(float snow,float fog,boolean sheltered,boolean cave) implements CustomPayload {
  public static final Id<Payload> ID=new Id<>(Identifier.of("unstableterrain","mist"));
  public static final PacketCodec<RegistryByteBuf,Payload> CODEC=new PacketCodec<>(){
   public Payload decode(RegistryByteBuf b){return new Payload(b.readFloat(),b.readFloat(),b.readBoolean(),b.readBoolean());}
   public void encode(RegistryByteBuf b,Payload p){b.writeFloat(p.snow());b.writeFloat(p.fog());b.writeBoolean(p.sheltered());b.writeBoolean(p.cave());}
  };
  public Id<? extends CustomPayload> getId(){return ID;}
 }
 private MistNetwork(){}
 public static void initialize(){PayloadTypeRegistry.playS2C().register(Payload.ID,Payload.CODEC);}
 public static void send(ServerPlayerEntity p,MistEffects.State s){if(ServerPlayNetworking.canSend(p,Payload.ID))ServerPlayNetworking.send(p,new Payload(s.snow(),s.fog(),s.sheltered(),s.cave()));}
}
