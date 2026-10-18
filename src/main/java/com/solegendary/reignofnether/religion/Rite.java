package com.solegendary.reignofnether.religion;

// what faith buys once a temple stands: short boosts around a temple, each with its own wait before the next one
public enum Rite {
    BLESSING("Bénédiction", 60, 3 * 60, "textures/item/blaze_powder.png",
        "Soldats +15 % de dégâts pendant 1 min, à moins de 24 blocs du temple"),
    HARVEST("Fête des moissons", 50, 5 * 60, "textures/item/bread.png",
        "Fermes de la ville du temple 50 % plus rapides pendant 2 min"),
    PRAYER("Prière", 40, 2 * 60, "textures/item/golden_apple.png",
        "Soigne d'un coup (30 % de leur vie) vos unités et celles de vos alliés à moins de 20 blocs du temple");

    public final String frName;
    public final int faith;
    public final int cooldownSeconds;
    public final String icon;
    public final String description;

    Rite(String frName, int faith, int cooldownSeconds, String icon, String description) {
        this.frName = frName;
        this.faith = faith;
        this.cooldownSeconds = cooldownSeconds;
        this.icon = icon;
        this.description = description;
    }
}
