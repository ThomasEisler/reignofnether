package com.solegendary.reignofnether.nation;

import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;

// Nation choice panel (opened by the villagers start button when the player has no nation yet) and the
// player's nation shown at the top of the RTS screen
public class NationClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final int PANEL_WIDTH = 300;
    private static final int ROW_HEIGHT = 24;
    private static final int HEADER_HEIGHT = 22;

    private static Map<String, Nation> nations = new HashMap<>();
    private static boolean selecting = false;
    private static boolean startAfterChoice = false;

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
        } else {
            CursorClientEvents.setLeftClickAction(UnitAction.STARTRTS_VILLAGERS);
        }
    }

    private static int panelX() { return (MC.getWindow().getGuiScaledWidth() - PANEL_WIDTH) / 2; }
    private static int panelY() { return (MC.getWindow().getGuiScaledHeight() - (HEADER_HEIGHT + ROW_HEIGHT * Nation.values().length)) / 2; }

    @Nullable
    private static Nation getNationAt(double mouseX, double mouseY) {
        int x = panelX(), y = panelY() + HEADER_HEIGHT;
        if (mouseX < x || mouseX > x + PANEL_WIDTH || mouseY < y)
            return null;
        int row = (int) ((mouseY - y) / ROW_HEIGHT);
        return row < Nation.values().length ? Nation.values()[row] : null;
    }

    @SubscribeEvent
    public static void onDrawScreen(ScreenEvent.Render.Post evt) {
        if (!OrthoviewClientEvents.isEnabled() || MC.player == null)
            return;
        GuiGraphics g = evt.getGuiGraphics();

        Nation own = getOwnNation();
        if (own != null) {
            String label = own.displayName + " — " + own.leader;
            int w = MC.font.width(label);
            int x = (MC.getWindow().getGuiScaledWidth() - w) / 2;
            g.fill(x - 6, 2, x + w + 6, 15, 0x99000000);
            g.fill(x - 6, 14, x + w + 6, 15, 0xFF000000 | own.colour);
            g.drawString(MC.font, label, x, 4, 0xFFFFFF);
        }
        if (!selecting)
            return;

        int x = panelX(), y = panelY();
        int height = HEADER_HEIGHT + ROW_HEIGHT * Nation.values().length;
        g.pose().pushPose();
        g.pose().translate(0, 0, 500); // above the HUD
        g.fill(x - 2, y - 2, x + PANEL_WIDTH + 2, y + height + 2, 0xFF2A2A2A);
        g.fill(x, y, x + PANEL_WIDTH, y + height, 0xEE101010);
        g.drawCenteredString(MC.font, "Choisissez votre nation", x + PANEL_WIDTH / 2, y + 7, 0xFFD700);
        Nation hovered = getNationAt(evt.getMouseX(), evt.getMouseY());
        for (int i = 0; i < Nation.values().length; i++) {
            Nation nation = Nation.values()[i];
            int rowY = y + HEADER_HEIGHT + i * ROW_HEIGHT;
            String takenBy = getTakenBy(nation);
            boolean available = takenBy == null;
            if (nation == hovered && available)
                g.fill(x + 2, rowY, x + PANEL_WIDTH - 2, rowY + ROW_HEIGHT - 1, 0x44FFFFFF);
            g.fill(x + 6, rowY + 4, x + 14, rowY + 12, 0xFF000000 | nation.colour);
            g.drawString(MC.font, nation.displayName + " — " + nation.leader, x + 20, rowY + 3, available ? 0xFFFFFF : 0x777777);
            String second = available ? nation.bonusText : "Prise par " + takenBy;
            g.pose().pushPose();
            g.pose().translate(x + 20, rowY + 13, 0);
            g.pose().scale(0.75f, 0.75f, 1);
            g.drawString(MC.font, second, 0, 0, available ? 0xAAAAAA : 0x666666);
            g.pose().popPose();
        }
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre evt) {
        if (!selecting)
            return;
        evt.setCanceled(true); // the panel takes the click, not the map behind it
        if (evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_1) {
            selecting = false;
            startAfterChoice = false;
            return;
        }
        Nation nation = getNationAt(evt.getMouseX(), evt.getMouseY());
        if (nation != null && getTakenBy(nation) == null) {
            selecting = false;
            NationServerboundPacket.chooseNation(nation);
        } else if (nation == null) {
            int x = panelX(), y = panelY();
            if (evt.getMouseX() < x || evt.getMouseX() > x + PANEL_WIDTH || evt.getMouseY() < y) {
                selecting = false;
                startAfterChoice = false;
            }
        }
    }
}
