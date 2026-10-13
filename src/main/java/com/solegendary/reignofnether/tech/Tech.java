package com.solegendary.reignofnether.tech;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.research.ResearchClient;
import com.solegendary.reignofnether.research.ResearchServerEvents;

import javax.annotation.Nullable;
import java.util.List;

// The technology tree (villagers). Technologies are researched one at a time in a Library and unlock buildings or
// building levels. Costs, durations, unlocks and positions in the tree screen are all set here.
public enum Tech {
    //              name              food wood ore  seconds  column row  prerequisites   unlocked buildings (Building.name)              unlocked levels ("Building name:level index")
    MASONRY        ("Maçonnerie",     0,   200, 100, 300,     0,     0,   List.of(),       List.of("Watchtower", "Blacksmith", "Stone Rampart", "Rampart Tower"),             List.of()),
    COMMERCE       ("Commerce",       150, 150, 0,   300,     0,     2,   List.of(),       List.of("Town Market"),                          List.of()),
    METALLURGY     ("Métallurgie",    0,   150, 250, 360,     1,     0,   List.of("MASONRY"), List.of("Iron Golem", "Portcullis"),                      List.of(),
                    List.of("golem_smithing"), List.of("superior_blacksmith")),
    ARCHITECTURE   ("Architecture",   0,   300, 200, 360,     1,     1,   List.of("MASONRY"), List.of(),                                  List.of("Barracks:1", "Town Centre:1")),
    MYSTICISM      ("Mysticisme",     200, 100, 150, 360,     1,     2,   List.of("COMMERCE"), List.of("Witch Hut", "Shrine of Prosperity"), List.of(),
                    List.of(), List.of("grand_library")),
    FORTIFICATIONS ("Fortifications", 0,   300, 400, 600,     2,     0,   List.of("METALLURGY"), List.of("Castle"),                       List.of("Watchtower:2")),
    ENGINEERING    ("Ingénierie",     0,   400, 400, 480,     2,     1,   List.of("ARCHITECTURE", "METALLURGY"), List.of(),               List.of("Barracks:2", "Town Centre:2")),
    ARCANA         ("Arcanes",        300, 200, 300, 480,     2,     2,   List.of("MYSTICISM"), List.of("Arcane Tower"),                 List.of()),

    // the original game's researches (were bought in buildings), now in the tree
    //              name                     food wood ore  seconds col row  prerequisites           researches given (production item ids)     building upgrades unlocked (production item ids)
    COMMAND        ("Commandement",          200, 150, 150, 240,    3,  0,   List.of("FORTIFICATIONS"), List.of(), List.of(), List.of(),                                     List.of("castle_flag")),
    SIEGE          ("Artillerie de siège",   400, 0,   350, 300,    4,  0,   List.of("COMMAND"),     List.of(), List.of(), List.of("ravager_cavalry"),                       List.of()),
    ARCHERY        ("Tir à l'arc",           250, 500, 0,   300,    0,  1,   List.of(),              List.of(), List.of(), List.of("militia_bows"),                          List.of()),
    VEXES          ("Invocations vexantes",  500, 0,   300, 240,    3,  2,   List.of("ARCANA"),      List.of(), List.of(), List.of("evoker_vexes"),                          List.of()),
    WINDS          ("Porté par le vent",     300, 150, 150, 280,    4,  2,   List.of("VEXES"),       List.of(), List.of(), List.of("upgraded_windcallers"),                  List.of()),
    TRADE          ("Grand commerce",        100, 150, 100, 300,    1,  3,   List.of("COMMERCE"),    List.of(), List.of(), List.of(),                                        List.of("research_market_upgrade_villager")),
    ALCHEMY        ("Alchimie",              0,   400, 400, 300,    2,  3,   List.of("MYSTICISM"),   List.of(), List.of(), List.of("water_potions", "lingering_potions"),    List.of()),
    HEALING        ("Potions de soin",       0,   350, 350, 300,    3,  3,   List.of("ALCHEMY"),     List.of(), List.of(), List.of("healing_potions"),                       List.of()),
    CARRY_BAGS     ("Sacs de récolte",       200, 200, 0,   180,    0,  4,   List.of(),              List.of(), List.of(), List.of("resource_capacity"),                     List.of()),
    BACKPACKS      ("Sacs à dos",            0,   100, 100, 180,    1,  4,   List.of("CARRY_BAGS"),  List.of(), List.of(), List.of("item_backpacks"),                        List.of());

