package com.solegendary.reignofnether.unit;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.fogofwar.FogOfWarClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

// gold stars above army units that have gone up a rank (UnitRankEvents)
@Mod.EventBusSubscriber(modid = ReignOfNether.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class UnitRankClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final double MAX_DIST = 96;

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || MC.level == null)
            return;
        Camera camera = evt.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack matrix = evt.getPoseStack();
        MultiBufferSource.BufferSource buffers = MC.renderBuffers().bufferSource();
        Font font = MC.font;
        float pt = evt.getPartialTick();
        boolean any = false;
        for (Entity e : MC.level.entitiesForRendering()) {
            if (!(e instanceof LivingEntity le) || !(e instanceof Unit) || !le.isAlive() || le.isInvisible() || !UnitRankEvents.canRankUp(le))
                continue;
            int rank = UnitRankEvents.getRank(le);
            if (rank <= 0)
                continue;
            double x = Mth.lerp(pt, le.xo, le.getX()), y = Mth.lerp(pt, le.yo, le.getY()) + le.getBbHeight() + 1.0, z = Mth.lerp(pt, le.zo, le.getZ());
            if (cam.distanceToSqr(x, y, z) > MAX_DIST * MAX_DIST || !FogOfWarClientEvents.isInBrightChunk(le))
                continue;
            String stars = "★".repeat(rank);
            float scale = OrthoviewClientEvents.isEnabled() ? 0.04f : 0.025f;
            matrix.pushPose();
            matrix.translate(x - cam.x, y - cam.y, z - cam.z);
            matrix.mulPose(camera.rotation());
            matrix.scale(-scale, -scale, scale);
            // one see-through pass (like the city names: the depth-tested pass is lost with shaders)
            font.drawInBatch(stars, -font.width(stars) / 2f, 0, 0xFFFFD24A, false, matrix.last().pose(), buffers,
                Font.DisplayMode.SEE_THROUGH, 0, 0xF000F0);
            matrix.popPose();
            any = true;
        }
        if (any)
            buffers.endBatch();
    }
}
