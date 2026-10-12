package com.solegendary.reignofnether.building.buildings.neutral;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlaceButton;
import com.solegendary.reignofnether.building.level.BuildingLevel;
import com.solegendary.reignofnether.faction.Faction;
import com.solegendary.reignofnether.faction.Factions;
import com.solegendary.reignofnether.keybinds.Keybinding;
import com.solegendary.reignofnether.resources.ResourceCost;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;

// A barbarian camp, owned by the barbarians AI (see barbarian/BarbarianServerEvents). It can't be built by players.
// It grows through 3 levels over time; each level has its own structure.
// PLACEHOLDER structures: barbarian_camp(_lvl2/_lvl3).nbt are copies of other buildings until real ones are made
public class BarbarianCamp extends Building {

    public final static String buildingName = "Barbarian Camp";
    public final static String structureName = "barbarian_camp";

    public BarbarianCamp() {
        super(structureName, ResourceCost.Building(0, 0, 0, 0), false);
        this.name = buildingName;
        this.portraitBlock = Blocks.CAMPFIRE;
        this.icon = ResourceLocation.fromNamespaceAndPath("minecraft", "textures/block/campfire_log_lit.png");
        this.selfBuilding = true;
        this.capturable = false;
        this.invulnerable = false;
        this.explodeChance = 0.2f;

        setLevels(
            BuildingLevel.of(structureName).maxHealth(200d).build(),
            BuildingLevel.of("barbarian_camp_lvl2").maxHealth(350d).build(),
            BuildingLevel.of("barbarian_camp_lvl3").maxHealth(500d).build()
        );
    }

    public Faction getFaction() {
        return Factions.NEUTRAL;
    }

    // never in a player's build menu
    public BuildingPlaceButton getBuildButton(Keybinding hotkey) {
        return null;
    }
}