    public final String displayName;
    public final int food, wood, ore;
    public final int seconds;
    public final int column, row;
    private final List<String> prerequisiteNames;
    public final List<String> unlockedBuildings;
    public final List<String> unlockedLevels;
    public final List<String> grantedResearch;   // original researches given when done (their effects then apply)
    public final List<String> unlockedUpgrades;  // building upgrades (original researches done in one building) unlocked

    Tech(String displayName, int food, int wood, int ore, int seconds, int column, int row,
         List<String> prerequisiteNames, List<String> unlockedBuildings, List<String> unlockedLevels) {
        this.displayName = displayName;
        this.food = food;
        this.wood = wood;
        this.ore = ore;
        this.seconds = seconds;
        this.column = column;
        this.row = row;
        this.prerequisiteNames = prerequisiteNames;
        this.unlockedBuildings = unlockedBuildings;
        this.unlockedLevels = unlockedLevels;
        this.grantedResearch = List.of();
        this.unlockedUpgrades = List.of();
    }

    Tech(String displayName, int food, int wood, int ore, int seconds, int column, int row,
         List<String> prerequisiteNames, List<String> unlockedBuildings, List<String> unlockedLevels,
         List<String> grantedResearch, List<String> unlockedUpgrades) {
        this.displayName = displayName;
        this.food = food;
        this.wood = wood;
        this.ore = ore;
        this.seconds = seconds;
        this.column = column;
        this.row = row;
        this.prerequisiteNames = prerequisiteNames;
        this.unlockedBuildings = unlockedBuildings;
        this.unlockedLevels = unlockedLevels;
        this.grantedResearch = grantedResearch;
        this.unlockedUpgrades = unlockedUpgrades;
    }

    @Nullable
    public static com.solegendary.reignofnether.building.production.ProductionItem getProductionItem(String id) {
        return com.solegendary.reignofnether.api.ReignOfNetherRegistries.PRODUCTION_ITEM.get(
            net.minecraft.resources.ResourceLocation.fromNamespaceAndPath(com.solegendary.reignofnether.ReignOfNether.MOD_ID, id));
    }

    // the technology needed for a building upgrade (production item), or null if it isn't locked
    @Nullable
    public static Tech getRequiredForProduction(com.solegendary.reignofnether.building.production.ProductionItem item) {
        for (Tech tech : values())
            for (String id : tech.unlockedUpgrades)
                if (getProductionItem(id) == item)
                    return tech;
        return null;
    }

    public List<Tech> getPrerequisites() {
        return prerequisiteNames.stream().map(Tech::valueOf).toList();
    }

    // the technology needed to build a building, or null if it's always available
    @Nullable
    public static Tech getRequiredFor(Building building) {
        for (Tech tech : values())
            if (tech.unlockedBuildings.contains(building.name))
                return tech;
        return null;
    }

    // the technology needed to upgrade a building to a level (index, 1 = level 2), or null
    @Nullable
    public static Tech getRequiredForLevel(Building building, int levelIndex) {
        String key = building.name + ":" + levelIndex;
        for (Tech tech : values())
            if (tech.unlockedLevels.contains(key))
                return tech;
        return null;
    }

    public static boolean hasTech(boolean isClientSide, String ownerName, @Nullable Tech tech) {
        if (tech == null)
            return true;
        if (isClientSide)
            return ResearchClient.hasCheat("modifythephasevariance") || TechClientEvents.hasTech(tech);
        return ResearchServerEvents.playerHasCheat(ownerName, "modifythephasevariance") || TechServerEvents.hasTech(ownerName, tech);
    }

    public static boolean canBuild(boolean isClientSide, String ownerName, Building building) {
        return hasTech(isClientSide, ownerName, getRequiredFor(building));
    }
}
