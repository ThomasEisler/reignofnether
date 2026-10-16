package com.solegendary.reignofnether.territory;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.rampart.RampartBuilding;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

// A civilisation's territory: the land around its buildings, wider around its cities (Town Centres). Where two
// territories meet, each spot goes to the building it is deepest inside. Worked out the same way on both sides:
// the client draws the borders (TerritoryClientEvents), the server uses it for diplomacy (units on someone's land).
public class Territory {

    public static final int CITY_RADIUS = 48;
    public static final int BUILDING_RADIUS = 28;
    public static final int CELL = 2; // borders follow a grid of 2 x 2 blocks

    // how far a building's land reaches (0: none, eg. walls, bridges, unowned)
    public static int radius(BuildingPlacement b, Predicate<String> isCiv) {
        if (b.ownerName.isBlank() || !isCiv.test(b.ownerName) || b.getBuilding() instanceof RampartBuilding ||
            b.getBuilding() instanceof AbstractBridge)
            return 0;
        return b.getBuilding().isCapitol ? CITY_RADIUS : BUILDING_RADIUS;
    }

    // whose land this spot is, or null
    @Nullable
    public static String ownerAt(List<BuildingPlacement> buildings, Predicate<String> isCiv, double x, double z) {
        String best = null;
        double bestDepth = 0;
        for (BuildingPlacement b : buildings) {
            int r = radius(b, isCiv);
            if (r == 0)
                continue;
            double dx = x - (b.centrePos.getX() + 0.5), dz = z - (b.centrePos.getZ() + 0.5);
            double depth = r - Math.sqrt(dx * dx + dz * dz);
            if (depth > bestDepth) {
                bestDepth = depth;
                best = b.ownerName;
            }
        }
        return best;
    }

    public static long cellKey(int cx, int cz) {
        return ((long) cx << 32) | (cz & 0xFFFFFFFFL);
    }

    // owner of every cell of land (cell = CELL x CELL blocks, keyed by cellKey of its grid position)
    public static Map<Long, String> grid(List<BuildingPlacement> buildings, Predicate<String> isCiv) {
        Map<Long, String> owners = new HashMap<>();
        Map<Long, Double> depths = new HashMap<>();
        for (BuildingPlacement b : buildings) {
            int r = radius(b, isCiv);
            if (r == 0)
                continue;
            double bx = b.centrePos.getX() + 0.5, bz = b.centrePos.getZ() + 0.5;
            int c0x = Math.floorDiv((int) bx - r, CELL), c1x = Math.floorDiv((int) bx + r, CELL);
            int c0z = Math.floorDiv((int) bz - r, CELL), c1z = Math.floorDiv((int) bz + r, CELL);
            for (int cx = c0x; cx <= c1x; cx++)
                for (int cz = c0z; cz <= c1z; cz++) {
                    double dx = cx * CELL + CELL / 2.0 - bx, dz = cz * CELL + CELL / 2.0 - bz;
                    double depth = r - Math.sqrt(dx * dx + dz * dz);
                    if (depth <= 0)
                        continue;
                    long k = cellKey(cx, cz);
                    if (depth > depths.getOrDefault(k, 0d)) {
                        depths.put(k, depth);
                        owners.put(k, b.ownerName);
                    }
                }
        }
        return owners;
    }
}
