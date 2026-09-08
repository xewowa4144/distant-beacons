/** Common mod entry point: initializes networking and starts the server-side beacon registry. */
package com.xewowa4144.distantbeacons;

import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class DistantBeaconsMod implements ModInitializer {
    public static final String MOD_ID = "distantbeacons";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    @Override
    public void onInitialize() {
        LOGGER.info("Distant Beacons loaded. Waiting for a server world to start.");
        BeaconNetwork.initCommon();
        ServerLifecycleEvents.SERVER_STARTED.register(DistantBeaconsScanManager::start);
    }
}
