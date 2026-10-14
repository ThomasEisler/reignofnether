package com.solegendary.reignofnether.nation;

import com.solegendary.reignofnether.building.Building;
import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;

// Cities: every Town Centre (capitol) is a city, named after a city of its owner's nation. Cities keep apart: a new
// one can't be too close to the player's (or an ally's) other cities, and must be even further from enemy cities.
public class Cities {

    public static final int OWN_SPACING = 100; // blocks between the centres of two cities of the same player (or allies)
    public static final int ENEMY_SPACING = 180; // blocks to the centre of an enemy city

    public static final Map<Nation, List<String>> NAMES = Map.of(
        Nation.FRANCE, List.of("Paris", "Lyon", "Marseille", "Bordeaux", "Toulouse", "Rouen", "Reims", "Orléans", "Tours", "Nantes", "Strasbourg", "Grenoble"),
        Nation.ENGLAND, List.of("Londres", "York", "Cantorbéry", "Winchester", "Oxford", "Bristol", "Norwich", "Douvres", "Lincoln", "Exeter", "Chester", "Durham"),
        Nation.SPAIN, List.of("Madrid", "Tolède", "Séville", "Grenade", "Barcelone", "Valence", "Burgos", "Salamanque", "Cordoue", "Saragosse", "Léon", "Cadix"),
        Nation.HRE, List.of("Aix-la-Chapelle", "Vienne", "Prague", "Francfort", "Nuremberg", "Cologne", "Mayence", "Augsbourg", "Ratisbonne", "Hambourg", "Lübeck", "Brême"),
        Nation.ROME, List.of("Rome", "Ostie", "Capoue", "Ravenne", "Milan", "Vérone", "Antium", "Brindisi", "Pompéi", "Tarente", "Naples", "Aquilée"),
        Nation.PORTUGAL, List.of("Lisbonne", "Porto", "Coimbra", "Braga", "Évora", "Faro", "Guimarães", "Leiria", "Setúbal", "Viseu", "Lagos", "Tomar"),
        Nation.RUSSIA, List.of("Moscou", "Novgorod", "Kiev", "Vladimir", "Souzdal", "Pskov", "Tver", "Smolensk", "Riazan", "Iaroslavl", "Kazan", "Saint-Pétersbourg"),
        Nation.SWEDEN, List.of("Stockholm", "Uppsala", "Göteborg", "Kalmar", "Visby", "Linköping", "Västerås", "Örebro", "Lund", "Malmö", "Norrköping", "Sigtuna")
    );

    // the n-th city (0 = first) of a player of this nation
    public static String getName(@Nullable Nation nation, int index) {
        List<String> names = nation != null ? NAMES.get(nation) : null;
        if (names == null)
            return "Ville " + (index + 1);
        return index < names.size() ? names.get(index) : names.get(index % names.size()) + " " + (index / names.size() + 1);
    }

    public static boolean isCity(Building building) {
        return building.isCapitol;
    }

    private static boolean isFriendly(Level level, String owner, String other) {
        if (owner.equals(other))
            return true;
        return level.isClientSide() ? com.solegendary.reignofnether.alliance.AlliancesClient.isAlliedOrOwned(owner, other)
            : com.solegendary.reignofnether.alliance.AlliancesServerEvents.isAlliedOrOwned(owner, other);
    }

    public static List<BuildingPlacement> getCities(Level level) {
        return BuildingUtils.getBuildingsList(level.isClientSide()).stream().filter(b -> isCity(b.getBuilding()) && !b.ownerName.isBlank()).toList();
    }

    // error message key, or null when a city of this player can stand there
    @Nullable
    public static String getPlacementError(Level level, String owner, double x, double z) {
        for (BuildingPlacement city : getCities(level)) {
            double dx = city.centrePos.getX() + 0.5 - x, dz = city.centrePos.getZ() + 0.5 - z;
            boolean friendly = isFriendly(level, owner, city.ownerName);
            int spacing = friendly ? OWN_SPACING : ENEMY_SPACING;
            if (dx * dx + dz * dz < (double) spacing * spacing)
                return friendly ? "building.reignofnether.city_too_close_own" : "building.reignofnether.city_too_close_enemy";
        }
        return null;
    }

    // for the red zone while placing a Town Centre
    public static boolean isColumnAllowed(Level level, List<BuildingPlacement> cities, String owner, int x, int z) {
        for (BuildingPlacement city : cities) {
            double dx = city.centrePos.getX() - x, dz = city.centrePos.getZ() - z;
            int spacing = isFriendly(level, owner, city.ownerName) ? OWN_SPACING : ENEMY_SPACING;
            if (dx * dx + dz * dz < (double) spacing * spacing)
                return false;
        }
        return true;
    }
}
