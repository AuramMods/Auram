#define GL_SILENCE_DEPRECATION
#import <Foundation/Foundation.h>
#import <Metal/Metal.h>
#import <IOSurface/IOSurface.h>
#import <OpenGL/OpenGL.h>
#import <OpenGL/CGLIOSurface.h>
#import <OpenGL/gl3.h>
#import <time.h>
#include <jni.h>

enum { Occupancy, Palette, Reprojection, FirstLight, SecondLight, TextureCount };

@interface PhotonLightVolume : NSObject
@property unsigned side;
@property unsigned columns;
@property (strong) id<MTLCommandQueue> queue;
@property (strong) id<MTLComputePipelineState> pipeline;
@property (strong) id<MTLComputePipelineState> planner;
@property (strong) id<MTLBuffer> schedule;
@property (strong) NSArray<id<MTLTexture>> *textures;
@property (strong) NSArray<id<MTLBuffer>> *flags;
@end

@implementation PhotonLightVolume
@end

static void fail(JNIEnv *env, NSString *message) {
    jclass exception = (*env)->FindClass(env, "java/lang/IllegalStateException");
    if (exception) (*env)->ThrowNew(env, exception, message.UTF8String);
}

static id<MTLTexture> sharedTexture(id<MTLDevice> device, CGLContextObj context, GLuint name,
                                   unsigned width, unsigned height, MTLPixelFormat pixelFormat,
                                   GLenum internalFormat, GLenum format, GLenum type, size_t element, BOOL output,
                                   NSInteger *attachError) {
    size_t row = IOSurfaceAlignProperty(kIOSurfaceBytesPerRow, width * element);
    IOSurfaceRef surface = IOSurfaceCreate((__bridge CFDictionaryRef)@{
        (__bridge NSString *)kIOSurfaceWidth: @(width),
        (__bridge NSString *)kIOSurfaceHeight: @(height),
        (__bridge NSString *)kIOSurfaceBytesPerElement: @(element),
        (__bridge NSString *)kIOSurfaceBytesPerRow: @(row),
        (__bridge NSString *)kIOSurfaceAllocSize: @(row * height)
    });
    if (!surface) return nil;
    MTLTextureDescriptor *descriptor = [MTLTextureDescriptor texture2DDescriptorWithPixelFormat:pixelFormat
        width:width height:height mipmapped:NO];
    descriptor.storageMode = MTLStorageModeShared;
    descriptor.usage = MTLTextureUsageShaderRead | (output ? MTLTextureUsageShaderWrite : 0);
    id<MTLTexture> texture = [device newTextureWithDescriptor:descriptor iosurface:surface plane:0];
    if (texture) {
        glBindTexture(GL_TEXTURE_RECTANGLE, name);
        CGLError attach = CGLTexImageIOSurface2D(context, GL_TEXTURE_RECTANGLE, internalFormat,
            width, height, format, type, surface, 0);
        *attachError = attach;
        if (attach == kCGLNoError) {
            glTexParameteri(GL_TEXTURE_RECTANGLE, GL_TEXTURE_MIN_FILTER, output ? GL_LINEAR : GL_NEAREST);
            glTexParameteri(GL_TEXTURE_RECTANGLE, GL_TEXTURE_MAG_FILTER, output ? GL_LINEAR : GL_NEAREST);
            glTexParameteri(GL_TEXTURE_RECTANGLE, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_RECTANGLE, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
        } else texture = nil;
    }
    CFRelease(surface);
    return texture;
}

JNIEXPORT jlong JNICALL Java_dev_auram_photon_compat_lpv_MetalLightVolume_createNativeWithTileReset(
        JNIEnv *env, jclass type, jstring kernel, jint side, jint columns, jint width, jint height, jintArray names) {
    (void)type;
    @autoreleasepool {
        CGLContextObj context = CGLGetCurrentContext();
        id<MTLDevice> device = MTLCreateSystemDefaultDevice();
        if (!context || !device) {
            fail(env, @"A current OpenGL context and a Metal device are required for the shared light volume");
            return 0;
        }
        const char *utf8 = (*env)->GetStringUTFChars(env, kernel, NULL);
        if (!utf8) return 0;
        NSString *source = [NSString stringWithUTF8String:utf8];
        (*env)->ReleaseStringUTFChars(env, kernel, utf8);
        MTLCompileOptions *options = [[MTLCompileOptions alloc] init];
        options.fastMathEnabled = NO;
        NSError *error = nil;
        id<MTLLibrary> library = [device newLibraryWithSource:source options:options error:&error];
        if (!library) {
            fail(env, [@"AuramPhoton Metal kernel compilation failed. " stringByAppendingString:error.localizedDescription]);
            return 0;
        }
        id<MTLFunction> transport = [library newFunctionWithName:@"propagateLight"];
        id<MTLFunction> scheduleTiles = [library newFunctionWithName:@"scheduleTiles"];
        if (!transport || !scheduleTiles) {
            fail(env, @"AuramPhoton Metal kernels are missing from the compiled library");
            return 0;
        }
        id<MTLComputePipelineState> pipeline = [device newComputePipelineStateWithFunction:transport error:&error];
        id<MTLCommandQueue> queue = [device newCommandQueue];
        if (!pipeline || !queue) {
            fail(env, [@"AuramPhoton Metal pipeline creation failed. " stringByAppendingString:error.localizedDescription ?: @""]);
            return 0;
        }
        id<MTLComputePipelineState> planner = [device newComputePipelineStateWithFunction:scheduleTiles error:&error];
        if (!planner) {
            fail(env, error.localizedDescription ?: @"AuramPhoton could not create the Metal tile planner");
            return 0;
        }

        jint textureNames[TextureCount];
        (*env)->GetIntArrayRegion(env, names, 0, TextureCount, textureNames);
        if ((*env)->ExceptionCheck(env)) return 0;

        GLint oldBinding;
        glGetIntegerv(GL_TEXTURE_BINDING_RECTANGLE, &oldBinding);
        NSMutableArray<id<MTLTexture>> *textures = [NSMutableArray arrayWithCapacity:TextureCount];
        for (int i = 0; i < TextureCount; ++i) {
            BOOL isLight = i >= FirstLight;
            BOOL isOccupancy = i == Occupancy;
            BOOL isReprojection = i == Reprojection;
            unsigned w = i == Palette ? 48 : isReprojection ? 4 : (unsigned)width;
            unsigned h = i == Palette ? 2 : isReprojection ? 1 : (unsigned)height;
            NSInteger attachError = 0;
            id<MTLTexture> texture = sharedTexture(device, context, textureNames[i], w, h,
                isOccupancy ? MTLPixelFormatR8Uint : isReprojection ? MTLPixelFormatRGBA32Float : MTLPixelFormatRGBA16Float,
                isOccupancy ? GL_R8UI : isReprojection ? GL_RGBA32F : GL_RGBA16F,
                isOccupancy ? GL_RED_INTEGER : GL_RGBA,
                isOccupancy ? GL_UNSIGNED_BYTE : isReprojection ? GL_FLOAT : GL_HALF_FLOAT,
                isOccupancy ? 1 : isReprojection ? 16 : 8, isLight, &attachError);
            if (!texture) {
                glBindTexture(GL_TEXTURE_RECTANGLE, oldBinding);
                fail(env, [NSString stringWithFormat:@"AuramPhoton IOSurface %d (%u×%u) failed. CGL status %ld",
                    i, w, h, (long)attachError]);
                return 0;
            }
            [textures addObject:texture];
        }
        glBindTexture(GL_TEXTURE_RECTANGLE, oldBinding);
        NSMutableArray<id<MTLBuffer>> *flags = [NSMutableArray arrayWithCapacity:2];
        NSUInteger flagSize = (NSUInteger)(side / 8) * (side / 8) * (side / 4) * sizeof(uint32_t);
        for (int i = 0; i < 2; ++i) {
            id<MTLBuffer> mask = [device newBufferWithLength:flagSize options:MTLResourceStorageModeShared];
            if (!mask) {
                fail(env, @"AuramPhoton Metal activity buffer allocation failed");
                return 0;
            }
            memset(mask.contents, 0, flagSize);
            [flags addObject:mask];
        }
        id<MTLBuffer> schedule = [device newBufferWithLength:flagSize options:MTLResourceStorageModeShared];
        if (!schedule) {
            fail(env, @"AuramPhoton Metal tile schedule allocation failed");
            return 0;
        }
        PhotonLightVolume *state = [[PhotonLightVolume alloc] init];
        state.pipeline = pipeline;
        state.queue = queue;
        state.side = side;
        state.columns = columns;
        state.textures = textures;
        state.flags = flags;
        state.planner = planner;
        state.schedule = schedule;
        return (jlong)(intptr_t)(__bridge_retained void *)state;
    }
}

static double now(void) {
    struct timespec time;
    clock_gettime(CLOCK_MONOTONIC, &time);
    return (double)time.tv_sec + (double)time.tv_nsec / 1.e9;
}

JNIEXPORT void JNICALL Java_dev_auram_photon_compat_lpv_MetalLightVolume_propagateNative(
        JNIEnv *env, jclass type, jlong handle, jint published, jfloatArray trace) {
    (void)type;
    @autoreleasepool {
        PhotonLightVolume *state = (__bridge PhotonLightVolume *)(void *)(intptr_t)handle;
        unsigned next = (unsigned)published ^ 1;
        double start = trace ? now() : 0;
        glFinish();
        IOSurfaceRef source = state.textures[Reprojection].iosurface;
        float projection[16];
        if (IOSurfaceLock(source, kIOSurfaceLockReadOnly, NULL) != kIOReturnSuccess) {
            fail(env, @"AuramPhoton could not lock the completed reprojection surface");
            return;
        }
        memcpy(projection, IOSurfaceGetBaseAddress(source), sizeof projection);
        IOSurfaceUnlock(source, kIOSurfaceLockReadOnly, NULL);
        double afterGL = trace ? now() : 0;
        id<MTLCommandBuffer> command = [state.queue commandBuffer];
        unsigned side = state.side, columns = state.columns;
        id<MTLComputeCommandEncoder> planner = [command computeCommandEncoder];
        if (!planner) {
            fail(env, @"AuramPhoton could not create the Metal tile planner encoder");
            return;
        }
        [planner setComputePipelineState:state.planner];
        [planner setBytes:&side length:sizeof(side) atIndex:0];
        [planner setBuffer:state.flags[published] offset:0 atIndex:2];
        [planner setBuffer:state.flags[next] offset:0 atIndex:3];
        [planner setBytes:projection length:sizeof projection atIndex:4];
        [planner setBuffer:state.schedule offset:0 atIndex:5];
        [planner dispatchThreads:MTLSizeMake(side / 8, side / 8, side / 4) threadsPerThreadgroup:MTLSizeMake(8, 4, 4)];
        [planner endEncoding];
        id<MTLComputeCommandEncoder> encoder = [command computeCommandEncoder];
        if (!encoder) {
            fail(env, @"AuramPhoton could not create a Metal compute encoder");
            return;
        }
        [encoder setComputePipelineState:state.pipeline];
        [encoder setTexture:state.textures[FirstLight + published] atIndex:0];
        [encoder setTexture:state.textures[Occupancy] atIndex:1];
        [encoder setTexture:state.textures[Palette] atIndex:2];
        [encoder setTexture:state.textures[FirstLight + next] atIndex:4];
        [encoder setBytes:&side length:sizeof(side) atIndex:0];
        [encoder setBytes:&columns length:sizeof(columns) atIndex:1];
        [encoder setBuffer:state.flags[next] offset:0 atIndex:3];
        [encoder setBytes:projection length:sizeof projection atIndex:4];
        [encoder setBuffer:state.schedule offset:0 atIndex:5];
        [encoder dispatchThreads:MTLSizeMake(side, side, side) threadsPerThreadgroup:MTLSizeMake(8, 8, 4)];
        [encoder endEncoding];
        [command commit];
        [command waitUntilCompleted];
        if (command.status != MTLCommandBufferStatusCompleted) {
            fail(env, [@"AuramPhoton Metal propagation failed. " stringByAppendingString:command.error.localizedDescription ?: @""]);
            return;
        }
        if (trace) {
            float values[3] = {(float)((afterGL-start)*1000),
                (float)((command.GPUEndTime-command.GPUStartTime)*1000), (float)((now()-afterGL)*1000)};
            (*env)->SetFloatArrayRegion(env, trace, 0, 3, values);
        }
    }
}

JNIEXPORT void JNICALL Java_dev_auram_photon_compat_lpv_MetalLightVolume_destroyNative(
        JNIEnv *env, jclass type, jlong handle) {
    (void)env;
    (void)type;
    @autoreleasepool {
        id state = (__bridge_transfer id)(void *)(intptr_t)handle;
        (void)state;
    }
}
