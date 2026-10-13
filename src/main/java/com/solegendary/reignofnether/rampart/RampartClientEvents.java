package com.solegendary.reignofnether.rampart;

import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.HudClientEvents;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.UnitClientEvents;
import com.solegendary.reignofnether.unit.interfaces.Unit;
import com.solegendary.reignofnether.unit.interfaces.WorkerUnit;
import com.solegendary.reignofnether.util.MyRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

// Drawing a rampart: pick it in the workers' build menu, click where it starts, then where it ends (the wall and its
// price follow the cursor). Shift-click to keep drawing from there; right click or Escape to stop.
public class RampartClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();

    @Nullable private static RampartBuilding placing = null;
    @Nullable private static BlockPos start = null;

    private static BlockPos previewEnd = null;
    private static RampartLine.Plan preview = null;

    public static void startPlacing(RampartBuilding building) {
        BuildingClientEvents.setBuildingToPlace(null);
        placing = building;
        start = null;
        preview = null;
    }

    public static boolean isPlacing(RampartBuilding building) {
        return placing == building;
    }

    private static void stop() {
        placing = null;
        start = null;
        preview = null;
    }

    private static int[] getSelectedWorkerIds() {
        List<Integer> ids = new ArrayList<>();
        String me = MC.player != null ? MC.player.getName().getString() : "";
        for (LivingEntity le : UnitClientEvents.getSelectedUnits())
            if (le instanceof WorkerUnit && le instanceof Unit u && u.getOwnerName().equals(me))
                ids.add(le.getId());
        return ids.stream().mapToInt(Integer::intValue).toArray();
    }

    private static void updatePreview() {
        if (placing == null || MC.level == null || MC.player == null)
            return;
        if (!OrthoviewClientEvents.isEnabled() || getSelectedWorkerIds().length == 0) {
            stop();
            return;
        }
        BlockPos cursor = CursorClientEvents.getPreselectedBlockPos();
        BlockPos from = start != null ? start : cursor;
        if (preview != null && cursor.equals(previewEnd))
            return;
        previewEnd = cursor;
        preview = RampartLine.plan(MC.level, MC.player.getName().getString(), placing, from, cursor);
    }

    private static String getPreviewText() {
        if (preview == null || placing == null)
            return null;
        String name = net.minecraft.client.resources.language.I18n.get("buildings.reignofnether." +
            com.solegendary.reignofnether.api.ReignOfNetherRegistries.BUILDING.getKey(placing).getPath());
        if (!preview.isValid())
            return name + " : " + preview.error();
        String cost = preview.wood() + " bois" + (preview.ore() > 0 ? ", " + preview.ore() + " minerai" : "");
        if (placing.isTower())
            return name + " — " + cost;
        if (start == null)
            return name + " : cliquez où il commence";
        return name + " : " + preview.columns().size() + " blocs de long — " + cost;
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent evt) {
        if (evt.getStage() != RenderLevelStageEvent.Stage.AFTER_CUTOUT_BLOCKS || placing == null)
            return;
        updatePreview();
        if (preview == null || preview.columns().isEmpty())
            return;
        boolean valid = preview.isValid();
        ResourceLocation rl = ResourceLocation.parse("forge:textures/white.png");
        var vertexConsumer = MC.renderBuffers().bufferSource().getBuffer(RenderType.entityTranslucent(rl));
        for (RampartShapes.Column c : preview.columns())
            for (int h = 1; h <= c.height(); h++)
                MyRenderer.drawBox(evt.getPoseStack(), vertexConsumer, new BlockPos(c.x(), c.y() + h, c.z()),
                    valid ? 0.3f : 0.9f, valid ? 0.9f : 0.3f, 0.3f, start == null ? 0.2f : 0.35f);
    }

    @SubscribeEvent
    public static void onDrawScreen(ScreenEvent.Render.Post evt) {
        if (placing == null || !OrthoviewClientEvents.isEnabled())
            return;
        String text = getPreviewText();
        if (text == null)
            return;
        GuiGraphics g = evt.getGuiGraphics();
        int x = evt.getMouseX() + 12, y = evt.getMouseY() + 8;
        int width = MC.font.width(text);
        g.fill(x - 3, y - 3, x + width + 3, y + MC.font.lineHeight + 1, 0xAA000000);
        g.drawString(MC.font, text, x, y, preview != null && preview.isValid() ? 0xFFFFFF : 0xFF6060);
    }

    // runs before the other click handlers so the units don't move or get deselected while drawing
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre evt) {
        if (placing == null || !OrthoviewClientEvents.isEnabled() || HudClientEvents.isMouseOverAnyButtonOrHud())
            return;
        if (evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_2) {
            stop();
            evt.setCanceled(true);
            return;
        }
        if (evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_1)
            return;
        evt.setCanceled(true);
        BlockPos cursor = CursorClientEvents.getPreselectedBlockPos();
        if (placing.isTower()) { // one click
            if (preview != null && preview.isValid()) {
                RampartServerboundPacket.build(placing, cursor, cursor, getSelectedWorkerIds());
                if (!Keybindings.shiftMod.isDown())
                    stop();
            }
            return;
        }
        if (start == null) {
            start = cursor;
            preview = null;
            return;
        }
        if (preview != null && preview.isValid()) {
            RampartServerboundPacket.build(placing, start, cursor, getSelectedWorkerIds());
            if (Keybindings.shiftMod.isDown() && !placing.isGate()) {
                start = cursor; // keep drawing from the end of this wall
                preview = null;
            } else {
                stop();
            }
        }
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre evt) {
        if (placing != null && evt.getKeyCode() == GLFW.GLFW_KEY_ESCAPE) {
            stop();
            evt.setCanceled(true);
        }
    }
}
