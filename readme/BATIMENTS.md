# Liste des bâtiments

Bâtiments qui existent dans ma version du mod (faction **villageois**, la seule jouable).
Prix en bois / minerai. Durée de construction avec **1 ouvrier** (3 ouvriers : 60 % du temps, 5 ouvriers : 43 %).

> Tenu à jour avec le code. Dernière mise à jour : 13 octobre 2026.

## Bâtiments des joueurs

| Bâtiment | Nom dans le code | Prix | Durée | PV | Technologie requise | À quoi il sert |
|---|---|---|---|---|---|---|
| **Town Centre** (centre-ville) | `Town Centre` | 350 bois, 250 minerai | 150 s | 380 (niv. 2 : 500, niv. 3 : 650) | — | Bâtiment principal. Produit villageois, chien et chat éclaireurs. +10 population. Améliorable niv. 2 et 3 |
| **Maison** | `Villager House` | 90 bois | 40 s | 175 | — | +10 population |
| **Ferme de blé** | `Wheat Farm` | 150 bois | 30 s | 65 | — | Nourriture (les villageois y récoltent) |
| **Entrepôt** | `Stockpile` | 75 bois | 40 s | 95 | — | Dépôt des ressources. Peut être construit loin de la base |
| **Caserne** | `Barracks` | 150 bois | 60 s | 150 (niv. 2 : 225, niv. 3 : 300) | — | Vindicateurs, pillards. Améliorable niv. 2 et 3 |
| **Bibliothèque** | `Library` | 300 bois, 100 minerai | 90 s | 125 | — | **Arbre des technologies** ; enchantements des unités ; recherches (vex des évocateurs, grande bibliothèque…) |
| **Pont en chêne** | `Oak Bridge` | 100 bois | — | 350 | — | Traverser l'eau. Peut être construit loin de la base |
| **Tour de guet** | `Watchtower` | 100 bois, 75 minerai | 75 s | 240 | Maçonnerie | Garnison qui tire de loin |
| **Forge** | `Blacksmith` | 100 bois, 300 minerai | 90 s | 300 | Maçonnerie | Golem de fer ; recherches (arcs de milice, forge supérieure…) |
| **Marché** | `Town Market` | 75 bois, 75 minerai | 90 s | 300 | Commerce | Échanger des ressources |
| **Golem de fer** (bâtiment) | `Iron Golem` | 50 bois, 250 minerai | 60 s | — | Métallurgie | Se transforme en golem de fer une fois construit |
| **Hutte de sorcière** | `Witch Hut` | 200 bois, 50 minerai | 90 s | 250 | Mysticisme | Sorcières ; recherches de potions |
| **Sanctuaire de prospérité** | `Shrine of Prosperity` | 125 bois, 50 minerai | 90 s | 240 | Mysticisme | Héros : garde royal, enchanteur (et leur résurrection) |
| **Château** | `Castle` | 400 bois, 300 minerai | 180 s | 800 | Fortifications | Ravageurs ; recherches (cavalerie, drapeau) |
| **Tour arcanique** | `Arcane Tower` | 100 bois, 200 minerai | 120 s | 340 | Arcanes | Évocateurs, invocateurs de vent |

Limite : on construit à **24 blocs maximum** de ses bâtiments, sauf entrepôt et pont.

## Barbares (ordinateur, ne se construisent pas par les joueurs)

| Bâtiment | Nom dans le code | PV | Rôle |
|---|---|---|---|
| **Camp barbare** | `Barbarian Camp` | 200 / 350 / 500 selon le niveau | Centre du camp, garnison, raids, butin |
| **Tente** | `barbarian_tent` | — | Bâtiment annexe (1 à 3 selon le niveau) |
| **Tour barbare** | `barbarian_tower` | — | Bâtiment annexe dès le niveau 2 |

## Bâtiments neutres (cartes spéciales, modes de jeu)

| Bâtiment | Nom dans le code | Rôle |
|---|---|---|
| Balise | `Beacon` | 500 bois, 500 minerai ; condition de victoire dans certains modes |
| Balise à capturer | `The Beacon` | Mode « balise » |
| Fontaine de soin | `Healing Fountain` | Soigne les unités autour |
| Portail de transport neutre | `Neutral Transport Portal` | Téléporte les unités |
| Portail de l'End | `End Portal` | Carte spéciale |

## Factions cachées

Les bâtiments des **monstres** et des **piglins** existent toujours dans le code (pour suivre les mises à jour de l'auteur) mais ne sont plus jouables.

## Formes à construire par moi

| Fichier de structure | État |
|---|---|
| `barracks`, `town_centre` | ✅ faites (niveau 1) |
| `barracks_lvl2`, `barracks_lvl3`, `town_centre_lvl2`, `town_centre_lvl3` | ⚠️ provisoires |
| `barbarian_camp`, `_lvl2`, `_lvl3`, `barbarian_tent`, `barbarian_tower` | ⚠️ provisoires |

Comment faire : voir « Ajouter ou modifier un bâtiment » dans [MON_PROJET.md](MON_PROJET.md).
