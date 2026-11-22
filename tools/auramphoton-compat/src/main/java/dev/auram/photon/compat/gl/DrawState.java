package dev.auram.photon.compat.gl;

import org.lwjgl.system.MemoryStack;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL41C.*;

public final class DrawState implements AutoCloseable {
    private static final int[] CAPABILITIES = {
            GL_BLEND, GL_CULL_FACE, GL_DEPTH_TEST, GL_SCISSOR_TEST, GL_STENCIL_TEST,
            GL_RASTERIZER_DISCARD, GL_COLOR_LOGIC_OP
    };

    private final int framebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
    private final int program = glGetInteger(GL_CURRENT_PROGRAM);
    private final int vao;
    private final float[] viewport = new float[4];
    private final boolean[] colorMask = new boolean[4];
    private final boolean[] enabled = new boolean[CAPABILITIES.length];
    private final boolean changedPointSize;

    public DrawState(int vao, boolean points) {
        this.vao = vao == 0 ? -1 : glGetInteger(GL_VERTEX_ARRAY_BINDING);
        changedPointSize = points && !glIsEnabled(GL_PROGRAM_POINT_SIZE);
        glGetFloati_v(GL_VIEWPORT, 0, viewport);
        try (MemoryStack stack = MemoryStack.stackPush()) {
            ByteBuffer previousMask = stack.malloc(4);
            glGetBooleani_v(GL_COLOR_WRITEMASK, 0, previousMask);
            for (int i = 0; i < colorMask.length; i++) {
                colorMask[i] = previousMask.get(i) != 0;
            }
        }
        for (int i = 0; i < CAPABILITIES.length; i++) {
            int capability = CAPABILITIES[i];
            if (capability == GL_BLEND || capability == GL_SCISSOR_TEST) {
                enabled[i] = glIsEnabledi(capability, 0);
                if (enabled[i]) glDisablei(capability, 0);
            } else {
                enabled[i] = glIsEnabled(capability);
                if (enabled[i]) glDisable(capability);
            }
        }
        glColorMaski(0, true, true, true, true);
        if (changedPointSize) glEnable(GL_PROGRAM_POINT_SIZE);
        if (vao != 0) glBindVertexArray(vao);
    }

    public void bind(int target, int shader, int width, int height) {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, target);
        glUseProgram(shader);
        glViewportIndexedf(0, 0, 0, width, height);
    }

    @Override
    public void close() {
        glViewportIndexedf(0, viewport[0], viewport[1], viewport[2], viewport[3]);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
        glUseProgram(program);
        if (vao >= 0) glBindVertexArray(vao);
        if (changedPointSize) glDisable(GL_PROGRAM_POINT_SIZE);
        glColorMaski(0, colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
        for (int i = 0; i < CAPABILITIES.length; i++) {
            if (!enabled[i]) continue;
            int capability = CAPABILITIES[i];
            if (capability == GL_BLEND || capability == GL_SCISSOR_TEST) {
                glEnablei(capability, 0);
            } else {
                glEnable(capability);
            }
        }
    }
}
