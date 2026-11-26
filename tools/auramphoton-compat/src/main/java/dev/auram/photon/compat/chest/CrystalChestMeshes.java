package dev.auram.photon.compat.chest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.IdentityHashMap;
import java.util.Set;
import me.jellysquid.mods.sodium.client.model.color.interop.ItemColorsExtended;
import me.jellysquid.mods.sodium.client.util.ModelQuadUtil;
import net.caffeinemc.mods.sodium.api.math.MatrixHelper;
import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription;
import net.irisshaders.iris.compat.sodium.impl.vertex_format.entity_xhfp.EntityVertex;
import net.irisshaders.iris.shaderpack.materialmap.WorldRenderingSettings;
import net.irisshaders.iris.uniforms.CapturedRenderingState;
import net.minecraft.client.color.item.ItemColors;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.client.resources.model.SimpleBakedModel;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix3f;
import org.lwjgl.BufferUtils;
import org.lwjgl.system.MemoryStack;

import static org.lwjgl.system.MemoryUtil.*;

public final class CrystalChestMeshes {
    private static final Set<String> ORDINARY_ITEMS = Set.of(
            "diamond", "emerald", "gold_ingot", "iron_ingot", "copper_ingot", "lapis_lazuli", "redstone", "amethyst_shard");
    // Oculus 1.8's extended entity vertex, matched by identity when capturing.
    private static final int LIGHT = 24;
    private static final int OVERLAY = 28;
    private static final int NORMAL = 32;
    private static final int ENTITY_ID = 36;
    private static final int BLOCK_ENTITY_ID = 38;
    private static final int ITEM_ID = 40;
    private static final int TANGENT = 50;
    private static final int MAX_MESH_BYTES = 16 * 1024;
    private static boolean enabled = Boolean.parseBoolean(System.getProperty("auramphoton.chest.cache", "true"));
    private static boolean inChest;

    private final IdentityHashMap<Item, Entry> meshes = new IdentityHashMap<>();
    private final int[] rotatedAxes = new int[6];
    private ByteBuffer scratch;

    public static void setEnabled(boolean enabled) { CrystalChestMeshes.enabled = enabled; }

    public static boolean beginChest() {
        boolean previous = inChest;
        inChest = enabled;
        return previous;
    }

    public static void endChest(boolean previous) { inChest = previous; }

    public static boolean inChest() { return inChest; }

    public void clear() { meshes.clear(); }

    public boolean draw(BakedModel model, ItemStack stack, int light, int overlay, PoseStack.Pose pose,
                        VertexConsumer target, ItemColors itemColors, MeshCapture capture) {
        if (model.getClass() != SimpleBakedModel.class || stack.m_41782_()
                || !WorldRenderingSettings.INSTANCE.shouldUseExtendedVertexFormat()) return false;
        VertexBufferWriter writer = VertexBufferWriter.tryOf(target);
        if (writer == null || !writer.isFullWriter()) return false;
        Item item = stack.m_41720_();
        Entry entry = meshes.get(item);
        if (entry == null || entry.model != model) {
            var itemId = BuiltInRegistries.f_257033_.m_7981_(item);
            Mesh mesh = null;
            if (itemId.m_135827_().equals("minecraft") && ORDINARY_ITEMS.contains(itemId.m_135815_())
                    && itemColors instanceof ItemColorsExtended colors && colors.sodium$getColorProvider(stack) == null) {
                var local = new RecordingConsumer();
                try {
                    capture.render(model, stack, 0, overlay, new PoseStack(), local);
                    mesh = local.finish();
                } catch (UnsupportedOperationException ignored) {
                    // Alternate vertex layouts use their original renderer.
                }
            }
            entry = new Entry(model, mesh);
            meshes.put(item, entry);
        }
        if (entry.mesh == null) return false;
        replay(entry.mesh, writer, pose, light, overlay);
        return true;
    }

