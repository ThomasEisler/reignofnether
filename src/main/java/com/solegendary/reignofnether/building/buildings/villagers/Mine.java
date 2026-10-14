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
import com.solegendary.reignofnether.resources.ResourceName;
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

// Mine: built next to a quarry (QUARRY_RANGE blocks), villagers sent to it work inside and it makes ore without end
// (works like the sawmill, see SawmillPlacement). Needs Metallurgy.
// PLACEHOLDER structure: mine.nbt is a yard with a mine entrance made by a script until a real one is made
public class Mine extends AbstractFarm {

    public final static String buildingName = "Mine";
    public final static String structureName = "mine";
    public final static ResourceCost cost = ResourceCost.Building(0, 250, 50, 0);
    public static final int QUARRY_RANGE = 24;

    public Mine() {
        super(structureName, cost, false);
        this.name = buildingName;
        this.portraitBlock = Blocks.IRON_ORE;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/iron_pickaxe.png");
        this.explodeChance = 0;
        this.maxHealth = 150d;
        this.startingBlockTypes.add(Blocks.GRAVEL);
    }

    @Override
    public BuildingPlacement createBuildingPlacement(Level level, BlockPos pos, Rotation rotation, String ownerName) {
        return new SawmillPlacement(this, level, pos, rotation, ownerName, getAbsoluteBlockData(getRelativeBlockData(level), level, pos, rotation), ResourceName.ORE);
    }

    public static boolean isNearQuarry(Level level, BlockPos pos) {
        List<BlockPos> quarries = level.isClientSide()
            ? com.solegendary.reignofnether.mapfeatures.MapIconsClientEvents.getQuarries()
            : com.solegendary.reignofnether.mapfeatures.MapFeaturesServerEvents.getQuarryCentres();
        for (BlockPos q : quarries)
            if (q.distToCenterSqr(pos.getX(), q.getY(), pos.getZ()) <= (double) QUARRY_RANGE * QUARRY_RANGE)
                return true;
        return false;
    }

    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        ResourceLocation key = ReignOfNetherRegistries.BUILDING.getKey(this);
        String name = key != null ? Component.translatable("buildings." + key.getNamespace() + "." + key.getPath()).getString() : buildingName;
        return new BuildingPlaceButton(
            name,
            ResourceLocation.fromNamespaceAndPath("minecraft", "textures/item/iron_pickaxe.png"),
            hotkey,
            () -> BuildingClientEvents.getBuildingToPlace() == Buildings.MINE,
            TutorialClientEvents::isEnabled,
            () -> BuildingClientEvents.hasFinishedBuilding(Buildings.TOWN_CENTRE) || ResearchClient.hasCheat("modifythephasevariance"),
            List.of(
                Component.translatable("buildings.reignofnether.mine").withStyle(Style.EMPTY.withBold(true)).getVisualOrderText(),
                ResourceCosts.getFormattedCost(cost),
                FormattedCharSequence.forward("", Style.EMPTY),
                Component.translatable("buildings.reignofnether.mine.tooltip1").getVisualOrderText(),
                Component.translatable("buildings.reignofnether.mine.tooltip2").getVisualOrderText()
            ),
            this
        );
    }
}
