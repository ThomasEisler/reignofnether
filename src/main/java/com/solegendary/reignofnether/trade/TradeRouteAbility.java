package com.solegendary.reignofnether.trade;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.cursor.CursorClientEvents;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.unit.UnitAction;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

// a market's trade route slot: empty, click it then a city to open a route there; open, click it to close the route
public class TradeRouteAbility extends Ability {

    private final int slot;

    public TradeRouteAbility(int slot) {
        super(slot == 0 ? UnitAction.OPEN_TRADE_ROUTE_1 : UnitAction.OPEN_TRADE_ROUTE_2, 0, 0, 0, false);
        this.slot = slot;
    }

    private static String me() {
        return Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getName().getString();
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, BuildingPlacement placement) {
        return new AbilityButton("Trade route " + slot,
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/emerald.png"),
            hotkey,
            () -> CursorClientEvents.getLeftClickAction() == action,
            () -> !placement.ownerName.equals(me()) || slot >= TradeClientEvents.slotsOf(placement),
            () -> placement.isBuilt,
            () -> {
                if (TradeClientEvents.route(placement.originPos, slot) != null)
                    TradeServerboundPacket.close(placement.originPos, slot);
                else
                    CursorClientEvents.setLeftClickAction(action);
            },
            null,
            tooltip(placement),
            this,
            placement
        ) {
            @Override
            public void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
                this.tooltipLines = tooltip(placement);
                super.renderTooltip(guiGraphics, mouseX, mouseY);
            }
        };
    }

    private List<FormattedCharSequence> tooltip(BuildingPlacement placement) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        TradeClientboundPacket.RouteInfo r = TradeClientEvents.route(placement.originPos, slot);
        if (r == null) {
            lines.add(FormattedCharSequence.forward("Ouvrir une route commerciale", Style.EMPTY.withBold(true)));
            lines.add(FormattedCharSequence.forward("Puis cliquez sur l'Hôtel de ville d'une ville reliée à celle de ce marché par une route royale.", Style.EMPTY));
            lines.add(FormattedCharSequence.forward("Un marchand et ses lamas (" + TradeServerEvents.MERCHANT_FOOD + " nourriture, " + TradeServerEvents.MERCHANT_WOOD +
                " bois) y font les allers-retours et rapportent des émeraudes,", Style.EMPTY));
            lines.add(FormattedCharSequence.forward("deux fois plus avec une ville étrangère. 1 route par marché, 2 avec le Grand marché.", Style.EMPTY));
        } else {
            lines.add(FormattedCharSequence.forward("Route commerciale vers " + TradeClientEvents.cityName(r.target()), Style.EMPTY.withBold(true)));
            lines.add(FormattedCharSequence.forward(r.state().isEmpty() ? (r.hasMerchant() ? "Le marchand fait les allers-retours" : "Un marchand arrive") :
                "Arrêtée : " + r.state(), Style.EMPTY));
            lines.add(FormattedCharSequence.forward("Cliquer pour fermer la route (le marchand s'en va)", Style.EMPTY));
        }
        return lines;
    }

    @Override
    public void use(Level level, BuildingPlacement market, BlockPos targetBp) {
        if (!level.isClientSide())
            TradeServerEvents.openRoute(market.ownerName, market, slot, targetBp);
    }
}
