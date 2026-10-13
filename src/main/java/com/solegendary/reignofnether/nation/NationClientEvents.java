package com.solegendary.reignofnether.nation;

import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.npc.Villager;
import net.minecraft.world.entity.npc.VillagerProfession;
import net.minecraft.world.entity.npc.VillagerType;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.joml.Quaternionf;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

// Nation choice screen (opened by the villagers start button when the player has no nation yet): nearly full screen,
// a big 3D villager of the nation on the left (drag to turn it) with its flag and cards showing its leader and bonus,
// and the list of nations on the right. Also shows the player's nation at the top of the RTS screen.
public class NationClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final int MARGIN = 16;
    private static final int ROW_HEIGHT = 26;

    private static Map<String, Nation> nations = new HashMap<>();
    private static boolean selecting = false;
    private static boolean startAfterChoice = false;
    private static Nation previewed = Nation.FRANCE;
    private static float villagerYaw = 20;
    private static Villager villager = null;
    private static Nation villagerNation = null;

    public static void setNations(Map<String, Nation> newNations) {
        nations = newNations;
        if (startAfterChoice && getOwnNation() != null) {
            startAfterChoice = false;
            CursorClientEvents.setLeftClickAction(UnitAction.STARTRTS_VILLAGERS);
        }
    }

    @Nullable
    public static Nation getNation(String playerName) {
        return nations.get(playerName);
    }

    @Nullable
    public static Nation getOwnNation() {
        return MC.player != null ? nations.get(MC.player.getName().getString()) : null;
    }

    @Nullable
    private static String getTakenBy(Nation nation) {
        for (Map.Entry<String, Nation> entry : nations.entrySet())
            if (entry.getValue() == nation)
                return entry.getKey();
        return null;
    }

    // villagers start button: pick a nation first if needed, then place the starting workers
    public static void onStartButtonClicked() {
        if (getOwnNation() == null) {
            selecting = true;
            startAfterChoice = true;
            for (Nation n : Nation.values()) {
                if (getTakenBy(n) == null) {
                    previewed = n;
                    break;
                }
            }
        } else {
            CursorClientEvents.setLeftClickAction(UnitAction.STARTRTS_VILLAGERS);
        }
    }

    private static void close() {
        selecting = false;
        startAfterChoice = false;
    }

    // ---- layout -------------------------------------------------------------------------------------------------

    private static int screenW() { return MC.getWindow().getGuiScaledWidth(); }
    private static int screenH() { return MC.getWindow().getGuiScaledHeight(); }
    private static int panelX0() { return MARGIN; }
    private static int panelY0() { return MARGIN; }
    private static int panelX1() { return screenW() - MARGIN; }
    private static int panelY1() { return screenH() - MARGIN; }
    private static int splitX() { return panelX0() + (int) ((panelX1() - panelX0()) * 0.6f); }
    private static int listX0() { return splitX() + 10; }
    private static int listX1() { return panelX1() - 10; }
    private static int listY0() { return panelY0() + 40; }
    private static int buttonY() { return panelY1() - 34; }
    private static int chooseButtonX0() { return listX0(); }
    private static int chooseButtonX1() { return listX0() + (listX1() - listX0()) * 2 / 3 - 4; }
    private static int cancelButtonX0() { return chooseButtonX1() + 8; }

    private static boolean inside(double mx, double my, int x0, int y0, int x1, int y1) {
        return mx >= x0 && mx <= x1 && my >= y0 && my <= y1;
    }

    @Nullable
    private static Nation getRowAt(double mx, double my) {
        if (mx < listX0() || mx > listX1() || my < listY0())
            return null;
        int row = (int) ((my - listY0()) / ROW_HEIGHT);
        return row < Nation.values().length ? Nation.values()[row] : null;
    }

    // ---- the villager representing the nation --------------------------------------------------------------------

    private static VillagerType getVillagerType(Nation nation) {
        return switch (nation) {
            case SPAIN -> VillagerType.DESERT;
            case ROME -> VillagerType.SAVANNA;
            case PORTUGAL -> VillagerType.JUNGLE;
            case RUSSIA, SWEDEN -> VillagerType.SNOW;
            case ENGLAND, HRE -> VillagerType.TAIGA;
            default -> VillagerType.PLAINS;
        };
    }

    private static VillagerProfession getProfession(Nation nation) {
        return switch (nation) {
            case FRANCE -> VillagerProfession.LIBRARIAN;
            case ENGLAND -> VillagerProfession.CARTOGRAPHER;
            case SPAIN -> VillagerProfession.CLERIC;
            case HRE -> VillagerProfession.ARMORER;
            case ROME -> VillagerProfession.MASON;
            case PORTUGAL -> VillagerProfession.FISHERMAN;
            case RUSSIA -> VillagerProfession.WEAPONSMITH;
            case SWEDEN -> VillagerProfession.TOOLSMITH;
        };
    }

    @Nullable
    private static Villager getVillager(Nation nation) {
        if (MC.level == null)
            return null;
        if (villager == null || villager.level() != MC.level) {
            villager = new Villager(EntityType.VILLAGER, MC.level);
            villagerNation = null;
        }
        if (villagerNation != nation) {
            villager.setVillagerData(villager.getVillagerData().setType(getVillagerType(nation))
                .setProfession(getProfession(nation)).setLevel(5));
            villagerNation = nation;
        }
        return villager;
    }

    // ---- drawing ------------------------------------------------------------------------------------------------

    private static int darker(int colour, float factor) {
        int r = (int) (((colour >> 16) & 0xFF) * factor), g = (int) (((colour >> 8) & 0xFF) * factor), b = (int) ((colour & 0xFF) * factor);
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    private static void drawFrame(GuiGraphics g, int x0, int y0, int x1, int y1, int border, int fill) {
        g.fill(x0 - 1, y0 - 1, x1 + 1, y1 + 1, border);
        g.fill(x0, y0, x1, y1, fill);
    }

    private static void drawScaledString(GuiGraphics g, String text, int x, int y, int colour, float scale, boolean centred) {
        g.pose().pushPose();
        g.pose().translate(x, y, 0);
        g.pose().scale(scale, scale, 1);
        if (centred)
            g.drawCenteredString(MC.font, text, 0, 0, colour);
        else
            g.drawString(MC.font, text, 0, 0, colour);
        g.pose().popPose();
    }

    // a card with a title and wrapped text
    private static void drawCard(GuiGraphics g, int x, int y, int width, String title, String text, int accent) {
        List<FormattedCharSequence> lines = MC.font.split(Component.literal(text), width - 12);
        int height = 20 + lines.size() * 10;
        drawFrame(g, x, y, x + width, y + height, accent, 0xEE1C1C1C);
        g.fill(x, y, x + 3, y + height, accent);
        g.drawString(MC.font, title, x + 8, y + 5, 0xFFD27F);
        for (int i = 0; i < lines.size(); i++)
            g.drawString(MC.font, lines.get(i), x + 8, y + 16 + i * 10, 0xE0E0E0);
    }

    // a flag on a pole, waving gently
    private static void drawFlag(GuiGraphics g, int poleX, int poleTop, int poleBottom, int colour) {
        g.fill(poleX, poleTop - 3, poleX + 3, poleBottom, 0xFF8B6A3E);
        g.fill(poleX - 1, poleTop - 5, poleX + 4, poleTop - 2, 0xFFD4AF37);
        float time = (MC.level != null ? MC.level.getGameTime() : 0) + MC.getFrameTime();
        int clothW = 48, clothH = 30, slices = 12;
        for (int i = 0; i < slices; i++) {
            int sx = poleX + 3 + i * clothW / slices;
            int ex = poleX + 3 + (i + 1) * clothW / slices;
            int wave = (int) Math.round(Math.sin(time * 0.15 + i * 0.6) * (1 + i * 0.25));
            int top = poleTop + wave;
            g.fill(sx, top, ex, top + clothH, 0xFF000000 | colour);
            g.fill(sx, top + clothH / 2 - 2, ex, top + clothH / 2 + 2, darker(colour, 0.6f)); // stripe
            g.fill(sx, top + clothH - 2, ex, top + clothH, darker(colour, 0.75f));
        }
    }

    private static void drawSelection(GuiGraphics g, int mouseX, int mouseY) {
        int x0 = panelX0(), y0 = panelY0(), x1 = panelX1(), y1 = panelY1();
        Nation nation = previewed;
        int accent = 0xFF000000 | nation.colour;

        g.fill(0, 0, screenW(), screenH(), 0x88000000); // dim the map
        drawFrame(g, x0, y0, x1, y1, 0xFF6B5A3A, 0xF0141414);
        drawScaledString(g, "Choisissez votre nation", (x0 + x1) / 2, y0 + 10, 0xFFD700, 2f, true);

        // left: the nation's villager, flag and cards
        int leftX0 = x0 + 10, leftX1 = splitX() - 6, leftY0 = y0 + 40, leftY1 = y1 - 10;
        drawFrame(g, leftX0, leftY0, leftX1, leftY1, darker(nation.colour, 0.7f), 0xFF1E1E26);
        int centreX = (leftX0 + leftX1) / 2;
        drawScaledString(g, nation.displayName, centreX, leftY0 + 8, 0xFFFFFF, 2f, true);
        g.drawCenteredString(MC.font, "dirigée par " + nation.leader, centreX, leftY0 + 28, 0xBBBBBB);

        int areaH = leftY1 - leftY0;
        int feetY = leftY1 - 24;
        int scale = Math.max(30, (int) (areaH * 0.30f));
        Villager v = getVillager(nation);
        if (v != null) {
            v.yBodyRot = 180 + villagerYaw;
            v.yBodyRotO = v.yBodyRot;
            v.setYRot(180 + villagerYaw);
            v.yRotO = v.getYRot();
            v.setXRot(0);
            v.yHeadRot = v.getYRot();
            v.yHeadRotO = v.yHeadRot;
            g.pose().pushPose();
            g.pose().translate(0, 0, 150);
            InventoryScreen.renderEntityInInventory(g, centreX, feetY, scale,
                new Quaternionf().rotateZ((float) Math.PI), new Quaternionf(), v);
            g.pose().popPose();
        }
        g.drawCenteredString(MC.font, "Glissez pour tourner", centreX, leftY1 - 12, 0x777777);

        // flag next to the villager, cards around him
        int villagerHalfW = (int) (scale * 0.45f);
        drawFlag(g, centreX + villagerHalfW + 14, feetY - (int) (scale * 2.0f), feetY, nation.colour);
        int cardW = Math.min(150, (leftX1 - leftX0) / 2 - villagerHalfW - 16);
        String takenBy = getTakenBy(nation);
        drawCard(g, leftX0 + 8, leftY0 + 48, cardW, "Dirigeant", nation.leader, accent);
        drawCard(g, leftX0 + 8, leftY0 + 48 + (areaH - 60) / 2, cardW, "Bonus", nation.bonusText, accent);
        drawCard(g, leftX1 - 8 - cardW, leftY0 + 48 + (areaH - 60) / 2, cardW, "Statut",
            takenBy == null ? "Disponible" : "Déjà prise par " + takenBy, takenBy == null ? 0xFF3C9A3C : 0xFF9A3C3C);

        // right: list of nations
        g.drawString(MC.font, "Nations", listX0(), y0 + 28, 0xFFD27F);
        Nation hovered = getRowAt(mouseX, mouseY);
        for (int i = 0; i < Nation.values().length; i++) {
            Nation n = Nation.values()[i];
            int rowY = listY0() + i * ROW_HEIGHT;
            boolean available = getTakenBy(n) == null;
            int bg = n == previewed ? 0xFF3A3424 : n == hovered ? 0xFF2A2A2A : 0xFF1A1A1A;
            drawFrame(g, listX0(), rowY, listX1(), rowY + ROW_HEIGHT - 3, n == previewed ? 0xFFD4AF37 : 0xFF333333, bg);
            g.fill(listX0() + 4, rowY + 4, listX0() + 18, rowY + ROW_HEIGHT - 7, 0xFF000000 | n.colour);
            g.drawString(MC.font, n.displayName, listX0() + 24, rowY + 3, available ? 0xFFFFFF : 0x777777);
            drawScaledString(g, available ? n.leader : "prise par " + getTakenBy(n), listX0() + 24, rowY + 13,
                available ? 0xAAAAAA : 0x666666, 0.8f, false);
        }

        // buttons
        boolean canChoose = getTakenBy(previewed) == null;
        int by = buttonY();
        boolean hoverChoose = inside(mouseX, mouseY, chooseButtonX0(), by, chooseButtonX1(), by + 24);
        drawFrame(g, chooseButtonX0(), by, chooseButtonX1(), by + 24, canChoose ? 0xFFD4AF37 : 0xFF444444,
            canChoose ? (hoverChoose ? 0xFF4E6B2E : 0xFF3A5222) : 0xFF2A2A2A);
        g.drawCenteredString(MC.font, "Jouer " + previewed.displayName, (chooseButtonX0() + chooseButtonX1()) / 2, by + 8,
            canChoose ? 0xFFFFFF : 0x777777);
        boolean hoverCancel = inside(mouseX, mouseY, cancelButtonX0(), by, listX1(), by + 24);
        drawFrame(g, cancelButtonX0(), by, listX1(), by + 24, 0xFF555555, hoverCancel ? 0xFF4A2A2A : 0xFF2A2020);
        g.drawCenteredString(MC.font, "Annuler", (cancelButtonX0() + listX1()) / 2, by + 8, 0xDDDDDD);
    }

    @SubscribeEvent
    public static void onDrawScreen(ScreenEvent.Render.Post evt) {
        if (!OrthoviewClientEvents.isEnabled() || MC.player == null)
            return;
        GuiGraphics g = evt.getGuiGraphics();

        Nation own = getOwnNation();
        if (own != null && !selecting) {
            String label = own.displayName + " — " + own.leader;
            int w = MC.font.width(label);
            int x = (screenW() - w) / 2;
            g.fill(x - 6, 2, x + w + 6, 15, 0x99000000);
            g.fill(x - 6, 14, x + w + 6, 15, 0xFF000000 | own.colour);
            g.drawString(MC.font, label, x, 4, 0xFFFFFF);
        }
        if (!selecting)
            return;
        g.pose().pushPose();
        g.pose().translate(0, 0, 400); // above the HUD
        drawSelection(g, evt.getMouseX(), evt.getMouseY());
        g.pose().popPose();
    }

    // ---- input: while the screen is open it takes every click, drag and scroll ---------------------------------

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre evt) {
        if (!selecting)
            return;
        evt.setCanceled(true);
        if (evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_1)
            return;
        double mx = evt.getMouseX(), my = evt.getMouseY();
        Nation row = getRowAt(mx, my);
        if (row != null) {
            previewed = row;
        } else if (inside(mx, my, chooseButtonX0(), buttonY(), chooseButtonX1(), buttonY() + 24)) {
            if (getTakenBy(previewed) == null) {
                selecting = false;
                NationServerboundPacket.chooseNation(previewed);
            }
        } else if (inside(mx, my, cancelButtonX0(), buttonY(), listX1(), buttonY() + 24)) {
            close();
        }
    }

    @SubscribeEvent
    public static void onMouseRelease(ScreenEvent.MouseButtonReleased.Pre evt) {
        if (selecting)
            evt.setCanceled(true);
    }

    @SubscribeEvent
    public static void onMouseDrag(ScreenEvent.MouseDragged.Pre evt) {
        if (!selecting)
            return;
        evt.setCanceled(true);
        if (evt.getMouseX() < splitX())
            villagerYaw -= (float) evt.getDragX() * 2.5f;
    }

    @SubscribeEvent
    public static void onMouseScroll(ScreenEvent.MouseScrolled.Pre evt) {
        if (selecting)
            evt.setCanceled(true);
    }

    @SubscribeEvent
    public static void onKeyPressed(ScreenEvent.KeyPressed.Pre evt) {
        if (selecting && evt.getKeyCode() == GLFW.GLFW_KEY_ESCAPE) {
            close();
            evt.setCanceled(true);
        }
    }
}
