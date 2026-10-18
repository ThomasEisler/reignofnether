package com.solegendary.reignofnether.religion;

// The religions that can be founded, one civilisation each (first come). Each has a symbol, a colour and an imposed
// belief; the icon is the vanilla block shown on its "found" button.
public enum Religion {
    CATHOLICISM("Catholicisme", "✝", 0xE8C547, Belief.TITHE, "yellow_concrete"),
    ORTHODOXY("Orthodoxie", "☦", 0x3F6FD8, Belief.BUILDER_MONKS, "blue_concrete"),
    PROTESTANTISM("Protestantisme", "✝", 0xF0F0F0, Belief.WORK_ETHIC, "white_concrete"),
    ISLAM("Islam", "☪", 0x2EA043, Belief.HOUSES_OF_WISDOM, "green_concrete"),
    JUDAISM("Judaïsme", "✡", 0x7EC8F0, Belief.FAITHFULNESS, "light_blue_concrete"),
    BUDDHISM("Bouddhisme", "☸", 0xF09030, Belief.COMPASSION, "orange_concrete"),
    HINDUISM("Hindouisme", "ॐ", 0xD03030, Belief.BLESSED_HARVEST, "red_concrete"),
    NORSE("Paganisme nordique", "ᛟ", 0x909090, Belief.SAGAS, "gray_concrete");

    public final String frName;
    public final String symbol;
    public final int colour;
    public final Belief belief;
    public final String iconBlock;

    Religion(String frName, String symbol, int colour, Belief belief, String iconBlock) {
        this.frName = frName;
        this.symbol = symbol;
        this.colour = colour;
        this.belief = belief;
        this.iconBlock = iconBlock;
    }

    // the beliefs that can be bought as a second one (the imposed ones of the religions come too, when free)
    public static final java.util.List<Belief> EXTRA_BELIEFS = java.util.List.of(Belief.CRUSADE, Belief.PILGRIMAGE, Belief.FRATERNITY);
}
