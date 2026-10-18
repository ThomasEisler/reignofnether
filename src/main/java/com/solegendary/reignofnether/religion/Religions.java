package com.solegendary.reignofnether.religion;

import com.solegendary.reignofnether.building.BuildingPlacement;
import com.solegendary.reignofnether.building.BuildingUtils;
import com.solegendary.reignofnether.player.PlayerClientEvents;
import com.solegendary.reignofnether.player.PlayerServerEvents;
import com.solegendary.reignofnether.territory.Territory;

import javax.annotation.Nullable;
import java.util.*;
import java.util.function.Predicate;

// Questions about religions that both sides ask (the client to show things, the server to apply beliefs):
// which city a spot belongs to, which religion a city follows, which beliefs apply where.
// A spot belongs to the nearest city of the civilisation whose land it is (Territory); a city follows the religion
// with the most followers there.
public class Religions {

    public static final float MIN_FOLLOWERS = 1f; // below this a religion doesn't count in a city

    public static ReligionSaveData data(boolean clientSide) {
        return clientSide ? ReligionClient.data : ReligionServerEvents.data();
    }

    public static Predicate<String> isCiv(boolean clientSide) {
        return clientSide ? PlayerClientEvents::isRTSPlayer : PlayerServerEvents::isRTSPlayer;
    }

    public static boolean anyFounded(boolean clientSide) {
        return !data(clientSide).founders.isEmpty();
    }

    public static List<BuildingPlacement> cities(boolean clientSide) {
        List<BuildingPlacement> list = new ArrayList<>();
        for (BuildingPlacement b : BuildingUtils.getBuildingsList(clientSide))
            if (b.getBuilding().isCapitol && !b.ownerName.isBlank())
                list.add(b);
        return list;
    }

    public static List<BuildingPlacement> citiesOf(boolean clientSide, String owner) {
        return cities(clientSide).stream().filter(c -> c.ownerName.equals(owner)).toList();
    }

    // ---- which city a spot belongs to (cached for a couple of seconds: asked every tick by workers and farms) ----

    private static final Map<Long, Optional<BuildingPlacement>> clientCache = new HashMap<>(), serverCache = new HashMap<>();
    private static long clientCacheTime = 0, serverCacheTime = 0;

    @Nullable
    public static BuildingPlacement cityAt(boolean clientSide, double x, double z) {
        Map<Long, Optional<BuildingPlacement>> cache = clientSide ? clientCache : serverCache;
        long now = System.currentTimeMillis();
        if (now - (clientSide ? clientCacheTime : serverCacheTime) > 2000) {
            cache.clear();
            if (clientSide) clientCacheTime = now; else serverCacheTime = now;
        }
        long key = Territory.cellKey(Math.floorDiv((int) Math.floor(x), Territory.CELL), Math.floorDiv((int) Math.floor(z), Territory.CELL));
        return cache.computeIfAbsent(key, k -> Optional.ofNullable(findCityAt(clientSide, x, z))).orElse(null);
    }

    @Nullable
    private static BuildingPlacement findCityAt(boolean clientSide, double x, double z) {
        List<BuildingPlacement> buildings = new ArrayList<>(BuildingUtils.getBuildingsList(clientSide));
        String owner = Territory.ownerAt(buildings, isCiv(clientSide), x, z);
        if (owner == null)
            return null;
        BuildingPlacement best = null;
        double bestDist = Double.MAX_VALUE;
        for (BuildingPlacement c : buildings) {
            if (!c.getBuilding().isCapitol || !c.ownerName.equals(owner))
                continue;
            double dx = c.centrePos.getX() - x, dz = c.centrePos.getZ() - z, d = dx * dx + dz * dz;
            if (d < bestDist) {
                bestDist = d;
                best = c;
            }
        }
        return best;
    }

    // ---- religions of cities ----------------------------------------------------------------------------------------

