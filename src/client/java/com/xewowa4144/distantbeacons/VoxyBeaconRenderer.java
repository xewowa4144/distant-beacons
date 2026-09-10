package com.xewowa4144.distantbeacons;

import me.cortex.voxy.client.core.gl.GlBuffer;
import me.cortex.voxy.client.core.gl.GlVertexArray;
import me.cortex.voxy.client.core.gl.shader.Shader;
import me.cortex.voxy.client.core.gl.shader.ShaderType;
import me.cortex.voxy.client.core.rendering.Viewport;
import org.joml.Matrix4f;
import org.lwjgl.system.MemoryStack;
import org.lwjgl.system.MemoryUtil;

import java.nio.FloatBuffer;
import java.util.Collection;
import java.util.concurrent.atomic.AtomicReference;

import static org.lwjgl.opengl.GL11C.GL_BLEND;
import static org.lwjgl.opengl.GL11C.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_FUNC;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_TEST;
import static org.lwjgl.opengl.GL11C.GL_DEPTH_WRITEMASK;
import static org.lwjgl.opengl.GL11C.GL_FLOAT;
import static org.lwjgl.opengl.GL11C.GL_TRIANGLES;
import static org.lwjgl.opengl.GL11C.GL_ONE_MINUS_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.GL_SRC_ALPHA;
import static org.lwjgl.opengl.GL11C.glBlendFunc;
import static org.lwjgl.opengl.GL11C.glDepthFunc;
import static org.lwjgl.opengl.GL11C.glDepthMask;
import static org.lwjgl.opengl.GL11C.glDisable;
import static org.lwjgl.opengl.GL11C.glDrawArrays;
import static org.lwjgl.opengl.GL11C.glEnable;
import static org.lwjgl.opengl.GL11C.glGetBoolean;
import static org.lwjgl.opengl.GL11C.glGetInteger;
import static org.lwjgl.opengl.GL11C.glIsEnabled;
import static org.lwjgl.opengl.GL20C.glUniformMatrix4fv;
import static org.lwjgl.opengl.GL44C.GL_DYNAMIC_STORAGE_BIT;
import static org.lwjgl.opengl.GL45C.glNamedBufferSubData;

/**
 * Renders Distant Beacons inside Voxy's own render pass.
 *
 * Voxy keeps its LOD terrain in a separate framebuffer/depth pipeline. Drawing
 * the remote beacon geometry here lets the normal OpenGL depth test compare the
 * beam against Voxy's LOD terrain instead of forcing Voxy to write its LOD depth
 * into Minecraft's vanilla depth buffer.
 */
public final class VoxyBeaconRenderer {
    private static final int FLOATS_PER_VERTEX = 7;
    private static final int VERTICES_PER_BEAM = 48;
    private static final float SOLID_BEAM_RADIUS = 0.2F;
    private static final float BEAM_GLOW_RADIUS = 0.25F;
    private static final float BEAM_GLOW_ALPHA = 32.0F / 255.0F;

    private static final AtomicReference<Shader> SHADER = new AtomicReference<>();
    private static GlVertexArray vao;
    private static GlBuffer vertexBuffer;
    private static int vertexCount;

    private VoxyBeaconRenderer() {}

    public static void render(Viewport<?> viewport, int closerEqualDepthCompare) {
        // This renderer exists specifically as the shader+Voxy fallback. When shaders
        // are disabled, BeaconBeamRenderer uses the normal vanilla beacon renderer.
        if (!ShaderDetector.isShaderPackInUse()) {
            return;
        }

        Collection<BeaconBeamPayload> beams = BeaconBeamRenderer.snapshot();
        if (beams.isEmpty()) {
            return;
        }

        ensureGpuObjects();
        upload(viewport, beams);
        if (vertexCount == 0) {
            return;
        }

        boolean depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        int depthFunc = glGetInteger(GL_DEPTH_FUNC);
        boolean cull = glIsEnabled(GL_CULL_FACE);
        boolean blend = glIsEnabled(GL_BLEND);
        boolean depth = glIsEnabled(GL_DEPTH_TEST);

        glEnable(GL_DEPTH_TEST);
        glDepthFunc(closerEqualDepthCompare);
        glEnable(GL_BLEND);
        glDisable(GL_CULL_FACE);
        glDepthMask(true);
        glBlendFunc(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA);

        Shader shader = SHADER.get();
        shader.bind();
        try (MemoryStack stack = MemoryStack.stackPush()) {
            var matrix = stack.mallocFloat(16);
            new Matrix4f(viewport.MVP)
                .translate(-viewport.innerTranslation.x, -viewport.innerTranslation.y, -viewport.innerTranslation.z)
                .get(matrix);
            glUniformMatrix4fv(0, false, matrix);
        }

        vao.bind();
        glDrawArrays(GL_TRIANGLES, 0, vertexCount);

        glDepthMask(depthMask);
        glDepthFunc(depthFunc);
        if (!depth) glDisable(GL_DEPTH_TEST);
        if (!blend) glDisable(GL_BLEND);
        if (cull) glEnable(GL_CULL_FACE);
    }

