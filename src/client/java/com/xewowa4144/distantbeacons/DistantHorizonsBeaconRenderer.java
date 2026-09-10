package com.xewowa4144.distantbeacons;

import com.seibel.distanthorizons.api.DhApi;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderObjectFactory;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.interfaces.render.IDhApiCustomRenderRegister;
import com.seibel.distanthorizons.api.interfaces.world.IDhApiLevelWrapper;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBoxGroupShading;
import com.seibel.distanthorizons.api.enums.rendering.EDhApiBlockMaterial;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;

import java.awt.Color;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Sends remote beacon geometry into Distant Horizons' generic-object terrain pass.
 *
 * Because DH owns the pass, the beam is depth-tested against DH LOD terrain both
 * with the normal DH renderer and when Iris is driving DH's shader path.
 */
public final class DistantHorizonsBeaconRenderer {
    private static final String RESOURCE_LOCATION = "distantbeacons:beacon_beam";
    private static final Map<BeaconKey, IDhApiRenderableBoxGroup> GROUPS = new HashMap<>();
    private static IDhApiCustomRenderRegister currentRegister;

    private DistantHorizonsBeaconRenderer() {}

    /**
     * Returns true only when DH is actually rendering. Merely having the DH mod
     * installed is not enough: the player can disable DH rendering in its video
     * settings while the mod remains loaded. In that state we must fall back to
     * Minecraft's normal beacon renderer so the real beacon texture is used.
     */
    public static boolean isLoaded() {
        if (!FabricLoader.getInstance().isModLoaded("distanthorizons")) {
            return false;
        }

        try {
            if (DhApi.Delayed.configs == null
                || DhApi.Delayed.configs.graphics() == null
                || DhApi.Delayed.configs.graphics().genericRendering() == null) {
                return false;
            }

            // Both switches matter: renderingEnabled() disables DH's LOD/fake
            // chunk renderer, while beaconRenderingEnabled() controls the generic
            // beacon-object pass. If either is off, Minecraft must retain control
            // of the normal beacon rendering path.
            boolean dhRenderingEnabled = Boolean.TRUE.equals(
                DhApi.Delayed.configs.graphics().renderingEnabled().getValue()
            );
            boolean dhBeaconRenderingEnabled = Boolean.TRUE.equals(
                DhApi.Delayed.configs.graphics().genericRendering()
                    .beaconRenderingEnabled().getValue()
            );
            return dhRenderingEnabled && dhBeaconRenderingEnabled;
        } catch (RuntimeException ignored) {
            // If DH is still initializing or its renderer/config is unavailable,
            // safely fall back to the normal Minecraft renderer for this frame.
            return false;
        }
    }

    /**
     * Rebuilds the DH generic-object groups for the current dimension. This runs
     * during Minecraft's extraction phase, before DH draws its terrain pass.
     */
    public static void sync(Collection<BeaconBeamPayload> beams) {
        if (!isLoaded()) {
            clearGroups();
            return;
        }

        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null) {
            clearGroups();
            return;
        }

        IDhApiLevelWrapper level;
        try {
            if (DhApi.Delayed.worldProxy == null || !DhApi.Delayed.worldProxy.worldLoaded()) {
                clearGroups();
                return;
            }
            level = findCurrentLevel(minecraft.level.dimension().identifier().toString());
        } catch (RuntimeException ignored) {
            clearGroups();
            return;
        }

        if (level == null || level.getRenderRegister() == null || DhApi.Delayed.customRenderObjectFactory == null) {
            clearGroups();
            return;
        }

        IDhApiCustomRenderRegister register = level.getRenderRegister();
        IDhApiCustomRenderObjectFactory factory = DhApi.Delayed.customRenderObjectFactory;
        if (register != currentRegister) {
            clearGroups();
            currentRegister = register;
        }
        String dimension = minecraft.level.dimension().identifier().toString();
        var camera = minecraft.gameRenderer.mainCamera().position();

        Map<BeaconKey, BeaconBeamPayload> wanted = new HashMap<>();
        for (BeaconBeamPayload beam : beams) {
            if (beam.active() && !beam.sections().isEmpty() && dimension.equals(beam.dimension())) {
                wanted.put(new BeaconKey(beam.dimension(), beam.x(), beam.y(), beam.z()), beam);
            }
        }

