package com.solegendary.reignofnether.building.buildings.villagers;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingServerEvents;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.keybinds.Keybinding;
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

// Moulin: the player's farms within RANGE blocks grow their crops GROWTH_BONUS faster (one mill is enough).
// PLACEHOLDER structure: mill.nbt is a stone tower with sails made by a script until a real one is made
public class Mill extends Building {

    public final static String buildingName = "Mill";
    public final static String structureName = "mill";
    public final static ResourceCost cost = ResourceCost.Building(0, 150, 50, 0);
    public static final int RANGE = 24;
    public static final float GROWTH_BONUS = 1.3f;

    public Mill() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.HAY_BLOCK;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/wheat.png");
        this.maxHealth = 150d;
        this.startingBlockTypes.add(Blocks.COBBLESTONE);
    }

    // crop growth speed of a farm: faster with a finished mill of the same owner close by
    public static float getGrowthMultiplier(BuildingPlacement farm) {
        for (BuildingPlacement b : BuildingServerEvents.getBuildings())
            if (b.getBuilding() instanceof Mill && b.isBuilt && b.ownerName.equals(farm.ownerName) &&
                b.centrePos.distSqr(farm.centrePos) <= (double) RANGE * RANGE)
                return GROWTH_BONUS;
        return 1f;
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
            name,
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/wheat.png"),
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.MILL,
            TutorialClientEvents::isEnabled,
            () -> BuildingClientEvents.hasFinishedBuilding(Buildings.TOWN_CENTRE) || ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.mill").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.mill.tooltip1").getVisualOrderText()
            ),
            this
        );
    }
}
