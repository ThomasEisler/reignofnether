package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

// "Hôtel de ville" button (left, under the Technologies button) and the Home key: brings the camera back to the
// player's Town Centre. Pressed again, it goes to the next city.
public class HomeButtonClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static final ResourceLocation BUTTONS = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/gui/widgets.png");
    private static final int W = 104, H = 20;
    private static int nextCity = 0;

    private static int x() { return 4; }
    private static int y() { return MC.getWindow().getGuiScaledHeight() / 3 + 24; } // under the Technologies button

    private static List<BuildingPlacement> myCities() {
        if (MC.player == null)
            return List.of();
        String me = MC.player.getName().getString();
        return BuildingClientEvents.getBuildings().stream()
            .filter(b -> b.ownerName.equals(me) && b.isCapitol)
            .sorted(Comparator.comparingLong(b -> b.originPos.asLong()))
            .toList();
    }

    private static void goHome() {
        List<BuildingPlacement> cities = myCities();
        if (cities.isEmpty())
            return;
        BuildingPlacement city = cities.get(nextCity % cities.size());
        nextCity = (nextCity + 1) % cities.size();
        OrthoviewClientEvents.centreCameraOnPos(city.centrePos);
    }

    private static boolean inside(double mx, double my) {
        return mx >= x() && mx <= x() + W && my >= y() && my <= y() + H;
    }

    @SubscribeEvent
    public static void onDrawScreen(ScreenEvent.Render.Post evt) {
        if (!OrthoviewClientEvents.isEnabled() || myCities().isEmpty())
            return;
        GuiGraphics g = evt.getGuiGraphics();
        boolean hover = inside(evt.getMouseX(), evt.getMouseY());
        g.pose().pushPose();
        g.pose().translate(0, 0, 400);
        g.blitNineSliced(BUTTONS, x(), y(), W, H, 20, 4, 200, 20, 0, hover ? 86 : 66);
        g.renderFakeItem(new ItemStack(Items.BELL), x() + 3, y() + 2);
        g.drawString(MC.font, "Hôtel de ville", x() + 22, y() + 6, hover ? 0xFFFFA0 : 0xFFFFFF);
        g.pose().popPose();
    }

    @SubscribeEvent
    public static void onMouseClick(ScreenEvent.MouseButtonPressed.Pre evt) {
        if (!OrthoviewClientEvents.isEnabled() || evt.getButton() != GLFW.GLFW_MOUSE_BUTTON_1 || myCities().isEmpty())
            return;
        if (inside(evt.getMouseX(), evt.getMouseY())) {
            goHome();
            evt.setCanceled(true);
        }
    }

    // Home key (Origine / ↖ on Mac keyboards: fn + ←)
    @SubscribeEvent
    public static void onKey(ScreenEvent.KeyPressed.Pre evt) {
        if (OrthoviewClientEvents.isEnabled() && evt.getKeyCode() == GLFW.GLFW_KEY_HOME) {
            goHome();
            evt.setCanceled(true);
        }
    }
}
