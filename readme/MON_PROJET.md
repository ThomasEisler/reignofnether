# Mon projet : ma version de Reign of Nether

Version personnelle du mod RTS **Reign of Nether** (Minecraft 1.20.1, Forge), pour en faire un RTS multijoueur à ma façon.

- Dépôt d'origine : https://github.com/SoLegendary/reignofnether (branche `1.20.1-dev`)
- Ma copie : https://github.com/ThomasEisler/reignofnether
- Ma branche de travail : **`ma-version`** (ne jamais travailler directement sur `1.20.1-dev`)

> Ce fichier est tenu à jour à chaque étape. Dernière mise à jour : 14 octobre 2026.

---

## Comment ça marche chez moi

| Quoi | Où |
|---|---|
| Code du mod | `~/locallab/mc/RTS/reignofnether` |
| Java 17 | installé avec Homebrew (`openjdk@17`) |
| Profil de jeu pour tester | **Prism Launcher → profil « RTS »** (CurseForge n'est plus utilisé) |
| Mods du profil RTS | ma version de Reign of Nether, Distant Horizons, Embeddium, Oculus + shaders Complementary, Tectonic, Lithostitched, WorldEdit 7.2.15 (TEMPORAIRE : outil de construction, à retirer avant la version finale) |

**Fabriquer le mod** : `./gradlew build` → le fichier est dans `build/libs/reignofnether-1.5.0.jar`, à copier (jeu fermé) dans le dossier `mods` du profil RTS.

**Récupérer les nouveautés de l'auteur** : `git fetch upstream` puis, sur `ma-version`, `git merge upstream/1.20.1-dev`.

---

## Mon serveur (projet)

Un **serveur unique** avec ce mod : je ne partage pas le mod, on joue seulement sur mon serveur.

1. **Première connexion** : le joueur choisit sa **nation**.
2. La **vue RTS (F12) est forcée** : les joueurs ne peuvent pas jouer autrement.
3. Le serveur **l'envoie dans une zone encore libre**, que personne d'autre n'occupe.
4. **Distance de sécurité** : impossible de poser son Hôtel de ville trop près de celui d'un autre joueur.
5. Ensuite, on joue tous ensemble sur la même carte.

Rien de tout ça n'est fait. À prévoir aussi : retirer Distant Horizons et WorldEdit du serveur et du pack des joueurs.

---

## Ce qui est fait

### Terrain
- Le type de monde « Default » utilise le terrain de **Tectonic** (au lieu du générateur propre à Reign of Nether), en gardant les biomes de Reign of Nether.
- Réglages du relief : `config/tectonic.json` du profil RTS (moins haut, plus de plaines, moins de forêts). Le 13/10, relief **beaucoup plus plat** : `vertical_scale` 0,6 → **0,25** et `flat_terrain_skew` 0,6 → **1,0** (ancien réglage gardé dans `tectonic.json.bak-2026-10-13`). ⚠️ Ne vaut que pour les **nouveaux mondes** (ou les zones pas encore explorées).

### Bâtiments à niveaux (1, 2, 3)
- Système générique : chaque niveau a sa propre forme (structure) et ses statistiques : points de vie, portée de tir de la garnison, vitesse de production, coût et durée de l'amélioration.
- L'amélioration s'achète dans le bâtiment comme une recherche (bouton bouteille d'expérience, touche **E**). Vérifiée par le serveur, synchronisée chez tous les joueurs, sauvegardée.
- Appliqué à :

| Bâtiment | Niveau 1 | Niveau 2 | Niveau 3 |
|---|---|---|---|
| **Barracks** (caserne) | 150 PV | 225 PV, production +25 % (150 bois, 100 minerai, 60 s) | 300 PV, production +50 % (250 bois, 200 minerai, 90 s) |
| **Hôtel de ville** | 380 PV | 500 PV, production +25 % (300 bois, 200 minerai, 90 s) | 650 PV, production +50 % (500 bois, 400 minerai, 120 s) |

- **Tour de guet** (amélioration touche **E** quand elle est sélectionnée) : niveau 1 = 240 PV, portée 24 ; niveau 2 = 340 PV, portée 28, **dégâts de la garnison +25 %** (100 bois, 150 minerai, 45 s) ; niveau 3 = 450 PV, portée 32, dégâts +50 % (150 bois, 250 minerai, 60 s, il faut la recherche **Fortifications**). Formes `watchtower_lvl2` / `watchtower_lvl3` provisoires (copies de la tour).
- Un niveau **plus grand** (ou plus petit) que le niveau 1 se pose **centré** sur le bâtiment : on peut agrandir les formes des niveaux 2 et 3 librement.
- Mes nouvelles formes de **niveau 1** sont en place (caserne en bois, Hôtel de ville en campement).
- ⚠️ Les formes des **niveaux 2 et 3 sont provisoires** (copies des anciens bâtiments de pierre) : à construire.

### Chantiers
- **Durée propre à chaque bâtiment**, fixée dans un tableau (secondes avec **1 ouvrier** ; avec 3 ouvriers il faut 60 % du temps, avec 5 ouvriers 43 %) :

| Bâtiment | Durée (1 ouvrier) | | Bâtiment | Durée (1 ouvrier) |
|---|---|---|---|---|
| Ferme de blé | 30 s | | Marché | 90 s |
| Entrepôt (Stockpile) | 40 s | | Forge | 90 s |
| Maison | 40 s | | Bibliothèque | 90 s |
| Caserne | 60 s | | Hutte de sorcière | 90 s |
| Golem de fer | 60 s | | Sanctuaire de prospérité | 90 s |
| Tour de guet | 75 s | | Tour arcanique | 120 s |
| Hôtel de ville | 150 s | | Château | 180 s |

  Les bâtiments des autres factions (monstres, piglins) gardent le calcul d'origine, ralenti 1,5 fois. Les réparations ne changent pas.
- **Tours d'échafaudage** dans la case du bâtiment et juste autour (entre 30 % et 70 % de la hauteur du bâtiment, jamais au-dessus) : elles poussent au fur et à mesure du chantier, chacune à son rythme (un peu au hasard), et montent devant les murs. Retirées quand le bâtiment est terminé ou détruit.
- Une **barre de progression dorée** flotte au-dessus de chaque chantier visible.

### Formes des bâtiments (structures)
- Un bâtiment est fait de vrais blocs, enregistrés dans un fichier `.nbt` avec un **bloc de structure** en jeu.
- **Reconstruction automatique** : chaque bâtiment retient une « empreinte » de sa forme. Si je modifie une forme et mets le mod à jour, le serveur reconstruit tout seul les bâtiments déjà posés au redémarrage.
- **Fondations** : si une forme n'a aucun des blocs de fondation prévus par le mod, toute la rangée du bas sert de fondation (corrige la défaite immédiate).
- **Modèles avec sol** : pour les modèles refaits par moi, la rangée du bas (y = 0 dans le bloc de structure) **est le sol** : elle remplace la couche d'herbe du terrain au lieu d'être posée dessus. Si le bâtiment est détruit, ce sol redevient de la terre (pas de trou). Les modèles concernés sont listés dans `BuildingBlockData.GROUND_ROW_STRUCTURES` (pour l'instant : ferme de blé, Hôtel de ville niveau 1, entrepôt, maison) ; les autres sont encore posés sur l'herbe, comme avant.
- **Fermes** : la terre labourée reste humide toute seule, pas besoin d'eau.
- **Sol** : dans mes structures, les cases de sol en **chemin de terre** sont « adaptables » : elles sont posées dans le matériau de la route reliée au bâtiment (ou « chemin » s'il n'y a pas de route). Les autres blocs de sol restent comme je les ai construits.

### Routes
- Sélectionner un bâtiment → bouton **« Construire une route »** (touche **H**) → cliquer un autre de mes bâtiments.
- **Aperçu** : en survolant le second bâtiment, le tracé s'affiche en surbrillance au sol et le prix apparaît à côté du curseur (en rouge si la route est impossible, avec la raison).
- **Construite par les ouvriers** : une fois payée, la route est un chantier. Les 3 ouvriers libres les plus proches (dans un rayon de 64 blocs) y vont tout seuls et posent les blocs un par un, en avançant d'un bâtiment à l'autre. Tout ouvrier libre qui passe à moins de 12 blocs du chantier vient aider. Donner un autre ordre à un ouvrier le retire du chantier. Les améliorations de route se construisent de la même façon.
- Route de 3 blocs de large qui suit le relief et **contourne les obstacles** : eau, autres bâtiments, pentes de plus d'un bloc par case ; elle évite aussi les arbres et les montées quand le détour est court. Refusée s'il n'existe aucun chemin, si les bâtiments sont à plus de 256 blocs ou si la route ferait plus de 384 blocs.
- **En arc de cercle** : entre deux bâtiments à plus de 24 blocs, la route se courbe d'un côté (le milieu de l'arc s'écarte de 10 % de la distance, 10 blocs au plus) ; la recherche du chemin est attirée vers cet arc, donc les obstacles restent contournés. Réglages : `ARC_BEND`, `ARC_MAX_BEND`, `ARC_PULL` dans `RoadPathTracer`.
- Le **prix dépend de la longueur**. Si un des deux bâtiments est détruit pendant le chantier, la partie pas encore construite est **remboursée** (message à l'écran).
- Recliquer sur deux bâtiments déjà reliés **améliore la route** :

| Niveau | Mélange de blocs | Prix par bloc de longueur | Vitesse des unités |
|---|---|---|---|
| Chemin | gravier + terre grossière | 1 bois | +15 % |
| Pavée | andésite + gravier | 1 bois + 1 minerai | +25 % |
| Avancée | andésite + briques de pierre + pierre | 3 minerais | +35 % |

- Le mélange est fixe pour chaque case : une route reconstruite garde le même motif.
- **Petits détails au bord des routes**, rares et espacés (au moins 7 blocs entre deux) : quelques buissons de feuilles le long des chemins, quelques barrières le long des routes pavées, quelques murets surmontés d'une lanterne le long des routes avancées. Posés à la fin du chantier ; remplacés quand la route est améliorée. Réglages : `DECORATION_RARITY` et `DECORATION_SPACING` dans `RoadServerEvents`.
- Une **place irrégulière** (0 à 2 blocs) entoure chaque bâtiment terminé, en matériau « chemin », et prend le matériau de la route quand elle arrive.
- Les unités **préfèrent passer par les routes** : une case de route « coûte » moitié moins qu'une case de terre (`ROAD_COST = 0.5` dans `PathfinderConfig`), donc elles font un détour pour les prendre. Le déplacement propre au mod est maintenant **activé sur toutes les cartes** (avant : seulement avec une bordure de 1280 blocs ou moins ; sans bordure, pas de pré-calcul de la carte, à surveiller si ça rame). Les routes sont reconnues par leur position : le gravier ou la pierre naturels ne comptent pas.
- Le serveur connaît le **réseau** : quels bâtiments sont reliés entre eux. Sauvegardé avec le monde.

### Scierie
- **Scierie** (menu des ouvriers, 250 bois + 75 minerai, 60 s) : débloquée par la recherche **Sylviculture** (100 nourriture, 200 bois, 100 minerai, 4 min, après Sacs à dos). 9 emplacements de podzol : une jeune pousse (azalée) apparaît 15 s après qu'un emplacement est vide, devient un petit arbre (2 bûches + feuilles) 100 s plus tard. Clic droit avec des villageois : ils y coupent le bois comme dans une ferme. Réglages dans `SawmillPlacement` (`PLANT_SECONDS`, `GROW_SECONDS`, `TREE_LOGS`). Modèle `sawmill` provisoire.

### Éclaireurs et nuages
- **Éclaireur** (Hôtel de ville, 2e bouton, 75 nourriture, 20 s) : un villageois cartographe monté sur un gros chien. Plus rapide qu'un ouvrier (0,34 contre 0,25), voit à 40 blocs (16 pour les autres), ne se bat pas. Il remplace le chien et le chat éclaireurs. Le chien n'est qu'un dessin (pas une vraie bête), dessiné sous le villageois (taille `DOG_SCALE`, hauteur de selle `SEAT_HEIGHT` dans `ExplorerUnitRenderer`).
- **Nuages** sur tout ce que le joueur n'a pas encore exploré : chaque morceau de carte (16 × 16) vu une fois par ses unités ou bâtiments est découvert pour toujours (sauvegardé). Sous les nuages : unités, icônes et bâtiments ennemis cachés, minimap blanche, construction interdite (« Zone inexplorée »). Tout est sous les nuages dès que le joueur a commencé la partie (avant, pendant le choix de l'emplacement de départ, la carte est visible). Les nuages vont jusqu'à 1024 blocs autour de la caméra (nuages plats au-delà de la distance d'affichage) pour cacher aussi le terrain lointain de Distant Horizons ; plus loin encore, Distant Horizons peut encore montrer le relief. Bots et barbares : pas de nuages.
- Commandes : `/rts-nuages off` / `on` (sauvegardé avec le monde), `/rts-nuages reset` (tout redevient inexploré).

### Barbares
- Faction contrôlée par l'ordinateur, **ennemie de tous**. **Actifs par défaut** dans tous les mondes ; `/rts-barbarians off` pour les désactiver (`on` pour les remettre), réglage sauvegardé avec le monde.
- **Camps** : rien tant qu'aucun joueur n'a de caserne terminée. Après un délai (5 min en normal) suivant la première caserne d'un joueur, un camp apparaît **à 130-170 blocs des bâtiments des joueurs** (et à 110 blocs au moins d'un autre camp), puis un autre après le même délai, etc., jusqu'à **n + 1 camps** (n = joueurs ayant une caserne depuis assez longtemps). Un joueur qui perd toutes ses casernes ne compte plus. Un camp détruit est remplacé au plus tôt après ce délai. Le **premier camp commence toujours au niveau 1** ; les camps suivants commencent un niveau plus haut au même rythme que la montée de niveau, compté à partir de l'arrivée possible du premier camp.
- **5 niveaux** (depuis le 14/10/2026) ; chaque niveau garde tout ce qu'avait le précédent :

| Niveau | Nom | Nouveaux bâtiments | Remparts | Garnison (normal) | PV |
|---|---|---|---|---|---|
| 1 | Campement | 2 tentes, feu de camp | aucun | 3 | 200 |
| 2 | Repaire | tour, réserve de butin (les raids commencent) | aucun | 5 | 300 |
| 3 | Fort | forge, cor d'alarme | palissade, portes en bois (rayon ~23) | 7 | 400 |
| 4 | Forteresse | cages à prisonniers, écurie | pierre, herses, reconstruits plus loin (rayon ~31) | 9 | 500 |
| 5 | Citadelle | enclos à ravageurs, tente du chef | pierre, cercle encore agrandi (rayon ~35) | 12 | 650 |

- **Rôle des bâtiments** :
  - **Feu de camp** : les barbares à moins de 10 blocs regagnent 1 PV par seconde.
  - **Réserve de butin** : les pillards d'un camp qui en a une **volent** 10 de nourriture, bois et minerai de temps en temps quand ils sont près d'un bâtiment de joueur (message « Des barbares pillent vos réserves ! »). Détruire la réserve (ou le camp) donne tout ce qui a été volé au joueur dont une unité est la plus proche.
  - **Forge** : tant qu'elle tient, les barbares du camp ont Force et Résistance et portent un casque de mailles.
  - **Cor d'alarme** : un intrus approche → son du cor, **toute** la garnison attaque et le camp voisin le plus proche (140 blocs) envoie la moitié de la sienne. Sans cor, seuls les défenseurs à moins de 12 blocs de l'intrus réagissent.
  - **Cages** : 2 prisonniers au départ ; un villageois de joueur tué par un barbare de ce camp est **capturé** (8 au plus). Casser les cages (ou le camp) les libère : ils rejoignent le joueur le plus proche.
  - Ce qui a été volé et les prisonniers sont **sauvegardés avec le monde** (par camp, dans `BarbarianSaveData.campStores`).
  - **Écurie** : sans elle, plus de cavaliers. **Enclos à ravageurs** : sans lui, plus de ravageurs.
  - **Tente du chef** : un **chef barbare** (vindicateur à casque d'or, 5 fois plus de vie, 2 fois plus de dégâts). S'il tombe : la garnison a Faiblesse et Lenteur pendant 3 min ; il revient 5 min plus tard tant que sa tente tient.
- **Garnison** : milice et vindicateurs, puis pillards (niv. 2), plus de vindicateurs (niv. 3), cavaliers (niv. 4, avec écurie), ravageurs (niv. 5, avec enclos). Un soldat perdu est remplacé régulièrement. Ils patrouillent autour du camp.
- **Raids** : à partir du niveau 2, la moitié de la garnison part attaquer le bâtiment de joueur le plus proche.
- **Point rouge** au-dessus de chaque soldat barbare (caché dans le brouillard de guerre).
- **Butin** : détruire un camp rapporte 100 / 200 / 300 / 400 / 500 de nourriture, bois et minerai (au joueur dont une unité est la plus proche). Les survivants partent en raid.
- Les bâtiments se construisent tout seuls, sur deux cercles autour du centre (13 blocs, puis 20 blocs pour l'écurie, l'enclos et la tente du chef). Si le bâtiment principal est détruit, tout s'effondre avec lui.
- **Remparts** : un **cercle irrégulier fermé** (forme propre à chaque camp), fait de **segments de 5 colonnes qui sont des bâtiments** (vie, brèches). **2 portes** de 5 blocs qui **coulissent** pour les barbares. Palissade 150 PV par segment, porte en bois 220 ; pierre 300, herse 450. Ils se posent sur le terrain sans le creuser.
- **Apparence propre** : peau propre (fourrures, cheveux et barbe, peinture de guerre bleue, yeux rouges) : milice rousse, vindicateurs bruns, pillards blonds ; ravageurs à harnais rouge. Cavaliers : cheval noir en armure de cuir rouge.
- **Difficulté** : bouton « Barbares » (hache) dans le menu de départ, clic gauche : facile → normal → difficile → désactivés. Aussi `/rts-barbarians facile|normal|difficile`. Sauvegardé avec le monde.

| Difficulté | Camps (et 1er camp) | Montée de niveau | Niveau 5 après | Raids | Garnison niv. 1→5 | Remplacement | Camps en plus |
|---|---|---|---|---|---|---|---|
| Facile | 8 min | 60 min | 4 h | 10 min | 2 / 3 / 5 / 6 / 8 | 45 s | 0 |
| Normal | 5 min | 40 min | 2 h 40 | 6 min | 3 / 5 / 7 / 9 / 12 | 30 s | 1 |
| Difficile | 3 min 30 | 25 min | 1 h 40 | 4 min | 4 / 7 / 10 / 12 / 15 | 18 s | 2 |

- ⚠️ **Formes provisoires** : camp (niveaux 4 et 5 = copies du niveau 3), tente et tour (copies), et les 8 nouveaux bâtiments (formes simples faites par programme) : à construire par moi. Noms : `barbarian_camp_lvl4`, `barbarian_camp_lvl5`, `barbarian_campfire`, `barbarian_loot`, `barbarian_forge`, `barbarian_alarm`, `barbarian_cages`, `barbarian_stable`, `barbarian_ravager_pen`, `barbarian_chief_tent` (garder les annexes à 7 × 7 au plus).

### Carte : pêche, troupeaux, carrières et ruines
- Repérés la **première fois qu'un morceau de carte (chunk) est chargé**, marche aussi sur un monde existant pour les zones encore jamais chargées depuis. Sauvegardés avec le monde (`MapFeaturesSaveData`).
- **Coins de pêche** : sur l'eau profonde (2 blocs ou plus) près d'une rive (fleuves, lacs, mers), un emplacement sur 4 morceaux au plus, à 40 blocs au moins l'un de l'autre. On les voit à des **bulles** qui éclatent à la surface et de petites éclaboussures (cachées dans le brouillard de guerre).
- **Port de pêche** (menu des ouvriers, 150 bois + 25 minerai) : à poser **au bord de l'eau** (un côté doit toucher l'eau). **Clic droit sur le port avec des villageois** : ils deviennent **pêcheurs** (3 par port au plus). Chacun va au bord du port, **monte dans un bateau**, navigue (chemin calculé sur l'eau, 80 blocs au plus) jusqu'au **coin de pêche le plus proche**, pêche **20 s** (4 prises prises au coin de pêche), revient au port et **dépose 20 de nourriture**, puis repart, en boucle. Il s'arrête (et revient à terre) si on lui donne un autre ordre, si le port disparaît ou s'il n'y a plus de coin de pêche accessible. Un bateau coincé contre une rive saute au point suivant de son chemin. Les pêcheurs ne sont pas gardés en quittant le monde (leurs bateaux disparaissent). ⚠️ Forme provisoire (`port`, cabane de pêcheur faite par programme) : à construire par moi.
- **Troupeaux sauvages** : vaches, moutons, cochons ou poules, **4 à 8 bêtes** ensemble. **Plus de bêtes seules** : tout animal chassable qui n'appartient pas à un troupeau est retiré (sauf dans le tutoriel), et l'Hôtel de ville ne fait plus apparaître d'animaux autour de lui.
  - **Placés comme des structures, à intervalles réguliers** : un troupeau par carré de 80 × 80 blocs sur toute la carte (à un endroit fixe dans le carré, sur de l'herbe), pour qu'on choisisse où s'installer entre forêt, carrière et troupeaux.
  - **Migration** : toutes les 30 s, le troupeau va au point suivant d'un petit cercle (rayon 8 à 14) autour de son coin. S'il est à moins de 48 blocs d'un bâtiment de joueur, son coin s'éloigne de 6 blocs à chaque étape.
  - **Repousse** : une bête de plus toutes les 90 s tant qu'il en manque ; un troupeau chassé jusqu'à la dernière revient au même endroit 5 min plus tard.
- **Carrières** : un affleurement de minerai (fer et charbon, ou cuivre, charbon et or) par carré de **128 × 128 blocs**, au milieu du morceau de carte qui contient l'endroit choisi pour ce carré, sur un sol sec et assez plat. Les ouvriers les minent comme d'habitude ; **un bloc de minerai miné repousse toutes les 45 s** (là où rien ne se tient).
- **Ruines avec trésor** : une par carré de **176 × 176 blocs** (pas dans le même morceau qu'une carrière). On y envoie **n'importe quelle unité** : à moins de 8 blocs du milieu, elle **fouille** (petits nuages de poussière) ; au bout de **8 s**, son joueur reçoit **150 à 300 de nourriture, de bois et de minerai** et les ruines **disparaissent** (icône retirée). S'éloigner remet la fouille à zéro. Réglages `RUIN_SEARCH_RANGE`, `RUIN_SEARCH_TICKS` dans `MapFeaturesServerEvents`. (Avant le 14/10 au soir : il fallait les détruire, ça ne marchait pas bien.)
- ⚠️ **Modèles provisoires** faits par programme : `quarry_1`, `quarry_2` (carrières), `ruin_1`, `ruin_2` (ruines), 9 × 9. Pour en ajouter : nouveaux fichiers + leur nom dans `QUARRY_MODELS` (carrières) ou un bâtiment `RUIN_n` dans `Buildings` et `ruinBuildings()` (ruines). Le rang du bas des carrières est enfoncé dans le sol ; celui des ruines est posé sur le sol.
- **Ressources épuisables** : une carrière fait repousser **30 blocs de minerai** en tout, puis s'épuise (icône retirée quand son dernier minerai est miné) ; un troupeau fait repousser **10 bêtes** en tout, puis disparaît avec sa dernière bête ; un coin de pêche donne **240 prises** (60 sorties de pêcheur, 1 200 nourriture), puis ses bulles disparaissent (message). Réglages : `QUARRY_REGROW_TOTAL`, `HERD_REGROW_TOTAL`, `FISHING_CATCHES` dans `MapFeaturesServerEvents`.
- **Icônes sur la minimap** : un lingot brut de fer pour les carrières, un coffre pour les ruines (disparaît quand elles sont détruites), un poisson pour chaque coin de pêche, de la viande pour chaque troupeau (bœuf, mouton, porc ou poulet selon l'animal). Une icône n'apparaît qu'une fois l'endroit vu au moins une fois (sorti du brouillard de guerre) ; ce souvenir se perd en quittant le jeu.
- Code : dossier `mapfeatures/` (`PortFishingServerEvents` : pêcheurs et bateaux ; `MapFeaturesServerEvents` : repérage, troupeaux ; `FishingClientEvents` : bulles ; `MapIconsClientEvents` : icônes de la minimap), `building/buildings/villagers/Port.java`, règle de pose (toucher l'eau) dans `BuildingValidators.getPortError`, ordre `UnitAction.FISH`.

### Limite de distance pour construire
- On ne peut construire qu'à **24 blocs maximum** d'un de ses bâtiments (même en construction). Exceptions : **entrepôts** et **ponts**. Un joueur sans bâtiment construit où il veut (premier Hôtel de ville).
- **Aperçu** : pendant le placement d'un bâtiment, le sol trop loin est **teinté en rouge** autour du curseur, et le bâtiment fantôme devient rouge avec le message « Trop loin de vos bâtiments ». Vérifié aussi par le serveur.

### Français
- **Tous les textes du mod sont traduits** en français (`lang/fr_fr.json` complet : 815 textes ajoutés, 57 restés en anglais traduits), **Minecraft reste en anglais mais le mod est en français** : au moment de fabriquer le mod, `build.gradle` (bloc `processResources` à la fin) remplace son `en_us.json` par `fr_fr.json`. Le vrai `en_us.json` de l'auteur reste dans les sources, pour récupérer ses mises à jour. Noms choisis : Windcaller = « Mande-vent », Bogged = « Embourbé ».
- Restent en anglais : quelques messages de débogage ou d'erreur technique écrits directement dans le code.

### Monde
- **Un peu plus d'arbres dans les plaines** (et les prairies) : environ un arbre isolé tous les deux chunks en plus, pour avoir assez de bois (`data/reignofnether/forge/biome_modifier/extra_plains_trees.json` ; densité réglée par `chance` dans `worldgen/placed_feature/extra_plains_trees.json`). Seulement dans les nouvelles zones.
- **Plus de villages Minecraft** générés (seulement dans les nouvelles zones / nouveaux mondes).

### Mes remparts
- Dans le **menu de construction des ouvriers** : **Palissade** (touche **D**), **Rempart de pierre** (**J**, après la recherche Maçonnerie), **Porte en bois** (**K**), **Herse** (**Z**, après Métallurgie).
- **Mur** : cliquer où il commence, puis où il finit ; le mur (jusqu'à 48 blocs de long) et son prix suivent le curseur, en vert si c'est possible, en rouge sinon (avec la raison). **Maj + clic** : continuer le mur depuis ce point. Clic droit ou Échap : arrêter.
- **Tour de rempart** (touche **V**, après Maçonnerie) : tour de pierre 3 × 3, 8 blocs de haut, posée d'un clic, **aussi sur un de mes murs** (le mur est coupé autour d'elle, les morceaux restants restent debout). **2 unités** peuvent s'y installer en garnison et tirer d'en haut (portée 22) ; le haut ne s'effondre pas tant que la tour tient.
- **Porte** : 5 blocs de large, cliquer où elle commence puis dans sa direction. Elle **coulisse pour mes unités** (et mes alliés) et reste fermée pour les autres, qui doivent la casser.
- Construits par les **ouvriers sélectionnés**, morceau par morceau (5 blocs). Chaque morceau a de la vie : les barbares (et les ennemis) peuvent les attaquer et faire une brèche.
- Le mur suit le terrain sans le creuser, s'arrête à l'eau, et s'accroche aux murs et bâtiments existants. Il faut rester à portée de ses bâtiments (24 blocs).

Hauteurs (depuis le 14/10/2026) : palissade 2 ou 3 blocs, pierre 4 (créneaux compris), porte en bois 3, herse 4, tour 6. Les remparts déjà posés gardent leur ancienne hauteur. Quand un morceau est fini, environ 1 emplacement sur 7 au pied du mur reçoit un buisson ou un caillou (`RampartBuilding.onBuilt`, pas sur les portes).

| Sorte | Prix | PV par morceau |
|---|---|---|
| Palissade | 4 bois par bloc de long | 150 |
| Rempart de pierre | 3 bois + 4 minerai par bloc | 300 |
| Porte en bois | 80 bois | 220 |
| Herse | 60 bois + 100 minerai | 450 |
| Tour de rempart | 100 bois + 150 minerai | 500 |

### Cavalier (nouvelle unité)
- Produit dans la **caserne** (touche **R**) : un **vindicateur sur un cheval de guerre** (brun, sellé, armure de fer). Plus fort que le vindicateur et le pillard : 250 nourriture, 100 bois, 75 minerai, 50 s, 5 de population.
- Le **cheval est l'unité** qu'on sélectionne : il se déplace vite (0,34 contre 0,28 pour le vindicateur), a 120 PV + armure, et frappe fort au corps à corps (10 dégâts). Le vindicateur est assis dessus ; si le cheval meurt, il descend et continue à pied.
- Code : `unit/units/villagers/HorseUnit.java` (le cheval, statistiques en haut du fichier), `CavalierProd.java` (production : cheval + cavalier), coût `UnitCosts.CAVALIER` dans `ReignOfNetherCommonConfigs`. Icônes `textures/mobheads/cavalier.png`, `horse.png`, `horse_half.png`.

### Une seule faction : les villageois
- Les boutons de départ **Monstres** et **Piglins** sont cachés : on ne joue plus que les villageois (leur code reste dans le mod, simplement inaccessible, pour pouvoir récupérer les mises à jour de l'auteur).
- Le **mode Survie** est retiré du choix des modes de jeu (les barbares le remplacent).

### Nations (comme dans Civilization)
- **En arrivant dans une partie** (pas encore commencée, hors bac à sable et tutoriel), la fenêtre de choix de nation **s'ouvre toute seule**. « Annuler » la ferme ; le bouton de départ villageois la rouvre.
- En cliquant sur le bouton de départ villageois, on choisit d'abord sa **nation** dans une **fenêtre presque plein écran** : à gauche un **grand villageois 3D** de la nation (on le fait tourner en glissant la souris ; tenue différente selon la nation), sa **bannière** (une vraie bannière de Minecraft, motifs propres à chaque nation, qui flotte au vent ; petite bannière aussi dans la liste), et des **cartes** autour (dirigeant, bonus, statut) ; à droite la **liste des nations** et le bouton « Jouer … » (Échap ou « Annuler » pour fermer). Puis on pose ses villageois. **Une nation par joueur** (une nation prise est grisée).
- Le nom de la nation et de son dirigeant s'affiche en haut de l'écran, avec sa couleur. Un message annonce le choix à tous.
- `/rts-reset` efface les choix de nation.

| Nation | Dirigeant | Bonus |
|---|---|---|
| France | Louis XIV | Bâtiments construits 20 % plus vite |
| Angleterre | Élisabeth I | Routes 30 % moins chères |
| Espagne | Isabelle I | Butin des camps barbares doublé |
| Saint-Empire | Frédéric Barberousse | Unités produites 15 % plus vite |
| Rome | Auguste | Routes construites deux fois plus vite |
| Portugal | Jean II | +200 de nourriture, bois et minerai au départ |
| Russie | Pierre le Grand | Récolte 15 % plus rapide |
| Suède | Gustave II Adolphe | Unités 10 % plus rapides |

### Civilisations dirigées par l'ordinateur (bots) — étapes 1, 2 et 3 faites
- But : jouer seul ou à deux avec **4 civilisations** ; les bots s'attaquent aussi **entre eux** (étape 4).
- **`/rts-bot ajouter`** (triches) : ajoute un bot. Il prend une **nation libre au hasard** (son nom = celui de la nation, ex. « Espagne »), s'installe dans une **zone libre** à 220-320 blocs des autres villes (et à 100 blocs au moins des camps barbares), reçoit 3 villageois et les ressources de départ, et pose son **Hôtel de ville** (construit par ses villageois).
- **Économie** (le bot réfléchit toutes les 2 s) : fait des villageois jusqu'à **18** ; répartit ceux qui ne font rien : **35 % nourriture** (dans ses fermes, 2 par ferme), **20 % minerai**, le reste **bois** (ressource la plus proche à moins de 48 blocs) ; construit une **maison** quand il lui reste 2 places de population ou moins, des **fermes** au fil de la croissance (jusqu'à 4), un **entrepôt** près de la forêt la plus proche ; tout bâtiment en chantier reçoit un constructeur.
- **Développement** (étape 3) : construit dans l'ordre caserne (8 villageois), bibliothèque (10), forge et 2 tours de guet (12, après Maçonnerie), marché (14, après Commerce), 2e caserne (20), 4 tours (22) ; recherche les technologies à la suite (Maçonnerie, Sacs de récolte, Commerce, Architecture, Tir à l'arc, Métallurgie… en gardant 100 de nourriture) ; améliore Hôtel de ville (dès 14 villageois) et casernes ; fonde une **nouvelle ville** à 110-150 blocs dès 16 villageois (3 villes au plus, 8 villageois de plus par ville, chaque ville en forme). Réglages en tête de `BotServerEvents` (`WISHES`, `RESEARCH`).
- Reste à faire : **étape 4** (armée, défense, attaques, y compris entre bots et contre les barbares), **étape 5** (bouton dans le menu de départ : nombre de bots et difficulté).
- Code : dossier `bot/` (`BotServerEvents` : arrivée `addBot`/`findStart`, cerveau `think` ; `BotSaveData`).

### Villes
- On peut construire **plusieurs Hôtels de ville** (menu des ouvriers) : chacun est une **ville**, nommée d'après la **prochaine ville de sa nation** (France : Paris, Lyon, Marseille, Bordeaux… ; 12 noms par nation, puis « Paris 2 »… ; « Ville 1 », « Ville 2 »… sans nation). Le nom flotte au-dessus de l'Hôtel de ville, dans la couleur de la nation. Les noms ne resservent pas (même si une ville est détruite) ; ils repartent du début avec `/rts-reset`.
- **Distances entre villes** (centre à centre) : **100 blocs** de ses propres villes (et de celles des alliés), **180 blocs** d'une ville ennemie. Zone rouge pendant la pose d'un Hôtel de ville là où c'est trop près.
- Un Hôtel de ville n'a pas besoin d'être à 24 blocs de ses bâtiments (il fonde une nouvelle zone de construction autour de lui).
- Code : `nation/Cities.java` (noms, distances `OWN_SPACING`, `ENEMY_SPACING`), `CityServerEvents` (nom donné à la pose, `CitySaveData`), `CityClientEvents` (nom au-dessus).

### Arbre des technologies
- Pendant une recherche, un **bandeau au-dessus de la fenêtre de l'arbre** (pas sur l'écran principal) montre son icône, son nom en grand, une grande barre de progression (pourcentage) et le temps restant (« en pause » sans Bibliothèque).
- Bouton **« Technologies x/18 »** à gauche de l'écran RTS (une barre bleue montre la recherche en cours), ou bouton **« Technologies »** de la **Bibliothèque** (touche **P**) : ouvre l'arbre presque plein écran (Échap pour fermer).
- Écran **dans le style des Progrès** de Minecraft : même fenêtre, fond en planches de chêne noir, cadres (carré, arrondi, pointu selon le rang) avec une icône par technologie, traits blancs entre elles, cadre doré quand c'est découvert, assombri quand c'est verrouillé. **Au survol** : barre de titre (bleue, dorée au fur et à mesure de la recherche) et encadré avec l'état, le prix, la durée et **les images des bâtiments débloqués** (les mêmes que dans le menu de construction, avec leur nom). On **fait glisser** l'arbre à la souris. Clic = lancer la recherche ; « Annuler » (sous la fenêtre) rembourse tout.
- **Une seule recherche à la fois**, et elle n'avance que si on a une **Bibliothèque terminée** (sinon en pause). Sauvegardée avec le monde, effacée par `/rts-reset`. `warpten` accélère, `modifythephasevariance` ignore les technologies.
- Les bâtiments verrouillés sont **cachés du menu de construction** ; les niveaux 2 et 3 de la caserne et du Hôtel de ville demandent Architecture / Ingénierie.

| Recherche | Il faut d'abord | Débloque | Prix | Durée |
|---|---|---|---|---|
| Maçonnerie | — | Tour de guet, Forge | 200 bois, 100 minerai | 5 min |
| Commerce | — | Marché | 150 nourr., 150 bois | 5 min |
| Métallurgie | Maçonnerie | Golem de fer | 150 bois, 250 minerai | 6 min |
| Architecture | Maçonnerie | Caserne niv. 2, Hôtel de ville niv. 2 | 300 bois, 200 minerai | 6 min |
| Mysticisme | Commerce | Hutte de sorcière, Sanctuaire | 200 nourr., 100 bois, 150 minerai | 6 min |
| Fortifications | Métallurgie | Château | 300 bois, 400 minerai | 10 min |
| Ingénierie | Architecture + Métallurgie | Caserne niv. 3, Hôtel de ville niv. 3 | 400 bois, 400 minerai | 8 min |
| Arcanes | Mysticisme | Tour arcanique | 300 nourr., 200 bois, 300 minerai | 8 min |

**Recherches du jeu d'origine intégrées à l'arbre** (14/10/2026) : elles ne s'achètent plus dans les bâtiments ; leurs effets s'appliquent dès que la technologie est découverte. Métallurgie donne aussi la Forge de golems et débloque l'amélioration « Forgeron supérieur » ; Mysticisme débloque « Grande bibliothèque ». Les 4 améliorations de bâtiment (forgeron, bibliothèque, château, marché) restent dans leur bâtiment mais n'apparaissent qu'une fois la technologie découverte.

| Recherche | Il faut d'abord | Effet (recherche d'origine) | Prix | Durée |
|---|---|---|---|---|
| Commandement | Fortifications | Amélioration du château : drapeau de capitaine | 200 nourr., 150 bois, 150 minerai | 4 min |
| Artillerie de siège | Commandement | Pillards sur ravageurs (flèches explosives) | 400 nourr., 350 minerai | 5 min |
| Tir à l'arc | — | Milice archère | 250 nourr., 500 bois | 5 min |
| Invocations vexantes | Arcanes | Les invocateurs appellent des Vex | 500 nourr., 300 minerai | 4 min |
| Porté par le vent | Invocations vexantes | Mande-vents améliorés | 300 nourr., 150 bois, 150 minerai | 4 min 40 |
| Grand commerce | Commerce | Amélioration du marché : Grand marché | 100 nourr., 150 bois, 100 minerai | 5 min |
| Alchimie | Mysticisme | Fioles d'eau + potions persistantes étendues | 400 bois, 400 minerai | 5 min |
| Potions de soin | Alchimie | Potions de soin des sorcières | 350 bois, 350 minerai | 5 min |
| Sacs de récolte | — | Ouvriers portent 200 au lieu de 100 | 200 nourr., 200 bois | 3 min |
| Sacs à dos | Sacs de récolte | Les unités portent des objets | 100 bois, 100 minerai | 3 min |

- **Zoom** dans l'arbre : molette (de 50 % à 150 %). Fenêtre de l'arbre agrandie.

Sans recherche : Hôtel de ville, maison, ferme, entrepôt, caserne, Bibliothèque, pont.

---

## Ajouter ou modifier un bâtiment

1. En jeu (créatif, triches activées) : `/give @s minecraft:structure_block`.
2. Bloc de structure en mode **Load**, nom `reignofnether:barracks` (par exemple) → Load : le bâtiment actuel apparaît.
3. Le modifier. La **rangée du bas (y = 0) est le sol** : elle remplace l'herbe du terrain. Pour la faire apparaître au niveau du sol, mettre la **position relative** du bloc de structure à **1, -1, 0** (le bloc reste posé sur l'herbe, à côté de la zone). Les cases en **chemin de terre** (`minecraft:dirt_path`) prendront le matériau de la route reliée. Les autres blocs de sol restent tels quels.
4. Mode **Save**, nom du fichier :
   - niveau 1 : `reignofnether:barracks`
   - niveau 2 : `reignofnether:barracks_lvl2`
   - niveau 3 : `reignofnether:barracks_lvl3`
   - camps barbares : `reignofnether:barbarian_camp`, `reignofnether:barbarian_camp_lvl2`, `reignofnether:barbarian_camp_lvl3`, tente `reignofnether:barbarian_tent`, tour `reignofnether:barbarian_tower`
   - (camps : garder les bâtiments annexes à moins de ~8 blocs de large pour qu'ils tiennent entre le centre et les remparts)
5. Le fichier est dans `saves/<monde>/generated/reignofnether/structures/` : le copier dans le mod (dossiers `assets` **et** `data`), **ajouter son nom à `GROUND_ROW_STRUCTURES`**, puis reconstruire le mod.

## Commandes utiles (triches activées)

| Commande | Effet |
|---|---|
| `greedisgood 5000` (dans le chat, sans `/`) | +5000 de chaque ressource |
| `warpten` | construction et production rapides |
| `modifythephasevariance` | ignore les bâtiments requis |
| `/rts-rebuild-buildings reignofnether:barracks` | reconstruit tous les bâtiments d'un type avec leur forme actuelle |
| `/rts-road <pos A> <pos B> [path\|paved\|advanced]` | construit une route entre deux bâtiments (test) |
| `/rts-barbarians on` / `off` | active / désactive les barbares |
| `/rts-barbarians facile` / `normal` / `difficile` | choisit la difficulté des barbares |
| `/rts-barbarian-camp <pos> [1-5]` | pose un camp barbare du niveau choisi (test) |

---

## À faire

- **Avant la version finale** : retirer WorldEdit du dossier mods du profil RTS (outil de construction seulement).
- **Pour le serveur** : ne pas mettre **Distant Horizons** (ni dans le serveur, ni dans le pack des joueurs) ; je le garde seulement dans mon profil à moi.

### Routes
- [ ] Bâtiments qui **exigent une route** : aucun pour l'instant (décidé le 13/10), à revoir plus tard.
- [x] Remboursement de la partie non construite si un bâtiment relié est détruit pendant le chantier.

### Bâtiments
- [ ] Refaire tous les modèles avec leur sol en y = 0 (fait : ferme de blé, Hôtel de ville niveau 1, entrepôt, maison).
- [ ] Construire les formes des niveaux 2 et 3 (caserne, Hôtel de ville).
- [x] Tours de guet améliorables (portée et dégâts par niveau).
- [ ] Construire les formes des tours de guet niveaux 2 et 3 (`watchtower_lvl2`, `watchtower_lvl3`).

### Remparts
- [x] Blocs et bâtiments de rempart (palissade, rempart de pierre, porte en bois, herse) avec de la vie, portes qui s'ouvrent pour leurs unités ; utilisés par les camps barbares.
- [x] Remparts de joueurs : tracé en deux clics, aperçu et prix, construits par les ouvriers, portes qui s'ouvrent pour ses unités.
- [x] Tour de rempart intégrable dans les murs.

### Arbre des technologies
- [x] Zoom à la molette ; recherches d'origine intégrées (effets).
- [ ] Plus tard : encore plus de technologies.

### Barbares
- [ ] Construire mes propres formes de camps (niveaux 1 à 5), de la tente et de la tour.
- [ ] Construire les modèles des 8 nouveaux bâtiments barbares (feu de camp, réserve de butin, forge, cor d'alarme, cages, écurie, enclos à ravageurs, tente du chef) : `barbarian_campfire`, `barbarian_loot`, `barbarian_forge`, `barbarian_alarm`, `barbarian_cages`, `barbarian_stable`, `barbarian_ravager_pen`, `barbarian_chief_tent` (7 × 7 au plus).
- [x] Apparence propre, cavaliers au niveau 3, réglages de difficulté.
- [x] Camps à 5 niveaux et 8 nouveaux bâtiments (feu de camp, réserve de butin, forge, cor d'alarme, cages, écurie, enclos à ravageurs, tente du chef).

### Serveur
- [ ] Vue RTS (F12) forcée pour tous les joueurs.
- [ ] Choix de la nation à la première connexion, puis envoi dans une zone libre.
- [ ] Distance de sécurité entre les Hôtels de ville de joueurs différents.

### Terrain
- [ ] Vérifier en jeu (nouveau monde) si le relief est assez plat ; sinon baisser encore `vertical_scale`.
- [ ] **Structures neutres générées avec la carte** (demandé le 14/10/2026) :
  - [x] **Ruines avec trésors** (faites le 14/10/2026).
  - [x] **Carrières** (faites le 14/10/2026).
  - [x] **Coins de pêche** et **port** (faits le 14/10/2026, voir « Carte : pêche et troupeaux »).
  - [x] **Troupeaux sauvages** (faits le 14/10/2026).
  - [x] Icônes sur la minimap (pêche, troupeaux, carrières, ruines).
  - [ ] **Mes modèles** : plusieurs modèles de **carrières** et plusieurs de **ruines** (et de coins de pêche), choisis au hasard à la génération.
  - Autres pistes nourriture non retenues pour l'instant : prairies fertiles, bosquets de baies, vergers.

### À vérifier en jeu (codé, compilé, pas encore testé par moi)
- [ ] Amélioration des bâtiments (touche **E** dans la caserne / le Hôtel de ville).
- [ ] Routes : tracé, prix, place autour des bâtiments, passage aux niveaux pavée / avancée, sol des bâtiments qui change de matériau.
- [ ] Chantiers : durées du tableau, tours d'échafaudage qui montent, barre de progression dorée.
- [ ] Tours de guet : amélioration niveaux 2 et 3, portée et dégâts de la garnison qui augmentent.
- [ ] Tour de rempart : pose sur un mur (mur coupé autour), garnison de 2 qui tire d'en haut, sortie à côté de la tour.
- [ ] Mes remparts : boutons du menu des ouvriers, tracé et aperçu, Maj pour continuer, construction par les ouvriers, portes qui s'ouvrent pour mes unités, barbares qui les attaquent.
- [ ] Cavalier : production à la caserne, cheval qui charge, vindicateur assis dessus, nom « Cavalier » dans l'interface.
- [ ] Remparts barbares : cercle fermé, segments qui cassent, portes qui s'ouvrent pour les barbares, remplacement par la pierre au niveau 3.
- [ ] Bots (`/rts-bot ajouter`, 3 fois pour 4 civilisations) : arrivée loin des villes, Hôtel de ville, villageois produits et répartis, maisons, fermes, entrepôt.
- [ ] Villes : 2e Hôtel de ville loin du premier, nom au-dessus, refus trop près (100 / 180 blocs) et zone rouge.
- [ ] Carrières (minage, minerai qui repousse) et ruines (deviennent attaquables quand on approche, trésor) ; icônes.
- [ ] Pêche et troupeaux : bulles sur l'eau près des rives, port posé contre l'eau, pêcheurs en bateau (clic droit sur le port) qui vont aux bulles et rapportent, troupeaux qui tournent en rond, s'éloignent des villes, repoussent ; plus de bêtes seules (nouveau monde conseillé).
- [ ] Camps à 5 niveaux (`/rts-barbarian-camp ~ ~ ~ 5` pour voir le plus grand) : bâtiments qui apparaissent, remparts qui s'agrandissent, feu qui soigne, vol en raid et réserve qui rend, forge, cor d'alarme, prisonniers libérés, chef et sa défaite ; camps plus loin (130-170 blocs).
- [ ] Barbares : nouvelle apparence (milice, vindicateur, pillard, ravageur), cavaliers au niveau 3, bouton de difficulté et ses effets.
- [ ] Barbares : apparition des camps, patrouilles, défense, raids, montée de niveau, butin, bâtiments annexes, remparts (actifs par défaut, ou `/rts-barbarian-camp ~ ~ ~ 1` pour tester).
- [ ] Limite de distance (24 blocs) et zone rouge pendant le placement ; plus de villages.
- [ ] Nations : fenêtre de choix qui s'ouvre toute seule, bannières Minecraft, une nation par joueur, nom en haut de l'écran, bonus de chaque nation.
- [ ] Routes qui contournent les obstacles (eau, bâtiments, falaises, arbres).
- [ ] Chantier de route par les ouvriers (envoi automatique, aide des ouvriers proches, nouvel ordre = quitte le chantier) et aperçu du tracé/prix.
- [ ] Les unités vont plus vite sur la route et la préfèrent pour leurs trajets.
- [ ] Reconstruction automatique d'un bâtiment après modification de sa forme.
- [ ] Arbre des technologies : nouvelles recherches et leurs effets (milice archère, Vex, potions, sacs…), améliorations visibles seulement après la technologie, zoom à la molette.
- [ ] Caméra RTS (F12) : zoom à la molette seule (sauf pendant la pose d'un pont).
- [ ] Arbre des technologies : bouton à gauche, écran, recherche une à la fois, bâtiments cachés tant que non découverts, niveaux bloqués.
- [ ] Traduction française : vérifier les textes trop longs pour leurs cases.
- [ ] La commande `/rts-rebuild-buildings` n'était pas reconnue lors d'un essai : probablement triches non activées dans ce monde, cause non confirmée.

---

## Notes techniques pour reprendre (pour Claude)

- Les noms des boutons de construction utilisaient des clés de traduction inexistantes (`buildings.villagers.reignofnether.…`) : corrigé en `buildings.reignofnether.…` partout (43 endroits).

**Prochaine étape prévue** (14/10/2026) : faire tester en jeu les éclaireurs, les nuages (allure, ralentissements, avec et sans shaders), les noms de villes (maintenant toujours visibles, même à travers le relief) et les unités qui n'entrent plus dans l'eau ; puis tout ce qui a été ajouté le 14/10 — bots (`/rts-bot ajouter` ×3), noms de villes (affichage corrigé, à revérifier), pêche en bateau, troupeaux, carrières, ruines, camps barbares à 5 niveaux, routes en arc, déplacement du mod activé partout (surveiller les ralentissements), rectangles orange de Distant Horizons. Puis bots étapes 4 (armée, attaques, entre bots aussi) et 5 (bouton nombre de bots + difficulté).

**Façon de répondre** : en français, concis et direct, sans jargon ; chiffrer la taille d'une tâche en **briques** (1 = un réglage, 10 = petite fonction, 100 = gros ensemble comme les camps à 5 niveaux), jamais « gros morceau ». Le jeu doit être fermé pour installer le mod : si l'utilisateur dit « ferme et installe », arrêter Minecraft proprement (`kill -TERM` du processus java) puis copier le jar.

**Où est le code de mes ajouts** (`src/main/java/com/solegendary/reignofnether/`) :
- Niveaux de bâtiments : `building/level/BuildingLevel.java` (fiche d'un niveau : PV, portée et dégâts de la garnison `garrisonDamage`, vitesse de production ; bonus de dégâts appliqué dans `AttackerUnit.getUnitAttackDamage`), `building/level/BuildingLevelUpgrade.java` (bouton d'amélioration, réutilise la file de production) ; `Building.setLevels(...)`, `BuildingPlacement.applyLevel(...)`, action réseau `BuildingAction.CHANGE_LEVEL`. Boutons déclarés dans `building/production/ProductionItems.java` (`BARRACKS_LEVEL_2`…).
- Empreinte des structures : `BuildingPlacement.getStructureHash()` / `rebuildBlocksInWorld()`, sauvegardée dans `BuildingSaveData` (`structureHash`), comparée au chargement dans `BuildingServerEvents`.
- Fondations de secours : `BuildingServerEvents` (placement d'un bâtiment, après la boucle sur `startingBlockTypes`).
- Routes : dossier `road/` — `RoadType` (niveaux, mélanges, prix, vitesse), `RoadConnection`, `RoadPathTracer` (tracé A* qui contourne les obstacles + place autour des bâtiments), `RoadServerEvents` (réseau, paiement, sauvegarde, sol adaptable), `RoadSaveData`, `RoadServerboundPacket`. Chantier par les ouvriers : `RoadServerEvents.tickRoadWorkers` (toutes les 10 ticks ; `RoadConnection.built` = avancement, `upgradeTo` = amélioration en cours ; seuls les blocs posés comptent pour la vitesse et l'itinéraire). Aperçu : `RoadClientEvents` (tracé calculé côté joueur, réseau reçu par `RoadClientboundPacket`). Bouton : `ability/abilities/BuildRoad.java` (ajouté à tous les bâtiments dans le constructeur de `Building`, touche `hotkey10` = H). Vitesse : effet `ROAD_MOVEMENT_SPEED` dans `MobEffectRegistrar`, appliqué dans `Unit.tick`. Itinéraire : `KIND_ROAD` dans `unit/pathfinding/WalkabilityBuilder.java`, coût `PathfinderConfig.ROAD_COST`.
- Chantiers : `building/BuildTimes.java` (tableau des durées), `building/ConstructionSiteServerEvents.java` (tours d'échafaudage), `BuildingPlacement.CONSTRUCTION_TIME_MULTIPLIER` (bâtiments hors tableau), `HealthBarClientEvents.renderConstructionBars` (barre).
- Remparts : dossier `rampart/` — `RampartBuilding` (un segment = un bâtiment ; 4 sortes, blocs générés depuis les colonnes), `RampartShapes` (colonnes de chaque segment, sauvegardées dans `RampartSaveData` et envoyées aux joueurs avant les segments par `RampartShapeClientboundPacket`), `RampartServerEvents` (`placeSegment`, `removeSegment`, portes qui coulissent), `RampartGateBlock` (bloc de porte, propriété `OPEN`), `RampartTowerBuilding` (tour 3 × 3 avec garnison ; le mur est coupé par `RampartServerEvents.cutWallsUnder`). Remparts des joueurs : `RampartLine` (tracé, prix, découpe en morceaux ; commun au joueur et au serveur), `RampartClientEvents` (mode de tracé, aperçu), `RampartServerboundPacket` → `RampartServerEvents.buildPlayerRampart` ; boutons dans `RampartBuilding.getBuildButton`, touches dans `Factions`. Blocs dans `BlockRegistrar` (`PALISADE`, `PALISADE_TOP`, `STONE_RAMPART`, `WOODEN_GATE`, `IRON_GATE`), bâtiments dans `Buildings`. Les portes comptent comme passables pour le calcul des chemins (`WalkabilityBuilder`).
- Barbares : dossier `barbarian/` (`BarbarianServerEvents` : camps, garnisons, raids, butin, table `SLOTS` des bâtiments annexes et leurs rôles (`tickCampfire`, `tickForge`, `soundAlarm`, `spawnChief`, `steal`, `freePrisoners`), `findCampSpot` (distance), `WALL_RADIUS` par niveau ; `BarbarianSaveData` ; `BarbarianDifficulty` = réglages par difficulté ; `BarbarianClientEvents` = bouton ; paquets `Barbarian*boundPacket`), bâtiments `building/buildings/neutral/BarbarianCamp.java` et `BarbarianOutbuilding.java` (tente, tour ; propriétaire « Barbarians »). Point rouge : `HealthBarClientEvents.renderBarbarianMarkers`. Annexes : `ensureOutbuildings` (emplacements `SLOT_*`) ; remparts : `planWalls` / `buildWalls` (`WALL_RADIUS`, `WALL_RADIUS_VARIATION`, forme dans `wallRadius`).
- Limite de distance : `building/BuildRange.java` (rayon `RANGE`, exceptions), vérifiée dans `BuildingValidators.getPlacementValidityError`, zone rouge dans `BuildingClientEvents.drawBuildRangeZone`. Villages : tags vides `data/minecraft/tags/worldgen/biome/has_structure/village_*.json`.
- Nations : dossier `nation/` (`Nation` = liste, dirigeants, couleurs, bonus ; `NationServerEvents` = qui joue quoi, sauvegarde ; `NationClientEvents` = fenêtre de choix et nom en haut de l'écran). Bonus branchés dans `BuildingPlacement` (construction), `RoadServerEvents`/`RoadClientEvents` (routes), `BarbarianServerEvents` (butin), `ProductionItem` (production), `GatherResourcesGoal` (récolte), `ResourcesServerEvents.resetResources` (départ), `Unit.tick` (vitesse). Factions cachées dans `hud/buttons/StartButtons.java`, Survie retirée dans `ClientGameModeHelper.cycleGameMode`.
- Technologies : dossier `tech/` (`Tech` = liste, prix, durées, déblocages, position dans l'écran ; `TechServerEvents` = recherches des joueurs, sauvegarde `TechSaveData` ; `TechClientEvents` = écran et bouton à gauche). Bâtiments cachés dans `BuildingPlaceButton`, vérifié dans `BuildingValidators` ; niveaux dans `BuildingLevelUpgrade`. Bouton Bibliothèque : `OpenTechTree`.
- Éclaireur : `unit/units/villagers/ExplorerUnit.java` / `ExplorerProd.java` (icône `mobheads/explorer.png`) ; chien : `exploration/ExplorerDogClientEvents.java` + `unit/modelling/renderers/ExplorerUnitRenderer.java`. Nuages : dossier `exploration/` (`ExplorationServerEvents` = morceaux vus par joueur toutes les secondes, `ExplorationSaveData`, paquet, `ExplorationClientEvents` = dessin des nuages, réglage `TOP_ABOVE_GROUND` ; nuages posés au sol, du point le plus bas au-dessus des arbres ; pas dessinés pendant le calcul des ombres d'Oculus, `isShadowPass`). Cachés sous les nuages via `FogOfWarClientEvents.isInBrightChunk` ; minimap dans `MinimapClientEvents.updateMapTerrain` ; construction dans `BuildingValidators`.
- Travail des ouvriers après rechargement : `VillagerUnit` (sauvegarde `buildTargets`, `carriedItems`, puis `restoreWorkAfterLoad` 2 s après le chargement) ; la ferme est relue via `TargetResourcesSave.farmPos` (avant, elle était cherchée avant que les bâtiments existent, donc perdue).
- Eau : `MobilityClass.costFor` (eau infranchissable sauf unités aquatiques) ; une unité terrestre dans l'eau est remise sur la rive après 2 s (`Unit.tick`).
- Retour à l'Hôtel de ville : `hud/HomeButtonClientEvents.java`.
- Commandes : `commands/CommandsServerEvents.java` (`rts-rebuild-buildings`, `rts-road`).
- Terrain : `src/main/resources/data/minecraft/worldgen/world_preset/normal.json` (`settings` = `minecraft:overworld`).

**Habitudes de travail** :
- Compiler : `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew build` (tests compris).
- Installer : copier `build/libs/reignofnether-1.5.0.jar` dans `~/Library/Application Support/PrismLauncher/instances/RTS/.minecraft/mods/` **jeu fermé**.
- Lancer : `"/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher" --launch RTS`, puis surveiller `.../instances/RTS/.minecraft/logs/latest.log` (« Sound engine started » = prêt).
- Les structures `.nbt` vont dans **`assets/` et `data/`** `reignofnether/structures/`.
- Côté joueur (client), Minecraft ne tient à jour que les cartes de hauteur `WORLD_SURFACE` et `MOTION_BLOCKING` : ne jamais utiliser `MOTION_BLOCKING_NO_LEAVES` ou `OCEAN_FLOOR` dans du code qui tourne chez le joueur (bug « Aucun chemin possible » du 13/10).
- `lang/fr_fr.json` a des fins de ligne Windows (CRLF) : ajouter des lignes sans reformater le fichier. Après une mise à jour de l'auteur, comparer avec `en_us.json` pour traduire les nouveaux textes.
- Je ne peux pas cliquer dans la fenêtre du jeu (processus « java » non pilotable) : c'est l'utilisateur qui teste en jeu.
- Mes documents sont dans le dossier `readme/` : ce journal et `BATIMENTS.md` (liste des bâtiments avec le nom à mettre dans le bloc de structure, à tenir à jour quand un bâtiment change).
- Enregistrer sur GitHub (`git push` sur `ma-version`) quand l'utilisateur le demande, et mettre ce fichier à jour à chaque étape.

---

## Historique

| Date | Étape |
|---|---|
| 12/10/2026 | Copie du mod, branche `ma-version`, Java 17, premier test en jeu |
| 12/10/2026 | Tectonic pour le type de monde « Default » |
| 12/10/2026 | Bâtiments à niveaux (caserne, Hôtel de ville), empreinte des structures, commande de reconstruction |
| 13/10/2026 | Correction des fondations, routes (tracé, prix, vitesse, réseau, niveaux, place autour des bâtiments, préférence des unités) |
| 13/10/2026 | Routes en mélanges de blocs (gravier, terre grossière, andésite, pierre), journal `MON_PROJET.md` |
| 13/10/2026 | Routes construites par les ouvriers, aperçu du tracé et du prix |
| 13/10/2026 | Routes qui contournent les obstacles |
| 13/10/2026 | Chantiers : durées par bâtiment, tours d'échafaudage qui montent, barre de progression |
| 14/10/2026 | Bouton « Hôtel de ville » (à gauche, sous Technologies) et touche Origine : la caméra revient à l'Hôtel de ville (puis à la ville suivante) |
| 14/10/2026 | Scierie (recherche Sylviculture) : arbres qui repoussent, coupés par les villageois comme une ferme ; utilisée aussi par les bots |
| 14/10/2026 | Bots étape 3 : bâtiments dans l'ordre (caserne, bibliothèque, forge, tours, marché…), recherches à la suite, niveaux de l'Hôtel de ville et de la caserne, jusqu'à 3 villes |
| 14/10/2026 | Ruines : on les fouille avec une unité (8 s), trésor puis disparition ; nuages sombres la nuit |
| 14/10/2026 | Correction crash au lancement direct dans un monde (minimap) ; bots qui restaient bloqués (villageois coincés jamais réaffectés) ; nuages au-dessus de chaque arbre ; éclaireur monté sur son chien |
| 14/10/2026 | Ouvriers : chantiers en cours, ferme attribuée et ressources transportées gardés quand on quitte la partie |
| 14/10/2026 | Éclaireurs (villageois + chien) ; nuages sur la carte inexplorée ; noms de villes toujours visibles (shaders) ; unités terrestres interdites dans l'eau |
| 14/10/2026 | Noms de villes : affichage corrigé (lettres brouillées par l'ombre et les shaders) |
| 14/10/2026 | Bots : arrivée et économie (`/rts-bot ajouter`) |
| 14/10/2026 | Unités qui préfèrent les routes sur toutes les cartes (déplacement du mod activé partout) |
| 14/10/2026 | Routes en arc de cercle (au lieu de droites) |
| 14/10/2026 | Plusieurs Hôtels de ville = villes nommées selon la nation, distances minimales entre villes |
| 14/10/2026 | Port au bord de l'eau ; pêcheurs en bateau vers les bulles |
| 14/10/2026 | Carrières, troupeaux et coins de pêche épuisables |
| 14/10/2026 | Carrières (minerai qui repousse) et ruines à trésor sur toute la carte, avec leurs icônes |
| 14/10/2026 | Icônes des coins de pêche et des troupeaux sur la minimap |
| 14/10/2026 | Coins de pêche (bulles), port de pêche, troupeaux sauvages qui migrent et repoussent, plus de bêtes seules |
| 14/10/2026 | Butin volé et prisonniers des camps sauvegardés avec le monde |
| 14/10/2026 | Camps barbares à 5 niveaux, 8 nouveaux bâtiments avec leur rôle, remparts plus tardifs qui s'agrandissent, camps plus loin des bases |
| 14/10/2026 | Pied des remparts : de temps en temps un buisson (feuilles) ou un caillou (pierre taillée, moussue ou non) posé au sol contre le mur |
| 14/10/2026 | Remparts moins hauts : palissade 2-3, pierre 4, porte en bois 3, herse 4, tour 6 |
| 14/10/2026 | Barbares : apparence propre, cavaliers au niveau 3, difficulté (facile / normal / difficile) |
| 13/10/2026 | Barbares : camps à 3 niveaux, garnisons, raids, butin |
| 13/10/2026 | Camps barbares : bâtiments annexes et remparts (palissade puis pierre) |
| 13/10/2026 | Point rouge au-dessus des barbares ; une seule faction (villageois), Survie retirée, nations façon Civilization avec bonus |
| 14/10/2026 | Arbre des technologies : 10 recherches du jeu d'origine intégrées, améliorations de bâtiments verrouillées, zoom à la molette ; caméra RTS : la molette seule zoome, plus près et plus loin qu'avant |
| 13/10/2026 | Arbre des technologies (8 recherches dans la Bibliothèque, bâtiments et niveaux à débloquer) |
| 13/10/2026 | Dossier `readme/` (journal + liste des bâtiments) |
| 13/10/2026 | Traduction française complète, jeu en français |
| 13/10/2026 | Choix de nation ouvert automatiquement, bannières Minecraft ; noms de structure dans `BATIMENTS.md` |
| 13/10/2026 | Correction : l'aperçu des routes disait « Aucun chemin possible » partout (et zone rouge à la mauvaise hauteur) |
| 13/10/2026 | « Town Centre » renommé « Hôtel de ville » en français ; écran des technologies façon Progrès |
| 13/10/2026 | Modèles avec sol (y = 0 = sol du terrain) ; nouvelle ferme de blé (10 × 10, 29 cases) ; terre des fermes toujours humide |
| 13/10/2026 | Remparts des camps barbares en cercle irrégulier ; fenêtre des technologies corrigée |
| 13/10/2026 | Un peu plus d'arbres dans les plaines |
| 13/10/2026 | Remparts en segments attaquables (palissade, pierre, porte en bois, herse) ; camps barbares fermés avec 2 portes coulissantes |
| 13/10/2026 | Nouvel Hôtel de ville niveau 1 avec son sol |
| 13/10/2026 | Nouvel entrepôt avec son sol |
| 13/10/2026 | Bandeau de recherche en haut de l'écran ; noms des bâtiments corrigés dans les boutons ; remparts qui ne creusent plus le terrain |
| 13/10/2026 | Mod en français même avec Minecraft en anglais |
| 13/10/2026 | Correction : les camps barbares commençaient trop haut (temps compté depuis la création du monde) |
| 13/10/2026 | Nouvelle unité : le Cavalier (vindicateur sur un cheval de guerre), produit à la caserne |
| 13/10/2026 | Nouvelle maison de villageois avec son sol |
| 13/10/2026 | Mes remparts : palissade, rempart de pierre, porte en bois et herse, tracés en deux clics et construits par les ouvriers |
| 13/10/2026 | Tours de guet à 3 niveaux (PV, portée et dégâts de la garnison) |
| 13/10/2026 | Routes : remboursement de la partie non construite si un bâtiment relié est détruit |
| 13/10/2026 | Tour de rempart (3 × 3, garnison de 2), posable sur un mur |
| 13/10/2026 | Niveaux de bâtiments plus grands centrés sur le bâtiment (décision : agrandir tous les bâtiments) |
| 13/10/2026 | Détails rares au bord des routes (feuillage, barrières, lanternes) |
| 14/10/2026 | Messages d'avertissement (ressources insuffisantes, etc.) en grand au milieu de l'écran |
