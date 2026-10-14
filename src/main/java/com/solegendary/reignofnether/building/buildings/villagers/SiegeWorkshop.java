package com.solegendary.reignofnether.building.buildings.villagers;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.production.ProductionBuilding;
import com.solegendary.reignofnether.building.production.ProductionItems;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.keybinds.Keybindings;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.tutorial.TutorialClientEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

// Atelier de siège: builds catapults. Needs Engineering.
// PLACEHOLDER structure: siege_workshop.nbt is an open shed with timber and tools made by a script until a real one is made
public class SiegeWorkshop extends ProductionBuilding {

    public final static String buildingName = "Siege Workshop";
    public final static String structureName = "siege_workshop";
    public final static ResourceCost cost = ResourceCost.Building(0, 300, 150, 0);

    public SiegeWorkshop() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.SMITHING_TABLE;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/smithing_table_front.png");
        this.maxHealth = 220d;
        this.startingBlockTypes.add(Blocks.STRIPPED_OAK_LOG);
        this.productions.add(ProductionItems.CATAPULT, Keybindings.abilitySlot1);
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
            name,
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/smithing_table_front.png"),
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.SIEGE_WORKSHOP,
            TutorialClientEvents::isEnabled,
            () -> BuildingClientEvents.hasFinishedBuilding(Buildings.TOWN_CENTRE) || ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.siege_workshop").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.siege_workshop.tooltip1").getVisualOrderText()
            ),
            this
        );
    }
}
