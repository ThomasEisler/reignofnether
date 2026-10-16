package com.solegendary.reignofnether.rampart;

import com.solegendary.reignofnether.ability.Ability;
import com.solegendary.reignofnether.barbarian.BarbarianServerEvents;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.hud.buttons.AbilityButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.rampart.RampartBuilding.Family;
import com.solegendary.reignofnether.unit.UnitAction;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

import static com.solegendary.reignofnether.unit.UnitClientEvents.sendUnitCommand;

// the buttons of a selected rampart segment: make it a tower, make it a gate, upgrade the whole wall (RampartUpgrades)
public class RampartAbility extends Ability {

    public RampartAbility(UnitAction action) {
        super(action, 0, 0, 0, false);
    }

    @Override
    public AbilityButton getButton(Keybinding hotkey, BuildingPlacement placement) {
        if (!(placement.getBuilding() instanceof RampartBuilding rb) || placement.ownerName.isBlank() ||
            placement.ownerName.equals(BarbarianServerEvents.OWNER_NAME))
            return null;
        int level = rb.kind.level;
        String icon = switch (action) {
            case RAMPART_TOWER -> level <= 1 ? "textures/block/spruce_log.png" : level == 2 ? "textures/block/chiseled_stone_bricks.png" : "textures/block/polished_deepslate.png";
            case RAMPART_GATE -> level <= 1 ? "textures/block/spruce_trapdoor.png" : "textures/block/iron_bars.png";
            default -> level <= 1 ? "textures/block/stone_bricks.png" : "textures/block/deepslate_bricks.png";
        };
        // towers and gates only from a plain wall segment; the whole wall can be upgraded from any of its segments
        if (action != UnitAction.RAMPART_UPGRADE && rb.kind.family != Family.WALL)
            return null;
        return new AbilityButton(action.name(),
            ResourceLocation.fromNamespaceAndPath("minecraft", icon),
            hotkey,
            () -> false,
            () -> false,
            () -> placement.isBuilt,
            () -> sendUnitCommand(action),
            null,
            tooltip(placement),
            this,
            placement
        ) {
            @Override
            public void renderTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY) {
                this.tooltipLines = tooltip(placement); // the wall changes: price worked out again when hovered
                super.renderTooltip(guiGraphics, mouseX, mouseY);
            }
        };
    }

    private List<FormattedCharSequence> tooltip(BuildingPlacement placement) {
        int level = ((RampartBuilding) placement.getBuilding()).kind.level;
        List<FormattedCharSequence> tooltip = new ArrayList<>();
        switch (action) {
            case RAMPART_TOWER -> {
                int[] c = RampartUpgrades.towerCost(placement);
                tooltip.add(line("Faire une tour (" + RampartUpgrades.levelName(level) + ")", true));
                tooltip.add(line(RampartUpgrades.cost(c[0], c[1]), false));
                tooltip.add(line("Tour de 3 x 3 au milieu de ce morceau ; 2 unités peuvent s'y installer et tirer d'en haut.", false));
            }
            case RAMPART_GATE -> {
                int[] c = RampartUpgrades.gateCost(placement);
                tooltip.add(line(level <= 1 ? "Faire une porte en bois" : level == 2 ? "Faire une herse" : "Faire une herse renforcée", true));
                tooltip.add(line(RampartUpgrades.cost(c[0], c[1]), false));
                String error = RampartUpgrades.gateError(placement.getLevel(), placement);
                tooltip.add(line(error != null ? error : "Ce morceau devient une porte qui s'ouvre pour vos unités.", false));
            }
            default -> {
                RampartUpgrades.Plan plan = RampartUpgrades.upgradePlan(placement.getLevel(), placement);
                tooltip.add(line("Améliorer tout le mur" + (plan.isValid() ? " en " + RampartUpgrades.levelName(plan.level()) : ""), true));
                if (plan.isValid()) {
                    tooltip.add(line(RampartUpgrades.cost(plan.wood(), plan.ore()), false));
                    tooltip.add(line(plan.segments().size() + " morceaux" + (plan.towers() > 0 ? ", dont " + plan.towers() + " tour(s)" : "") +
                        (plan.gates() > 0 ? (plan.towers() > 0 ? " et " : ", dont ") + plan.gates() + " porte(s)" : ""), false));
                    tooltip.add(line("Les ouvriers les plus proches reconstruisent chaque morceau.", false));
                } else
                    tooltip.add(line(plan.error(), false));
            }
        }
        return tooltip;
    }

    private static FormattedCharSequence line(String text, boolean bold) {
        return FormattedCharSequence.forward(text, Style.EMPTY.withBold(bold));
    }

    @Override
    public void use(Level level, BuildingPlacement placement, BlockPos targetBp) {
        if (!(level instanceof ServerLevel serverLevel) || !placement.isBuilt || !(placement.getBuilding() instanceof RampartBuilding))
            return;
        switch (action) {
            case RAMPART_TOWER -> RampartUpgrades.makeTower(serverLevel, placement);
            case RAMPART_GATE -> RampartUpgrades.makeGate(serverLevel, placement);
            case RAMPART_UPGRADE -> RampartUpgrades.upgrade(serverLevel, placement);
            default -> { }
        }
    }
}
