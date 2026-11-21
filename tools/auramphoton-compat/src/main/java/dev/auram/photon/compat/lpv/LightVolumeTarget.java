package dev.auram.photon.compat.lpv;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL41C.*;

record LightVolumeTarget(int texture, int framebuffer) implements AutoCloseable {
    static LightVolumeTarget volume(int side, boolean integer) {
        return allocate(side, side, side, integer);
    }

    static LightVolumeTarget palette() {
        return allocate(48, 2, 0, false);
    }

    private static LightVolumeTarget allocate(int width, int height, int depth, boolean integer) {
        int target = depth == 0 ? GL_TEXTURE_2D : GL_TEXTURE_3D;
        int oldTexture = glGetInteger(depth == 0 ? GL_TEXTURE_BINDING_2D : GL_TEXTURE_BINDING_3D);
        int oldDraw = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int oldUnpack = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
        LightVolumeTarget result = new LightVolumeTarget(glGenTextures(), glGenFramebuffers());
        try {
            glBindTexture(target, result.texture);
            glTexParameteri(target, GL_TEXTURE_MIN_FILTER, integer ? GL_NEAREST : GL_LINEAR);
            glTexParameteri(target, GL_TEXTURE_MAG_FILTER, integer ? GL_NEAREST : GL_LINEAR);
            glTexParameteri(target, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(target, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
            if (depth == 0) {
                glTexImage2D(target, 0, GL_RGBA16F, width, height, 0, GL_RGBA, GL_HALF_FLOAT, (ByteBuffer) null);
            } else {
                glTexParameteri(target, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
                glTexImage3D(target, 0, integer ? GL_R8UI : GL_RGBA16F, width, height, depth, 0,
                        integer ? GL_RED_INTEGER : GL_RGBA, integer ? GL_UNSIGNED_BYTE : GL_HALF_FLOAT,
                        (ByteBuffer) null);
            }

            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, result.framebuffer);
            glFramebufferTexture(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, result.texture, 0);
            glDrawBuffer(GL_COLOR_ATTACHMENT0);
            int status = glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("AuramPhoton LPV framebuffer for " + width + "×" + height + "×"
                        + depth + " is incomplete (OpenGL 0x" + Integer.toHexString(status) + ")");
            }
            return result;
        } catch (RuntimeException | Error failure) {
            result.close();
            throw failure;
        } finally {
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, oldUnpack);
            glBindTexture(target, oldTexture);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, oldDraw);
        }
    }

    @Override
    public void close() {
        glDeleteFramebuffers(framebuffer);
        glDeleteTextures(texture);
    }
}
