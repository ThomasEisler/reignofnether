package com.solegendary.reignofnether.hud;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.hud.buttons.HelperButtons;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

import java.util.Comparator;
import java.util.List;

// House button (top right, left of the chat button; drawn by HudClientEvents) and the Home key: bring the camera
// back to the player's Town Centre. Pressed again, it goes to the next city.
public class HomeButtonClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    private static int nextCity = 0;

    public static final Button BUTTON = new Button(
        "Home",
        HelperButtons.ICON_SIZE,
        ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/home.png"),
        (Keybinding) null,
        () -> false,
        () -> myCities().isEmpty(),
        () -> true,
        HomeButtonClientEvents::goHome,
        null,
        List.of(FormattedCharSequence.forward("Retour à l'Hôtel de ville (touche Origine)", Style.EMPTY))
    );

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

    // Home key (Origine; fn + ← on Mac keyboards)
    @SubscribeEvent
    public static void onKey(ScreenEvent.KeyPressed.Pre evt) {
        if (OrthoviewClientEvents.isEnabled() && evt.getKeyCode() == GLFW.GLFW_KEY_HOME) {
            goHome();
            evt.setCanceled(true);
        }
    }
}
