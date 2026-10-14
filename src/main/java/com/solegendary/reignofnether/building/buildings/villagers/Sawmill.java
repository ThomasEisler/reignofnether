package com.solegendary.reignofnether.building.buildings.villagers;

import com.solegendary.reignofnether.api.ReignOfNetherRegistries;
import com.solegendary.reignofnether.building.BuildingClientEvents;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.Buildings;
import com.solegendary.reignofnether.building.buildings.placements.SawmillPlacement;
import com.solegendary.reignofnether.building.buildings.shared.AbstractFarm;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.resources.ResourceCost;
import com.solegendary.reignofnether.resources.ResourceCosts;
import com.solegendary.reignofnether.tutorial.TutorialClientEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Rotation;

import java.util.List;

import static com.solegendary.reignofnether.building.BuildingUtils.getAbsoluteBlockData;

// Scierie: villagers sent to it (right click, like a farm) work inside and it makes wood by itself (SawmillPlacement),
// so a town keeps getting wood once the forests are gone.
// Needs the Sylviculture technology.
// PLACEHOLDER structure: sawmill.nbt is a simple yard with a shed and log piles made by a script until a real one is made
public class Sawmill extends AbstractFarm {

    public final static String buildingName = "Sawmill";
    public final static String structureName = "sawmill";
    public final static ResourceCost cost = ResourceCost.Building(0, 250, 75, 0);

    public Sawmill() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.STONECUTTER;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/stripped_oak_log.png");
        this.explodeChance = 0;
        this.maxHealth = 120d;
        this.startingBlockTypes.add(Blocks.STRIPPED_OAK_LOG);
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new SawmillPlacement(this, level, pos, rotation, ownerName, getAbsoluteBlockData(getRelativeBlockData(level), level, pos, rotation));
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
            name,
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/stripped_oak_log.png"),
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.SAWMILL,
            TutorialClientEvents::isEnabled,
            () -> BuildingClientEvents.hasFinishedBuilding(Buildings.TOWN_CENTRE) ||
                ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.sawmill").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.sawmill.tooltip1").getVisualOrderText(),
                Component.translatable("buildings.reignofnether.sawmill.tooltip2").getVisualOrderText()
            ),
            this
        );
    }
}
