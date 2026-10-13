package com.solegendary.reignofnether.building.buildings.villagers;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.mapfeatures.MapFeaturesServerEvents;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.Resources;
import com.solegendary.reignofnether.resources.ResourcesServerEvents;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.tutorial.TutorialClientEvents;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;

import java.util.List;

// A fishing port: built on the shore next to a fishing spot (bubbles on the water), it brings in food on its own.
// PLACEHOLDER structure: port.nbt is a simple fisherman's hut until a real one is made
public class Port extends Building {

    public final static String buildingName = "Port";
    public final static String structureName = "port";
    public final static ResourceCost cost = ResourceCost.Building(0, 150, 25, 0);

    public static final int FISHING_SPOT_RANGE = 10; // from the port's centre to the fishing spot
    public static final int PORT_SPACING = 20; // one port per fishing spot
    public static final int FOOD_PER_CATCH = 5;
    public static final int TICKS_PER_CATCH = 20 * 5;

    public Port() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.BARREL;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/fishing_rod.png");
        this.buildTimeModifier = 0.8f;
        this.maxHealth = 175d;
        this.startingBlockTypes.add(Blocks.SPRUCE_PLANKS);
        this.startingBlockTypes.add(Blocks.SPRUCE_LOG);
    }

    @Override
    public void tick(Level level, BuildingPlacement placement) {
        super.tick(level, placement);
        if (level.isClientSide() || !placement.isBuilt || placement.ownerName.isBlank() || level.getGameTime() % TICKS_PER_CATCH != 0)
            return;
        if (MapFeaturesServerEvents.getNearestFishingSpot(false, placement.centrePos.getX() + 0.5, placement.centrePos.getZ() + 0.5,
                FISHING_SPOT_RANGE + 2) != null)
            ResourcesServerEvents.addSubtractResources(new Resources(placement.ownerName, FOOD_PER_CATCH, 0, 0));
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
            name,
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/fishing_rod.png"),
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.PORT,
            TutorialClientEvents::isEnabled,
            () -> BuildingClientEvents.hasFinishedBuilding(Buildings.TOWN_CENTRE) ||
                ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.port").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.port.tooltip1").getVisualOrderText(),
                Component.translatable("buildings.reignofnether.port.tooltip2", FOOD_PER_CATCH, TICKS_PER_CATCH / 20).getVisualOrderText()
            ),
            this
        );
    }
}
