package com.solegendary.reignofnether.unit.units.villagers;

import com.solegendary.reignofnether.ReignOfNether;
import com.solegendary.reignofnether.building.buildings.placements.ProductionPlacement;
import com.solegendary.reignofnether.building.production.ProductionItem;
import com.solegendary.reignofnether.building.production.StopProductionButton;
import com.solegendary.reignofnether.building.production.IUnitProductionItem;
import com.solegendary.reignofnether.hud.buttons.UnitSpawnButton;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.registrars.EntityRegistrar;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.building.production.StartProductionButton;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;

// A Cavalier: a Vindicator on a war horse, the strongest Barracks unit (the horse is the unit, the rider sits on it)
public class CavalierProd extends ProductionItem implements IUnitProductionItem {

    public final static String itemName = "Cavalier";
    public final static ResourceCost cost = ResourceCosts.CAVALIER;

    public CavalierProd() {
        super(cost);
        this.onComplete = (Level level, ProductionPlacement placement) -> {
            if (!level.isClientSide()) {
                net.minecraft.world.entity.Entity horse = placement.produceUnit((ServerLevel) level, EntityRegistrar.HORSE_UNIT.get(), placement.ownerName, true);
                if (horse instanceof HorseUnit horseUnit) {
                    horseUnit.equipWarHorse();
                    net.minecraft.world.entity.Entity rider = com.solegendary.reignofnether.unit.UnitServerEvents.spawnMob(
                        EntityRegistrar.VINDICATOR_UNIT.get(), (ServerLevel) level, horse.blockPosition(), placement.ownerName);
                    if (rider != null)
                        rider.startRiding(horse, true);
                }
            }
        };
    }

    public String getItemName() {
        return CavalierProd.itemName;
    }

    public UnitSpawnButton getPlaceButton() {
        return new UnitSpawnButton(
                itemName,
                ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/cavalier.png"),
                List.of(
                        Component.translatable("entity.reignofnether.cavalier_unit").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        FormattedCharSequence.EMPTY,
                        Component.translatable("entity.reignofnether.cavalier_unit.tooltip1").getVisualOrderText()
                )
        );
    }

    public StartProductionButton getStartButton(ProductionPlacement prodBuilding, Keybinding hotkey) {
        List<FormattedCharSequence> tooltipLines = new ArrayList<>(List.of(
                Component.translatable("entity.reignofnether.cavalier_unit").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                ResourceCosts.getFormattedPopAndTime(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("entity.reignofnether.cavalier_unit.tooltip1").getVisualOrderText()
        ));
        return new StartProductionButton(
            CavalierProd.itemName,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/cavalier.png"),
            hotkey,
            () -> false,
            () -> true,
            tooltipLines,
            this
        );
    }

    public StopProductionButton getCancelButton(ProductionPlacement prodBuilding, boolean first) {
        return new StopProductionButton(
            CavalierProd.itemName,
            ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/cavalier.png"),
            prodBuilding,
            this,
            first
        );
    }
}
