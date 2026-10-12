package com.solegendary.reignofnether.building;

import javax.annotation.Nullable;
import java.util.Map;

// Construction time of each building, in seconds with ONE worker (more workers go faster: 3 workers take 60% of
// this time, 5 workers 43%, like in the original mod). Keyed by the building's name (Building.name).
// Buildings not listed keep the original mod's calculation (number of blocks x their buildTimeModifier),
// slowed down by BuildingPlacement.CONSTRUCTION_TIME_MULTIPLIER.
// Repairs are not affected.
public class BuildTimes {

    public static final Map<String, Integer> SECONDS_WITH_ONE_WORKER = Map.ofEntries(
        // Villagers
        Map.entry("Wheat Farm", 30),
        Map.entry("Stockpile", 40),
        Map.entry("Villager House", 40),
        Map.entry("Barracks", 60),
        Map.entry("Iron Golem", 60),
        Map.entry("Watchtower", 75),
        Map.entry("Town Market", 90),
        Map.entry("Blacksmith", 90),
        Map.entry("Library", 90),
        Map.entry("Witch Hut", 90),
        Map.entry("Shrine of Prosperity", 90),
        Map.entry("Arcane Tower", 120),
        Map.entry("Town Centre", 150),
        Map.entry("Castle", 180)
    );

    @Nullable
    public static Integer getSecondsWithOneWorker(Building building) {
        return SECONDS_WITH_ONE_WORKER.get(building.name);
    }
}
