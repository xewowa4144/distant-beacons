/** Stores server-supplied beacon beam data and renders it independently of normal beacon chunk rendering. */
package com.xewowa4144.distantbeacons;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BeaconRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class BeaconBeamRenderer {
    private static final Map<BeaconKey, BeaconBeamPayload> BEAMS = new ConcurrentHashMap<>();
    static float solidRadius() { return BeaconRenderer.SOLID_BEAM_RADIUS; }

    private static final Identifier WHITE_TEXTURE = Identifier.fromNamespaceAndPath(
        "distantbeacons", "textures/misc/white.png"
    );

    private BeaconBeamRenderer() {}

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
        if (DistantHorizonsBeaconRenderer.isLoaded()) {
            DistantHorizonsBeaconRenderer.clearGroups();
        }
    }

    /** Snapshot view used by the optional Voxy render pass. */
    public static java.util.Collection<BeaconBeamPayload> snapshot() {
        return BEAMS.values();
    }

    public static void render(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || BEAMS.isEmpty()) {
            return;
        }

        boolean voxyLoaded = FabricLoader.getInstance().isModLoaded("voxy");
        boolean shadersActive = ShaderDetector.isShaderPackInUse();

        // Distant Horizons owns the far terrain pass in the dedicated DH build.
        // DistantHorizonsBeaconRenderer submits the remote beam into that pass so
        // DH's LOD depth can occlude it with or without Iris shaders.
        if (DistantHorizonsBeaconRenderer.isLoaded()) {
            DistantHorizonsBeaconRenderer.sync(BEAMS.values());
            return;
        }

        // Voxy has its own proven custom-beam pass. When both Voxy and Iris are active,
        // do not submit a second beam here: VoxyBeaconRenderer draws the beam inside
        // Voxy's framebuffer/depth pipeline so it can be occluded by Voxy LODs.
        if (voxyLoaded && shadersActive) {
            return;
        }

        String currentDimension = minecraft.level.dimension().identifier().toString();
        var cameraPos = context.gameRenderer().mainCamera().position();
        long gameTime = minecraft.level.getGameTime();
        PoseStack poseStack = context.poseStack();

        // Iris + no Voxy: deliberately use ordinary entity geometry instead of
        // BeaconRenderer.submitBeaconBeam(). This keeps the beam out of Iris's special
        // beacon-beam shader path and gives us the same square/prismatic geometry as
        // the Voxy custom implementation.
        for (BeaconBeamPayload beam : BEAMS.values()) {
            if (!beam.dimension().equals(currentDimension)) {
                continue;
            }

            double dx = beam.x() + 0.5D - cameraPos.x;
            double dz = beam.z() + 0.5D - cameraPos.z;
            double horizontalDistance = Math.sqrt(dx * dx + dz * dz);

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
            // Vanilla BeaconRenderer geometry is already centered around the
            // block origin. The custom square geometry is centered at +0.5/+0.5.
            // Keep the two coordinate conventions separate so the normal
            // (no-shader) beam stays centered on the beacon block.
            double renderX = beam.x() - cameraPos.x;
            double renderZ = beam.z() - cameraPos.z;
            if (shadersActive) {
                renderX += 0.5D;
                renderZ += 0.5D;
            }
            poseStack.translate(renderX, beam.y() - cameraPos.y, renderZ);

            if (shadersActive) {
                context.submitNodeCollector().submitCustomGeometry(
                    poseStack,
                    RenderTypes.entityCutout(WHITE_TEXTURE),
                    (pose, consumer) -> renderFakeBeam(pose, consumer, beam, solidRadius, glowRadius)
                );
            } else {
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

                    BeaconRenderer.submitBeaconBeam(
                        poseStack,
                        context.submitNodeCollector(),
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

            if (!shadersActive) {
                int depth = Math.round(DistantBeaconsConfig.beamDepth);
                if (depth > 0 && !beam.sections().isEmpty()) {
                    BeaconBeamPayload.Section section = beam.sections().get(beam.sections().size() - 1);
                    poseStack.pushPose();
                    poseStack.translate(0.0D, -depth, 0.0D);
                    BeaconRenderer.submitBeaconBeam(
                        poseStack,
                        context.submitNodeCollector(),
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
            }

            poseStack.popPose();
        }
    }

    private static void renderFakeBeam(PoseStack.Pose pose, VertexConsumer consumer,
                                       BeaconBeamPayload beam, float solidRadius, float glowRadius) {
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

            int color = section.color();
            float r = ((color >> 16) & 0xFF) / 255.0F;
            float g = ((color >> 8) & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;
            float y0 = beamStart;
            float y1 = y0 + height;

            // The solid pass is the important part: it is a plain colored prism,
            // not a Minecraft beacon beam, so Iris cannot route it through the
            // special beacon-beam shader.
            writeBox(consumer, pose, y0, y1, solidRadius, r, g, b, 1.0F);
            beamStart += height;
        }

        int depth = Math.round(DistantBeaconsConfig.beamDepth);
        if (depth > 0 && !sections.isEmpty()) {
            int color = sections.get(sections.size() - 1).color();
            float r = ((color >> 16) & 0xFF) / 255.0F;
            float g = ((color >> 8) & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;
            writeBox(consumer, pose, -depth, 0.0F, glowRadius, r, g, b, 1.0F);
        }
    }

    private static void writeBox(VertexConsumer out, PoseStack.Pose pose,
                                 float y0, float y1, float radius,
                                 float r, float g, float b, float a) {
        writeQuad(out, pose,
            -radius, y0, -radius, radius, y0, -radius,
            radius, y1, -radius, -radius, y1, -radius, r, g, b, a);
        writeQuad(out, pose,
            radius, y0, radius, -radius, y0, radius,
            -radius, y1, radius, radius, y1, radius, r, g, b, a);
        writeQuad(out, pose,
            -radius, y0, radius, -radius, y0, -radius,
            -radius, y1, -radius, -radius, y1, radius, r, g, b, a);
        writeQuad(out, pose,
            radius, y0, -radius, radius, y0, radius,
            radius, y1, radius, radius, y1, -radius, r, g, b, a);
    }

    private static void writeQuad(VertexConsumer out, PoseStack.Pose pose,
                                  float ax, float ay, float az,
                                  float bx, float by, float bz,
                                  float cx, float cy, float cz,
                                  float dx, float dy, float dz,
                                  float r, float g, float b, float a) {
        vertex(out, pose, ax, ay, az, r, g, b, a, 0.0F, 0.0F);
        vertex(out, pose, bx, by, bz, r, g, b, a, 1.0F, 0.0F);
        vertex(out, pose, cx, cy, cz, r, g, b, a, 1.0F, 1.0F);
        vertex(out, pose, ax, ay, az, r, g, b, a, 0.0F, 0.0F);
        vertex(out, pose, cx, cy, cz, r, g, b, a, 1.0F, 1.0F);
        vertex(out, pose, dx, dy, dz, r, g, b, a, 0.0F, 1.0F);
    }

    private static void vertex(VertexConsumer out, PoseStack.Pose pose,
                               float x, float y, float z,
                               float r, float g, float b, float a,
                               float u, float v) {
        out.addVertex(pose, x, y, z)
            .setColor(
                Math.round(r * 255.0F),
                Math.round(g * 255.0F),
                Math.round(b * 255.0F),
                Math.round(a * 255.0F)
            )
            .setUv(u, v)
            .setOverlay(0)
            .setLight(0x00F000F0)
            .setNormal(pose, 0.0F, 1.0F, 0.0F);
    }

    private record BeaconKey(String dimension, int x, int y, int z) {}
}
