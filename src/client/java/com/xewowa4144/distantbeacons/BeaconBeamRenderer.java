/** Stores server-supplied beacon beam data and renders it independently of normal beacon chunk rendering. */
package com.xewowa4144.distantbeacons;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BeaconBeamRenderer {
    private static final Map<BeaconKey, BeaconBeamPayload> BEAMS = new ConcurrentHashMap<>();

    private BeaconBeamRenderer() {}

    // Replace the cached beacon state, or remove it when the server reports it inactive.
    public static void accept(BeaconBeamPayload payload) {
        BeaconKey key = new BeaconKey(payload.dimension(), payload.x(), payload.y(), payload.z());
        if (payload.active() && !payload.sections().isEmpty()) {
            BEAMS.put(key, payload);
        } else {
            BEAMS.remove(key);
        }
    }

    public static void clear() {
        BEAMS.clear();
    }

    // Render only beams in the player's current dimension. The source chunks need not be client-loaded.
    public static void render(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || BEAMS.isEmpty()) {
            return;
        }

        String currentDimension = minecraft.level.dimension().identifier().toString();
        var cameraPos = context.gameRenderer().mainCamera().position();
        long gameTime = minecraft.level.getGameTime();
        PoseStack poseStack = context.poseStack();
        var collector = context.submitNodeCollector();

        for (BeaconBeamPayload beam : BEAMS.values()) {
            if (!beam.dimension().equals(currentDimension)) {
                continue;
            }

            double dx = beam.x() + 0.5D - cameraPos.x;
            double dz = beam.z() + 0.5D - cameraPos.z;
            // Use horizontal distance because beacon thickness is meant to respond to how far
            // the camera is from the beacon on the X/Z plane, not to its vertical position.
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

            // The divisor controls how quickly distant beams grow. The maximum prevents the
            // configured width from becoming unbounded at extreme distances.
            float distantWidthScale = Math.max(
                1.0F,
                Math.min(
                    DistantBeaconsConfig.widthMax,
                    (float) horizontalDistance / DistantBeaconsConfig.widthDivisor
                )
            );
            float solidRadius = BeaconRenderer.SOLID_BEAM_RADIUS * distantWidthScale;
            float glowRadius = BeaconRenderer.BEAM_GLOW_RADIUS * distantWidthScale;

            poseStack.pushPose();
            poseStack.translate(
                beam.x() - cameraPos.x,
                beam.y() - cameraPos.y,
                beam.z() - cameraPos.z
            );

            {
                int beamStart = 0;
                var sections = beam.sections();
                for (int i = 0; i < sections.size(); i++) {
                    BeaconBeamPayload.Section section = sections.get(i);
                    int height = section.height();
                    if (i == sections.size() - 1) {
                        height = Math.max(1, Math.round(DistantBeaconsConfig.beamHeight) - beamStart);
                    }
                    if (height <= 0) {
                        continue;
                    }

                    // Reuse Minecraft's own beacon submission routine so the remote beam keeps
                    // vanilla beam textures, animation, colors, and glow behavior.
                    BeaconRenderer.submitBeaconBeam(
                        poseStack,
                        collector,
                        BeaconRenderer.BEAM_LOCATION,
                        1.0F,
                        (float) gameTime,
                        beamStart,
                        height,
                        section.color(),
                        solidRadius,
                        glowRadius
                    );
                    beamStart += height;
                }
            }

            // The downward extension is separate from the normal upward sections. This keeps
            // the beam visually continuous when the terrain below a distant beacon is unloaded.
            int depth = Math.round(DistantBeaconsConfig.beamDepth);
            if (depth > 0 && !beam.sections().isEmpty()) {
                BeaconBeamPayload.Section section = beam.sections().get(beam.sections().size() - 1);
                poseStack.pushPose();
                poseStack.translate(0.0D, -depth, 0.0D);
                BeaconRenderer.submitBeaconBeam(
                    poseStack,
                    collector,
                    BeaconRenderer.BEAM_LOCATION,
                    1.0F,
                    (float) gameTime,
                    0,
                    depth,
                    section.color(),
                    solidRadius,
                    glowRadius
                );
                poseStack.popPose();
            }

            poseStack.popPose();
        }
    }

    private record BeaconKey(String dimension, int x, int y, int z) {}
}