    private static void ensureGpuObjects() {
        if (SHADER.get() == null) {
            Shader shader = Shader.make()
                .addSource(ShaderType.VERTEX, """
                    #version 460 core
                    layout(location = 0) uniform mat4 MVP;
                    layout(location = 0) in vec3 Position;
                    layout(location = 1) in vec4 Colour;
                    layout(location = 0) out vec4 colour;
                    void main() {
                        gl_Position = MVP * vec4(Position, 1.0);
                        colour = Colour;
                    }
                    """)
                .addSource(ShaderType.FRAGMENT, """
                    #version 460 core
                    layout(location = 0) in vec4 colour;
                    layout(location = 0) out vec4 outColour;
                    void main() {
                        outColour = colour;
                    }
                    """)
                .compile()
                .name("Distant Beacons Voxy shader");
            if (!SHADER.compareAndSet(null, shader)) {
                shader.free();
            }
        }
        if (vao == null) {
            vao = new GlVertexArray()
                .setStride(FLOATS_PER_VERTEX * Float.BYTES)
                .setF(0, GL_FLOAT, 3, 0)
                .setF(1, GL_FLOAT, 4, 3 * Float.BYTES);
        }
    }

    private static void upload(Viewport<?> viewport, Collection<BeaconBeamPayload> beams) {
        // The payload cache is updated independently of Voxy's viewport movement,
        // so upload every render. This is still cheap because only compact beam
        // records are converted to a contiguous GPU buffer here.

        int currentVertexCount = 0;
        int depth = Math.round(DistantBeaconsConfig.beamDepth);
        for (BeaconBeamPayload beam : beams) {
            if (!beam.active() || beam.sections().isEmpty()) {
                continue;
            }
            currentVertexCount += beam.sections().size() * VERTICES_PER_BEAM;
            if (depth > 0) {
                currentVertexCount += 24;
            }
        }
        vertexCount = currentVertexCount;
        long size = (long) vertexCount * FLOATS_PER_VERTEX * Float.BYTES;
        if (size == 0) {
            return;
        }

        if (vertexBuffer == null || vertexBuffer.size() < size) {
            if (vertexBuffer != null) {
                vertexBuffer.free();
            }
            vertexBuffer = new GlBuffer(size, GL_DYNAMIC_STORAGE_BIT, false).name("Distant Beacons Voxy buffer");
            vao.bindBuffer(vertexBuffer.id);
        }

        FloatBuffer buffer = MemoryUtil.memAllocFloat((int) (size / Float.BYTES));
        try {
            String dimension = net.minecraft.client.Minecraft.getInstance().level.dimension().identifier().toString();
            for (BeaconBeamPayload beam : beams) {
                if (!beam.active() || beam.sections().isEmpty() || !beam.dimension().equals(dimension)) {
                    continue;
                }
                writeBeam(buffer, viewport, beam);
            }
            buffer.flip();
            glNamedBufferSubData(vertexBuffer.id, 0, buffer);
        } finally {
            MemoryUtil.memFree(buffer);
        }
    }

