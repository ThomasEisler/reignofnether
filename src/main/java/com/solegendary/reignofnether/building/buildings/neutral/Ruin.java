package com.solegendary.reignofnether.building.buildings.neutral;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.mapfeatures.MapFeaturesServerEvents;
import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;

// Ruins with a treasure, found on the map (see mapfeatures/MapFeaturesServerEvents). They are plain blocks until a
// player's unit comes close, then become this building: destroying it gives a good amount of resources.
// PLACEHOLDER structures: ruin_1.nbt, ruin_2.nbt are simple shapes made by a script until real ones are made
public class Ruin extends Building {

    public Ruin(String structureName) {
        super(structureName, ResourceCost.Building(0, 0, 0, 0), false);
        this.name = "Ruins";
        this.portraitBlock = Blocks.MOSSY_STONE_BRICKS;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/mossy_stone_bricks.png");
        this.maxHealth = 150d;
        this.capturable = false;
        this.invulnerable = false;
        this.explodeChance = 0f;
    }

    @Override
    public void destroy(ServerLevel level, BuildingPlacement placement) {
        super.destroy(level, placement);
        MapFeaturesServerEvents.onRuinDestroyed(level, placement);
    }

    public Faction getFaction() {
        return Factions.NEUTRAL;
    }

    // never in a player's build menu
    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        return null;
    }
}
