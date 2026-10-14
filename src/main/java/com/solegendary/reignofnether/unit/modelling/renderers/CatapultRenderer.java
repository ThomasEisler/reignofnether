package com.solegendary.reignofnether.unit.modelling.renderers;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.solegendary.reignofnether.unit.units.villagers.CatapultUnit;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.block.BlockRenderDispatcher;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;

import java.util.Map;
import java.util.WeakHashMap;

// The catapult drawn with (scaled) blocks: a wooden frame on four wheels, two uprights with a crossbar, and the throwing
// arm with a counterweight and a bucket. The arm is pulled back while the catapult loads (the pillager's crossbow
// charge, synced by the game) and swings forward when it fires.
// A living-entity renderer with an empty model (so the game's selection portrait and stats work), the blocks being
// drawn by a layer.
@OnlyIn(Dist.CLIENT)
public class CatapultRenderer extends LivingEntityRenderer<CatapultUnit, EntityModel<CatapultUnit>> {

    private static class EmptyModel extends EntityModel<CatapultUnit> {
        @Override public void setupAnim(CatapultUnit e, float a, float b, float c, float d, float f) { }
        @Override public void renderToBuffer(PoseStack pose, VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) { }
    }

    private static final BlockState LOG = Blocks.OAK_LOG.defaultBlockState();
    private static final BlockState LOG_X = LOG.setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
    private static final BlockState LOG_Z = LOG.setValue(RotatedPillarBlock.AXIS, Direction.Axis.Z);
    private static final BlockState PLANKS = Blocks.SPRUCE_PLANKS.defaultBlockState();
    private static final BlockState WHEEL = Blocks.STRIPPED_DARK_OAK_LOG.defaultBlockState().setValue(RotatedPillarBlock.AXIS, Direction.Axis.X);
    private static final BlockState WEIGHT = Blocks.COBBLESTONE.defaultBlockState();
    private static final BlockState BUCKET = Blocks.BARREL.defaultBlockState();
    private static final BlockState IRON = Blocks.IRON_BLOCK.defaultBlockState();

    private static final float ARM_REST = 35f;     // degrees, bucket end down at the back
    private static final float ARM_LOADED = 55f;   // pulled further back while loading
    private static final float ARM_FIRED = -50f;   // swung forward

    private final Map<CatapultUnit, Float> lastFire = new WeakHashMap<>();
    private final Map<CatapultUnit, Boolean> wasLoading = new WeakHashMap<>();

    public CatapultRenderer(EntityRendererProvider.Context context) {
        super(context, new EmptyModel(), 1.1f);
        this.addLayer(new RenderLayer<>(this) {
            @Override
            public void render(PoseStack pose, MultiBufferSource buf, int light, CatapultUnit cat, float limbSwing, float limbSwingAmount,
                               float partialTicks, float ageInTicks, float headYaw, float headPitch) {
                // back from the model's space (flipped, 1.5 up) to the entity's, already turned to face its way
                pose.pushPose();
                pose.scale(-1, -1, 1);
                pose.translate(0, -1.501f, 0);
                drawCatapult(cat, partialTicks, pose, buf, light);
                pose.popPose();
            }
        });
    }

    @Override
    protected boolean shouldShowName(CatapultUnit entity) {
        return false;
    }

    @Override
    public ResourceLocation getTextureLocation(CatapultUnit entity) {
        return ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/oak_planks.png");
    }

    // one block, scaled to (sx, sy, sz) with its lower-left corner at (x, y, z)
    private static void part(BlockRenderDispatcher d, PoseStack pose, MultiBufferSource buf, int light, BlockState s,
                             float x, float y, float z, float sx, float sy, float sz) {
        pose.pushPose();
        pose.translate(x, y, z);
        pose.scale(sx, sy, sz);
        d.renderSingleBlock(s, pose, buf, light, OverlayTexture.NO_OVERLAY);
        pose.popPose();
    }

