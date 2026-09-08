/** Loads, validates, saves, and resets the client-side rendering configuration. */
package com.xewowa4144.distantbeacons;

import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Client-only settings for the existing beam scaling, camera culling distance, and
 * the final remote beam height.
 */
public final class DistantBeaconsConfig {
    public static final float DEFAULT_WIDTH_DIVISOR = 128.0F;
    public static final float DEFAULT_WIDTH_MAX = 16384.0F;
    public static final float DEFAULT_CULL_DISTANCE = 1048576.0F;
    public static final float DEFAULT_BEAM_HEIGHT = 100000.0F;
    public static final float DEFAULT_BEAM_DEPTH = 100000.0F;

    // These fields are read directly by the renderer, so changes take effect immediately.
    public static float widthDivisor = DEFAULT_WIDTH_DIVISOR;
    public static float widthMax = DEFAULT_WIDTH_MAX;
    public static float cullDistance = DEFAULT_CULL_DISTANCE;
    public static float beamHeight = DEFAULT_BEAM_HEIGHT;
    public static float beamDepth = DEFAULT_BEAM_DEPTH;

    private static final Path FILE = FabricLoader.getInstance()
        .getConfigDir()
        .resolve("distantbeacons.properties");

    private DistantBeaconsConfig() {}

    // Load saved values and clamp them to safe supported ranges.
    public static void load() {
        if (!Files.exists(FILE)) {
            save();
            return;
        }

        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(FILE)) {
            properties.load(reader);

            widthDivisor = readFloat(properties, "width_divisor", DEFAULT_WIDTH_DIVISOR, 1.0F, 256.0F);
            widthMax = readFloat(properties, "width_max", DEFAULT_WIDTH_MAX, 1.0F, 16384.0F);
            cullDistance = readFloat(properties, "cull_distance", DEFAULT_CULL_DISTANCE, 1024.0F, 1048576.0F);
            beamHeight = readFloat(properties, "beam_height", DEFAULT_BEAM_HEIGHT, 2048.0F, 2000000.0F);
            beamDepth = readFloat(properties, "beam_depth", DEFAULT_BEAM_DEPTH, 0.0F, 2000000.0F);
        } catch (IOException | RuntimeException ignored) {
            reset();
            save();
        }
    }

    // Persist the current values in the client config file.
    public static void save() {
        Properties properties = new Properties();
        properties.setProperty("width_divisor", Float.toString(widthDivisor));
        properties.setProperty("width_max", Float.toString(widthMax));
        properties.setProperty("cull_distance", Float.toString(cullDistance));
        properties.setProperty("beam_height", Float.toString(beamHeight));
        properties.setProperty("beam_depth", Float.toString(beamDepth));

        try {
            Files.createDirectories(FILE.getParent());
            try (Writer writer = Files.newBufferedWriter(FILE)) {
                properties.store(writer, "Distant Beacons client settings");
            }
        } catch (IOException ignored) {
            // Keep the current in-memory settings if the config file cannot be written.
        }
    }

    // Restore all settings to their built-in defaults.
    public static void reset() {
        widthDivisor = DEFAULT_WIDTH_DIVISOR;
        widthMax = DEFAULT_WIDTH_MAX;
        cullDistance = DEFAULT_CULL_DISTANCE;
        beamHeight = DEFAULT_BEAM_HEIGHT;
        beamDepth = DEFAULT_BEAM_DEPTH;
    }

    // Invalid, missing, or out-of-range values fall back safely to the supplied default.
    private static float readFloat(Properties properties, String key, float fallback, float min, float max) {
        try {
            float value = Float.parseFloat(properties.getProperty(key, Float.toString(fallback)));
            if (!Float.isFinite(value)) {
                return fallback;
            }
            return Math.max(min, Math.min(max, value));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
