package com.solegendary.reignofnether.nation;

import javax.annotation.Nullable;

// Playable nations, like civilizations in Civilization: each player picks one before starting (one nation per player).
// All nations play the villagers faction; each has a leader, a colour and one bonus.
public enum Nation {
    //       name            leader                    colour     bonus text                                          build  roadCost roadBuild loot prod  gather speed startRes
    FRANCE  ("France",       "Louis XIV",              0x3060E0, "Bâtiments construits 20 % plus vite",              1.2f,  1f,      1,        1f,  1f,   1f,    0f,   0),
    ENGLAND ("Angleterre",   "Élisabeth I",          0xD02828, "Routes 30 % moins chères",                         1f,    0.7f,    1,        1f,  1f,   1f,    0f,   0),
    SPAIN   ("Espagne",      "Isabelle I",           0xE8B020, "Butin des camps barbares doublé",                  1f,    1f,      1,        2f,  1f,   1f,    0f,   0),
    HRE     ("Saint-Empire", "Frédéric Barberousse",   0x303030, "Unités produites 15 % plus vite",                  1f,    1f,      1,        1f,  1.15f, 1f,   0f,   0),
    ROME    ("Rome",         "Auguste",                0x8020A0, "Routes construites deux fois plus vite",           1f,    1f,      2,        1f,  1f,   1f,    0f,   0),
    PORTUGAL("Portugal",     "Jean II",                0x20A050, "+200 de nourriture, bois et minerai au départ",    1f,    1f,      1,        1f,  1f,   1f,    0f,   200),
    RUSSIA  ("Russie",       "Pierre le Grand",        0xE0E0E0, "Récolte 15 % plus rapide",                         1f,    1f,      1,        1f,  1f,   1.15f, 0f,   0),
    SWEDEN  ("Suède",        "Gustave II Adolphe",     0x40A0E0, "Unités 10 % plus rapides",                         1f,    1f,      1,        1f,  1f,   1f,    0.1f, 0);

    public final String displayName;
    public final String leader;
    public final int colour;
    public final String bonusText;
    public final float buildSpeedMult; // construction of buildings (not repairs)
    public final float roadCostMult;
    public final int roadBuildMult; // road blocks placed per worker per step
    public final float barbarianLootMult;
    public final float productionSpeedMult;
    public final float gatherSpeedMult;
    public final float unitSpeedBonus; // 0.1 = +10% movement speed
    public final int startingResourcesBonus; // added to food, wood and ore at the start

    Nation(String displayName, String leader, int colour, String bonusText, float buildSpeedMult, float roadCostMult,
           int roadBuildMult, float barbarianLootMult, float productionSpeedMult, float gatherSpeedMult,
           float unitSpeedBonus, int startingResourcesBonus) {
        this.displayName = displayName;
        this.leader = leader;
        this.colour = colour;
        this.bonusText = bonusText;
        this.buildSpeedMult = buildSpeedMult;
        this.roadCostMult = roadCostMult;
        this.roadBuildMult = roadBuildMult;
        this.barbarianLootMult = barbarianLootMult;
        this.productionSpeedMult = productionSpeedMult;
        this.gatherSpeedMult = gatherSpeedMult;
        this.unitSpeedBonus = unitSpeedBonus;
        this.startingResourcesBonus = startingResourcesBonus;
    }

    // nation of a player, on either side; null if they haven't picked one
    @Nullable
    public static Nation of(boolean isClientSide, String ownerName) {
        return isClientSide ? NationClientEvents.getNation(ownerName) : NationServerEvents.getNation(ownerName);
    }

    public static float buildSpeed(boolean isClientSide, String owner) { Nation n = of(isClientSide, owner); return n != null ? n.buildSpeedMult : 1f; }
    public static float roadCost(boolean isClientSide, String owner) { Nation n = of(isClientSide, owner); return n != null ? n.roadCostMult : 1f; }
    public static int roadBuild(boolean isClientSide, String owner) { Nation n = of(isClientSide, owner); return n != null ? n.roadBuildMult : 1; }
    public static float barbarianLoot(boolean isClientSide, String owner) { Nation n = of(isClientSide, owner); return n != null ? n.barbarianLootMult : 1f; }
    public static float productionSpeed(boolean isClientSide, String owner) { Nation n = of(isClientSide, owner); return n != null ? n.productionSpeedMult : 1f; }
    public static float gatherSpeed(boolean isClientSide, String owner) { Nation n = of(isClientSide, owner); return n != null ? n.gatherSpeedMult : 1f; }
    public static float unitSpeed(boolean isClientSide, String owner) { Nation n = of(isClientSide, owner); return n != null ? n.unitSpeedBonus : 0f; }
    public static int startingResources(boolean isClientSide, String owner) { Nation n = of(isClientSide, owner); return n != null ? n.startingResourcesBonus : 0; }
}