        GROUPS.entrySet().removeIf(entry -> {
            if (wanted.containsKey(entry.getKey())) {
                return false;
            }
            try {
                register.remove(entry.getValue().getId());
            } catch (RuntimeException ignored) {
                // The DH world may already be unloading.
            }
            return true;
        });

        for (Map.Entry<BeaconKey, BeaconBeamPayload> entry : wanted.entrySet()) {
            BeaconKey key = entry.getKey();
            BeaconBeamPayload beam = entry.getValue();
            IDhApiRenderableBoxGroup group = GROUPS.get(key);

            if (group == null) {
                List<DhApiRenderableBox> boxes = buildBoxes(beam, camera.x, camera.z);
                group = factory.createRelativePositionedGroup(
                    RESOURCE_LOCATION,
                    new DhApiVec3d(beam.x() + 0.5D, beam.y(), beam.z() + 0.5D),
                    boxes
                );
                group.setShading(DhApiRenderableBoxGroupShading.getUnshaded());
                group.setSkyLight(15);
                group.setBlockLight(15);
                group.setSsaoEnabled(false);
                register.add(group);
                GROUPS.put(key, group);
            } else {
                group.clear();
                group.addAll(buildBoxes(beam, camera.x, camera.z));
                group.triggerBoxChange();
            }
        }
    }

    public static void clearGroups() {
        if (!GROUPS.isEmpty()) {
            for (IDhApiRenderableBoxGroup group : GROUPS.values()) {
                try {
                    if (currentRegister != null) {
                        currentRegister.remove(group.getId());
                    }
                } catch (RuntimeException ignored) {
                    // World/DH may already be unloading.
                }
            }
            GROUPS.clear();
        }
        currentRegister = null;
    }

    private static IDhApiLevelWrapper findCurrentLevel(String dimension) {
        if (DhApi.Delayed.worldProxy == null || !DhApi.Delayed.worldProxy.worldLoaded()) {
            return null;
        }
        for (IDhApiLevelWrapper level : DhApi.Delayed.worldProxy.getAllLoadedLevelWrappers()) {
            if (dimension == null || dimension.equals(level.getDimensionName())) {
                return level;
            }
        }
        return null;
    }

    private static List<DhApiRenderableBox> buildBoxes(BeaconBeamPayload beam, double cameraX, double cameraZ) {
        double dx = beam.x() + 0.5D - cameraX;
        double dz = beam.z() + 0.5D - cameraZ;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float widthScale = Math.max(
            1.0F,
            Math.min(
                DistantBeaconsConfig.widthMax,
                (float) horizontalDistance / DistantBeaconsConfig.widthDivisor
            )
        );

        float radius = com.xewowa4144.distantbeacons.BeaconBeamRenderer.solidRadius() * widthScale;
        List<DhApiRenderableBox> boxes = new ArrayList<>();

        int beamStart = 0;
        for (int i = 0; i < beam.sections().size(); i++) {
            BeaconBeamPayload.Section section = beam.sections().get(i);
            int height = section.height();
            if (i == beam.sections().size() - 1) {
                height = Math.max(1, Math.round(DistantBeaconsConfig.beamHeight) - beamStart);
            }
            if (height <= 0) {
                continue;
            }

            boxes.add(new DhApiRenderableBox(
                new DhApiVec3d(-radius, beamStart, -radius),
                new DhApiVec3d(radius, beamStart + height, radius),
                color(section.color()),
                EDhApiBlockMaterial.ILLUMINATED
            ));
            beamStart += height;
        }

        int depth = Math.round(DistantBeaconsConfig.beamDepth);
        if (depth > 0 && !beam.sections().isEmpty()) {
            boxes.add(new DhApiRenderableBox(
                new DhApiVec3d(-radius, -depth, -radius),
                new DhApiVec3d(radius, 0.0D, radius),
                color(beam.sections().get(beam.sections().size() - 1).color()),
                EDhApiBlockMaterial.ILLUMINATED
            ));
        }

        return boxes;
    }

    private static Color color(int rgb) {
        return new Color(
            (rgb >> 16) & 0xFF,
            (rgb >> 8) & 0xFF,
            rgb & 0xFF,
            255
        );
    }

    private record BeaconKey(String dimension, int x, int y, int z) {}
}
