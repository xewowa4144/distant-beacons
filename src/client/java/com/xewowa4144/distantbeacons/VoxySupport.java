package com.xewowa4144.distantbeacons;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Optional Voxy integration without making Voxy a hard runtime dependency. */
public final class VoxySupport {
    private VoxySupport() {}

    public static boolean isRenderingEnabled() {
        if (!FabricLoader.getInstance().isModLoaded("voxy")) {
            return false;
        }

        try {
            Class<?> configClass = Class.forName("me.cortex.voxy.client.config.VoxyConfig");
            Field configField = configClass.getField("CONFIG");
            Object config = configField.get(null);
            Method method = configClass.getMethod("isRenderingEnabled");
            return Boolean.TRUE.equals(method.invoke(config));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // If the optional Voxy config API is unavailable, preserve the old
            // behavior and assume Voxy rendering is active when Voxy is loaded.
            return true;
        }
    }
}
