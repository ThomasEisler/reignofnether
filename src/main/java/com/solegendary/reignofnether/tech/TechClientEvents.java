package com.solegendary.reignofnether.tech;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

// Technology tree screen (nearly full screen): one card per technology, linked to the ones it requires, with its
// cost, duration and small icons of the buildings and levels it unlocks. Click an available one to research it.
// Opened by the "Technologies" button at the top of the RTS screen or from a Library.
public class TechClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final int MARGIN = 16;
    private static final int ICON = 18;

    private static Set<Tech> done = EnumSet.noneOf(Tech.class);
    @Nullable private static Tech current = null;
    private static int ticksLeft = 0;
    private static boolean open = false;

    public static void setState(Set<Tech> newDone, @Nullable Tech newCurrent, int newTicksLeft) {
        done = newDone;
        current = newCurrent;
        ticksLeft = newTicksLeft;
    }

    public static boolean hasTech(Tech tech) {
        return done.contains(tech);
    }

    public static void openTree() {
        open = true;
    }

    private static boolean isAvailable(Tech tech) {
        if (done.contains(tech))
            return false;
        for (Tech pre : tech.getPrerequisites())
            if (!done.contains(pre))
                return false;
        return true;
    }

    private static boolean hasLibrary() {
        if (MC.player == null)
            return false;
        String name = MC.player.getName().getString();
        for (BuildingPlacement b : BuildingClientEvents.getBuildings())
            if (b.ownerName.equals(name) && b.isBuilt && b.getBuilding() == Buildings.LIBRARY)
                return true;
        return false;
    }

    private static boolean hasStarted() {
        if (MC.player == null)
            return false;
        String name = MC.player.getName().getString();
        for (BuildingPlacement b : BuildingClientEvents.getBuildings())
            if (b.ownerName.equals(name))
                return true;
        return false;
    }

    @Nullable
    private static Building findBuilding(String name) {
        for (Building b : ReignOfNetherRegistries.BUILDING)
            if (name.equals(b.name))
                return b;
        return null;
    }

    private static String formatTime(int seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    // ---- layout -------------------------------------------------------------------------------------------------

    private static int screenW() { return MC.getWindow().getGuiScaledWidth(); }
    private static int screenH() { return MC.getWindow().getGuiScaledHeight(); }
    private static int x0() { return MARGIN; }
    private static int y0() { return MARGIN; }
    private static int x1() { return screenW() - MARGIN; }
    private static int y1() { return screenH() - MARGIN; }
    private static int treeY0() { return y0() + 58; }
    private static int colWidth() { return (x1() - x0() - 20) / 3; }
    private static int rowHeight() { return (y1() - 10 - treeY0()) / 3; }
    private static int cardW() { return colWidth() - 40; }
    private static int cardH() { return Math.min(rowHeight() - 14, 78); }
    private static int cardX(Tech t) { return x0() + 10 + t.column * colWidth() + 20; }
    private static int cardY(Tech t) { return treeY0() + t.row * rowHeight() + (rowHeight() - cardH()) / 2; }

    // the HUD button that opens the tree
    private static final int HUD_BTN_W = 96, HUD_BTN_H = 14;
    private static int hudBtnX() { return 6; }
    private static int hudBtnY() { return screenH() / 3; }

    // cancel button of the current research
    private static int cancelX0() { return x1() - 80; }
    private static int cancelY0() { return y0() + 30; }

    @Nullable
    private static Tech getCardAt(double mx, double my) {
        for (Tech t : Tech.values())
            if (mx >= cardX(t) && mx <= cardX(t) + cardW() && my >= cardY(t) && my <= cardY(t) + cardH())
                return t;
        return null;
    }

    private static boolean inside(double mx, double my, int x0, int y0, int w, int h) {
        return mx >= x0 && mx <= x0 + w && my >= y0 && my <= y0 + h;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------

    private static void frame(GuiGraphics g, int x0, int y0, int x1, int y1, int border, int fill) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, border);
        g.fill(x0, y0, x1, y1, fill);
    }

    private static void scaled(GuiGraphics g, String text, int x, int y, int colour, float scale, boolean centred) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        if (centred)
            g.drawCenteredString(MC.font, text, 0, 0, colour);
        else
            g.drawString(MC.font, text, 0, 0, colour);
        g.pose().popPose();
    }

    // small square with the icon of an unlocked building (and the level number for level unlocks)
    private static void drawUnlockIcon(GuiGraphics g, Building building, @Nullable String levelLabel, int x, int y, boolean lit) {
        frame(g, x, y, x + ICON, y + ICON, lit ? 0xFFD4AF37 : 0xFF555555, 0xFF101010);
        if (building.icon != null)
            g.blit(building.icon, x + 1, y + 1, 0, 0, ICON - 2, ICON - 2, ICON - 2, ICON - 2);
        if (!lit)
            g.fill(x, y, x + ICON, y + ICON, 0x88000000);
        if (levelLabel != null) {
            g.pose().pushPose();
            g.pose().translate(0, 0, 10);
            g.drawString(MC.font, levelLabel, x + ICON - 6, y + ICON - 8, 0xFFD700);
            g.pose().popPose();
        }
    }

    private static List<String> getUnlockNames(Tech tech) {
        List<String> names = new ArrayList<>();
        for (String b : tech.unlockedBuildings) {
            Building building = findBuilding(b);
            names.add(building != null ? building.name : b);
        }
        for (String l : tech.unlockedLevels) {
            String[] parts = l.split(":");
            names.add(parts[0] + " niv. " + (Integer.parseInt(parts[1]) + 1));
        }
        return names;
    }

    private static void drawCard(GuiGraphics g, Tech tech, boolean hovered) {
        int x = cardX(tech), y = cardY(tech), w = cardW(), h = cardH();
        boolean isDone = done.contains(tech);
        boolean isCurrent = tech == current;
        boolean available = isAvailable(tech) && current == null;
        int border = isDone ? 0xFFD4AF37 : isCurrent ? 0xFF4FA3E0 : available ? (hovered ? 0xFFFFFFFF : 0xFFAAAAAA) : 0xFF444444;
        int fill = isDone ? 0xFF2E2816 : isCurrent ? 0xFF172634 : available && hovered ? 0xFF2C2C2C : 0xFF1A1A1A;
        frame(g, x, y, x + w, y + h, border, fill);
        int titleColour = isDone || isCurrent || isAvailable(tech) ? 0xFFFFFF : 0x777777;
        g.drawString(MC.font, tech.displayName, x + 6, y + 5, titleColour);
        String status = isDone ? "Découverte" : isCurrent ? "En cours" : isAvailable(tech) ? "Disponible" : "Verrouillée";
        int statusColour = isDone ? 0xD4AF37 : isCurrent ? 0x4FA3E0 : isAvailable(tech) ? 0x7FD37F : 0x777777;
        scaled(g, status, x + w - 6 - (int) (MC.font.width(status) * 0.8f), y + 6, statusColour, 0.8f, false);

        String cost = (tech.food > 0 ? tech.food + " nourr.  " : "") + (tech.wood > 0 ? tech.wood + " bois  " : "") +
            (tech.ore > 0 ? tech.ore + " minerai  " : "") + "· " + formatTime(tech.seconds);
        scaled(g, cost, x + 6, y + 17, isDone ? 0x888888 : 0xCCCCCC, 0.8f, false);

        // unlocks: small squares like in the build menu
        int ix = x + 6, iy = y + 28;
        for (String b : tech.unlockedBuildings) {
            Building building = findBuilding(b);
            if (building != null) {
                drawUnlockIcon(g, building, null, ix, iy, isDone);
                ix += ICON + 4;
            }
        }
        for (String l : tech.unlockedLevels) {
            String[] parts = l.split(":");
            Building building = findBuilding(parts[0]);
            if (building != null) {
                drawUnlockIcon(g, building, String.valueOf(Integer.parseInt(parts[1]) + 1), ix, iy, isDone);
                ix += ICON + 4;
            }
        }
        if (h >= 62)
            scaled(g, "Débloque : " + String.join(", ", getUnlockNames(tech)), x + 6, iy + ICON + 4, 0x999999, 0.7f, false);

        if (isCurrent) {
            float progress = 1f - ticksLeft / (float) (tech.seconds * 20);
            g.fill(x, y + h - 4, x + w, y + h, 0xFF0E1A24);
            g.fill(x, y + h - 4, x + (int) (w * progress), y + h, 0xFF4FA3E0);
        }
    }

    // links from each technology to the ones it unlocks
    private static void drawLinks(GuiGraphics g) {
        for (Tech tech : Tech.values()) {
            for (Tech pre : tech.getPrerequisites()) {
                int sx = cardX(pre) + cardW(), sy = cardY(pre) + cardH() / 2;
                int ex = cardX(tech), ey = cardY(tech) + cardH() / 2;
                int midX = (sx + ex) / 2 + (tech.row - pre.row) * 3;
                int colour = done.contains(pre) ? 0xFFD4AF37 : 0xFF555555;
                g.fill(sx, sy - 1, midX, sy + 1, colour);
                g.fill(midX - 1, Math.min(sy, ey) - 1, midX + 1, Math.max(sy, ey) + 1, colour);
                g.fill(midX, ey - 1, ex, ey + 1, colour);
            }
        }
    }

    private static void drawTree(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 0, screenW(), screenH(), 0x88000000);
        frame(g, x0(), y0(), x1(), y1(), 0xFF6B5A3A, 0xF0141414);
        scaled(g, "Arbre des technologies", (x0() + x1()) / 2, y0() + 8, 0xFFD700, 2f, true);

        String info;
        int infoColour = 0xCCCCCC;
        if (current != null) {
            info = "Recherche en cours : " + current.displayName + " — reste " + formatTime(Math.max(0, ticksLeft / 20));
            if (!hasLibrary()) {
                info += "  (en pause : il faut une Bibliothèque)";
                infoColour = 0xFF8080;
            }
        } else if (!hasLibrary()) {
            info = "Construisez une Bibliothèque pour faire des recherches";
            infoColour = 0xFF8080;
        } else {
            info = "Cliquez une technologie disponible pour la rechercher (une à la fois)";
        }
        g.drawString(MC.font, info, x0() + 12, y0() + 34, infoColour);
        if (current != null) {
            boolean hover = inside(mouseX, mouseY, cancelX0(), cancelY0(), 70, 14);
            frame(g, cancelX0(), cancelY0(), cancelX0() + 70, cancelY0() + 14, 0xFF777777, hover ? 0xFF4A2A2A : 0xFF2A2020);
            g.drawCenteredString(MC.font, "Annuler", cancelX0() + 35, cancelY0() + 3, 0xDDDDDD);
        }
        drawLinks(g);
        Tech hovered = getCardAt(mouseX, mouseY);
        for (Tech tech : Tech.values())
            drawCard(g, tech, tech == hovered);
        g.drawCenteredString(MC.font, "Échap pour fermer", (x0() + x1()) / 2, y1() - 11, 0x777777);
    }

    private static void drawHudButton(GuiGraphics g, int mouseX, int mouseY) {
        int x = hudBtnX(), y = hudBtnY();
        boolean hover = inside(mouseX, mouseY, x, y, HUD_BTN_W, HUD_BTN_H);
        frame(g, x, y, x + HUD_BTN_W, y + HUD_BTN_H, 0xFF6B5A3A, hover ? 0xEE3A3424 : 0xCC141414);
        g.drawString(MC.font, "Technologies " + done.size() + "/" + Tech.values().length, x + 4, y + 3, 0xFFD27F);
        if (current != null) {
            float progress = 1f - ticksLeft / (float) (current.seconds * 20);
            g.fill(x, y + HUD_BTN_H - 2, x + (int) (HUD_BTN_W * progress), y + HUD_BTN_H, 0xFF4FA3E0);
        }
    }

    @SubscribeEvent
    public static void onDrawScreen(ScreenEvent.Render.Post evt) {
        if (!OrthoviewClientEvents.isEnabled() || MC.player == null)
            return;
        GuiGraphics g = evt.getGuiGraphics();
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        if (open)
            drawTree(g, evt.getMouseX(), evt.getMouseY());
        else if (hasStarted())
            drawHudButton(g, evt.getMouseX(), evt.getMouseY());
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre evt) {
        if (!OrthoviewClientEvents.isEnabled())
            return;
        double mx = evt.getMouseX(), my = evt.getMouseY();
        if (!open) {
            if (hasStarted() && evt.getButton() == GLFW.GLFW_MOUSE_BUTTON_1 && inside(mx, my, hudBtnX(), hudBtnY(), HUD_BTN_W, HUD_BTN_H)) {
                open = true;
                evt.setCanceled(true);
            }
            return;
        }
        evt.setCanceled(true);
        if (evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_1)
            return;
        if (current != null && inside(mx, my, cancelX0(), cancelY0(), 70, 14)) {
            TechServerboundPacket.cancel();
            return;
        }
        Tech tech = getCardAt(mx, my);
        if (tech != null && current == null && isAvailable(tech))
            TechServerboundPacket.start(tech);
    }

    @SubscribeEvent
    public static void onMouseRelease(ScreenEvent.MouseButtonReleased.Pre evt) {
        if (open)
            evt.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseDrag(ScreenEvent.MouseDragged.Pre evt) {
        if (open)
            evt.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseScroll(ScreenEvent.MouseScrolled.Pre evt) {
        if (open)
            evt.setCanceled(true);
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre evt) {
        if (open && evt.getKeyCode() == GLFW.GLFW_KEY_ESCAPE) {
            open = false;
            evt.setCanceled(true);
        }
    }
}
