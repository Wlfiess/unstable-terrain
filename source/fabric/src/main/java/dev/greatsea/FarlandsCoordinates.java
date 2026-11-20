package dev.greatsea;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.RegistryByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

/** Server-authoritative display frame for the continuous Overworld Farlands. */
public final class FarlandsCoordinates {
    public record Origin(long x,long z) {}
    public record Payload(boolean active,long originX,long originZ,int entryBoundary,int fireworkDisableDepth) implements CustomPayload {
        public static final Id<Payload> ID=new Id<>(Identifier.of("unstableterrain","farlands_coordinates"));
        public static final PacketCodec<RegistryByteBuf,Payload> CODEC=new PacketCodec<>() {
            @Override public Payload decode(RegistryByteBuf buf) {
                return new Payload(buf.readBoolean(),buf.readLong(),buf.readLong(),buf.readVarInt(),buf.readVarInt());
            }
            @Override public void encode(RegistryByteBuf buf,Payload value) {
                buf.writeBoolean(value.active());buf.writeLong(value.originX());buf.writeLong(value.originZ());
                buf.writeVarInt(value.entryBoundary());buf.writeVarInt(value.fireworkDisableDepth());
            }
        };
        @Override public Id<? extends CustomPayload> getId() {return ID;}
    }
    private record ClientState(UUID player,Payload payload) {}
    private static final Map<ServerPlayerEntity,Payload> SENT=new WeakHashMap<>();
    private static volatile ClientState clientState;
    private FarlandsCoordinates() {}
    public static void initialize() {
        PayloadTypeRegistry.playS2C().register(Payload.ID,Payload.CODEC);
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for(ServerPlayerEntity player:server.getPlayerManager().getPlayerList())update(player,false);
        });
        ServerPlayConnectionEvents.JOIN.register((handler,sender,server)->update(handler.player,true));
        ServerPlayConnectionEvents.DISCONNECT.register((handler,server)->SENT.remove(handler.player));
        ServerLifecycleEvents.SERVER_STOPPED.register(server->SENT.clear());
    }
    private static void update(ServerPlayerEntity player,boolean force) {
        boolean active=FarlandsWorld.activePlayer(player);
        FarlandsCoordinateData data=(FarlandsCoordinateData)player;
        Origin origin=data.greatsea$coordinateOrigin();
        if(active && origin==null) {
            origin=entryOrigin(player.getX(),player.getZ(),GreatSea.FARLANDS.entryBoundary);
            data.greatsea$coordinateOrigin(origin);
        } else if(!active && origin!=null) {
            data.greatsea$coordinateOrigin(null);origin=null;
        }
        int boostLimit=GameplaySettings.Rule.FARLANDS_FIREWORKS.enabled()?Integer.MAX_VALUE:GreatSea.FARLANDS.fireworkDisableDepth;
        Payload packet=new Payload(active,origin==null?0:origin.x(),origin==null?0:origin.z(),
                GreatSea.FARLANDS.entryBoundary,boostLimit);
        if(force || !packet.equals(SENT.get(player))) {
            SENT.put(player,packet);
            if(ServerPlayNetworking.canSend(player,Payload.ID))ServerPlayNetworking.send(player,packet);
        }
    }
    /** The crossed border is zero; the entry tangent is the other zero. Preserve axis signs. */
    public static Origin entryOrigin(double x,double z,int boundary) {
        return Math.abs(x)>=Math.abs(z) ? new Origin(x<0?-boundary:boundary,(long)Math.floor(z)):
                new Origin((long)Math.floor(x),z<0?-boundary:boundary);
    }
    public static void receiveClient(Payload packet,PlayerEntity player) {
        clientState=player==null?null:new ClientState(player.getUuid(),packet);
    }
    public static void clearClient() {clientState=null;}
    public static boolean clientActive(PlayerEntity player) {
        ClientState state=clientState;
        return state!=null && state.payload().active() && state.player().equals(player.getUuid()) &&
                player.getEntityWorld().getRegistryKey().equals(World.OVERWORLD) &&
                Math.max(Math.abs(player.getX()),Math.abs(player.getZ()))>=state.payload().entryBoundary();
    }
    public static double clientDepth(PlayerEntity player) {
        ClientState state=clientState;
        return state==null || !clientActive(player)?0:Math.max(0,Math.max(Math.abs(player.getX()),Math.abs(player.getZ()))-state.payload().entryBoundary());
    }
    public static int clientFireworkDisableDepth() {
        ClientState state=clientState;
        return state==null || !state.payload().active()?Integer.MAX_VALUE:state.payload().fireworkDisableDepth();
    }
    public static String relativeClientLine(String line) {
        ClientState state=clientState;
        return state==null || !state.payload().active()?line:FarlandsCoordinateText.relative(line,state.payload().originX(),state.payload().originZ());
    }
}
