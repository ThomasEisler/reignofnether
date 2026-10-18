package com.solegendary.reignofnether.trade;

import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.nation.CityClientEvents;
import com.solegendary.reignofnether.orthoview.OrthoviewClientEvents;
import com.solegendary.reignofnether.road.RoadClientEvents;
import com.solegendary.reignofnether.unit.UnitAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

// trade routes on the player's screen: the routes of each market (for its buttons), and what a route to the
// hovered city would bring while choosing it
public class TradeClientEvents {

    private static final Minecraft MC = Minecraft.getInstance();
    public static List<TradeClientboundPacket.RouteInfo> routes = new ArrayList<>();

    @Nullable
    public static TradeClientboundPacket.RouteInfo route(BlockPos market, int slot) {
        for (var r : routes)
            if (r.market().equals(market) && r.slot() == slot)
                return r;
        return null;
    }

    public static int slotsOf(BuildingPlacement market) {
        return !market.isBuilt ? 0 : market.getUpgradeLevel() >= 1 ? 2 : 1;
    }

    public static String cityName(BlockPos townCentre) {
        for (BuildingPlacement b : BuildingClientEvents.getBuildings())
            if (b.originPos.equals(townCentre)) {
                String name = CityClientEvents.getName(b);
                return (name != null ? name : "une ville") + " (" + b.ownerName + ")";
            }
        return "une ville disparue";
    }

    @SubscribeEvent
    public static void onDrawScreen(ScreenEvent.Render.Post evt) {
        UnitAction action = CursorClientEvents.getLeftClickAction();
        if (!OrthoviewClientEvents.isEnabled() || (action != UnitAction.OPEN_TRADE_ROUTE_1 && action != UnitAction.OPEN_TRADE_ROUTE_2))
            return;
        BuildingPlacement market = BuildingClientEvents.getSelectedBuildings().isEmpty() ? null : BuildingClientEvents.getSelectedBuildings().get(0);
        BuildingPlacement target = BuildingClientEvents.getPreselectedBuilding();
        String text;
        boolean ok = false;
        if (market == null)
            return;
        BuildingPlacement home = TradeServerEvents.homeCity(true, market);
        if (target == null || !target.getBuilding().isCapitol)
            text = "Route commerciale : cliquez sur l'Hôtel de ville d'une ville";
        else if (home == null)
            text = "Ce marché n'est sur les terres d'aucune de vos villes";
        else if (target == home)
            text = "C'est la ville de ce marché";
        else if (!RoadClientEvents.isRoyalLinked(home.originPos, target.originPos))
            text = "Il faut d'abord une route royale terminée entre ces deux villes";
        else {
            text = "Route vers " + cityName(target.originPos) + " : environ " + TradeServerEvents.emeraldsPerTrip(home, target) + " émeraudes par voyage";
            ok = true;
        }
        GuiGraphics g = evt.getGuiGraphics();
        int x = evt.getMouseX() + 12, y = evt.getMouseY() + 8, w = MC.font.width(text);
        g.fill(x - 3, y - 3, x + w + 3, y + MC.font.lineHeight + 1, 0xAA000000);
        g.drawString(MC.font, text, x, y, ok ? 0x80FF80 : 0xFF6060);
    }
}
