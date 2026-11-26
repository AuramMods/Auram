package dev.auram.photon.compat.chest;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import me.jellysquid.mods.sodium.client.model.color.interop.ItemColorsExtended;
import me.jellysquid.mods.sodium.client.util.ModelQuadUtil;
import net.caffeinemc.mods.sodium.api.math.MatrixHelper;
import net.caffeinemc.mods.sodium.api.util.NormI8;
import net.caffeinemc.mods.sodium.api.vertex.attributes.CommonVertexAttribute;
import net.caffeinemc.mods.sodium.api.vertex.buffer.VertexBufferWriter;
import net.caffeinemc.mods.sodium.api.vertex.format.VertexFormatDescription;
import net.caffeinemc.mods.sodium.api.vertex.format.common.ModelVertex;
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
    // The descriptors are matched by identity when capturing.
    private static final int ENTITY_ID = 36;
    private static final int BLOCK_ENTITY_ID = 38;
    private static final int ITEM_ID = 40;
    private static final int TANGENT = 50;
    private static final int MAX_MESH_BYTES = 16 * 1024;
    private static boolean enabled = Boolean.parseBoolean(System.getProperty("auramphoton.chest.cache", "true"));
    private static boolean inChest;
    private static final CrystalChestMeshes FLEROVIUM = new CrystalChestMeshes();

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

    public static CrystalChestMeshes flerovium() { return FLEROVIUM; }

    public void clear() { meshes.clear(); }

    public boolean draw(BakedModel model, ItemStack stack, int light, int overlay, PoseStack.Pose pose,
                        VertexConsumer target, ItemColors itemColors, MeshCapture capture) {
        return drawPacked(model, 0, stack, light, overlay, pose, VertexBufferWriter.tryOf(target), itemColors, Layout.IRIS, capture);
    }

    public boolean drawFlerovium(SimpleBakedModel model, int faces, ItemStack stack, int light, int overlay,
                                 PoseStack.Pose pose, VertexBufferWriter writer, ItemColors itemColors, MeshCapture capture) {
        return drawPacked(model, faces, stack, light, overlay, pose, writer, itemColors, Layout.MODEL, capture);
    }

    private boolean drawPacked(BakedModel model, int variant, ItemStack stack, int light, int overlay, PoseStack.Pose pose,
                               VertexBufferWriter writer, ItemColors itemColors, Layout layout, MeshCapture capture) {
        if (model.getClass() != SimpleBakedModel.class || stack.m_41782_()
                || !WorldRenderingSettings.INSTANCE.shouldUseExtendedVertexFormat()) return false;
        if (writer == null || !writer.isFullWriter()) return false;
        Item item = stack.m_41720_();
        Entry entry = meshes.get(item);
        if (entry == null || entry.model != model) {
            entry = new Entry(model, new HashMap<>());
            meshes.put(item, entry);
        }
        Mesh mesh = entry.variants.get(variant);
        if (mesh == null && !entry.variants.containsKey(variant)) {
            var itemId = BuiltInRegistries.f_257033_.m_7981_(item);
            if (itemId.m_135827_().equals("minecraft") && ORDINARY_ITEMS.contains(itemId.m_135815_())
                    && itemColors instanceof ItemColorsExtended colors && colors.sodium$getColorProvider(stack) == null) {
                var local = new RecordingConsumer(layout);
                try {
                    capture.render(model, variant, stack, 0, overlay, new PoseStack(), local, itemColors);
                    mesh = local.finish();
                } catch (UnsupportedOperationException ignored) {
                    // Alternate vertex layouts use their original renderer.
                }
            }
            entry.variants.put(variant, mesh);
        }
        if (mesh == null) return false;
        replay(mesh, writer, pose, light, overlay);
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
        Layout layout = mesh.layout;

        // Submit one mesh to the original render-type batch, preserving quad order and shader attributes.
        for (int vertex = 0; vertex < vertices; vertex++) {
            long address = destination + (long) vertex * layout.stride;
            float x = memGetFloat(address), y = memGetFloat(address + 4), z = memGetFloat(address + 8);
            memPutFloat(address, MatrixHelper.transformPositionX(position, x, y, z));
            memPutFloat(address + 4, MatrixHelper.transformPositionY(position, x, y, z));
            memPutFloat(address + 8, MatrixHelper.transformPositionZ(position, x, y, z));
            int vertexLight = light;
            if (mesh.hasBakedLight) {
                int baked = memGetInt(address + layout.light);
                vertexLight = layout == Layout.IRIS ? ModelQuadUtil.mergeBakedLight(baked, light) : Math.max(baked, light);
            }
            memPutInt(address + layout.light, vertexLight);
            memPutInt(address + layout.overlay, overlay);
            memPutInt(address + layout.normal, rotatedAxes[mesh.axes[2 * vertex]]);
            if (layout == Layout.IRIS) {
                int handedness = memGetInt(address + TANGENT) & 0xff000000;
                memPutInt(address + TANGENT, rotatedAxes[mesh.axes[2 * vertex + 1]] | handedness);
                memPutShort(address + ENTITY_ID, entityId);
                memPutShort(address + BLOCK_ENTITY_ID, blockEntityId);
                memPutShort(address + ITEM_ID, itemId);
            }
        }
        try (var stack = MemoryStack.stackPush()) {
            writer.push(stack, destination, vertices, layout.format);
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
        void render(BakedModel model, int variant, ItemStack stack, int light, int overlay, PoseStack pose,
                    VertexConsumer consumer, ItemColors colors);
    }

    private record Entry(BakedModel model, Map<Integer, Mesh> variants) { }
    private record Mesh(Layout layout, ByteBuffer bytes, byte[] axes, boolean hasBakedLight) { }

    private enum Layout {
        IRIS(EntityVertex.FORMAT, 56),
        MODEL(ModelVertex.FORMAT, 36);

        private final VertexFormatDescription format;
        private final int stride;
        private final int light;
        private final int overlay;
        private final int normal;

        Layout(VertexFormatDescription format, int stride) {
            this.format = format;
            this.stride = stride;
            light = format.getElementOffset(CommonVertexAttribute.LIGHT);
            overlay = format.getElementOffset(CommonVertexAttribute.OVERLAY);
            normal = format.getElementOffset(CommonVertexAttribute.NORMAL);
        }
    }

    private static final class RecordingConsumer implements VertexConsumer, VertexBufferWriter {
        private final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        private final Layout layout;
        private boolean valid = true;

        private RecordingConsumer(Layout layout) {
            this.layout = layout;
        }

        @Override
        public void push(MemoryStack stack, long address, int vertices, VertexFormatDescription format) {
            if (!valid) return;
            int length = vertices * format.stride();
            if (format != layout.format || format.stride() != layout.stride
                    || bytes.size() + length > MAX_MESH_BYTES || vertices % 4 != 0) {
                valid = false;
                return;
            }
            byte[] quad = new byte[length];
            memByteBuffer(address, length).get(quad);
            bytes.writeBytes(quad);
        }

        private Mesh finish() {
            if (!valid || bytes.size() == 0) return null;
            var packed = BufferUtils.createByteBuffer(bytes.size()).put(bytes.toByteArray()).flip();
            int vertices = packed.limit() / layout.stride;
            byte[] axes = new byte[2 * vertices];
            boolean hasBakedLight = false;
            for (int index = 0; index < vertices; index++) {
                int offset = index * layout.stride;
                int normal = axis(packed.getInt(offset + layout.normal));
                int tangent = layout == Layout.IRIS ? axis(packed.getInt(offset + TANGENT)) : 0;
                if (normal < 0 || tangent < 0) {
                    throw new UnsupportedOperationException("non-axis-aligned normal or tangent");
                }
                axes[2 * index] = (byte) normal;
                axes[2 * index + 1] = (byte) tangent;
                hasBakedLight |= packed.getInt(offset + layout.light) != 0;
            }
            return new Mesh(layout, packed, axes, hasBakedLight);
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
