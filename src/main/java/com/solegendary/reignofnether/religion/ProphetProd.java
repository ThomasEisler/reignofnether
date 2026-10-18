package com.solegendary.reignofnether.religion;

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

// A prophet, at a temple: only once the owner has founded a religion, and only as many as it may have
// (ReligionServerEvents.allowedProphets); the first ones are free, later ones (more, or to replace the dead) cost faith.
public class ProphetProd extends ProductionItem implements IUnitProductionItem {

    public final static String itemName = "Prophet";
    public final static ResourceCost cost = ResourceCost.Unit(75, 0, 50, 30, 1);

    private final static ResourceLocation TEXTURE_LOCATION = ResourceLocation.fromNamespaceAndPath(ReignOfNether.MOD_ID, "textures/mobheads/prophet.png");

    public ProphetProd() {
        super(cost);
        this.onComplete = (Level level, ProductionPlacement placement) -> {
            if (!level.isClientSide()) {
                ReligionServerEvents.onProphetTrained(placement.ownerName);
                placement.produceUnit((ServerLevel) level, EntityRegistrar.PROPHET_UNIT.get(), placement.ownerName, true);
            }
        };
    }

    @Override
    @javax.annotation.Nullable
    public String getProduceErrorMsg(ProductionPlacement pp) {
        return ReligionServerEvents.prophetError(pp.ownerName);
    }

    public String getItemName() {
        return ProphetProd.itemName;
    }

    public UnitSpawnButton getPlaceButton() {
        return new UnitSpawnButton(
                itemName,
                TEXTURE_LOCATION,
                List.of(
                        Component.translatable("entity.reignofnether.prophet_unit").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        FormattedCharSequence.EMPTY,
                        Component.translatable("entity.reignofnether.prophet_unit.tooltip1").getVisualOrderText(),
                        Component.translatable("entity.reignofnether.prophet_unit.tooltip2").getVisualOrderText()
                )
        );
    }

    public StartProductionButton getStartButton(ProductionPlacement prodBuilding, Keybinding hotkey) {
        StartProductionButton button = new StartProductionButton(
                ProphetProd.itemName,
                TEXTURE_LOCATION,
                hotkey,
                () -> false,
                () -> true,
                List.of(
                        Component.translatable("entity.reignofnether.prophet_unit").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                        ResourceCosts.getFormattedCost(cost),
                        ResourceCosts.getFormattedPopAndTime(cost),
                        FormattedCharSequence.forward("+ " + ReligionServerEvents.PROPHET_FAITH + " de foi après les " + ReligionServerEvents.FREE_PROPHETS + " premiers", Style.EMPTY),
                        FormattedCharSequence.EMPTY,
                        Component.translatable("entity.reignofnether.prophet_unit.tooltip1").getVisualOrderText(),
                        Component.translatable("entity.reignofnether.prophet_unit.tooltip2").getVisualOrderText()
                ),
                this
        );
        return button;
    }

    public StopProductionButton getCancelButton(ProductionPlacement prodBuilding, boolean first) {
        return new StopProductionButton(
            ProphetProd.itemName,
            TEXTURE_LOCATION,
            prodBuilding,
            this,
            first
        );
    }
}
