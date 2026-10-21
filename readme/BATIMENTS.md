# Liste des bâtiments

Bâtiments qui existent dans ma version du mod (faction **villageois**, la seule jouable).
Prix en bois / minerai. Durée de construction avec **1 ouvrier** (3 ouvriers : 60 % du temps, 5 ouvriers : 43 %).

> Tenu à jour avec le code. Dernière mise à jour : 21 octobre 2026.

**Couleur de la nation** : la laine blanche (et les tapis et bannières blancs) des modèles est posée dans la couleur de la nation du propriétaire (barbares : noir). Pour les tentes et les drapeaux.

## Le nom à mettre dans le bloc de structure

La **rangée du bas (y = 0) est le sol** du bâtiment pour les modèles refaits : utiliser la position relative **1, -1, 0** dans le bloc de structure pour qu'elle tombe au niveau de l'herbe.

La colonne **« Nom dans le bloc de structure »** donne le nom exact à taper dans le champ « Structure Name » du bloc de structure, en mode **Load** pour charger le bâtiment actuel, puis en mode **Save** pour enregistrer ma version (avec le même nom). Le bâtiment doit garder sa **rangée de sol** en chemin de terre. Détails : « Ajouter ou modifier un bâtiment » dans [MON_PROJET.md](MON_PROJET.md).

## Bâtiments des joueurs

