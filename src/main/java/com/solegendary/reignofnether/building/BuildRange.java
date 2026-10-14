package com.solegendary.reignofnether.building;

import com.solegendary.reignofnether.building.buildings.monsters.SpruceStockpile;
import com.solegendary.reignofnether.building.buildings.shared.AbstractBridge;
import com.solegendary.reignofnether.building.buildings.villagers.OakStockpile;
import net.minecraft.world.level.Level;

import java.util.List;

// Players can only build near their own buildings (so not in the middle of an enemy base), except for stockpiles
// and bridges. A player who has no building yet can build anywhere (their first Town Centre).
// Stockpiles, built far away by the forests, don't let other buildings be put up around them.
public class BuildRange {

    public static final int RANGE = 24; // max gap in blocks between the new building and one of the player's buildings

    public static boolean isExempt(Building building) {
        // Town Centres found new cities: they follow the spacing between cities instead (nation/Cities)
        return building instanceof OakStockpile || building instanceof SpruceStockpile || building instanceof AbstractBridge ||
            building.isCapitol;
    }

    // horizontal gap between a point and a building's footprint (0 if inside)
    private static double distanceTo(BuildingPlacement placement, double x, double z) {
        double dx = Math.max(0, Math.max(placement.minCorner.getX() - x, x - placement.maxCorner.getX()));
        double dz = Math.max(0, Math.max(placement.minCorner.getZ() - z, z - placement.maxCorner.getZ()));
        return Math.sqrt(dx * dx + dz * dz);
    }

    // horizontal gap between two footprints given by their corners
    private static double distanceTo(BuildingPlacement placement, int minX, int minZ, int maxX, int maxZ) {
        int dx = Math.max(0, Math.max(placement.minCorner.getX() - maxX, minX - placement.maxCorner.getX()));
        int dz = Math.max(0, Math.max(placement.minCorner.getZ() - maxZ, minZ - placement.maxCorner.getZ()));
        return Math.sqrt(dx * dx + dz * dz);
    }

    private static List<BuildingPlacement> getOwnBuildings(Level level, String ownerName) {
        return BuildingUtils.getBuildingsList(level.isClientSide()).stream()
            .filter(b -> b.ownerName.equals(ownerName) && !(b.getBuilding() instanceof AbstractBridge)).toList();
    }

    public static boolean hasAnyBuilding(Level level, String ownerName) {
        return !getOwnBuildings(level, ownerName).isEmpty();
    }

    // buildings that other buildings may be put up next to (not stockpiles)
    private static List<BuildingPlacement> getAnchors(Level level, String ownerName) {
        return getOwnBuildings(level, ownerName).stream()
            .filter(b -> !(b.getBuilding() instanceof OakStockpile) && !(b.getBuilding() instanceof SpruceStockpile)).toList();
    }

    public static boolean isFootprintInRange(Level level, String ownerName, int minX, int minZ, int maxX, int maxZ) {
        if (getOwnBuildings(level, ownerName).isEmpty())
            return true;
        for (BuildingPlacement b : getAnchors(level, ownerName))
            if (distanceTo(b, minX, minZ, maxX, maxZ) <= RANGE)
                return true;
        return false;
    }

    // for the red zone preview: is this column close enough to one of the given buildings
    public static boolean isColumnInRange(List<BuildingPlacement> own, int x, int z) {
        for (BuildingPlacement b : own)
            if (distanceTo(b, x + 0.5, z + 0.5) <= RANGE)
                return true;
        return false;
    }

    public static List<BuildingPlacement> getOwnBuildingsForPreview(Level level, String ownerName) {
        return getAnchors(level, ownerName);
    }
}
