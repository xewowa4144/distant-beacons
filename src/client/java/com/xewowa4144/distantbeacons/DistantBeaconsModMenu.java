/** Connects Distant Beacons to Mod Menu's configuration-screen entry point. */
package com.xewowa4144.distantbeacons;

import com.terraformersmc.modmenu.api.ConfigScreenFactory;
import com.terraformersmc.modmenu.api.ModMenuApi;

public final class DistantBeaconsModMenu implements ModMenuApi {
    @Override
    public ConfigScreenFactory<?> getModConfigScreenFactory() {
        return DistantBeaconsConfigScreen::new;
    }
}