| Bâtiment | Nom dans le bloc de structure | Prix | Durée | PV | Technologie requise | À quoi il sert |
|---|---|---|---|---|---|---|
| **Hôtel de ville** | `reignofnether:town_centre` · niv. 2 : `reignofnether:town_centre_lvl2` · niv. 3 : `reignofnether:town_centre_lvl3` | 350 bois, 250 minerai | 150 s | 380 (niv. 2 : 500, niv. 3 : 650) | — | Bâtiment principal. Produit villageois, chien et chat éclaireurs. +10 population. Améliorable niv. 2 et 3 |
| **Maison** | `reignofnether:villager_house` | 90 bois | 40 s | 175 | — | +10 population |
| **Ferme de blé** | `reignofnether:wheat_farm` | 150 bois | 30 s | 65 | — | Nourriture : 29 cases de blé, semées et récoltées par les villageois (1 bois par case ressemée). 10 × 10 blocs, avec son sol |
| **Entrepôt** | `reignofnether:stockpile_oak` | 75 bois | 40 s | 95 | — | Dépôt des ressources. Peut être construit loin de la base |
| **Caserne** | `reignofnether:barracks` · niv. 2 : `reignofnether:barracks_lvl2` · niv. 3 : `reignofnether:barracks_lvl3` | 150 bois | 60 s | 150 (niv. 2 : 225, niv. 3 : 300) | — | Vindicateurs, pillards, **cavaliers**. Améliorable niv. 2 et 3 |
| **Bibliothèque** | `reignofnether:library` · après la recherche « Grande bibliothèque » : `reignofnether:library_grand` | 300 bois, 100 minerai | 90 s | 125 | — | **Arbre des technologies** ; enchantements des unités ; recherches (vex des évocateurs, grande bibliothèque…) |
| **Pont en chêne** | `reignofnether:bridge_oak_orthogonal` · en diagonale : `reignofnether:bridge_oak_diagonal` | 100 bois | — | 350 | — | Traverser l'eau. Peut être construit loin de la base |
| **Tour de guet** | `reignofnether:watchtower` · niv. 2 : `reignofnether:watchtower_lvl2` · niv. 3 : `reignofnether:watchtower_lvl3` | 100 bois, 75 minerai | 75 s | 240 | Maçonnerie | Garnison qui tire de loin. Améliorable niv. 2 (340 PV, portée 28, dégâts +25 %) et niv. 3 (450 PV, portée 32, dégâts +50 %, recherche Fortifications) |
| **Scierie** | `reignofnether:sawmill` (provisoire, 12 × 9 ; rangée du bas = sol ; garder un **tailleur de pierre** = la scie, d'où sort la sciure) | 250 bois, 75 minerai | 60 s | 120 | Sylviculture | Les villageois envoyés dedans (3 au plus) y travaillent : elle produit du bois sans arbres ; fumée et sciure quand elle tourne |
| **Hospice** | `reignofnether:hospice` (provisoire, 9 × 9, rangée du bas = sol) | 175 bois, 100 minerai | 90 s | 200 | Mysticisme | Soigne mes unités à 12 blocs ; forme des prêtres |
| **Moulin** | `reignofnether:mill` (provisoire, 7 × 7, rangée du bas = sol) | 150 bois, 50 minerai | 60 s | 150 | — | Fermes à moins de 24 blocs 30 % plus rapides |
| **Mine** | `reignofnether:mine` (provisoire, 9 × 9, rangée du bas = sol ; garder une cour où les villageois se tiennent ; pas de vrai minerai dans le modèle) | 250 bois, 50 minerai | 75 s | 150 | Métallurgie | À moins de 24 blocs d'une carrière ; villageois dedans = minerai sans fin |
| **Taverne** | `reignofnether:tavern` (provisoire, 11 × 9, rangée du bas = sol) | 200 bois, 100 minerai | 90 s | 200 | Commerce | Mercenaires (épéiste, tirailleur) prêts en 5 s |
| **Atelier de siège** | `reignofnether:siege_workshop` (provisoire, 11 × 11, rangée du bas = sol) | 300 bois, 150 minerai | 120 s | 220 | Ingénierie | Construit des catapultes |
| **Forge** | `reignofnether:blacksmith` · après « Forge supérieure » : `reignofnether:blacksmith_superior` | 100 bois, 300 minerai | 90 s | 300 | Maçonnerie | Golem de fer ; recherches (arcs de milice, forge supérieure…) |
| **Marché** | `reignofnether:market_villagers1` · amélioré : `reignofnether:market_villagers2` | 75 bois, 75 minerai | 90 s | 300 | Commerce | Échanger des ressources |
| **Golem de fer** (bâtiment) | `reignofnether:iron_golem` | 50 bois, 250 minerai | 60 s | — | Métallurgie | Se transforme en golem de fer une fois construit |
| **Hutte de sorcière** | `reignofnether:witch_hut` | 200 bois, 50 minerai | 90 s | 250 | Mysticisme | Sorcières ; recherches de potions |
| **Panthéon** (ex-Sanctuaire de prospérité) | `reignofnether:shrine_of_prosperity` | 125 bois, 50 minerai | 90 s | 240 | Mysticisme | Héros : garde royal, enchanteur (et leur résurrection) |
| **Château** | `reignofnether:castle` · avec drapeau (recherche) : `reignofnether:castle_with_flag` | 400 bois, 300 minerai | 180 s | 800 | Fortifications | Ravageurs ; recherches (cavalerie, drapeau) |
| **Tour arcanique** | `reignofnether:arcane_tower` | 100 bois, 200 minerai | 120 s | 340 | Arcanes | Évocateurs, invocateurs de vent |
| **Temple** | `reignofnether:temple` (provisoire, 7 × 7, rangée du bas = sol) | 150 bois, 75 minerai | 60 s | 250 | Mysticisme | Foi, fondation de la religion, prophètes, rites. Un par ville |
| **Port de pêche** | `reignofnether:port` (provisoire, posé contre l'eau) | 150 bois, 25 minerai | — | — | — | Pêcheurs en bateau |

Limite : on construit à **24 blocs maximum** de ses bâtiments, sauf entrepôt et pont.

## Barbares (ordinateur, ne se construisent pas par les joueurs)

| Bâtiment | Nom dans le bloc de structure | PV | Rôle |
|---|---|---|---|
| **Camp barbare** | `reignofnether:barbarian_camp` · niv. 2 : `reignofnether:barbarian_camp_lvl2` · niv. 3 : `reignofnether:barbarian_camp_lvl3` | 200 / 350 / 500 selon le niveau | Centre du camp, garnison, raids, butin |
| **Tente** | `reignofnether:barbarian_tent` | — | Bâtiment annexe (1 à 3 selon le niveau) |
| **Tour barbare** | `reignofnether:barbarian_tower` | — | Bâtiment annexe dès le niveau 2 |
| **Camp niv. 4 et 5** | `reignofnether:barbarian_camp_lvl4`, `reignofnether:barbarian_camp_lvl5` | — | Grands camps (provisoires : copies du niv. 3) |
| **Annexes** (7 × 7 au plus) | `reignofnether:barbarian_campfire`, `barbarian_loot`, `barbarian_forge`, `barbarian_alarm`, `barbarian_cages`, `barbarian_stable`, `barbarian_ravager_pen`, `barbarian_chief_tent` | — | Feu de camp, butin, forge, cor d'alarme, cages, écurie, enclos à ravageurs, tente du chef (provisoires) |

## Carte (posés à la génération)

| Élément | Nom dans le bloc de structure |
|---|---|
| Carrières | `reignofnether:quarry_1`, `reignofnether:quarry_2` (provisoires, 9 × 9 ; rang du bas enfoncé dans le sol) |
| Ruines | `reignofnether:ruin_1`, `reignofnether:ruin_2` (provisoires, 9 × 9 ; posées sur le sol) |

## Remparts

Pas de fichier de structure : leur forme suit le tracé et le terrain. Un seul bouton « Rempart » (palissade) dans le menu des ouvriers ; ensuite, en cliquant sur un morceau construit : tour, porte, ou amélioration de tout le mur. 3 niveaux : palissade, pierre (Maçonnerie), pierre renforcée (Fortifications). Détails et prix : « Mes remparts » dans [MON_PROJET.md](MON_PROJET.md).

## Bâtiments neutres (cartes spéciales, modes de jeu)

| Bâtiment | Nom dans le bloc de structure | Rôle |
|---|---|---|
| Balise | `reignofnether:beacon_t0` (jusqu'à `beacon_t5`) | 500 bois, 500 minerai ; condition de victoire dans certains modes |
| Balise à capturer | `reignofnether:beacon_t5` | Mode « balise » |
| Fontaine de soin | `reignofnether:healing_fountain` | Soigne les unités autour |
| Portail de transport neutre | `reignofnether:neutral_transport_portal` | Téléporte les unités |
| Portail de l'End | `reignofnether:end_portal` | Carte spéciale |

## Factions cachées

Les bâtiments des **monstres** et des **piglins** existent toujours dans le code (pour suivre les mises à jour de l'auteur) mais ne sont plus jouables.

## Formes à construire par moi

| Fichier de structure | État |
|---|---|
| `wheat_farm` | ✅ refaite avec son sol (y = 0) ; retouchée le 21/10 |
| `town_centre` | ✅ refait avec son sol (y = 0), 11 × 10 × 11 |
| `stockpile_oak` | ✅ refait avec son sol (y = 0), 7 × 5 × 7 |
| `villager_house` | ✅ refaite avec son sol (y = 0), 7 × 7 × 5 ; retouchée le 21/10 |
| `library` | ✅ refaite le 21/10 avec son sol (y = 0), 7 × 8 × 7 (`library_grand` reste l'ancienne) |
| `barracks` | ✅ faite (niveau 1), sol à refaire |
| `barracks_lvl2`, `barracks_lvl3`, `town_centre_lvl2`, `town_centre_lvl3` | ⚠️ provisoires |
| `watchtower_lvl2`, `watchtower_lvl3` | ⚠️ provisoires (copies de la tour) |
| `barbarian_camp`, `_lvl2`, `_lvl3`, `barbarian_tent`, `barbarian_tower` | ⚠️ provisoires |

Comment faire : voir « Ajouter ou modifier un bâtiment » dans [MON_PROJET.md](MON_PROJET.md).
