package com.solegendary.reignofnether.tech;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
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

    // translated name, as shown in the build menu
    private static String displayName(Building building) {
        var key = ReignOfNetherRegistries.BUILDING.getKey(building);
        if (key == null)
            return building.name;
        return net.minecraft.network.chat.Component.translatable("buildings." + building.getFaction().getName() + "." +
            key.getNamespace() + "." + key.getPath()).getString();
    }

    private static String formatTime(int seconds) {
        return (seconds / 60) + ":" + String.format("%02d", seconds % 60);
    }

    // ---- look: the vanilla advancements screen ----------------------------------------------------------------

    private static final net.minecraft.resources.ResourceLocation WINDOW =
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/advancements/window.png");
    private static final net.minecraft.resources.ResourceLocation WIDGETS =
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/advancements/widgets.png");
    private static final net.minecraft.resources.ResourceLocation BUTTONS =
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/widgets.png");
    private static final net.minecraft.resources.ResourceLocation BACKGROUND =
        net.minecraft.resources.ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/dark_oak_planks.png");

    private static final int COL_SPACING = 112, ROW_SPACING = 56, FRAME = 26;
    private static double scrollX = 0, scrollY = 0;

    private static net.minecraft.world.item.ItemStack getIcon(Tech tech) {
        return new net.minecraft.world.item.ItemStack(switch (tech) {
            case MASONRY -> net.minecraft.world.item.Items.STONE_BRICKS;
            case COMMERCE -> net.minecraft.world.item.Items.EMERALD;
            case METALLURGY -> net.minecraft.world.item.Items.IRON_INGOT;
            case ARCHITECTURE -> net.minecraft.world.item.Items.BRICKS;
            case MYSTICISM -> net.minecraft.world.item.Items.BREWING_STAND;
            case FORTIFICATIONS -> net.minecraft.world.item.Items.SHIELD;
            case ENGINEERING -> net.minecraft.world.item.Items.PISTON;
            case ARCANA -> net.minecraft.world.item.Items.ENCHANTED_BOOK;
        });
    }

    // frame shape like advancements: square, rounded for the second tier, spiky for the last
    private static int frameU(Tech tech) {
        return tech.column == 0 ? 0 : tech.column == 1 ? 52 : 26;
    }

    private static int screenW() { return MC.getWindow().getGuiScaledWidth(); }
    private static int screenH() { return MC.getWindow().getGuiScaledHeight(); }

    // window
    private static int winW() { return Math.min(screenW() - 32, 400); }
    private static int winH() { return Math.min(screenH() - 70, 240); }
    private static int winX() { return (screenW() - winW()) / 2; }
    private static int winY() { return Math.max(8, (screenH() - winH()) / 2 - 14); }
    private static int inX0() { return winX() + 9; }
    private static int inY0() { return winY() + 18; }
    private static int inX1() { return winX() + winW() - 9; }
    private static int inY1() { return winY() + winH() - 9; }

    private static int treeW() { return 2 * COL_SPACING + FRAME; }
    private static int treeH() { return 2 * ROW_SPACING + FRAME; }
    private static int nodeX(Tech t) { return (inX0() + inX1() - treeW()) / 2 + (int) scrollX + t.column * COL_SPACING; }
    private static int nodeY(Tech t) { return (inY0() + inY1() - treeH()) / 2 + (int) scrollY + t.row * ROW_SPACING; }

    // the HUD button that opens the tree
    private static final int HUD_BTN_W = 104, HUD_BTN_H = 20;
    private static int hudBtnX() { return 4; }
    private static int hudBtnY() { return screenH() / 3; }

    // cancel button of the current research, under the window
    private static final int CANCEL_W = 70, CANCEL_H = 20;
    private static int cancelX0() { return (screenW() - CANCEL_W) / 2; }
    private static int cancelY0() { return winY() + winH() + 18; }

    @Nullable
    private static Tech getNodeAt(double mx, double my) {
        if (mx < inX0() || mx > inX1() || my < inY0() || my > inY1())
            return null;
        for (Tech t : Tech.values())
            if (mx >= nodeX(t) && mx <= nodeX(t) + FRAME && my >= nodeY(t) && my <= nodeY(t) + FRAME)
                return t;
        return null;
    }

    private static boolean inside(double mx, double my, int x0, int y0, int w, int h) {
        return mx >= x0 && mx <= x0 + w && my >= y0 && my <= y0 + h;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------

    private static void vanillaButton(GuiGraphics g, int x, int y, int w, int h, boolean hovered) {
        g.blitNineSliced(BUTTONS, x, y, w, h, 20, 4, 200, 20, 0, hovered ? 86 : 66);
    }

    private static List<String> getUnlockNames(Tech tech) {
        List<String> names = new ArrayList<>();
        for (String b : tech.unlockedBuildings) {
            Building building = findBuilding(b);
            names.add(building != null ? displayName(building) : b);
        }
        for (String l : tech.unlockedLevels) {
            String[] parts = l.split(":");
            Building building = findBuilding(parts[0]);
            names.add((building != null ? displayName(building) : parts[0]) + " niv. " + (Integer.parseInt(parts[1]) + 1));
        }
        return names;
    }

    private static String costText(Tech tech) {
        List<String> parts = new ArrayList<>();
        if (tech.food > 0) parts.add(tech.food + " nourriture");
        if (tech.wood > 0) parts.add(tech.wood + " bois");
        if (tech.ore > 0) parts.add(tech.ore + " minerai");
        return String.join(", ", parts);
    }

    // white links with a black outline, like advancements (grey when the required technology isn't known yet)
    private static void drawLinks(GuiGraphics g, boolean outline) {
        for (Tech tech : Tech.values()) {
            for (Tech pre : tech.getPrerequisites()) {
                int sx = nodeX(pre) + FRAME, sy = nodeY(pre) + FRAME / 2;
                int ex = nodeX(tech), ey = nodeY(tech) + FRAME / 2;
                int midX = (sx + ex) / 2 + (tech.row - pre.row) * 2;
                if (outline) {
                    g.hLine(sx, midX, sy - 1, 0xFF000000); g.hLine(sx, midX + 1, sy, 0xFF000000); g.hLine(sx, midX, sy + 1, 0xFF000000);
                    g.hLine(midX - 1, ex, ey - 1, 0xFF000000); g.hLine(midX, ex, ey, 0xFF000000); g.hLine(midX - 1, ex, ey + 1, 0xFF000000);
                    g.vLine(midX - 1, Math.min(sy, ey), Math.max(sy, ey), 0xFF000000); g.vLine(midX + 1, Math.min(sy, ey), Math.max(sy, ey), 0xFF000000);
                } else {
                    int colour = done.contains(pre) ? 0xFFFFFFFF : 0xFF8A8A8A;
                    g.hLine(sx, midX, sy, colour);
                    g.hLine(midX, ex, ey, colour);
                    g.vLine(midX, Math.min(sy, ey), Math.max(sy, ey), colour);
                }
            }
        }
    }

    private static void drawNode(GuiGraphics g, Tech tech) {
        int x = nodeX(tech), y = nodeY(tech);
        boolean obtained = done.contains(tech);
        g.blit(WIDGETS, x, y, frameU(tech), 128 + (obtained ? 0 : 26), FRAME, FRAME);
        g.renderFakeItem(getIcon(tech), x + 5, y + 5);
        if (!obtained && !isAvailable(tech) && tech != current) { // locked: darkened
            g.pose().pushPose();
            g.pose().translate(0, 0, 200);
            g.fill(x + 2, y + 2, x + FRAME - 2, y + FRAME - 2, 0x99000000);
            g.pose().popPose();
        }
        if (tech == current) { // progress bar under the frame
            float progress = 1f - ticksLeft / (float) (tech.seconds * 20);
            g.fill(x, y + FRAME + 1, x + FRAME, y + FRAME + 4, 0xFF000000);
            g.fill(x + 1, y + FRAME + 2, x + 1 + (int) ((FRAME - 2) * progress), y + FRAME + 3, 0xFF4FD34F);
        }
    }

    // hover: a title bar coming out of the frame and a description box under it, like advancements
    private static void drawHover(GuiGraphics g, Tech tech) {
        int x = nodeX(tech), y = nodeY(tech);
        boolean isDone = done.contains(tech), isCurrent = tech == current, available = isAvailable(tech);

        List<FormattedCharSequence> lines = new ArrayList<>();
        java.util.function.BiConsumer<String, Integer> add = (text, colour) ->
            lines.addAll(MC.font.split(net.minecraft.network.chat.Component.literal(text)
                .withStyle(st -> st.withColor(colour)), 180));
        if (isDone)
            add.accept("Découverte", 0xD4AF37);
        else if (isCurrent)
            add.accept("En cours — reste " + formatTime(Math.max(0, ticksLeft / 20)), 0x7FD3FF);
        else if (available)
            add.accept(current == null ? "Cliquez pour rechercher" : "Disponible (une recherche à la fois)", 0x7FD37F);
        else {
            List<String> missing = new ArrayList<>();
            for (Tech pre : tech.getPrerequisites())
                if (!done.contains(pre))
                    missing.add(pre.displayName);
            add.accept("Il faut d'abord : " + String.join(", ", missing), 0xFF8080);
        }
        if (!isDone) {
            add.accept("Coût : " + costText(tech), 0xE0E0E0);
            add.accept("Durée : " + formatTime(tech.seconds), 0xE0E0E0);
        }
        add.accept("Débloque : " + String.join(", ", getUnlockNames(tech)), 0xAAAAAA);

        int textW = 0;
        for (FormattedCharSequence l : lines)
            textW = Math.max(textW, MC.font.width(l));
        int w = Math.max(MC.font.width(tech.displayName) + FRAME + 16, textW + 12);
        int boxH = 8 + lines.size() * 9 + 4;
        boolean toLeft = x + w > inX1() + 40;
        int bx = toLeft ? x + FRAME - w : x;

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);
        // description box, then the bar on top of it
        g.blitNineSliced(WIDGETS, bx, y + 18, w, boxH, 10, 200, 26, 0, 52);
        for (int i = 0; i < lines.size(); i++)
            g.drawString(MC.font, lines.get(i), bx + 6, y + 18 + 8 + i * 9, 0xFFFFFF, false);
        // bar: gold for what's done (part of it for the research in progress), blue for the rest
        int filled = isDone ? w : isCurrent ? (int) (w * (1f - ticksLeft / (float) (tech.seconds * 20))) : 0;
        if (filled < w)
            g.blitNineSliced(WIDGETS, bx, y, w, FRAME, 10, 200, 26, 0, 26);
        if (filled > 0) {
            g.enableScissor(bx, y, bx + filled, y + FRAME);
            g.blitNineSliced(WIDGETS, bx, y, w, FRAME, 10, 200, 26, 0, 0);
            g.disableScissor();
        }
        g.drawString(MC.font, tech.displayName, toLeft ? bx + 6 : x + FRAME + 6, y + 9, 0xFFFFFF, true);
        g.pose().translate(0, 0, 10);
        g.blit(WIDGETS, x, y, frameU(tech), 128 + (isDone ? 0 : 26), FRAME, FRAME);
        g.renderFakeItem(getIcon(tech), x + 5, y + 5);
        g.pose().popPose();
    }

    private static void drawTree(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 0, screenW(), screenH(), 0xAA000000);

        // background, tiled and scrolling with the tree
        g.enableScissor(inX0(), inY0(), inX1(), inY1());
        int ox = Math.floorMod((int) scrollX, 16) - 16, oy = Math.floorMod((int) scrollY, 16) - 16;
        for (int tx = inX0() + ox; tx < inX1(); tx += 16)
            for (int ty = inY0() + oy; ty < inY1(); ty += 16)
                g.blit(BACKGROUND, tx, ty, 0, 0, 16, 16, 16, 16);
        g.fill(inX0(), inY0(), inX1(), inY1(), 0x55000000);
        drawLinks(g, true);
        drawLinks(g, false);
        for (Tech tech : Tech.values())
            drawNode(g, tech);
        g.disableScissor();

        // window frame and title
        g.pose().pushPose();
        g.pose().translate(0, 0, 250);
        g.blitNineSliced(WINDOW, winX(), winY(), winW(), winH(), 9, 18, 9, 9, 252, 140, 0, 0);
        g.drawString(MC.font, "Arbre des technologies", winX() + 8, winY() + 6, 0x404040, false);
        String count = done.size() + "/" + Tech.values().length;
        g.drawString(MC.font, count, winX() + winW() - 8 - MC.font.width(count), winY() + 6, 0x404040, false);
        g.pose().popPose();

        Tech hovered = getNodeAt(mouseX, mouseY);
        if (hovered != null)
            drawHover(g, hovered);

        // status under the window
        String info;
        int infoColour = 0xFFFFFF;
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
            info = "Cliquez une technologie pour la rechercher — glissez pour déplacer l'arbre";
        }
        g.drawCenteredString(MC.font, info, screenW() / 2, winY() + winH() + 5, infoColour);
        if (current != null) {
            boolean hover = inside(mouseX, mouseY, cancelX0(), cancelY0(), CANCEL_W, CANCEL_H);
            vanillaButton(g, cancelX0(), cancelY0(), CANCEL_W, CANCEL_H, hover);
            g.drawCenteredString(MC.font, "Annuler", cancelX0() + CANCEL_W / 2, cancelY0() + 6, hover ? 0xFFFFA0 : 0xFFFFFF);
        }
        g.drawCenteredString(MC.font, "Échap pour fermer", screenW() / 2,
            current != null ? cancelY0() + CANCEL_H + 4 : winY() + winH() + 18, 0xAAAAAA);
    }

    private static void drawHudButton(GuiGraphics g, int mouseX, int mouseY) {
        int x = hudBtnX(), y = hudBtnY();
        boolean hover = inside(mouseX, mouseY, x, y, HUD_BTN_W, HUD_BTN_H);
        vanillaButton(g, x, y, HUD_BTN_W, HUD_BTN_H, hover);
        g.renderFakeItem(new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.WRITABLE_BOOK), x + 3, y + 2);
        g.drawString(MC.font, "Technologies " + done.size() + "/" + Tech.values().length, x + 22, y + 6, hover ? 0xFFFFA0 : 0xFFFFFF);
        if (current != null) {
            float progress = 1f - ticksLeft / (float) (current.seconds * 20);
            g.fill(x + 2, y + HUD_BTN_H - 3, x + HUD_BTN_W - 2, y + HUD_BTN_H - 1, 0xFF000000);
            g.fill(x + 2, y + HUD_BTN_H - 3, x + 2 + (int) ((HUD_BTN_W - 4) * progress), y + HUD_BTN_H - 1, 0xFF4FD34F);
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
        if (current != null && inside(mx, my, cancelX0(), cancelY0(), CANCEL_W, CANCEL_H)) {
            TechServerboundPacket.cancel();
            return;
        }
        Tech tech = getNodeAt(mx, my);
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
        if (!open)
            return;
        evt.setCanceled(true);
        if (inside(evt.getMouseX(), evt.getMouseY(), inX0(), inY0(), inX1() - inX0(), inY1() - inY0())) {
            int limitX = Math.max(20, (treeW() - (inX1() - inX0())) / 2 + 40);
            int limitY = Math.max(20, (treeH() - (inY1() - inY0())) / 2 + 40);
            scrollX = Mth.clamp(scrollX + evt.getDragX(), -limitX, limitX);
            scrollY = Mth.clamp(scrollY + evt.getDragY(), -limitY, limitY);
        }
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
