# Mon projet : ma version de Reign of Nether

Version personnelle du mod RTS **Reign of Nether** (Minecraft 1.20.1, Forge), pour en faire un RTS multijoueur à ma façon.

- Dépôt d'origine : https://github.com/SoLegendary/reignofnether (branche `1.20.1-dev`)
- Ma copie : https://github.com/ThomasEisler/reignofnether
- Ma branche de travail : **`ma-version`** (ne jamais travailler directement sur `1.20.1-dev`)

> Ce fichier est tenu à jour à chaque étape. Dernière mise à jour : 13 octobre 2026.

---

## Comment ça marche chez moi

| Quoi | Où |
|---|---|
| Code du mod | `~/locallab/mc/RTS/reignofnether` |
| Java 17 | installé avec Homebrew (`openjdk@17`) |
| Profil de jeu pour tester | **Prism Launcher → profil « RTS »** (CurseForge n'est plus utilisé) |
| Mods du profil RTS | ma version de Reign of Nether, Distant Horizons, Embeddium, Oculus + shaders Complementary, Tectonic, Lithostitched |

**Fabriquer le mod** : `./gradlew build` → le fichier est dans `build/libs/reignofnether-1.5.0.jar`, à copier (jeu fermé) dans le dossier `mods` du profil RTS.

**Récupérer les nouveautés de l'auteur** : `git fetch upstream` puis, sur `ma-version`, `git merge upstream/1.20.1-dev`.

---

## Ce qui est fait

### Terrain
- Le type de monde « Default » utilise le terrain de **Tectonic** (au lieu du générateur propre à Reign of Nether), en gardant les biomes de Reign of Nether.
- Réglages du relief : `config/tectonic.json` du profil RTS (moins haut, plus de plaines, moins de forêts).

### Bâtiments à niveaux (1, 2, 3)
- Système générique : chaque niveau a sa propre forme (structure) et ses statistiques : points de vie, portée de tir de la garnison, vitesse de production, coût et durée de l'amélioration.
- L'amélioration s'achète dans le bâtiment comme une recherche (bouton bouteille d'expérience, touche **E**). Vérifiée par le serveur, synchronisée chez tous les joueurs, sauvegardée.
- Appliqué à :

| Bâtiment | Niveau 1 | Niveau 2 | Niveau 3 |
|---|---|---|---|
| **Barracks** (caserne) | 150 PV | 225 PV, production +25 % (150 bois, 100 minerai, 60 s) | 300 PV, production +50 % (250 bois, 200 minerai, 90 s) |
| **Town Centre** | 380 PV | 500 PV, production +25 % (300 bois, 200 minerai, 90 s) | 650 PV, production +50 % (500 bois, 400 minerai, 120 s) |

- Mes nouvelles formes de **niveau 1** sont en place (caserne en bois, Town Centre en campement).
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
| Town Centre | 150 s | | Château | 180 s |

  Les bâtiments des autres factions (monstres, piglins) gardent le calcul d'origine, ralenti 1,5 fois. Les réparations ne changent pas.
- **Tours d'échafaudage** dans la case du bâtiment et juste autour : elles poussent au fur et à mesure du chantier, chacune à son rythme (un peu au hasard), et montent devant les murs. Retirées quand le bâtiment est terminé ou détruit.
- Une **barre de progression dorée** flotte au-dessus de chaque chantier visible.

### Formes des bâtiments (structures)
- Un bâtiment est fait de vrais blocs, enregistrés dans un fichier `.nbt` avec un **bloc de structure** en jeu.
- **Reconstruction automatique** : chaque bâtiment retient une « empreinte » de sa forme. Si je modifie une forme et mets le mod à jour, le serveur reconstruit tout seul les bâtiments déjà posés au redémarrage.
- **Fondations** : si une forme n'a aucun des blocs de fondation prévus par le mod, toute la rangée du bas sert de fondation (corrige la défaite immédiate).
- **Sol** : dans mes structures, les cases de sol en **chemin de terre** sont « adaptables » : elles sont posées dans le matériau de la route reliée au bâtiment (ou « chemin » s'il n'y a pas de route). Les autres blocs de sol restent comme je les ai construits.

### Routes
- Sélectionner un bâtiment → bouton **« Construire une route »** (touche **H**) → cliquer un autre de mes bâtiments.
- **Aperçu** : en survolant le second bâtiment, le tracé s'affiche en surbrillance au sol et le prix apparaît à côté du curseur (en rouge si la route est impossible, avec la raison).
- **Construite par les ouvriers** : une fois payée, la route est un chantier. Les 3 ouvriers libres les plus proches (dans un rayon de 64 blocs) y vont tout seuls et posent les blocs un par un, en avançant d'un bâtiment à l'autre. Tout ouvrier libre qui passe à moins de 12 blocs du chantier vient aider. Donner un autre ordre à un ouvrier le retire du chantier. Les améliorations de route se construisent de la même façon.
- Route de 3 blocs de large qui suit le relief et **contourne les obstacles** : eau, autres bâtiments, pentes de plus d'un bloc par case ; elle évite aussi les arbres et les montées quand le détour est court. Refusée s'il n'existe aucun chemin, si les bâtiments sont à plus de 256 blocs ou si la route ferait plus de 384 blocs.
- Le **prix dépend de la longueur**.
- Recliquer sur deux bâtiments déjà reliés **améliore la route** :

| Niveau | Mélange de blocs | Prix par bloc de longueur | Vitesse des unités |
|---|---|---|---|
| Chemin | gravier + terre grossière | 1 bois | +15 % |
| Pavée | andésite + gravier | 1 bois + 1 minerai | +25 % |
| Avancée | andésite + briques de pierre + pierre | 3 minerais | +35 % |

- Le mélange est fixe pour chaque case : une route reconstruite garde le même motif.
- Une **place irrégulière** (0 à 2 blocs) entoure chaque bâtiment terminé, en matériau « chemin », et prend le matériau de la route quand elle arrive.
- Les unités **préfèrent passer par les routes** (uniquement avec le déplacement propre au mod, actif sur les cartes avec une bordure de 1280 blocs ou moins). Les routes sont reconnues par leur position : le gravier ou la pierre naturels ne comptent pas.
- Le serveur connaît le **réseau** : quels bâtiments sont reliés entre eux. Sauvegardé avec le monde.

### Barbares (première version)
- Faction contrôlée par l'ordinateur, **ennemie de tous**. **Actifs par défaut** dans tous les mondes ; `/rts-barbarians off` pour les désactiver (`on` pour les remettre), réglage sauvegardé avec le monde.
- **Camps** : rien tant qu'aucun joueur n'a de caserne terminée. **5 minutes** après la première caserne d'un joueur, un camp apparaît (à 70-100 blocs des bases), puis un autre 5 minutes plus tard, etc., jusqu'à **n + 1 camps** (n = joueurs ayant une caserne depuis 5 minutes). Un joueur qui perd toutes ses casernes ne compte plus. Un camp détruit est remplacé au plus tôt 5 minutes après. Plus la partie avance, plus les nouveaux camps commencent haut (un niveau de plus toutes les 15 minutes).
- **3 niveaux** : un camp gagne un niveau toutes les 10 minutes (200, 350 puis 500 PV).
- **Garnison** : 3 / 5 / 8 soldats selon le niveau (milice, vindicateurs, puis arbalétriers, puis parfois un ravageur). Un soldat perdu est remplacé toutes les 30 s. Ils patrouillent autour du camp et attaquent toute unité qui approche à moins de 18 blocs.
- **Raids** : à partir du niveau 2, toutes les 6 minutes, la moitié de la garnison part attaquer le bâtiment de joueur le plus proche.
- **Point rouge** au-dessus de chaque soldat barbare, pour les reconnaître d'un coup d'œil (caché dans le brouillard de guerre).
- **Butin** : détruire un camp rapporte 100 / 200 / 350 de nourriture, bois et minerai (au joueur dont une unité est la plus proche). Les survivants partent en raid.
- **Plusieurs bâtiments par camp** : le bâtiment principal au centre, et autour (à 13 blocs, à l'est, l'ouest, le nord, le sud) :

| Niveau | Bâtiments annexes |
|---|---|
| 1 | 1 tente |
| 2 | + 1 tente, + 1 tour |
| 3 | + 1 tente |

  Ils se construisent tout seuls. Si le bâtiment principal est détruit, les annexes s'effondrent avec lui.
- **Remparts** dès le niveau 2 : un carré de murs à 23 blocs du centre, avec une porte de 5 blocs au milieu de chaque côté. Niveau 2 : palissade de rondins d'épicéa (3-4 blocs de haut, irrégulière). Niveau 3 : remplacée par un mur de pierre (pierre taillée, moussue, pavé) avec créneaux. Les murs montent progressivement et restent en ruines quand le camp est détruit.
- ⚠️ Les **formes des camps sont provisoires** (copies de la caserne, du Town Centre, de la maison et de la tour de guet) : à construire par moi.

---

## Ajouter ou modifier un bâtiment

1. En jeu (créatif, triches activées) : `/give @s minecraft:structure_block`.
2. Bloc de structure en mode **Load**, nom `reignofnether:barracks` (par exemple) → Load : le bâtiment actuel apparaît.
3. Le modifier. **Inclure la rangée de sol** sous le bâtiment, en chemin de terre (sauf les blocs à garder tels quels).
4. Mode **Save**, nom du fichier :
   - niveau 1 : `reignofnether:barracks`
   - niveau 2 : `reignofnether:barracks_lvl2`
   - niveau 3 : `reignofnether:barracks_lvl3`
   - camps barbares : `reignofnether:barbarian_camp`, `reignofnether:barbarian_camp_lvl2`, `reignofnether:barbarian_camp_lvl3`, tente `reignofnether:barbarian_tent`, tour `reignofnether:barbarian_tower`
   - (camps : garder les bâtiments annexes à moins de ~8 blocs de large pour qu'ils tiennent entre le centre et les remparts)
5. Le fichier est dans `saves/<monde>/generated/reignofnether/structures/` : le copier dans le mod (dossiers `assets` **et** `data`), puis reconstruire le mod.

## Commandes utiles (triches activées)

| Commande | Effet |
|---|---|
| `greedisgood 5000` (dans le chat, sans `/`) | +5000 de chaque ressource |
| `warpten` | construction et production rapides |
| `modifythephasevariance` | ignore les bâtiments requis |
| `/rts-rebuild-buildings reignofnether:barracks` | reconstruit tous les bâtiments d'un type avec leur forme actuelle |
| `/rts-road <pos A> <pos B> [path\|paved\|advanced]` | construit une route entre deux bâtiments (test) |
| `/rts-barbarians on` / `off` | active / désactive les barbares |
| `/rts-barbarian-camp <pos> [1\|2\|3]` | pose un camp barbare du niveau choisi (test) |

---

## À faire

### Routes
- [ ] Bâtiments qui **exigent une route** : aucun pour l'instant (décidé le 13/10), à revoir plus tard.
- [ ] Remboursement si un bâtiment relié est détruit pendant le chantier (pour l'instant : perdu).

### Bâtiments
- [ ] Construire les formes des niveaux 2 et 3 (caserne, Town Centre).
- [ ] Tours de guet améliorables (portée et dégâts par niveau).
- [ ] **Limite de distance** pour poser un bâtiment : il faut être assez proche d'un de ses bâtiments (pas de construction dans une base ennemie), sauf entrepôts et quelques autres.

### Remparts
- [ ] Construire des **remparts** comme les routes : choisir deux points, **aperçu** du tracé et du prix, construits par les ouvriers.
- [ ] Pouvoir **intégrer une tour de garde** dans les remparts.

### Arbre des technologies (en attente de ma décision, rien de codé)
- [ ] Décidé : recherches dans la **Bibliothèque**, **une seule à la fois** par joueur, **5 min minimum**, l'arbre s'ouvre par un bouton de l'interface principale ou depuis la Bibliothèque ; les technologies **débloquent des bâtiments** et **des niveaux** de bâtiments.
- [ ] Proposition d'arbre (villageois), à valider, prix non fixés :

| Recherche | Il faut d'abord | Débloque | Durée |
|---|---|---|---|
| Maçonnerie | — | Tour de guet, Forge | 5 min |
| Commerce | — | Marché | 5 min |
| Métallurgie | Maçonnerie | Golem de fer | 6 min |
| Architecture | Maçonnerie | Caserne niv. 2, Town Centre niv. 2 | 6 min |
| Mysticisme | Commerce | Hutte de sorcière, Sanctuaire | 6 min |
| Fortifications | Métallurgie | Château | 10 min |
| Ingénierie | Architecture + Métallurgie | Caserne niv. 3, Town Centre niv. 3 | 8 min |
| Arcanes | Mysticisme | Tour arcanique | 8 min |

  Sans recherche : Town Centre, maison, ferme, entrepôt, caserne, Bibliothèque.
- [ ] Écran prévu : cases reliées par des traits (comme l'écran des Progrès), glisser/zoomer, infobulle (prix, durée, effet), clic = lancer la recherche.

### Barbares
- [ ] Construire mes propres formes de camps (niveaux 1, 2, 3), de la tente et de la tour.
- [ ] Plus tard : d'autres bâtiments dans les camps, unités barbares à eux (apparence propre), réglages de difficulté.

### Terrain
- [ ] Monde généré **beaucoup plus plat**.

### À vérifier en jeu (codé, compilé, pas encore testé par moi)
- [ ] Amélioration des bâtiments (touche **E** dans la caserne / le Town Centre).
- [ ] Routes : tracé, prix, place autour des bâtiments, passage aux niveaux pavée / avancée, sol des bâtiments qui change de matériau.
- [ ] Chantiers : durées du tableau, tours d'échafaudage qui montent, barre de progression dorée.
- [ ] Barbares : apparition des camps, patrouilles, défense, raids, montée de niveau, butin, bâtiments annexes, remparts (actifs par défaut, ou `/rts-barbarian-camp ~ ~ ~ 1` pour tester).
- [ ] Routes qui contournent les obstacles (eau, bâtiments, falaises, arbres).
- [ ] Chantier de route par les ouvriers (envoi automatique, aide des ouvriers proches, nouvel ordre = quitte le chantier) et aperçu du tracé/prix.
- [ ] Les unités vont plus vite sur la route et la préfèrent pour leurs trajets.
- [ ] Reconstruction automatique d'un bâtiment après modification de sa forme.
- [ ] La commande `/rts-rebuild-buildings` n'était pas reconnue lors d'un essai : probablement triches non activées dans ce monde, cause non confirmée.

---

## Notes techniques pour reprendre (pour Claude)

**Prochaine étape prévue** : faire tester le chantier de route et l'aperçu, puis le reste de la liste « À faire » (limite de distance, terrain plat, remparts, arbre des technologies, formes niveaux 2/3 et camps barbares). Je préfère des réponses courtes, en français, sans jargon.

**Où est le code de mes ajouts** (`src/main/java/com/solegendary/reignofnether/`) :
- Niveaux de bâtiments : `building/level/BuildingLevel.java` (fiche d'un niveau), `building/level/BuildingLevelUpgrade.java` (bouton d'amélioration, réutilise la file de production) ; `Building.setLevels(...)`, `BuildingPlacement.applyLevel(...)`, action réseau `BuildingAction.CHANGE_LEVEL`. Boutons déclarés dans `building/production/ProductionItems.java` (`BARRACKS_LEVEL_2`…).
- Empreinte des structures : `BuildingPlacement.getStructureHash()` / `rebuildBlocksInWorld()`, sauvegardée dans `BuildingSaveData` (`structureHash`), comparée au chargement dans `BuildingServerEvents`.
- Fondations de secours : `BuildingServerEvents` (placement d'un bâtiment, après la boucle sur `startingBlockTypes`).
- Routes : dossier `road/` — `RoadType` (niveaux, mélanges, prix, vitesse), `RoadConnection`, `RoadPathTracer` (tracé A* qui contourne les obstacles + place autour des bâtiments), `RoadServerEvents` (réseau, paiement, sauvegarde, sol adaptable), `RoadSaveData`, `RoadServerboundPacket`. Chantier par les ouvriers : `RoadServerEvents.tickRoadWorkers` (toutes les 10 ticks ; `RoadConnection.built` = avancement, `upgradeTo` = amélioration en cours ; seuls les blocs posés comptent pour la vitesse et l'itinéraire). Aperçu : `RoadClientEvents` (tracé calculé côté joueur, réseau reçu par `RoadClientboundPacket`). Bouton : `ability/abilities/BuildRoad.java` (ajouté à tous les bâtiments dans le constructeur de `Building`, touche `hotkey10` = H). Vitesse : effet `ROAD_MOVEMENT_SPEED` dans `MobEffectRegistrar`, appliqué dans `Unit.tick`. Itinéraire : `KIND_ROAD` dans `unit/pathfinding/WalkabilityBuilder.java`, coût `PathfinderConfig.ROAD_COST`.
- Chantiers : `building/BuildTimes.java` (tableau des durées), `building/ConstructionSiteServerEvents.java` (tours d'échafaudage), `BuildingPlacement.CONSTRUCTION_TIME_MULTIPLIER` (bâtiments hors tableau), `HealthBarClientEvents.renderConstructionBars` (barre).
- Barbares : dossier `barbarian/` (`BarbarianServerEvents` : camps, garnisons, raids, butin ; `BarbarianSaveData`), bâtiments `building/buildings/neutral/BarbarianCamp.java` et `BarbarianOutbuilding.java` (tente, tour ; propriétaire « Barbarians »). Point rouge : `HealthBarClientEvents.renderBarbarianMarkers`. Annexes : `ensureOutbuildings` (emplacements `SLOT_*`) ; remparts : `planWalls` / `buildWalls` (`WALL_RADIUS`).
- Commandes : `commands/CommandsServerEvents.java` (`rts-rebuild-buildings`, `rts-road`).
- Terrain : `src/main/resources/data/minecraft/worldgen/world_preset/normal.json` (`settings` = `minecraft:overworld`).

**Habitudes de travail** :
- Compiler : `export JAVA_HOME=/opt/homebrew/opt/openjdk@17/libexec/openjdk.jdk/Contents/Home && ./gradlew build` (tests compris).
- Installer : copier `build/libs/reignofnether-1.5.0.jar` dans `~/Library/Application Support/PrismLauncher/instances/RTS/.minecraft/mods/` **jeu fermé**.
- Lancer : `"/Applications/Prism Launcher.app/Contents/MacOS/prismlauncher" --launch RTS`, puis surveiller `.../instances/RTS/.minecraft/logs/latest.log` (« Sound engine started » = prêt).
- Les structures `.nbt` vont dans **`assets/` et `data/`** `reignofnether/structures/`.
- `lang/fr_fr.json` a des fins de ligne Windows (CRLF) : ajouter des lignes sans reformater le fichier.
- Je ne peux pas cliquer dans la fenêtre du jeu (processus « java » non pilotable) : c'est l'utilisateur qui teste en jeu.
- Enregistrer sur GitHub (`git push` sur `ma-version`) quand l'utilisateur le demande, et mettre ce fichier à jour à chaque étape.

---

## Historique

| Date | Étape |
|---|---|
| 12/10/2026 | Copie du mod, branche `ma-version`, Java 17, premier test en jeu |
| 12/10/2026 | Tectonic pour le type de monde « Default » |
| 12/10/2026 | Bâtiments à niveaux (caserne, Town Centre), empreinte des structures, commande de reconstruction |
| 13/10/2026 | Correction des fondations, routes (tracé, prix, vitesse, réseau, niveaux, place autour des bâtiments, préférence des unités) |
| 13/10/2026 | Routes en mélanges de blocs (gravier, terre grossière, andésite, pierre), journal `MON_PROJET.md` |
| 13/10/2026 | Routes construites par les ouvriers, aperçu du tracé et du prix |
| 13/10/2026 | Routes qui contournent les obstacles |
| 13/10/2026 | Chantiers : durées par bâtiment, tours d'échafaudage qui montent, barre de progression |
| 13/10/2026 | Barbares : camps à 3 niveaux, garnisons, raids, butin |
| 13/10/2026 | Camps barbares : bâtiments annexes et remparts (palissade puis pierre) |
