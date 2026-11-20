package dev.greatsea;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;

public final class FarlandsClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        MistClient.initialize();
        ClientPlayNetworking.registerGlobalReceiver(FarlandsCoordinates.Payload.ID,(packet,context)->
                context.client().execute(()->FarlandsCoordinates.receiveClient(packet,context.player())));
        ClientPlayConnectionEvents.DISCONNECT.register((handler,client)->FarlandsCoordinates.clearClient());
        if(Boolean.getBoolean("unstableterrain.captureSpokelandsClient"))SpokelandsClientCapture.initialize();
    }
}
