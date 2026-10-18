package com.solegendary.reignofnether.religion;

import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.hud.buttons.Button;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.territory.Territory;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.LivingEntity;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

// religion on the player's screen: the "expel" button shown when a foreign prophet on one's land is selected,
// the followers of a city when its Town Centre is selected, and the religion map (a button on the full-screen map)
public class ReligionClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    public static boolean religionMap = false;
    private static int mapButtonX0, mapButtonY0, mapButtonX1, mapButtonY1;

    // ---- followers of the selected city ----------------------------------------------------------------------------

    @net.minecraftforge.eventbus.api.SubscribeEvent
    public static void onDrawScreen(net.minecraftforge.client.event.ScreenEvent.Render.Post evt) {
        if (!com.solegendary.reignofnether.orthoview.OrthoviewClientEvents.isEnabled() ||
            !(evt.getScreen() instanceof com.solegendary.reignofnether.guiscreen.TopdownGui))
            return;
        net.minecraft.client.gui.GuiGraphics g = evt.getGuiGraphics();
        int sw = MC.getWindow().getGuiScaledWidth(), sh = MC.getWindow().getGuiScaledHeight();
        // religion map button, top left of the full-screen map
        if (com.solegendary.reignofnether.minimap.MinimapClientEvents.isLargeMap() && Religions.anyFounded(true)) {
            String label = religionMap ? "☸ Religions : oui" : "☸ Religions : non";
            mapButtonX0 = 8; mapButtonY0 = 8; mapButtonX1 = 8 + MC.font.width(label) + 8; mapButtonY1 = 8 + 14;
            g.fill(mapButtonX0, mapButtonY0, mapButtonX1, mapButtonY1, religionMap ? 0xC0603080 : 0xC0202020);
            g.drawString(MC.font, label, mapButtonX0 + 4, mapButtonY0 + 3, 0xFFFFFF);
        } else
            mapButtonX1 = 0;
        // followers of the selected city
        var b = com.solegendary.reignofnether.hud.HudClientEvents.hudSelectedPlacement;
        if (b == null || !b.getBuilding().isCapitol || com.solegendary.reignofnether.minimap.MinimapClientEvents.isLargeMap())
            return;
        java.util.Map<Religion, Float> gauges = ReligionClient.data.cities.get(b.originPos.asLong());
        if (gauges == null || gauges.isEmpty())
            return;
        List<java.util.Map.Entry<Religion, Float>> list = new ArrayList<>(gauges.entrySet());
        list.sort((a, c) -> Float.compare(c.getValue(), a.getValue()));
        float none = 100f;
        for (var e : list) none -= e.getValue();
        int lines = list.size() + 1 + (none >= 1 ? 1 : 0);
        int w = 150, h = lines * 11 + 6, x = sw / 2 - w / 2, y = sh - h - 50;
        g.fill(x, y, x + w, y + h, 0xB0000000);
        g.drawString(MC.font, "Religions de la ville", x + 5, y + 4, 0xFFE0E0E0);
        int ly = y + 15;
        for (var e : list) {
            Religion r = e.getKey();
            g.drawString(MC.font, r.symbol + " " + r.frName, x + 5, ly, 0xFF000000 | r.colour);
            String pct = Math.round(e.getValue()) + " %";
            g.drawString(MC.font, pct, x + w - 5 - MC.font.width(pct), ly, 0xFFFFFFFF);
            int barW = (int) ((w - 10) * e.getValue() / 100f);
            g.fill(x + 5, ly + 9, x + 5 + barW, ly + 10, 0xFF000000 | r.colour);
            ly += 11;
        }
        if (none >= 1) {
            g.drawString(MC.font, "Sans religion", x + 5, ly, 0xFFA0A0A0);
            String pct = Math.round(none) + " %";
            g.drawString(MC.font, pct, x + w - 5 - MC.font.width(pct), ly, 0xFFA0A0A0);
        }
    }

    @net.minecraftforge.eventbus.api.SubscribeEvent(priority = net.minecraftforge.eventbus.api.EventPriority.HIGHEST)
    public static void onMouseClick(net.minecraftforge.client.event.ScreenEvent.MouseButtonPressed.Pre evt) {
        if (mapButtonX1 > 0 && evt.getButton() == 0 && evt.getMouseX() >= mapButtonX0 && evt.getMouseX() <= mapButtonX1 &&
            evt.getMouseY() >= mapButtonY0 && evt.getMouseY() <= mapButtonY1) {
            religionMap = !religionMap;
            evt.setCanceled(true);
        }
    }

    @Nullable
    public static Button getExpelButton(LivingEntity selected) {
        if (!(selected instanceof ProphetUnit p) || MC.player == null)
            return null;
        String me = MC.player.getName().getString();
        if (p.getOwnerName().equals(me) || !PlayerClientEvents.isRTSPlayer(me))
            return null;
        String land = Territory.ownerAt(new ArrayList<>(BuildingClientEvents.getBuildings()), PlayerClientEvents::isRTSPlayer, p.getX(), p.getZ());
        if (!me.equals(land))
            return null;
        return new Button(
            "Expel prophet",
            14,
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/barrier.png"),
            (Keybinding) null,
            () -> false,
            () -> false,
            () -> Religions.faith(true, me) >= ReligionServerEvents.EXPEL_FAITH,
            () -> ReligionServerboundPacket.expel(p.getId()),
            null,
            List.of(
                FormattedCharSequence.forward("Expulser ce prophète", Style.EMPTY.withBold(true)),
                FormattedCharSequence.forward(ReligionServerEvents.EXPEL_FAITH + " de foi (vous en avez " + Religions.faith(true, me) + ")", Style.EMPTY),
                FormattedCharSequence.forward("Il rentre chez lui et ne revient pas sur vos terres avant 5 min.", Style.EMPTY),
                FormattedCharSequence.forward("Une expulsion toutes les 3 min ; son propriétaire ne va pas aimer.", Style.EMPTY))
        );
    }
}
