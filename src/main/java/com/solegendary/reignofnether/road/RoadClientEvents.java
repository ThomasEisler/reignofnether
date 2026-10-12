package com.solegendary.reignofnether.road;

import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

// While choosing where to build a road, shows its route on the ground and its cost next to the cursor
public class RoadClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final int RECALC_TICKS = 100; // the route search isn't free, only redo it now and then while hovering

    private static List<RoadClientboundPacket.RoadInfo> roads = new ArrayList<>();

    // current preview, recalculated when the hovered building changes
    private static BlockPos previewFrom = null;
    private static BlockPos previewTo = null;
    private static long previewTick = 0;
    private static List<BlockPos> previewBlocks = List.of();
    private static String previewText = null;
    private static boolean previewValid = false;

    public static void setRoads(List<RoadClientboundPacket.RoadInfo> newRoads) {
        roads = newRoads;
        previewFrom = null; // force a recalculation
    }

    @Nullable
    private static RoadClientboundPacket.RoadInfo getRoad(BlockPos a, BlockPos b) {
        for (RoadClientboundPacket.RoadInfo road : roads)
            if (road.connects(a) && road.connects(b))
                return road;
        return null;
    }

    @Nullable
    private static BuildingPlacement getRoadStart() {
        for (BuildingPlacement building : BuildingClientEvents.getSelectedBuildings())
            if (building != null && MC.player != null && building.ownerName.equals(MC.player.getName().getString()))
                return building;
        return null;
    }

    private static void clearPreview() {
        previewFrom = null;
        previewTo = null;
        previewBlocks = List.of();
        previewText = null;
    }

    private static void updatePreview() {
        if (MC.level == null || MC.player == null || !OrthoviewClientEvents.isEnabled() ||
            CursorClientEvents.getLeftClickAction() != UnitAction.BUILD_ROAD) {
            clearPreview();
            return;
        }
        BuildingPlacement from = getRoadStart();
        BuildingPlacement to = BuildingClientEvents.getPreselectedBuilding();
        if (from == null || to == null || to == from) {
            clearPreview();
            return;
        }
        long tick = MC.level.getGameTime();
        if (from.originPos.equals(previewFrom) && to.originPos.equals(previewTo) && tick - previewTick < RECALC_TICKS)
            return;
        previewFrom = from.originPos;
        previewTo = to.originPos;
        previewTick = tick;
        previewBlocks = List.of();
        previewValid = false;

        if (!to.ownerName.equals(MC.player.getName().getString())) {
            previewText = I18n.get("hud.reignofnether.road_not_owned");
            return;
        }
        if (!from.isBuilt || !to.isBuilt) {
            previewText = I18n.get("hud.reignofnether.road_not_built");
            return;
        }
        RoadClientboundPacket.RoadInfo existing = getRoad(from.originPos, to.originPos);
        if (existing != null) {
            if (existing.underConstruction())
                previewText = I18n.get("hud.reignofnether.road_under_construction");
            else if (existing.type().ordinal() >= RoadType.values().length - 1)
                previewText = I18n.get("hud.reignofnether.road_max_level");
            else {
                RoadType next = RoadType.values()[existing.type().ordinal() + 1];
                previewText = I18n.get("hud.reignofnether.road_preview_upgrade",
                    I18n.get("hud.reignofnether.road_type." + next.name().toLowerCase()), getCostText(next, existing.length()));
                previewValid = true;
            }
            return;
        }
        RoadPathTracer.Result path = RoadPathTracer.trace(MC.level, from, to);
        if (path.errorKey != null) {
            previewText = I18n.get(path.errorKey);
            return;
        }
        previewBlocks = path.blocks;
        previewText = I18n.get("hud.reignofnether.road_preview", path.length, getCostText(RoadType.PATH, path.length));
        previewValid = true;
    }

    private static String getCostText(RoadType type, int length) {
        List<String> parts = new ArrayList<>();
        if (type.woodPerLength > 0)
            parts.add(I18n.get("hud.reignofnether.road_cost_wood", type.woodPerLength * length));
        if (type.orePerLength > 0)
            parts.add(I18n.get("hud.reignofnether.road_cost_ore", type.orePerLength * length));
        return String.join(", ", parts);
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS)
            return;
        updatePreview();
        if (previewBlocks.isEmpty())
            return;
        ResourceLocation rl = ResourceLocation.parse("forge:textures/white.png");
        var vertexConsumer = MC.renderBuffers().bufferSource().getBuffer(RenderType.entityTranslucent(rl));
        for (BlockPos bp : previewBlocks)
            MyRenderer.drawBox(evt.getPoseStack(), vertexConsumer, bp, 0.9f, 0.75f, 0.3f, 0.35f);
    }

    @SubscribeEvent
    public static void onDrawScreen(ScreenEvent.Render.Post evt) {
        if (previewText == null || !OrthoviewClientEvents.isEnabled())
            return;
        GuiGraphics g = evt.getGuiGraphics();
        int x = evt.getMouseX() + 12;
        int y = evt.getMouseY() + 8;
        int width = MC.font.width(previewText);
        g.fill(x - 3, y - 3, x + width + 3, y + MC.font.lineHeight + 1, 0xAA000000);
        g.drawString(MC.font, previewText, x, y, previewValid ? 0xFFFFFF : 0xFF6060);
    }
}
