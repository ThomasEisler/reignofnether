package com.solegendary.reignofnether.building.buildings.neutral;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Block;

// A secondary building of a barbarian camp (tent, tower...), placed around the camp's main building by
// barbarian/BarbarianServerEvents. It can't be built by players.
// PLACEHOLDER structures: barbarian_tent.nbt and barbarian_tower.nbt are copies of other buildings until real ones are made
public class BarbarianOutbuilding extends Building {

    public BarbarianOutbuilding(String buildingName, String structureName, double maxHealth, Block portraitBlock, String iconTexture) {
        super(structureName, ResourceCost.Building(0, 0, 0, 0), false);
        this.name = buildingName;
        this.portraitBlock = portraitBlock;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", iconTexture);
        this.maxHealth = maxHealth;
        this.selfBuilding = true;
        this.capturable = false;
        this.invulnerable = false;
        this.explodeChance = 0.2f;
    }

    public Faction getFaction() {
        return Factions.NEUTRAL;
    }

    // never in a player's build menu
    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        return null;
    }
}
