package com.solegendary.reignofnether.nation;

import com.mojang.blaze3d.vertex.PoseStack;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;

// each city's name, floating above its Town Centre in its nation's colour
public class CityClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final double MAX_DIST = 220;
    private static Map<Long, String> names = new HashMap<>();

    public static void setNames(Map<Long, String> newNames) {
        names = newNames;
    }

    public static String getName(BuildingPlacement placement) {
        return names.get(placement.originPos.asLong());
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_TRANSLUCENT_BLOCKS || MC.level == null || names.isEmpty())
            return;
        Camera camera = evt.getCamera();
        Vec3 cam = camera.getPosition();
        PoseStack matrix = evt.getPoseStack();
        MultiBufferSource.BufferSource buffers = MC.renderBuffers().bufferSource();
        Font font = MC.font;
        for (BuildingPlacement b : BuildingClientEvents.getBuildings()) {
            String name = names.get(b.originPos.asLong());
            if (name == null || !b.isExploredClientside)
                continue;
            double x = b.centrePos.getX() + 0.5, y = b.maxCorner.getY() + 2.5, z = b.centrePos.getZ() + 0.5;
            if (cam.distanceToSqr(x, cam.y, z) > MAX_DIST * MAX_DIST)
                continue;
            Nation nation = NationClientEvents.getNation(b.ownerName);
            int colour = nation != null ? nation.colour : 0xFFFFFF;
            float scale = OrthoviewClientEvents.isEnabled() ? 0.09f : 0.04f;
            matrix.pushPose();
            matrix.translate(x - cam.x, y - cam.y, z - cam.z);
            matrix.mulPose(camera.rotation());
            matrix.scale(-scale, -scale, scale);
            float w = font.width(name) / 2f;
            // drawn in one see-through pass only: with shaders (Oculus) the usual second, depth-tested pass
            // is lost and only a faint grey name remained. Dark nation colours are lightened to stay readable.
            font.drawInBatch(name, -w, 0, 0xFF000000 | lighten(colour), false, matrix.last().pose(), buffers,
                Font.DisplayMode.SEE_THROUGH, 0x90000000, 0xF000F0);
            matrix.popPose();
        }
        buffers.endBatch();
    }

    // mixes a colour 40% towards white
    private static int lighten(int rgb) {
        int r = (rgb >> 16) & 0xFF, g = (rgb >> 8) & 0xFF, b = rgb & 0xFF;
        r += (255 - r) * 2 / 5; g += (255 - g) * 2 / 5; b += (255 - b) * 2 / 5;
        return (r << 16) | (g << 8) | b;
    }
}
