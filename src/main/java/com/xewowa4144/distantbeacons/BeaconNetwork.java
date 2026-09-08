/** Registers the beacon payload and sends beacon updates to players in the matching dimension. */
package com.xewowa4144.distantbeacons;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;

public final class BeaconNetwork {
    private BeaconNetwork() {}

    // Register the custom payload codec on the common/server side.
    public static void initCommon() {
        PayloadTypeRegistry.clientboundPlay().register(
            BeaconBeamPayload.TYPE,
            BeaconBeamPayload.CODEC
        );

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            DistantBeaconsScanManager.sendAllActiveBeaconsTo(handler.getPlayer());
        });
    }

    // Send one beacon update to a specific player.
    public static void send(ServerPlayer player, BeaconBeamPayload payload) {
        if (ServerPlayNetworking.canSend(player, BeaconBeamPayload.TYPE)) {
            // Fabric handles packet encoding through the payload codec registered above.
        ServerPlayNetworking.send(player, payload);
        }
    }
}
