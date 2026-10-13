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
    MASONRY        ("Maçonnerie",     0,   200, 100, 300,     0,     0,   List.of(),       List.of("Watchtower", "Blacksmith", "Stone Rampart"),             List.of()),
    COMMERCE       ("Commerce",       150, 150, 0,   300,     0,     2,   List.of(),       List.of("Town Market"),                          List.of()),
    METALLURGY     ("Métallurgie",    0,   150, 250, 360,     1,     0,   List.of("MASONRY"), List.of("Iron Golem", "Portcullis"),                      List.of()),
    ARCHITECTURE   ("Architecture",   0,   300, 200, 360,     1,     1,   List.of("MASONRY"), List.of(),                                  List.of("Barracks:1", "Town Centre:1")),
    MYSTICISM      ("Mysticisme",     200, 100, 150, 360,     1,     2,   List.of("COMMERCE"), List.of("Witch Hut", "Shrine of Prosperity"), List.of()),
    FORTIFICATIONS ("Fortifications", 0,   300, 400, 600,     2,     0,   List.of("METALLURGY"), List.of("Castle"),                       List.of()),
    ENGINEERING    ("Ingénierie",     0,   400, 400, 480,     2,     1,   List.of("ARCHITECTURE", "METALLURGY"), List.of(),               List.of("Barracks:2", "Town Centre:2")),
    ARCANA         ("Arcanes",        300, 200, 300, 480,     2,     2,   List.of("MYSTICISM"), List.of("Arcane Tower"),                 List.of());

    public final String displayName;
    public final int food, wood, ore;
    public final int seconds;
    public final int column, row;
    private final List<String> prerequisiteNames;
    public final List<String> unlockedBuildings;
    public final List<String> unlockedLevels;

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
