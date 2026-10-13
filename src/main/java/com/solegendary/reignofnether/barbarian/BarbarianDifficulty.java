package com.solegendary.reignofnether.barbarian;

// how hard the barbarians are; chosen before or during a game (button next to the game mode, or /rts-barbarians)
public enum BarbarianDifficulty {
    //    first/next camp, level up, raids, replace defender, new camps stronger, extra camps, garrison per level
    // a camp takes 4 levels to reach the top: 4 h in easy, 2 h 40 in normal, 1 h 40 in hard
    EASY(  20 * 60 * 8,     20 * 60 * 60, 20 * 60 * 10, 20 * 45, 20 * 60 * 60, 0, new int[] { 2, 3, 5, 6, 8 }),
    NORMAL(20 * 60 * 5,     20 * 60 * 40, 20 * 60 * 6,  20 * 30, 20 * 60 * 40, 1, new int[] { 3, 5, 7, 9, 12 }),
    HARD(  20 * 60 * 7 / 2, 20 * 60 * 25, 20 * 60 * 4,  20 * 18, 20 * 60 * 25, 2, new int[] { 4, 7, 10, 12, 15 });

    public final int campSpawnInterval;     // delay before the first camp, then between camps
    public final int campUpgradeInterval;   // a camp gains a level this often
    public final int raidInterval;          // from level 2, a raid this often
    public final int unitSpawnInterval;     // a camp replaces one missing defender this often
    public final int newCampLevelInterval;  // new camps start one level higher this often
    public final int extraCamps;            // camps beyond one per player
    public final int[] garrisonSize;

    BarbarianDifficulty(int campSpawnInterval, int campUpgradeInterval, int raidInterval, int unitSpawnInterval,
                        int newCampLevelInterval, int extraCamps, int[] garrisonSize) {
        this.campSpawnInterval = campSpawnInterval;
        this.campUpgradeInterval = campUpgradeInterval;
        this.raidInterval = raidInterval;
        this.unitSpawnInterval = unitSpawnInterval;
        this.newCampLevelInterval = newCampLevelInterval;
        this.extraCamps = extraCamps;
        this.garrisonSize = garrisonSize;
    }

    public String langKey() {
        return "hud.reignofnether.barbarians." + name().toLowerCase();
    }
}
