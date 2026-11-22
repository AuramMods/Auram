package dev.auram.photon.compat;

import com.google.common.collect.ImmutableSet;
import dev.auram.photon.compat.gl.DrawState;
import net.irisshaders.iris.gl.program.Program;
import net.irisshaders.iris.gl.program.ProgramBuilder;
import net.irisshaders.iris.targets.RenderTarget;

import java.nio.ByteBuffer;

import static org.lwjgl.opengl.GL41C.*;

public final class ExposureMeter implements AutoCloseable {
    private static final int TILE_SIZE = 8;
    private static final String VERTEX = """
            #version 410 core
            void main() {
                vec2 position = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                gl_Position = vec4(position * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String FRAGMENT = """
            #version 410 core
            uniform sampler2D sourceColor;
            uniform ivec2 extent;
            uniform vec2 sourcePixelSize;
            uniform float tileWeight;
            out float auram_meter_luminance;

            void main() {
                ivec2 origin = ivec2(gl_FragCoord.xy) * %d;
                ivec2 limit = min(origin + %d, extent);
                vec3 sum = vec3(0.0);
                for (int y = origin.y; y < limit.y; y += 2) {
                    for (int x = origin.x; x < limit.x; x += 2) {
                        ivec2 size = min(ivec2(2), limit - ivec2(x, y));
                        vec2 center = vec2(x, y) + 0.5 * vec2(size);
                        // Linear sampling averages each complete 2x2 group. The
                        // final groups use only the pixels inside the viewport.
                        sum += textureLod(sourceColor, center * sourcePixelSize, 0.0).rgb
                            * float(size.x * size.y);
                    }
                }
                auram_meter_luminance = dot(sum, vec3(0.2627, 0.6780, 0.0593)) * tileWeight;
            }
            """.formatted(TILE_SIZE, TILE_SIZE);

    private final float scale;
    private Program program;
    private int vao;
    private int sampler;
    private int sourceLocation;
    private int extentLocation;
    private int pixelSizeLocation;
    private int weightLocation;
    private Atlas atlas;

    public ExposureMeter(float scale) {
        this.scale = scale;
    }

    public int texture() {
        return atlas.texture;
    }

    public void update(RenderTarget scene, boolean useAlt) {
        if (program == null) {
            program = ProgramBuilder.begin("auramphoton_exposure_meter", VERTEX, null, FRAGMENT, ImmutableSet.of()).build();
            vao = glGenVertexArrays();
            sampler = glGenSamplers();
            glSamplerParameteri(sampler, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glSamplerParameteri(sampler, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glSamplerParameteri(sampler, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glSamplerParameteri(sampler, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            int id = program.getProgramId();
            sourceLocation = glGetUniformLocation(id, "sourceColor");
            extentLocation = glGetUniformLocation(id, "extent");
            pixelSizeLocation = glGetUniformLocation(id, "sourcePixelSize");
            weightLocation = glGetUniformLocation(id, "tileWeight");
        }

        // Fragment centers on the upper edge of the scaled viewport are excluded.
        int width = Math.max(1, (int) Math.ceil(scene.getWidth() * scale - 0.5));
        int height = Math.max(1, (int) Math.ceil(scene.getHeight() * scale - 0.5));
        int columns = atlasSize(width);
        int rows = atlasSize(height);
        int unit = glGetInteger(GL_ACTIVE_TEXTURE) - GL_TEXTURE0;
        int previousTexture = glGetInteger(GL_TEXTURE_BINDING_2D);
        int previousSampler = glGetInteger(GL_SAMPLER_BINDING);
        try (DrawState draw = new DrawState(vao, false)) {
            if (atlas == null || atlas.width != columns || atlas.height != rows) {
                Atlas next = Atlas.allocate(columns, rows);
                if (atlas != null) atlas.close();
                atlas = next;
            }

            int id = program.getProgramId();
            draw.bind(atlas.framebuffer, id, columns, rows);
            glBindTexture(GL_TEXTURE_2D, useAlt ? scene.getAltTexture() : scene.getMainTexture());
            glBindSampler(unit, sampler);
            glUniform1i(sourceLocation, unit);
            glUniform2i(extentLocation, width, height);
            glUniform2f(pixelSizeLocation, 1.0f / scene.getWidth(), 1.0f / scene.getHeight());
            // All atlas levels split into equal areas. The atlas/pixel ratio
            // removes the padded tiles from the final average.
            glUniform1f(weightLocation, ((float) columns * rows) / ((float) width * height));
            glDrawArrays(GL_TRIANGLES, 0, 3);

            glBindTexture(GL_TEXTURE_2D, atlas.texture);
            glGenerateMipmap(GL_TEXTURE_2D);
        } finally {
            glBindSampler(unit, previousSampler);
            glBindTexture(GL_TEXTURE_2D, previousTexture);
        }
    }

    private static int atlasSize(int pixels) {
        int tiles = (pixels + TILE_SIZE - 1) / TILE_SIZE;
        return Integer.highestOneBit(2 * tiles - 1);
    }

    @Override
    public void close() {
        if (atlas != null) atlas.close();
        if (program != null) {
            glDeleteSamplers(sampler);
            glDeleteVertexArrays(vao);
            program.destroy();
        }
    }

    private record Atlas(int texture, int framebuffer, int width, int height) implements AutoCloseable {
        static Atlas allocate(int width, int height) {
            int unpack = glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING);
            Atlas next = new Atlas(glGenTextures(), glGenFramebuffers(), width, height);
            try {
                glBindTexture(GL_TEXTURE_2D, next.texture);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST_MIPMAP_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST);
                glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAX_LEVEL, 31 - Integer.numberOfLeadingZeros(Math.max(width, height)));
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0);
                glTexImage2D(GL_TEXTURE_2D, 0, GL_R32F, width, height, 0, GL_RED, GL_FLOAT, (ByteBuffer) null);
                glBindFramebuffer(GL_DRAW_FRAMEBUFFER, next.framebuffer);
                glFramebufferTexture2D(GL_DRAW_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, next.texture, 0);
                glDrawBuffer(GL_COLOR_ATTACHMENT0);
                int status = glCheckFramebufferStatus(GL_DRAW_FRAMEBUFFER);
                if (status != GL_FRAMEBUFFER_COMPLETE) {
                    throw new IllegalStateException("AuramPhoton exposure atlas framebuffer: 0x" + Integer.toHexString(status));
                }
                return next;
            } catch (RuntimeException | Error failure) {
                next.close();
                throw failure;
            } finally {
                glBindBuffer(GL_PIXEL_UNPACK_BUFFER, unpack);
            }
        }

        @Override
        public void close() {
            glDeleteFramebuffers(framebuffer);
            glDeleteTextures(texture);
        }
    }
}
