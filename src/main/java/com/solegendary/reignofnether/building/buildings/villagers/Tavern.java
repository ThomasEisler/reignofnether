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

// Taverne: hires mercenaries (swordsmen and crossbowmen), dearer than the barracks' soldiers but ready almost at once.
// Needs Commerce.
// PLACEHOLDER structure: tavern.nbt is a simple spruce house made by a script until a real one is made
public class Tavern extends ProductionBuilding {

    public final static String buildingName = "Tavern";
    public final static String structureName = "tavern";
    public final static ResourceCost cost = ResourceCost.Building(0, 200, 100, 0);

    public Tavern() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.BARREL;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/honey_bottle.png");
        this.maxHealth = 200d;
        this.startingBlockTypes.add(Blocks.COBBLESTONE);
        this.productions.add(ProductionItems.MERCENARY, Keybindings.abilitySlot1);
        this.productions.add(ProductionItems.MERCENARY_ARCHER, Keybindings.abilitySlot2);
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
            name,
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/honey_bottle.png"),
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.TAVERN,
            TutorialClientEvents::isEnabled,
            () -> BuildingClientEvents.hasFinishedBuilding(Buildings.TOWN_CENTRE) || ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.tavern").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.tavern.tooltip1").getVisualOrderText()
            ),
            this
        );
    }
}