    private void drawCatapult(CatapultUnit cat, float partialTicks, PoseStack pose, MultiBufferSource buf, int light) {
        BlockRenderDispatcher d = Minecraft.getInstance().getBlockRenderer();
        float age = cat.tickCount + partialTicks;

        // arm angle: loading pulls it back, a shot swings it forward then it eases back to rest
        boolean loading = cat.isChargingCrossbow();
        if (Boolean.TRUE.equals(wasLoading.get(cat)) && !loading)
            lastFire.put(cat, age);
        wasLoading.put(cat, loading);
        float arm = loading ? ARM_LOADED : ARM_REST;
        Float fired = lastFire.get(cat);
        if (fired != null && age - fired < 30) {
            float t = (age - fired) / 30f;
            arm = t < 0.15f ? Mth.lerp(t / 0.15f, ARM_LOADED, ARM_FIRED) : Mth.lerp((t - 0.15f) / 0.85f, ARM_FIRED, ARM_REST);
        }

        float wheelTurn = (cat.walkAnimation.position(partialTicks)) * 40f;

        pose.pushPose();

        // wheels (turning as it rolls)
        for (float wx : new float[] { -0.95f, 0.75f })
            for (float wz : new float[] { -0.95f, 0.65f }) {
                pose.pushPose();
                pose.translate(wx + 0.1f, 0.35f, wz + 0.15f);
                pose.mulPose(Axis.XP.rotationDegrees(wheelTurn));
                part(d, pose, buf, light, WHEEL, -0.1f, -0.35f, -0.35f, 0.2f, 0.7f, 0.7f);
                pose.popPose();
            }
        // frame: two side beams, three cross beams, a plank deck
        part(d, pose, buf, light, LOG_Z, -0.75f, 0.3f, -1.2f, 0.25f, 0.25f, 2.4f);
        part(d, pose, buf, light, LOG_Z, 0.5f, 0.3f, -1.2f, 0.25f, 0.25f, 2.4f);
        for (float z : new float[] { -1.1f, 0.0f, 0.95f })
            part(d, pose, buf, light, LOG_X, -0.75f, 0.55f, z, 1.5f, 0.2f, 0.2f);
        part(d, pose, buf, light, PLANKS, -0.5f, 0.5f, 0.2f, 1.0f, 0.1f, 0.75f);
        // uprights and crossbar (the arm stops against it)
        part(d, pose, buf, light, LOG, -0.6f, 0.55f, -0.1f, 0.2f, 1.25f, 0.2f);
        part(d, pose, buf, light, LOG, 0.4f, 0.55f, -0.1f, 0.2f, 1.25f, 0.2f);
        part(d, pose, buf, light, LOG_X, -0.6f, 1.7f, -0.15f, 1.2f, 0.2f, 0.25f);
        part(d, pose, buf, light, IRON, -0.45f, 0.75f, -0.05f, 0.9f, 0.1f, 0.1f); // axle

        // throwing arm, pivoting on the axle: counterweight at the front, bucket at the back
        pose.pushPose();
        pose.translate(0f, 0.8f, 0f);
        pose.mulPose(Axis.XP.rotationDegrees(arm));
        part(d, pose, buf, light, LOG_Z, -0.1f, -0.1f, -0.7f, 0.2f, 0.2f, 2.0f);
        part(d, pose, buf, light, WEIGHT, -0.3f, -0.45f, -0.95f, 0.6f, 0.5f, 0.45f);
        part(d, pose, buf, light, BUCKET, -0.22f, 0.05f, 0.95f, 0.44f, 0.3f, 0.44f);
        if (loading || fired == null || age - fired > 25)
            part(d, pose, buf, light, WEIGHT, -0.15f, 0.25f, 1.02f, 0.3f, 0.3f, 0.3f); // the boulder, until thrown
        pose.popPose();

        pose.popPose();
    }
}