    private static void writeBeam(FloatBuffer out, Viewport<?> viewport, BeaconBeamPayload beam) {
        float baseX = viewport.section.x << 5;
        float baseY = viewport.section.y << 5;
        float baseZ = viewport.section.z << 5;

        float cx = beam.x() - baseX + 0.5F;
        float cy = beam.y() - baseY;
        float cz = beam.z() - baseZ + 0.5F;

        double dx = beam.x() + 0.5D - viewport.cameraX;
        double dz = beam.z() + 0.5D - viewport.cameraZ;
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        float widthScale = Math.max(
            1.0F,
            Math.min(
                DistantBeaconsConfig.widthMax,
                (float) horizontalDistance / DistantBeaconsConfig.widthDivisor
            )
        );

        int beamStart = 0;
        for (int i = 0; i < beam.sections().size(); i++) {
            BeaconBeamPayload.Section section = beam.sections().get(i);
            int height = section.height();
            if (i == beam.sections().size() - 1) {
                height = Math.max(1, Math.round(DistantBeaconsConfig.beamHeight) - beamStart);
            }
            if (height <= 0) continue;

            int color = section.color();
            float r = ((color >> 16) & 0xFF) / 255.0F;
            float g = ((color >> 8) & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;
            float y0 = cy + beamStart;
            float y1 = y0 + height;

            writeBox(out, cx, y0, cz, y1, SOLID_BEAM_RADIUS * widthScale, r, g, b, 1.0F);
            writeBox(out, cx, y0, cz, y1, BEAM_GLOW_RADIUS * widthScale, r, g, b, BEAM_GLOW_ALPHA);
            beamStart += height;
        }

        int depth = Math.round(DistantBeaconsConfig.beamDepth);
        if (depth > 0) {
            BeaconBeamPayload.Section section = beam.sections().get(beam.sections().size() - 1);
            int color = section.color();
            float r = ((color >> 16) & 0xFF) / 255.0F;
            float g = ((color >> 8) & 0xFF) / 255.0F;
            float b = (color & 0xFF) / 255.0F;
            writeBox(out, cx, cy - depth, cz, cy, BEAM_GLOW_RADIUS * widthScale, r, g, b, BEAM_GLOW_ALPHA);
        }
    }

    private static void writeBox(FloatBuffer out, float cx, float y0, float cz, float y1, float radius,
                                 float r, float g, float b, float a) {
        writeQuad(out, cx - radius, y0, cz - radius, cx + radius, y0, cz - radius,
            cx + radius, y1, cz - radius, cx - radius, y1, cz - radius, r, g, b, a);
        writeQuad(out, cx + radius, y0, cz + radius, cx - radius, y0, cz + radius,
            cx - radius, y1, cz + radius, cx + radius, y1, cz + radius, r, g, b, a);
        writeQuad(out, cx - radius, y0, cz + radius, cx - radius, y0, cz - radius,
            cx - radius, y1, cz - radius, cx - radius, y1, cz + radius, r, g, b, a);
        writeQuad(out, cx + radius, y0, cz - radius, cx + radius, y0, cz + radius,
            cx + radius, y1, cz + radius, cx + radius, y1, cz - radius, r, g, b, a);
    }

    private static void writeQuad(FloatBuffer out,
                                  float ax, float ay, float az,
                                  float bx, float by, float bz,
                                  float cx, float cy, float cz,
                                  float dx, float dy, float dz,
                                  float r, float g, float b, float a) {
        writeVertex(out, ax, ay, az, r, g, b, a);
        writeVertex(out, bx, by, bz, r, g, b, a);
        writeVertex(out, cx, cy, cz, r, g, b, a);
        writeVertex(out, ax, ay, az, r, g, b, a);
        writeVertex(out, cx, cy, cz, r, g, b, a);
        writeVertex(out, dx, dy, dz, r, g, b, a);
    }

    private static void writeVertex(FloatBuffer out, float x, float y, float z,
                                    float r, float g, float b, float a) {
        out.put(x).put(y).put(z).put(r).put(g).put(b).put(a);
    }

    public static void free() {
        Shader shader = SHADER.getAndSet(null);
        if (shader != null) shader.free();
        if (vao != null) {
            vao.free();
            vao = null;
        }
        if (vertexBuffer != null) {
            vertexBuffer.free();
            vertexBuffer = null;
        }
        vertexCount = 0;
    }
}
