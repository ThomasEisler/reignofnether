package com.solegendary.reignofnether.religion;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.building.BuildingPlacement;
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

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;

// A temple's buttons: "found <religion>" (one per religion nobody has founded, until the owner has one), then
// "adopt <belief>" (one per free belief, once the religion has enough cities). Clicks go straight to the server
// in a ReligionServerboundPacket, as all these buttons would share one unit action.
public class ReligionAbility extends Ability {

    @Nullable private final Religion religion; // a "found" button
    @Nullable private final Belief belief;     // an "adopt" button

    public ReligionAbility(@Nullable Religion religion, @Nullable Belief belief) {
        super(UnitAction.NONE, 0, 0, 0, false);
        this.religion = religion;
        this.belief = belief;
    }

    private static String me() {
        return Minecraft.getInstance().player == null ? "" : Minecraft.getInstance().player.getName().getString();
    }

    private boolean isShown(BuildingPlacement placement) {
        String owner = placement.ownerName;
        if (!owner.equals(me()))
            return false;
        Religion mine = Religions.foundedBy(true, owner);
        if (religion != null)
            return mine == null && !ReligionClient.data.founders.containsKey(religion);
        return mine != null && !ReligionClient.data.secondBeliefs.containsKey(mine) && Religions.freeBeliefs(true).contains(belief);
    }

    private boolean isEnabled(BuildingPlacement placement) {
        if (!placement.isBuilt)
            return false;
        if (religion != null)
            return Religions.faith(true, placement.ownerName) >= ReligionServerEvents.FOUND_COST;
        Religion mine = Religions.foundedBy(true, placement.ownerName);
        return Religions.faith(true, placement.ownerName) >= ReligionServerEvents.BELIEF_COST &&
            Religions.followerCities(true, mine) >= ReligionServerEvents.BELIEF_CITIES;
    }

    private List<FormattedCharSequence> tooltip(BuildingPlacement placement) {
        List<FormattedCharSequence> lines = new ArrayList<>();
        if (religion != null) {
            lines.add(FormattedCharSequence.forward("Fonder " + religion.symbol + " " + religion.frName, Style.EMPTY.withBold(true).withColor(religion.colour)));
            lines.add(FormattedCharSequence.forward(ReligionServerEvents.FOUND_COST + " de foi (vous en avez " + Religions.faith(true, placement.ownerName) + ")", Style.EMPTY));
            lines.add(FormattedCharSequence.forward("Croyance : " + religion.belief.frName, Style.EMPTY.withBold(true)));
            lines.add(FormattedCharSequence.forward(religion.belief.description, Style.EMPTY));
            lines.add(FormattedCharSequence.forward("La ville de ce temple la suit aussitôt. Une seule civilisation par religion.", Style.EMPTY));
        } else {
            Religion mine = Religions.foundedBy(true, placement.ownerName);
            lines.add(FormattedCharSequence.forward("2e croyance : " + belief.frName, Style.EMPTY.withBold(true)));
            lines.add(FormattedCharSequence.forward(belief.description, Style.EMPTY));
            lines.add(FormattedCharSequence.forward(ReligionServerEvents.BELIEF_COST + " de foi (vous en avez " + Religions.faith(true, placement.ownerName) + ")", Style.EMPTY));
            lines.add(FormattedCharSequence.forward("Il faut " + ReligionServerEvents.BELIEF_CITIES + " villes qui suivent votre religion (" +
                Religions.followerCities(true, mine) + " maintenant)", Style.EMPTY));
        }
        return lines;
    }

    private String icon() {
        if (religion != null)
            return "textures/block/" + religion.iconBlock + ".png";
        return switch (belief) {
            case TITHE -> "textures/item/emerald.png";
            case BUILDER_MONKS -> "textures/item/iron_pickaxe.png";
            case WORK_ETHIC -> "textures/item/iron_axe.png";
            case HOUSES_OF_WISDOM -> "textures/item/book.png";
            case FAITHFULNESS -> "textures/item/totem_of_undying.png";
            case COMPASSION -> "textures/item/glistering_melon_slice.png";
            case BLESSED_HARVEST -> "textures/item/wheat.png";
            case SAGAS -> "textures/item/golden_sword.png";
            case CRUSADE -> "textures/item/iron_sword.png";
            case PILGRIMAGE -> "textures/item/compass_00.png";
            case FRATERNITY -> "textures/item/cake.png";
        };
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, BuildingPlacement placement) {
        return new AbilityButton(religion != null ? "Found " + religion.name() : "Belief " + belief.name(),
            ResourceLocation.fromNamespaceAndPath("minecraft", icon()),
            hotkey,
            () -> false,
            () -> !isShown(placement),
            () -> isEnabled(placement),
            () -> {
                if (religion != null)
                    ReligionServerboundPacket.found(religion, placement.originPos);
                else
                    ReligionServerboundPacket.adopt(belief, placement.originPos);
            },
            null,
            tooltip(placement),
            this,
            placement
        ) {
            @Override
            public void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
                this.tooltipLines = tooltip(placement); // faith and cities change
                super.renderTooltip(guiGraphics, mouseX, mouseY);
            }
        };
    }

    @Override
    public void use(Level level, BuildingPlacement buildingUsing, BlockPos targetBp) { }
}
