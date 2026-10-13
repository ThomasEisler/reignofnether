# Liste des bâtiments

Bâtiments qui existent dans ma version du mod (faction **villageois**, la seule jouable).
Prix en bois / minerai. Durée de construction avec **1 ouvrier** (3 ouvriers : 60 % du temps, 5 ouvriers : 43 %).

> Tenu à jour avec le code. Dernière mise à jour : 13 octobre 2026.

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
| **Caserne** | `reignofnether:barracks` · niv. 2 : `reignofnether:barracks_lvl2` · niv. 3 : `reignofnether:barracks_lvl3` | 150 bois | 60 s | 150 (niv. 2 : 225, niv. 3 : 300) | — | Vindicateurs, pillards. Améliorable niv. 2 et 3 |
| **Bibliothèque** | `reignofnether:library` · après la recherche « Grande bibliothèque » : `reignofnether:library_grand` | 300 bois, 100 minerai | 90 s | 125 | — | **Arbre des technologies** ; enchantements des unités ; recherches (vex des évocateurs, grande bibliothèque…) |
| **Pont en chêne** | `reignofnether:bridge_oak_orthogonal` · en diagonale : `reignofnether:bridge_oak_diagonal` | 100 bois | — | 350 | — | Traverser l'eau. Peut être construit loin de la base |
| **Tour de guet** | `reignofnether:watchtower` | 100 bois, 75 minerai | 75 s | 240 | Maçonnerie | Garnison qui tire de loin |
| **Forge** | `reignofnether:blacksmith` · après « Forge supérieure » : `reignofnether:blacksmith_superior` | 100 bois, 300 minerai | 90 s | 300 | Maçonnerie | Golem de fer ; recherches (arcs de milice, forge supérieure…) |
| **Marché** | `reignofnether:market_villagers1` · amélioré : `reignofnether:market_villagers2` | 75 bois, 75 minerai | 90 s | 300 | Commerce | Échanger des ressources |
| **Golem de fer** (bâtiment) | `reignofnether:iron_golem` | 50 bois, 250 minerai | 60 s | — | Métallurgie | Se transforme en golem de fer une fois construit |
| **Hutte de sorcière** | `reignofnether:witch_hut` | 200 bois, 50 minerai | 90 s | 250 | Mysticisme | Sorcières ; recherches de potions |
| **Sanctuaire de prospérité** | `reignofnether:shrine_of_prosperity` | 125 bois, 50 minerai | 90 s | 240 | Mysticisme | Héros : garde royal, enchanteur (et leur résurrection) |
| **Château** | `reignofnether:castle` · avec drapeau (recherche) : `reignofnether:castle_with_flag` | 400 bois, 300 minerai | 180 s | 800 | Fortifications | Ravageurs ; recherches (cavalerie, drapeau) |
| **Tour arcanique** | `reignofnether:arcane_tower` | 100 bois, 200 minerai | 120 s | 340 | Arcanes | Évocateurs, invocateurs de vent |

Limite : on construit à **24 blocs maximum** de ses bâtiments, sauf entrepôt et pont.

## Barbares (ordinateur, ne se construisent pas par les joueurs)

| Bâtiment | Nom dans le bloc de structure | PV | Rôle |
|---|---|---|---|
| **Camp barbare** | `reignofnether:barbarian_camp` · niv. 2 : `reignofnether:barbarian_camp_lvl2` · niv. 3 : `reignofnether:barbarian_camp_lvl3` | 200 / 350 / 500 selon le niveau | Centre du camp, garnison, raids, butin |
| **Tente** | `reignofnether:barbarian_tent` | — | Bâtiment annexe (1 à 3 selon le niveau) |
| **Tour barbare** | `reignofnether:barbarian_tower` | — | Bâtiment annexe dès le niveau 2 |

## Remparts

Faits de segments (5 colonnes de mur) qui sont chacun un bâtiment : ils ont de la vie et s'effondrent quand ils sont trop abîmés. Leur forme suit le tracé et le terrain (pas de fichier de structure).

| Bâtiment | Nom dans le code | PV par segment | Blocs | Rôle |
|---|---|---|---|---|
| **Palissade** | `palisade` | 150 | palissade + pointe de palissade (3-4 de haut) | Mur en bois |
| **Rempart de pierre** | `stone_rampart` | 300 | rempart de pierre, créneaux (5 de haut) | Mur en pierre |
| **Porte en bois** | `wooden_gate` | 220 | porte en bois de rempart (4 de haut) | Coulisse pour laisser passer ses unités |
| **Herse** | `iron_gate` | 450 | herse (5 de haut) | Coulisse pour laisser passer ses unités |

Pour l'instant, seuls les camps barbares en construisent.

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
| `wheat_farm` | ✅ refaite avec son sol (y = 0) |
| `town_centre` | ✅ refait avec son sol (y = 0), 11 × 10 × 11 |
| `stockpile_oak` | ✅ refait avec son sol (y = 0), 7 × 5 × 7 |
| `barracks` | ✅ faite (niveau 1), sol à refaire |
| `barracks_lvl2`, `barracks_lvl3`, `town_centre_lvl2`, `town_centre_lvl3` | ⚠️ provisoires |
| `barbarian_camp`, `_lvl2`, `_lvl3`, `barbarian_tent`, `barbarian_tower` | ⚠️ provisoires |

Comment faire : voir « Ajouter ou modifier un bâtiment » dans [MON_PROJET.md](MON_PROJET.md).
