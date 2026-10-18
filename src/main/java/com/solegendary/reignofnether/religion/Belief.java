package com.solegendary.reignofnether.religion;

// What a religion gives. Each religion has one imposed belief; a second one is bought later (Religion).
// Most act in the cities that follow the religion (whoever owns them); founderOnly ones (war, income) only for the
// civilisation that founded it.
public enum Belief {
    TITHE("Dîme", "+1 émeraude par ville qui suit la religion, toutes les minutes (10 villes au plus)", true),
    BUILDER_MONKS("Moines bâtisseurs", "Bâtiments construits 15 % plus vite dans ses villes", false),
    WORK_ETHIC("Éthique du travail", "Récolte 10 % plus rapide dans ses villes", false),
    HOUSES_OF_WISDOM("Maisons de la sagesse", "Recherches jusqu'à 15 % plus rapides (selon la part de vos villes qui la suivent)", false),
    FAITHFULNESS("Fidélité", "Ses villes sont 2 fois plus dures à convertir", false),
    COMPASSION("Compassion", "Les prêtres soignent 2 fois plus, et plus loin, dans ses villes", false),
    BLESSED_HARVEST("Récoltes bénies", "Fermes 10 % plus rapides dans ses villes", false),
    SAGAS("Sagas", "Héros jusqu'à +15 % de dégâts et de résistance (selon la part de vos villes qui la suivent)", false),
    CRUSADE("Croisade", "Soldats +10 % de dégâts sur les terres d'une ville d'une autre religion (fondateur seulement)", true),
    PILGRIMAGE("Pèlerinage", "+1 foi par ville qui suit la religion, toutes les minutes (10 villes au plus)", true),
    FRATERNITY("Fraternité", "Les bots dont les villes suivent la religion vous apprécient beaucoup plus", true);

    public final String frName;
    public final String description;
    public final boolean founderOnly;

    Belief(String frName, String description, boolean founderOnly) {
        this.frName = frName;
        this.description = description;
        this.founderOnly = founderOnly;
    }
}
