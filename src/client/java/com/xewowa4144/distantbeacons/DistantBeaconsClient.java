/** Client entry point: loads configuration, receives beacon data, and submits remote beams for rendering. */
package com.xewowa4144.distantbeacons;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayConnectionEvents;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;

public final class DistantBeaconsClient implements ClientModInitializer {
    // Register packet handling and the render callback used for remote beams.
    @Override
    public void onInitializeClient() {
        DistantBeaconsConfig.load();
        // The common side already registered the payload codec, but the client
        // also needs its receiver before packets can be handled.
        ClientPlayNetworking.registerGlobalReceiver(
            BeaconBeamPayload.TYPE,
            (payload, context) -> BeaconBeamRenderer.accept(payload)
        );

        ClientPlayConnectionEvents.DISCONNECT.register((handler, client) -> BeaconBeamRenderer.clear());
        LevelRenderEvents.COLLECT_SUBMITS.register(BeaconBeamRenderer::render);
    }
}
