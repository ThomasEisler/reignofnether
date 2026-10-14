package com.solegendary.reignofnether.unit.units.villagers;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.StartProductionButton;
import com.solegendary.reignofnether.building.production.StopProductionButton;
import com.solegendary.reignofnether.building.production.IUnitProductionItem;
import com.solegendary.reignofnether.hud.buttons.UnitSpawnButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import java.util.List;

public class PriestProd extends ProductionItem implements IUnitProductionItem {

    public final static String itemName = "Priest";
    public final static ResourceCost cost = ResourceCost.Unit(100, 0, 50, 25, 1);

    private final static ResourceLocation TEXTURE_LOCATION = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/priest.png");

    public PriestProd() {
        super(cost);
        this.onComplete = (Level level, ProductionPlacement placement) -> {
            if (!level.isClientSide())
                placement.produceUnit((ServerLevel) level, EntityRegistrar.PRIEST_UNIT.get(), placement.ownerName, true);
        };
    }

    public String getItemName() {
        return PriestProd.itemName;
    }

    public UnitSpawnButton getPlaceButton() {
        return new UnitSpawnButton(
                itemName,
                TEXTURE_LOCATION,
                List.of(
                        Component.translatable("entity.reignofnether.priest_unit").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        FormattedCharSequence.EMPTY,
                        Component.translatable("entity.reignofnether.priest_unit.tooltip1").getVisualOrderText(),
                        Component.translatable("entity.reignofnether.priest_unit.tooltip2").getVisualOrderText()
                )
        );
    }

    public StartProductionButton getStartButton(ProductionPlacement prodBuilding, Keybinding hotkey) {
        StartProductionButton button = new StartProductionButton(
                PriestProd.itemName,
                TEXTURE_LOCATION,
                hotkey,
                () -> false,
                () -> true,
                List.of(
                        Component.translatable("entity.reignofnether.priest_unit").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        ResourceCosts.getFormattedCost(cost),
                        ResourceCosts.getFormattedPopAndTime(cost),
                        FormattedCharSequence.EMPTY,
                        Component.translatable("entity.reignofnether.priest_unit.tooltip1").getVisualOrderText(),
                        Component.translatable("entity.reignofnether.priest_unit.tooltip2").getVisualOrderText()
                ),
                this
        );
        return button;
    }

    public StopProductionButton getCancelButton(ProductionPlacement prodBuilding, boolean first) {
        return new StopProductionButton(
            PriestProd.itemName,
            TEXTURE_LOCATION,
            prodBuilding,
            this,
            first
        );
    }
}