    @Nullable
    public static Religion religionOfCity(boolean clientSide, BuildingPlacement city) {
        Map<Religion, Float> gauges = data(clientSide).cities.get(city.originPos.asLong());
        if (gauges == null)
            return null;
        Religion best = null;
        float bestValue = MIN_FOLLOWERS;
        for (Map.Entry<Religion, Float> e : gauges.entrySet())
            if (e.getValue() >= bestValue && data(clientSide).founders.containsKey(e.getKey())) {
                bestValue = e.getValue();
                best = e.getKey();
            }
        return best;
    }

    @Nullable
    public static Religion religionAt(boolean clientSide, double x, double z) {
        if (!anyFounded(clientSide))
            return null;
        BuildingPlacement city = cityAt(clientSide, x, z);
        return city == null ? null : religionOfCity(clientSide, city);
    }

    // the religion a civilisation founded, or null
    @Nullable
    public static Religion foundedBy(boolean clientSide, String civ) {
        for (Map.Entry<Religion, String> e : data(clientSide).founders.entrySet())
            if (e.getValue().equals(civ))
                return e.getKey();
        return null;
    }

    @Nullable
    public static String founder(boolean clientSide, Religion religion) {
        return data(clientSide).founders.get(religion);
    }

    // the religion most of a civilisation's cities follow, or null
    @Nullable
    public static Religion mainReligion(boolean clientSide, String civ) {
        Map<Religion, Integer> count = new EnumMap<>(Religion.class);
        for (BuildingPlacement c : citiesOf(clientSide, civ)) {
            Religion r = religionOfCity(clientSide, c);
            if (r != null)
                count.merge(r, 1, Integer::sum);
        }
        return count.entrySet().stream().max(Map.Entry.comparingByValue()).map(Map.Entry::getKey).orElse(null);
    }

    // cities that follow a religion, everywhere
    public static int followerCities(boolean clientSide, Religion religion) {
        int n = 0;
        for (BuildingPlacement c : cities(clientSide))
            if (religionOfCity(clientSide, c) == religion)
                n++;
        return n;
    }

    // ---- beliefs ----------------------------------------------------------------------------------------------------

    public static boolean hasBelief(boolean clientSide, @Nullable Religion religion, Belief belief) {
        return religion != null && (religion.belief == belief || data(clientSide).secondBeliefs.get(religion) == belief);
    }

    // a belief that acts where it is (building, gathering, farms, priests): does it apply at this spot?
    public static boolean beliefAt(boolean clientSide, double x, double z, Belief belief) {
        return anyFounded(clientSide) && hasBelief(clientSide, religionAt(clientSide, x, z), belief);
    }

    // a belief that acts for the whole civilisation (research, heroes): share of its cities that follow it
    public static float civShare(boolean clientSide, String civ, Belief belief) {
        if (!anyFounded(clientSide))
            return 0;
        List<BuildingPlacement> cities = citiesOf(clientSide, civ);
        if (cities.isEmpty())
            return 0;
        int n = 0;
        for (BuildingPlacement c : cities)
            if (hasBelief(clientSide, religionOfCity(clientSide, c), belief))
                n++;
        return (float) n / cities.size();
    }

    // a belief only the founder gets: does this civilisation have it?
    public static boolean founderHas(boolean clientSide, String civ, Belief belief) {
        return hasBelief(clientSide, foundedBy(clientSide, civ), belief);
    }

    // beliefs nobody has yet: they can be bought as a second one
    public static List<Belief> freeBeliefs(boolean clientSide) {
        Set<Belief> taken = new HashSet<>();
        for (Religion r : data(clientSide).founders.keySet())
            taken.add(r.belief);
        taken.addAll(data(clientSide).secondBeliefs.values());
        List<Belief> free = new ArrayList<>();
        for (Belief b : Belief.values())
            if (!taken.contains(b))
                free.add(b);
        return free;
    }

    public static int faith(boolean clientSide, String civ) {
        return (int) Math.floor(data(clientSide).faith.getOrDefault(civ, 0d));
    }
}