    private void replay(Mesh mesh, VertexBufferWriter writer, PoseStack.Pose pose, int light, int overlay) {
        if (scratch == null) scratch = BufferUtils.createByteBuffer(MAX_MESH_BYTES);
        long destination = memAddress(scratch);
        memCopy(memAddress(mesh.bytes), destination, mesh.bytes.limit());
        var position = pose.m_252922_();
        rotateAxes(pose.m_252943_());
        var state = CapturedRenderingState.INSTANCE;
        short entityId = (short) state.getCurrentRenderedEntity();
        short blockEntityId = (short) state.getCurrentRenderedBlockEntity();
        short itemId = (short) state.getCurrentRenderedItem();
        int vertices = mesh.axes.length / 2;

        // Submit one mesh to the original render-type batch, preserving quad order and shader attributes.
        for (int vertex = 0; vertex < vertices; vertex++) {
            long address = destination + (long) vertex * EntityVertex.STRIDE;
            float x = memGetFloat(address), y = memGetFloat(address + 4), z = memGetFloat(address + 8);
            memPutFloat(address, MatrixHelper.transformPositionX(position, x, y, z));
            memPutFloat(address + 4, MatrixHelper.transformPositionY(position, x, y, z));
            memPutFloat(address + 8, MatrixHelper.transformPositionZ(position, x, y, z));
            memPutInt(address + LIGHT, ModelQuadUtil.mergeBakedLight(memGetInt(address + LIGHT), light));
            memPutInt(address + OVERLAY, overlay);
            memPutInt(address + NORMAL, rotatedAxes[mesh.axes[2 * vertex]]);
            int handedness = memGetInt(address + TANGENT) & 0xff000000;
            memPutInt(address + TANGENT, rotatedAxes[mesh.axes[2 * vertex + 1]] | handedness);
            memPutShort(address + ENTITY_ID, entityId);
            memPutShort(address + BLOCK_ENTITY_ID, blockEntityId);
            memPutShort(address + ITEM_ID, itemId);
        }
        try (var stack = MemoryStack.stackPush()) {
            writer.push(stack, destination, vertices, EntityVertex.FORMAT);
        }
    }

    private void rotateAxes(Matrix3f normal) {
        rotatedAxes[0] = NormI8.pack(normal.m00(), normal.m01(), normal.m02());
        rotatedAxes[1] = NormI8.pack(-normal.m00(), -normal.m01(), -normal.m02());
        rotatedAxes[2] = NormI8.pack(normal.m10(), normal.m11(), normal.m12());
        rotatedAxes[3] = NormI8.pack(-normal.m10(), -normal.m11(), -normal.m12());
        rotatedAxes[4] = NormI8.pack(normal.m20(), normal.m21(), normal.m22());
        rotatedAxes[5] = NormI8.pack(-normal.m20(), -normal.m21(), -normal.m22());
    }

    @FunctionalInterface
    public interface MeshCapture {
        void render(BakedModel model, ItemStack stack, int light, int overlay, PoseStack pose, VertexConsumer consumer);
    }

    private record Entry(BakedModel model, Mesh mesh) { }
    private record Mesh(ByteBuffer bytes, byte[] axes) { }

    private static final class RecordingConsumer implements VertexConsumer, VertexBufferWriter {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();

        @Override
        public void push(MemoryStack stack, long address, int vertices, VertexFormatDescription format) {
            int length = vertices * EntityVertex.STRIDE;
            if (format != EntityVertex.FORMAT || EntityVertex.STRIDE != 56
                    || bytes.size() + length > MAX_MESH_BYTES || vertices % 4 != 0) {
                throw new UnsupportedOperationException("vertex format stride " + format.stride());
            }
            byte[] quad = new byte[length];
            memByteBuffer(address, length).get(quad);
            bytes.writeBytes(quad);
        }

        private Mesh finish() {
            if (bytes.size() == 0) return null;
            var packed = BufferUtils.createByteBuffer(bytes.size()).put(bytes.toByteArray()).flip();
            int vertices = packed.limit() / EntityVertex.STRIDE;
            byte[] axes = new byte[2 * vertices];
            for (int index = 0; index < vertices; index++) {
                int offset = index * EntityVertex.STRIDE;
                int normal = axis(packed.getInt(offset + NORMAL));
                int tangent = axis(packed.getInt(offset + TANGENT));
                if (normal < 0 || tangent < 0) {
                    throw new UnsupportedOperationException("non-axis-aligned normal or tangent");
                }
                axes[2 * index] = (byte) normal;
                axes[2 * index + 1] = (byte) tangent;
            }
            return new Mesh(packed, axes);
        }

        private static int axis(int encoded) {
            return switch (encoded & 0x00ffffff) {
                case 0x00007f -> 0;
                case 0x000081 -> 1;
                case 0x007f00 -> 2;
                case 0x008100 -> 3;
                case 0x7f0000 -> 4;
                case 0x810000 -> 5;
                default -> -1;
            };
        }

        @Override public VertexConsumer m_5483_(double x, double y, double z) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer m_6122_(int r, int g, int b, int a) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer m_7421_(float u, float v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer m_7122_(int u, int v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer m_7120_(int u, int v) { throw new UnsupportedOperationException(); }
        @Override public VertexConsumer m_5601_(float x, float y, float z) { throw new UnsupportedOperationException(); }
        @Override public void m_5752_() { throw new UnsupportedOperationException(); }
        @Override public void m_7404_(int r, int g, int b, int a) { throw new UnsupportedOperationException(); }
        @Override public void m_141991_() { throw new UnsupportedOperationException(); }
    }
}
