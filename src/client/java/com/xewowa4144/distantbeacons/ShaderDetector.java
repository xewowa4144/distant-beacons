package com.xewowa4144.distantbeacons;

import net.fabricmc.loader.api.FabricLoader;

import java.lang.reflect.Method;

/**
 * Detects whether Iris currently has a shader pack actively rendering.
 *
 * Reflection is used deliberately so Distant Beacons does not require Iris
 * to be installed. When Iris is absent, this simply returns false.
 */
public final class ShaderDetector {
    private ShaderDetector() {}

    public static boolean isShaderPackInUse() {
        if (!FabricLoader.getInstance().isModLoaded("iris")) {
            return false;
        }

        try {
            Class<?> irisApiClass = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Method getInstance = irisApiClass.getMethod("getInstance");
            Object api = getInstance.invoke(null);
            Method isShaderPackInUse = irisApiClass.getMethod("isShaderPackInUse");
            return Boolean.TRUE.equals(isShaderPackInUse.invoke(api));
        } catch (ReflectiveOperationException | LinkageError ignored) {
            // If an older/incompatible Iris build does not expose the API,
            // fall back to the normal Minecraft renderer.
            return false;
        }
    }
}
