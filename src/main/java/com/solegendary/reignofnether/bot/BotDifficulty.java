package com.solegendary.reignofnether.bot;

// how strong the bots are; chosen with the bots button in the start menu (right click) or /rts-bot difficulte
public enum BotDifficulty {
    //    villagers, peace before attacking civilisations, first attack, max army, between attacks, resources given at start
    EASY(  14, 20 * 60 * 40, 8, 14, 20 * 60 * 5, 0,   "facile"),
    NORMAL(18, 20 * 60 * 25, 6, 24, 20 * 60 * 3, 0,   "normal"),
    HARD(  24, 20 * 60 * 15, 6, 32, 20 * 60 * 2, 300, "difficile");

    public final int maxVillagers;     // in the first city (more with each new one)
    public final int peaceTicks;       // no attack on players or other bots before this (barbarians any time)
    public final int firstAttackSize;  // soldiers for the first attack, then 2 more each time
    public final int maxArmy;
    public final int attackCooldown;
    public final int startBonus;       // more food, wood and ore when it arrives
    public final String frName;

    BotDifficulty(int maxVillagers, int peaceTicks, int firstAttackSize, int maxArmy, int attackCooldown, int startBonus, String frName) {
        this.maxVillagers = maxVillagers;
        this.peaceTicks = peaceTicks;
        this.firstAttackSize = firstAttackSize;
        this.maxArmy = maxArmy;
        this.attackCooldown = attackCooldown;
        this.startBonus = startBonus;
        this.frName = frName;
    }

    public static BotDifficulty fromFr(String s) {
        for (BotDifficulty d : values())
            if (d.frName.equalsIgnoreCase(s) || d.name().equalsIgnoreCase(s))
                return d;
        return null;
    }
}
