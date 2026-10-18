package com.solegendary.reignofnether.score;

// one civilisation's points, by domain (ScoreServerEvents works them out)
public record Score(String civ, int territory, int population, int technology, int military, int religion, int economy) {

    public static final String[] DOMAINS = { "Territoire", "Population", "Technologies", "Militaire", "Religion", "Économie" };

    public int total() {
        return territory + population + technology + military + religion + economy;
    }

    public int get(int domain) {
        return switch (domain) {
            case 0 -> territory;
            case 1 -> population;
            case 2 -> technology;
            case 3 -> military;
            case 4 -> religion;
            default -> economy;
        };
    }
}
