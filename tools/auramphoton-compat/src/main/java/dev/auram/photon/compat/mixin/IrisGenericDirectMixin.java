package dev.auram.photon.compat.mixin;

import com.seibel.distanthorizons.api.interfaces.render.IDhApiRenderableBoxGroup;
import com.seibel.distanthorizons.api.methods.events.sharedParameterObjects.DhApiRenderParam;
import com.seibel.distanthorizons.api.objects.math.DhApiVec3d;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBox;
import com.seibel.distanthorizons.api.objects.render.DhApiRenderableBoxGroupShading;
import net.irisshaders.iris.compat.dh.IrisGenericRenderProgram;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.awt.Color;

import static com.seibel.distanthorizons.core.util.LodUtil.getChunkPosFromDouble;
import static com.seibel.distanthorizons.core.util.LodUtil.getSubChunkPosFromDouble;
import static org.lwjgl.opengl.GL32C.*;

@Mixin(value = IrisGenericRenderProgram.class, remap = false)
abstract class IrisGenericDirectMixin {
    @Shadow public abstract int getId();
    @Shadow public abstract void fillIndirectUniformData(DhApiRenderParam render,
            DhApiRenderableBoxGroupShading shading, IDhApiRenderableBoxGroup group, DhApiVec3d camera);

    @Unique private boolean auramPhoton$usingDirect;
    @Unique private int auramPhoton$direct;
    @Unique private int auramPhoton$color;
    @Unique private int auramPhoton$scale;
    @Unique private int auramPhoton$chunk;
    @Unique private int auramPhoton$subChunk;
    @Unique private int auramPhoton$material;

    @ModifyArg(method = "<init>", at = @At(value = "INVOKE", ordinal = 0,
            target = "Lnet/irisshaders/iris/gl/shader/GlShader;<init>(Lnet/irisshaders/iris/gl/shader/ShaderType;Ljava/lang/String;Ljava/lang/String;)V"),
            index = 2)
    private String auramPhoton$useDirectBoxUniforms(String vertex) {
        int init = vertex.indexOf("void _vert_init");
        int end = vertex.indexOf('}', init);
        if (init < 0 || end < 0) {
            return vertex;
        }
        // Oculus emits _vert_init with the DH instance inputs. Direct draws supply the same values per box.
        String body = vertex.substring(init, end)
                .replace("iris_color", "(auram_direct ? auram_color : iris_color)")
                .replace("aScale", "(auram_direct ? auram_scale : aScale)")
                .replace("aTranslateChunk", "(auram_direct ? auram_chunk : aTranslateChunk)")
                .replace("aTranslateSubChunk", "(auram_direct ? auram_sub_chunk : aTranslateSubChunk)")
                .replace("aMaterial", "(auram_direct ? auram_material : aMaterial)");
        return vertex.substring(0, init) + """
                uniform bool auram_direct;
                uniform vec4 auram_color;
                uniform vec3 auram_scale;
                uniform ivec3 auram_chunk;
                uniform vec3 auram_sub_chunk;
                uniform int auram_material;
                """ + body + vertex.substring(end);
    }

    @Inject(method = "<init>", at = @At("RETURN"))
    private void auramPhoton$findDirectUniforms(CallbackInfo callback) {
        int id = getId();
        auramPhoton$direct = glGetUniformLocation(id, "auram_direct");
        auramPhoton$color = glGetUniformLocation(id, "auram_color");
        auramPhoton$scale = glGetUniformLocation(id, "auram_scale");
        auramPhoton$chunk = glGetUniformLocation(id, "auram_chunk");
        auramPhoton$subChunk = glGetUniformLocation(id, "auram_sub_chunk");
        auramPhoton$material = glGetUniformLocation(id, "auram_material");
    }

    @Inject(method = "fillIndirectUniformData", at = @At("RETURN"))
    private void auramPhoton$useInstanceAttributes(CallbackInfo callback) {
        if (auramPhoton$usingDirect) {
            glUniform1i(auramPhoton$direct, 0);
            auramPhoton$usingDirect = false;
        }
    }

    @Inject(method = "fillSharedDirectUniformData", at = @At("HEAD"), cancellable = true)
    private void auramPhoton$prepareDirectBoxes(DhApiRenderParam render, DhApiRenderableBoxGroupShading shading,
            IDhApiRenderableBoxGroup group, DhApiVec3d camera, CallbackInfo callback) {
        if (auramPhoton$direct == -1) {
            return;
        }
        fillIndirectUniformData(render, shading, group, camera);
        glUniform1i(auramPhoton$direct, 1);
        auramPhoton$usingDirect = true;
        callback.cancel();
    }

    @Inject(method = "fillDirectUniformData", at = @At("HEAD"), cancellable = true)
    private void auramPhoton$setDirectBox(DhApiRenderParam render, IDhApiRenderableBoxGroup group,
            DhApiRenderableBox box, DhApiVec3d camera, CallbackInfo callback) {
        if (auramPhoton$direct == -1) {
            return;
        }
        Color color = box.color;
        DhApiVec3d min = box.minPos;
        DhApiVec3d max = box.maxPos;
        glUniform4f(auramPhoton$color, color.getRed() / 255f, color.getGreen() / 255f,
                color.getBlue() / 255f, color.getAlpha() / 255f);
        glUniform3f(auramPhoton$scale, (float) (max.x - min.x), (float) (max.y - min.y), (float) (max.z - min.z));
        glUniform3i(auramPhoton$chunk, getChunkPosFromDouble(min.x), getChunkPosFromDouble(min.y), getChunkPosFromDouble(min.z));
        glUniform3f(auramPhoton$subChunk, getSubChunkPosFromDouble(min.x), getSubChunkPosFromDouble(min.y),
                getSubChunkPosFromDouble(min.z));
        glUniform1i(auramPhoton$material, box.material);
        callback.cancel();
    }
}
