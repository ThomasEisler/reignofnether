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
- Réglages du relief : `config/tectonic.json` du profil RTS (moins haut, plus de plaines, moins de forêts). Le 13/10, relief **beaucoup plus plat** : `vertical_scale` 0,6 → **0,25** et `flat_terrain_skew` 0,6 → **1,0** (ancien réglage gardé dans `tectonic.json.bak-2026-10-13`). ⚠️ Ne vaut que pour les **nouveaux mondes** (ou les zones pas encore explorées).

### Bâtiments à niveaux (1, 2, 3)
- Système générique : chaque niveau a sa propre forme (structure) et ses statistiques : points de vie, portée de tir de la garnison, vitesse de production, coût et durée de l'amélioration.
- L'amélioration s'achète dans le bâtiment comme une recherche (bouton bouteille d'expérience, touche **E**). Vérifiée par le serveur, synchronisée chez tous les joueurs, sauvegardée.
- Appliqué à :

| Bâtiment | Niveau 1 | Niveau 2 | Niveau 3 |
|---|---|---|---|
| **Barracks** (caserne) | 150 PV | 225 PV, production +25 % (150 bois, 100 minerai, 60 s) | 300 PV, production +50 % (250 bois, 200 minerai, 90 s) |
| **Hôtel de ville** | 380 PV | 500 PV, production +25 % (300 bois, 200 minerai, 90 s) | 650 PV, production +50 % (500 bois, 400 minerai, 120 s) |

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
- **Modèles avec sol** : pour les modèles refaits par moi, la rangée du bas (y = 0 dans le bloc de structure) **est le sol** : elle remplace la couche d'herbe du terrain au lieu d'être posée dessus. Si le bâtiment est détruit, ce sol redevient de la terre (pas de trou). Les modèles concernés sont listés dans `BuildingBlockData.GROUND_ROW_STRUCTURES` (pour l'instant : ferme de blé) ; les autres sont encore posés sur l'herbe, comme avant.
- **Fermes** : la terre labourée reste humide toute seule, pas besoin d'eau.
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
- ⚠️ Les **formes des camps sont provisoires** (copies de la caserne, du Hôtel de ville, de la maison et de la tour de guet) : à construire par moi.

### Limite de distance pour construire
- On ne peut construire qu'à **24 blocs maximum** d'un de ses bâtiments (même en construction). Exceptions : **entrepôts** et **ponts**. Un joueur sans bâtiment construit où il veut (premier Hôtel de ville).
- **Aperçu** : pendant le placement d'un bâtiment, le sol trop loin est **teinté en rouge** autour du curseur, et le bâtiment fantôme devient rouge avec le message « Trop loin de vos bâtiments ». Vérifié aussi par le serveur.

### Français
- **Tous les textes du mod sont traduits** en français (`lang/fr_fr.json` complet : 815 textes ajoutés, 57 restés en anglais traduits), et le jeu du profil RTS est réglé en français (`options.txt` : `lang:fr_fr`). Noms choisis : Windcaller = « Mande-vent », Bogged = « Embourbé ».
- Restent en anglais : quelques messages de débogage ou d'erreur technique écrits directement dans le code.

### Monde
- **Plus de villages Minecraft** générés (seulement dans les nouvelles zones / nouveaux mondes).

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

### Arbre des technologies
- Bouton **« Technologies x/8 »** à gauche de l'écran RTS (une barre bleue montre la recherche en cours), ou bouton **« Technologies »** de la **Bibliothèque** (touche **P**) : ouvre l'arbre presque plein écran (Échap pour fermer).
- Écran **dans le style des Progrès** de Minecraft : même fenêtre, fond en planches de chêne noir, cadres (carré, arrondi, pointu selon le rang) avec une icône par technologie, traits blancs entre elles, cadre doré quand c'est découvert, assombri quand c'est verrouillé. **Au survol** : barre de titre (bleue, dorée au fur et à mesure de la recherche) et encadré avec l'état, le prix, la durée et ce que ça débloque. On **fait glisser** l'arbre à la souris. Clic = lancer la recherche ; « Annuler » (sous la fenêtre) rembourse tout.
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
| `/rts-barbarian-camp <pos> [1\|2\|3]` | pose un camp barbare du niveau choisi (test) |

---

## À faire

### Routes
- [ ] Bâtiments qui **exigent une route** : aucun pour l'instant (décidé le 13/10), à revoir plus tard.
- [ ] Remboursement si un bâtiment relié est détruit pendant le chantier (pour l'instant : perdu).

### Bâtiments
- [ ] Refaire tous les modèles avec leur sol en y = 0 (fait : ferme de blé).
- [ ] Construire les formes des niveaux 2 et 3 (caserne, Hôtel de ville).
- [ ] Tours de guet améliorables (portée et dégâts par niveau).

### Remparts
- [ ] Construire des **remparts** comme les routes : choisir deux points, **aperçu** du tracé et du prix, construits par les ouvriers.
- [ ] Pouvoir **intégrer une tour de garde** dans les remparts.

### Arbre des technologies
- [ ] Plus tard : zoom, plus de technologies, effets autres que débloquer.

### Barbares
- [ ] Construire mes propres formes de camps (niveaux 1, 2, 3), de la tente et de la tour.
- [ ] Plus tard : d'autres bâtiments dans les camps, unités barbares à eux (apparence propre), réglages de difficulté.

### Terrain
- [ ] Vérifier en jeu (nouveau monde) si le relief est assez plat ; sinon baisser encore `vertical_scale`.

### À vérifier en jeu (codé, compilé, pas encore testé par moi)
- [ ] Amélioration des bâtiments (touche **E** dans la caserne / le Hôtel de ville).
- [ ] Routes : tracé, prix, place autour des bâtiments, passage aux niveaux pavée / avancée, sol des bâtiments qui change de matériau.
- [ ] Chantiers : durées du tableau, tours d'échafaudage qui montent, barre de progression dorée.
- [ ] Barbares : apparition des camps, patrouilles, défense, raids, montée de niveau, butin, bâtiments annexes, remparts (actifs par défaut, ou `/rts-barbarian-camp ~ ~ ~ 1` pour tester).
- [ ] Limite de distance (24 blocs) et zone rouge pendant le placement ; plus de villages.
- [ ] Nations : fenêtre de choix qui s'ouvre toute seule, bannières Minecraft, une nation par joueur, nom en haut de l'écran, bonus de chaque nation.
- [ ] Routes qui contournent les obstacles (eau, bâtiments, falaises, arbres).
- [ ] Chantier de route par les ouvriers (envoi automatique, aide des ouvriers proches, nouvel ordre = quitte le chantier) et aperçu du tracé/prix.
- [ ] Les unités vont plus vite sur la route et la préfèrent pour leurs trajets.
- [ ] Reconstruction automatique d'un bâtiment après modification de sa forme.
- [ ] Arbre des technologies : bouton à gauche, écran, recherche une à la fois, bâtiments cachés tant que non découverts, niveaux bloqués.
- [ ] Traduction française : vérifier les textes trop longs pour leurs cases.
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
- Limite de distance : `building/BuildRange.java` (rayon `RANGE`, exceptions), vérifiée dans `BuildingValidators.getPlacementValidityError`, zone rouge dans `BuildingClientEvents.drawBuildRangeZone`. Villages : tags vides `data/minecraft/tags/worldgen/biome/has_structure/village_*.json`.
- Nations : dossier `nation/` (`Nation` = liste, dirigeants, couleurs, bonus ; `NationServerEvents` = qui joue quoi, sauvegarde ; `NationClientEvents` = fenêtre de choix et nom en haut de l'écran). Bonus branchés dans `BuildingPlacement` (construction), `RoadServerEvents`/`RoadClientEvents` (routes), `BarbarianServerEvents` (butin), `ProductionItem` (production), `GatherResourcesGoal` (récolte), `ResourcesServerEvents.resetResources` (départ), `Unit.tick` (vitesse). Factions cachées dans `hud/buttons/StartButtons.java`, Survie retirée dans `ClientGameModeHelper.cycleGameMode`.
- Technologies : dossier `tech/` (`Tech` = liste, prix, durées, déblocages, position dans l'écran ; `TechServerEvents` = recherches des joueurs, sauvegarde `TechSaveData` ; `TechClientEvents` = écran et bouton à gauche). Bâtiments cachés dans `BuildingPlaceButton`, vérifié dans `BuildingValidators` ; niveaux dans `BuildingLevelUpgrade`. Bouton Bibliothèque : `OpenTechTree`.
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
| 13/10/2026 | Barbares : camps à 3 niveaux, garnisons, raids, butin |
| 13/10/2026 | Camps barbares : bâtiments annexes et remparts (palissade puis pierre) |
| 13/10/2026 | Point rouge au-dessus des barbares ; une seule faction (villageois), Survie retirée, nations façon Civilization avec bonus |
| 13/10/2026 | Arbre des technologies (8 recherches dans la Bibliothèque, bâtiments et niveaux à débloquer) |
| 13/10/2026 | Dossier `readme/` (journal + liste des bâtiments) |
| 13/10/2026 | Traduction française complète, jeu en français |
| 13/10/2026 | Choix de nation ouvert automatiquement, bannières Minecraft ; noms de structure dans `BATIMENTS.md` |
| 13/10/2026 | Correction : l'aperçu des routes disait « Aucun chemin possible » partout (et zone rouge à la mauvaise hauteur) |
| 13/10/2026 | « Town Centre » renommé « Hôtel de ville » en français ; écran des technologies façon Progrès |
| 13/10/2026 | Modèles avec sol (y = 0 = sol du terrain) ; nouvelle ferme de blé (10 × 10, 29 cases) ; terre des fermes toujours humide |
