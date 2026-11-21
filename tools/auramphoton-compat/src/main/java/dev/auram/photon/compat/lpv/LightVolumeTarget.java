package dev.auram.photon.compat.lpv;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL41C.*;

record LightVolumeTarget(int texture, int framebuffer) implements AutoCloseable {
    static LightVolumeTarget rectangle(int texture) {
        int saved = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        int framebuffer = glGenFramebuffers();
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
        try {
            glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_RECTANGLE, texture, 0);
            glDrawBuffer(GL_COLOR_ATTACHMENT0);
            int status = glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER);
            if (status != GL_FRAMEBUFFER_COMPLETE) {
                throw new IllegalStateException("AuramPhoton shared rectangle framebuffer status 0x" + Integer.toHexString(status));
            }
            return new LightVolumeTarget(texture, framebuffer);
        } catch (RuntimeException | Error e) {
            glDeleteFramebuffers(framebuffer);
            throw e;
        } finally {
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, saved);
        }
    }

    static LightVolumeTarget volume(int side) {
        return allocate(side, side, side, false);
    }

    static LightVolumeTarget occupancy(int width, int height) {
        return allocate(width, height, 0, true);
    }

    static LightVolumeTarget palette() {
        return allocate(48, 2, 0, false);
    }

    private static LightVolumeTarget allocate(int width, int height, int depth, boolean integer) {
        int target = depth == 0 ? GL_TEXTURE_2D : GL_TEXTURE_3D;
        boolean rgbVolume = depth > 0 && "Apple".equals(glGetString(GL_VENDOR));
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
                glTexImage2D(target, 0, integer ? GL_R8UI : GL_RGBA16F, width, height, 0,
                        integer ? GL_RED_INTEGER : GL_RGBA, integer ? GL_UNSIGNED_BYTE : GL_HALF_FLOAT,
                        (ByteBuffer) null);
            } else {
                glTexParameteri(target, GL_TEXTURE_WRAP_R, GL_CLAMP_TO_EDGE);
                // Every light-volume reader samples RGB. Apple's driver can render to RGB16F.
                glTexImage3D(target, 0, rgbVolume ? GL_RGB16F : GL_RGBA16F, width, height, depth, 0,
                        rgbVolume ? GL_RGB : GL_RGBA, GL_HALF_FLOAT, (ByteBuffer) null);
            }

            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, result.framebuffer);
            glFramebufferTexture(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, result.texture, 0);
            glDrawBuffer(GL_COLOR_ATTACHMENT0);
            int status = glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER);
            if (rgbVolume && status != GL_FRAMEBUFFER_COMPLETE) {
                // OpenGL 4.1 makes RGB16F texture storage available. Layered rendering to it is optional.
                glTexImage3D(target, 0, GL_RGBA16F, width, height, depth, 0, GL_RGBA, GL_HALF_FLOAT,
                        (ByteBuffer) null);
                status = glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER);
            }
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
