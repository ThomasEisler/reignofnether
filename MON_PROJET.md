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

### Formes des bâtiments (structures)
- Un bâtiment est fait de vrais blocs, enregistrés dans un fichier `.nbt` avec un **bloc de structure** en jeu.
- **Reconstruction automatique** : chaque bâtiment retient une « empreinte » de sa forme. Si je modifie une forme et mets le mod à jour, le serveur reconstruit tout seul les bâtiments déjà posés au redémarrage.
- **Fondations** : si une forme n'a aucun des blocs de fondation prévus par le mod, toute la rangée du bas sert de fondation (corrige la défaite immédiate).
- **Sol** : dans mes structures, les cases de sol en **chemin de terre** sont « adaptables » : elles sont posées dans le matériau de la route reliée au bâtiment (ou « chemin » s'il n'y a pas de route). Les autres blocs de sol restent comme je les ai construits.

### Routes
- Sélectionner un bâtiment → bouton **« Construire une route »** (touche **H**) → cliquer un autre de mes bâtiments.
- Route de 3 blocs de large, en ligne droite, qui suit le relief. Refusée si elle traverse de l'eau ou un autre bâtiment, ou dépasse 256 blocs.
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

---

## Ajouter ou modifier un bâtiment

1. En jeu (créatif, triches activées) : `/give @s minecraft:structure_block`.
2. Bloc de structure en mode **Load**, nom `reignofnether:barracks` (par exemple) → Load : le bâtiment actuel apparaît.
3. Le modifier. **Inclure la rangée de sol** sous le bâtiment, en chemin de terre (sauf les blocs à garder tels quels).
4. Mode **Save**, nom du fichier :
   - niveau 1 : `reignofnether:barracks`
   - niveau 2 : `reignofnether:barracks_lvl2`
   - niveau 3 : `reignofnether:barracks_lvl3`
5. Le fichier est dans `saves/<monde>/generated/reignofnether/structures/` : le copier dans le mod (dossiers `assets` **et** `data`), puis reconstruire le mod.

## Commandes utiles (triches activées)

| Commande | Effet |
|---|---|
| `greedisgood 5000` (dans le chat, sans `/`) | +5000 de chaque ressource |
| `warpten` | construction et production rapides |
| `modifythephasevariance` | ignore les bâtiments requis |
| `/rts-rebuild-buildings reignofnether:barracks` | reconstruit tous les bâtiments d'un type avec leur forme actuelle |
| `/rts-road <pos A> <pos B> [path\|paved\|advanced]` | construit une route entre deux bâtiments (test) |

---

## À faire

### Routes
- [ ] Bâtiments qui **exigent une route** : lesquels, et quel effet s'ils ne sont pas reliés ? (à décider)
- [ ] Route construite petit à petit **par les ouvriers** ? (à décider)
- [ ] Aperçu du tracé et du prix avant de cliquer.
- [ ] Routes qui contournent les obstacles (pour l'instant : ligne droite).

### Bâtiments
- [ ] Construire les formes des niveaux 2 et 3 (caserne, Town Centre).
- [ ] Tours de guet améliorables (portée et dégâts par niveau).
- [ ] **Limite de distance** pour poser un bâtiment : il faut être assez proche d'un de ses bâtiments (pas de construction dans une base ennemie), sauf entrepôts et quelques autres.

### Barbares (faction IA)
- [ ] Camps barbares générés sur la carte, plusieurs niveaux, unités qui patrouillent et attaquent, récompenses à la destruction, nouveaux camps loin des bases. S'appuyer sur le mode Survie existant.

### Terrain
- [ ] Monde généré **beaucoup plus plat**.

---

## Historique

| Date | Étape |
|---|---|
| 12/10/2026 | Copie du mod, branche `ma-version`, Java 17, premier test en jeu |
| 12/10/2026 | Tectonic pour le type de monde « Default » |
| 12/10/2026 | Bâtiments à niveaux (caserne, Town Centre), empreinte des structures, commande de reconstruction |
| 13/10/2026 | Correction des fondations, routes (tracé, prix, vitesse, réseau, niveaux, place autour des bâtiments, préférence des unités) |
| 13/10/2026 | Routes en mélanges de blocs (gravier, terre grossière, andésite, pierre), journal `MON_PROJET.md` |
