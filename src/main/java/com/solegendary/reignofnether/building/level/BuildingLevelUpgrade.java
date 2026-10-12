package com.solegendary.reignofnether.building.level;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ActiveProduction;
import com.solegendary.reignofnether.building.production.ProdDupeRule;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.StartProductionButton;
import com.solegendary.reignofnether.building.production.StopProductionButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

// Generic research that upgrades a levelled building to its next level.
// Uses the normal production queue, so cost checks, refunds on cancel and multiplayer sync all come
// from the existing production system; completion runs serverside only and syncs via CHANGE_LEVEL.
// Register one per (building, level) in ProductionItems and add it to the building's productions.
public class BuildingLevelUpgrade extends ProductionItem {

    private static final ResourceLocation ICON =
        ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/experience_bottle.png");
    private static final ResourceLocation FRAME =
        ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/hud/icon_frame_bronze.png");

    // supplier because Buildings and ProductionItems reference each other during static init
    private final Supplier<Building> building;
    private final int targetLevelIndex; // 1 = upgrade to level 2

    public BuildingLevelUpgrade(Supplier<Building> building, int targetLevelIndex) {
        super(ResourceCost.Research(0, 0, 0, 0), ProdDupeRule.DISALLOW_FOR_BUILDING);
        this.building = building;
        this.targetLevelIndex = targetLevelIndex;
        this.onComplete = (Level level, ProductionPlacement placement) -> {
            if (!level.isClientSide() && placement.getBuilding() == this.building.get())
                placement.applyLevel(this.targetLevelIndex);
        };
    }

    private BuildingLevel getTargetLevel() {
        return building.get().getLevels().get(targetLevelIndex);
    }

    @Override
    public ResourceCost getCost(boolean isClientSide, String ownerName) {
        ResourceCost cost = getTargetLevel().upgradeCost;
        return cost != null ? cost : defaultCost;
    }

    // validated serverside in ProductionPlacement.startProductionItem before resources are taken
    @Override
    public String getProduceErrorMsg(ProductionPlacement pp) {
        if (pp.getBuilding() != building.get() || !pp.isBuilt)
            return "hud.reignofnether.level_upgrade_unavailable";
        if (pp.getUpgradeLevel() != targetLevelIndex - 1 || isAnyLevelUpgradeQueued(pp))
            return "hud.reignofnether.level_upgrade_unavailable";
        return null;
    }

    private static boolean isAnyLevelUpgradeQueued(ProductionPlacement pp) {
        for (ActiveProduction active : pp.productionQueue)
            if (active.item instanceof BuildingLevelUpgrade)
                return true;
        return false;
    }

    public String getItemName() {
        return "Level " + (targetLevelIndex + 1);
    }

    public StartProductionButton getStartButton(ProductionPlacement prodBuilding, Keybinding hotkey) {
        BuildingLevel current = building.get().getLevels().get(targetLevelIndex - 1);
        BuildingLevel next = getTargetLevel();
        ResourceCost cost = getCost(true, prodBuilding.ownerName);

        List<FormattedCharSequence> tooltip = new ArrayList<>();
        tooltip.add(FormattedCharSequence.forward(
            I18n.get("research.reignofnether.level_upgrade", targetLevelIndex + 1), Style.EMPTY.withBold(true)));
        tooltip.add(ResourceCosts.getFormattedCost(cost));
        tooltip.add(ResourceCosts.getFormattedTime(cost));
        tooltip.add(FormattedCharSequence.forward("", Style.EMPTY));
        if (next.maxHealth > 0 && next.maxHealth != current.maxHealth)
            tooltip.add(FormattedCharSequence.forward(I18n.get("research.reignofnether.level_upgrade.health",
                (int) current.maxHealth, (int) next.maxHealth), Style.EMPTY));
        if (next.attackRange > 0 && next.attackRange != current.attackRange)
            tooltip.add(FormattedCharSequence.forward(I18n.get("research.reignofnether.level_upgrade.range",
                current.attackRange, next.attackRange), Style.EMPTY));
        if (next.productionSpeed != current.productionSpeed)
            tooltip.add(FormattedCharSequence.forward(I18n.get("research.reignofnether.level_upgrade.production",
                Math.round(current.productionSpeed * 100), Math.round(next.productionSpeed * 100)), Style.EMPTY));

        return new StartProductionButton(getItemName(), ICON, FRAME, hotkey,
            () -> prodBuilding.getUpgradeLevel() != targetLevelIndex - 1 || isAnyLevelUpgradeQueued(prodBuilding),
            () -> prodBuilding.isBuilt,
            tooltip,
            this
        );
    }

    public StopProductionButton getCancelButton(ProductionPlacement prodBuilding, boolean first) {
        return new StopProductionButton(getItemName(), ICON, FRAME, prodBuilding, this, first);
    }
}
