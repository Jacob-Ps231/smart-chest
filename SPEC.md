# Smart Chest — spécification

Document vivant. Il décrit **ce qu'on construit** et **les décisions prises**.
Les instructions de travail permanentes sont dans `CLAUDE.md`, les pièges de la
machine et de Minecraft 26.3 dans `../SETUP-MC-MODDING-26.3.md` (et §1 et §4 de
`../SETUP-MC-MODDING-26.2.md`).

Statut : étapes 1 à 7 faites. Prochaine session : étape 8.
Dernière mise à jour : 17/10/2026.

Conventions de ce fichier :

- ✅ **Confirmé** — validé par Jérôme, ne pas changer sans le lui demander.
- 🔷 **Proposé** — choix de Claude Code, modifiable librement, à valider en passant.
- ❓ **Ouvert** — décision à prendre, listée en §9.

Toute valeur numérique de ce document est un **défaut de configuration** : elle
est ajustable dans le fichier de config (§6), sauf mention contraire.

---

## 1. Concept

Un coffre « intelligent » qui se comporte comme un coffre vanilla (même taille,
même double coffre, même entonnoir, même comparateur, **même golem de cuivre**),
mais qui possède, à côté de son inventaire, des **emplacements d'amélioration**.
Deux améliorations : plus de capacité par emplacement, et compression
automatique en blocs.

**Hors périmètre** : un inventaire de plus de 27 emplacements par bloc, de
nouveaux mobs, de la redstone propre au mod, la compression d'objets qui n'ont
pas de recette inverse, tout objet à durabilité.

---

## 2. Le coffre ✅

| Propriété | Valeur | Statut |
| --- | --- | --- |
| Inventaire | 27 emplacements, comme un coffre | ✅ |
| Emplacements d'amélioration | 2 : un pour la capacité, un pour la compression | ✅ |
| Double coffre | oui, fusion comme en vanilla | ✅ |
| Entonnoirs | insertion et extraction | ✅ |
| Comparateur | signal selon le remplissage | ✅ |
| Variantes | les 12 bois vanilla de 26.3 (chêne, épicéa, bouleau, jungle, acacia, chêne noir, palétuvier, cerisier, chêne pâle, peuplier, cramoisi, biscornu), **pas de bambou**. Noms français en jeu alignés sur vanilla : sapin, acajou, carmin, biscornu | ✅ Jérôme 17/10/2026 |
| Golem de cuivre | dépose des objets dedans (voir §3) | ✅ |

### 2.1 Double coffre ✅

Les emplacements d'amélioration du **coffre principal** s'appliquent aux 54
emplacements, et l'écran du double coffre **n'affiche que ces 2-là** ✅ (Jérôme, 17/10/2026, après l'essai de l'étape 6 : le panneau grisé de l'autre moitié ne servait à rien).

**À la fusion** ✅ : la moitié secondaire ne garde jamais d'amélioration cachée. Pour chaque emplacement, si celui de la moitié principale est vide, l'amélioration secondaire y passe ; sinon elle est éjectée au sol par la face avant de la moitié secondaire. **À la séparation** (une moitié cassée) : rien de spécial, chacune garde ce qu'elle a, la moitié cassée lâche les siennes.

Limites acceptées (relecture de l'étape 6) : en multijoueur, un joueur qui avait ouvert une moitié **seule** avant la fusion garde ce menu, et peut y remettre une amélioration, cachée et sans effet ; elle n'est pas perdue (lâchée à la casse, visible à la séparation). L'empêcher demanderait un `mayPlace` qui lise le block entity, que le client n'a pas. Une copie créative (Ctrl + clic molette) n'emporte pas les améliorations (le contenu, lui, voyage dans le composant `CONTAINER`, étape 7) : sinon, chargées après la pose, elles écraseraient celles que la fusion vient de réunir. ⚠️ Deux coffres déjà posés côte à côte (accroupi) ne fusionnent **jamais** après coup (vanilla) : la fusion n'a lieu qu'à la pose d'un coffre neuf, qui n'a pas d'amélioration (ni le drop ni la copie créative n'en portent). L'éjection ne se produit donc plus en jeu normal ; elle reste en garde-fou (commandes, datapacks). Elle tombe quelle que soit la règle `block_drops`, comme le contenu d'un coffre cassé (`Containers.dropItemStack` ; `Block.popResource`, lui, obéit à la règle).
**Moitié principale : la première du `CompoundContainer`, c'est-à-dire la moitié `ChestType.RIGHT`** ✅ (validée en jeu à l'étape 6 ; relevé au `javap` à l'étape 6 : `ChestBlock.getBlockType` rend `FIRST` pour `RIGHT`, et `DoubleBlockCombiner` passe la moitié `FIRST` en premier ; ⚠️ la spec disait `LEFT` par erreur). Ses objets occupent les **3 rangées du haut** de l'écran, et ses améliorations sont à côté. D'après `getConnectedDirection` (le partenaire de `LEFT` est à `facing.getClockWise()`), c'est la moitié de **gauche quand on regarde la face** du coffre. Elle ne dépend pas de l'ordre de pose. Pour l'étape 7, c'est aussi la moitié dont `CompoundContainer.getMaxStackSize()` lit le plafond (§4.1).

### 2.2 Où vivent les améliorations 🔷

**Hors de la liste `items`** du `BlockEntity`, dans leur propre conteneur
sauvegardé à part. Sinon le golem, les entonnoirs et le comparateur les
verraient comme des emplacements de stockage et y rangeraient des objets.
Les améliorations sont rendues par l'écran seulement, et lâchées à la casse.

Implémenté à l'étape 6 : un `SimpleContainer` de 2 emplacements dans `SmartChestBlockEntity` (0 capacité, 1 compression), dont `setChanged` remonte au block entity ; sauvegardé sous les clés `capacity_upgrade` et `compression_upgrade` (`ItemStack.OPTIONAL_CODEC`, une pièce par emplacement, donc sans le piège des 99) ; lâché par `preRemoveSideEffects` après le contenu. Chaque moitié d'un double coffre a les siens et les lâche à sa casse ; à la fusion, `SmartChestBlockEntity.setBlockState` réunit ceux de la moitié secondaire dans la principale (§2.1) : `LevelChunk.setBlockState` l'appelle quand le bloc reste le même mais change d'état, et le coffre déjà posé passe de `SINGLE` à `LEFT`/`RIGHT` par `updateShape`, une fois le nouveau coffre et son block entity en place — quel que soit l'ordre de pose, serveur seulement. Éjection par `Block.popResourceFromFace` (face `FACING`). Emplacement capacité : les 4 items `SmartChestItems.CAPACITY_UPGRADES` ; compression : `minecraft:crafter` ; une seule pièce par emplacement (`Slot.getMaxStackSize`).

---

## 3. Compatibilité golem de cuivre ✅

**Relevé au `javap` sur 26.3 (17/10/2026)**, `CopperGolemAi` et
`TransportItemsBetweenContainers` :

| Fait | Conséquence pour nous |
| --- | --- |
| Source : `BlockTags.COPPER_CHESTS`. Destination : `Blocks.CHEST` ou `Blocks.TRAPPED_CHEST`, **comparées par identité** (`state.is(Block)`), pas par tag | Notre bloc n'est **jamais** une destination tel quel. **Un mixin est inévitable.** |
| Les deux prédicats sont des `static final` privés de `CopperGolemAi`, posés dans son `<clinit>` | Le mixin enveloppe `TRANSPORT_ITEM_DESTINATION_BLOCK` avec `.or(état -> état.is(#smartchest:golem_destinations))`. Ciblage par **tag**, pas de liste en dur. |
| `shouldQueueForTarget` teste `blockEntity instanceof ChestBlockEntity` | Faire étendre `ChestBlockEntity` à notre block entity (constructeur `protected` avec `BlockEntityType` : accessible). Gagne gratuitement la file d'attente et l'animation du couvercle. |
| `getBlockEntityContainer` : `block instanceof ChestBlock` → `ChestBlock.getContainer(...)` (gère le double coffre), sinon `blockEntity instanceof Container` | Faire étendre `ChestBlock` à notre bloc. |
| `addItemsToContainer` fusionne avec `ItemStack.getMaxStackSize()` — la limite **de l'objet**, pas celle du conteneur — et écrit par `Container.setItem` | Le golem n'ira jamais au-delà du stack vanilla dans un emplacement amélioré (acceptable, c'est vanilla). Surtout : **il contourne toute logique placée dans une méthode d'insertion** → l'autocraft doit réagir à `setItem`/`setChanged`, pas à un chemin d'insertion (voir §5). |
| `matchesLeavingItemsRequirement` : `container.isEmpty() \|\| hasItemMatchingHandItem(...)`, lu en entier à l'étape 4 | Voir la règle exacte ci-dessous. |

**Règle exacte du golem** (relevée au `javap` à l'étape 4, `TransportItemsBetweenContainers`) :

- **Choix de la cible** : parmi les block entities `instanceof ChestBlockEntity`
  des chunks chargés autour de lui, dans une boîte de **32 blocs à l'horizontale
  et 8 à la verticale** (1 et 1 s'il est monté), la plus proche d'abord. Une
  cible est écartée si son prédicat de bloc ne convient pas (source si la main
  est vide, destination sinon), si elle a déjà été visitée ou jugée
  inaccessible, ou si elle est verrouillée (`isLocked`). Elle est notée visitée
  dès qu'elle est choisie ; la liste des visitées est effacée après chaque prise
  ou dépôt réussi.
- **En route** : la cible est abandonnée si le dessus la bloque
  (`ChestBlock.isChestBlockedAt`, bloc opaque **ou chat assis**) ; elle est
  marquée inaccessible si le chemin échoue ou s'il ne voit aucune de ses faces.
- **Prendre** (main vide) : un coffre de `#minecraft:copper_chests`. Arrivé
  devant, il prend s'il **n'est pas vide** : `min(16, nombre)` du **premier
  emplacement non vide**.
- **Déposer** (main pleine) : un bloc du prédicat de destination. Arrivé
  devant, il dépose si le coffre est **vide**, ou s'il contient **déjà au moins
  une pile du même objet** (`ItemStack.isSameItem` : même item, composants
  ignorés). Sinon il abandonne la cible (déjà notée visitée) et en cherche une
  autre.
- **Insertion** (`addItemsToContainer`) : emplacements dans l'ordre ; le
  **premier emplacement vide prend toute la main**, une pile du même objet
  *et* des mêmes composants (`isSameItemSameComponents`) se complète jusqu'à
  `ItemStack.getMaxStackSize()`. Une pile incomplète placée *après* un
  emplacement vide n'est donc pas complétée.
- **Double coffre** : chaque moitié est trouvée séparément (c'est un
  `ChestBlockEntity`) ; une moitié visitée, ou occupée par un autre mob, compte
  pour les deux (`ChestBlock.TYPE`, `getConnectedBlockPos`). Le conteneur est le
  `CompoundContainer` de `ChestBlock.getContainer`.
- ⚠️ **Limites du tag** : seul un bloc dont le block entity est un
  `ChestBlockEntity` peut servir (un tonneau ajouté au tag reste ignoré). Un
  coffre de cuivre ajouté au tag par datapack deviendrait source **et**
  destination : le golem reposerait sur place ce qu'il vient de prendre. Pas de
  garde-fou dans le code (le tag ne contient que nos coffres).

**Mixin (étape 4)** : `@Inject` en `TAIL` du `<clinit>` de `CopperGolemAi`, avec
`@Shadow @Final @Mutable` sur `TRANSPORT_ITEM_DESTINATION_BLOCK`, qui devient
`vanilla.or(state -> state.is(#smartchest:golem_destinations))`. Le champ n'est
lu que par `initIdleActivity()` (`getstatic` passé au constructeur de
`TransportItemsBetweenContainers`, qui le copie dans `destinationBlockType`),
appelée par `getActivities()` à la création d'un cerveau de golem : toujours
après le `<clinit>`. Coffre et coffre piégé vanilla gardent leur test intact,
évalué en premier.

Le golem ne voit que le conteneur principal (jamais les améliorations, §2.2).

Implémenté à l'étape : 4.

---

## 4. Amélioration de capacité ✅

Item d'amélioration posé dans son emplacement. Le palier fixe un **plafond par
emplacement pour tous les objets non durables**, y compris ceux qui stackent à 1
ou 16 en vanilla (potions, perles…).

| Palier | Plafond par emplacement | Item (🔷 noms) |
| --- | --- | --- |
| 0 (aucun) | stack vanilla de l'objet | — |
| 1 | 128 | `capacity_upgrade_128` (centre : lingot de fer) |
| 2 | 256 | `capacity_upgrade_256` (centre : lingot d'or) |
| 3 | 512 | `capacity_upgrade_512` (centre : diamant) |
| 4 | 999 | `capacity_upgrade_999` (centre : fragment de netherite) |

**Recette ✅ (images de Jérôme, `Craft upgrade empilement à …png`)**, même grille pour les quatre, seul le centre change :

```
lapis       observer    lapis
éclat d'améthyste   MATÉRIAU   éclat d'améthyste
redstone    piston      redstone
```

Case du haut au milieu : un **observateur** ✅ (17/10/2026). Les quatre recettes sont indépendantes : pas besoin du palier précédent. Recettes en JSON (données).

Quatre items distincts, un seul actif dans l'emplacement ✅. Le palier 64 de la
demande est le défaut sans amélioration.

**Exclus** : tout objet pour lequel `ItemStack.isDamageableItem()` est vrai
(outils, armes, armures). ✅ Plus un tag `#smartchest:no_capacity_upgrade` (données), qui contient les boîtes de shulker, les sacs et les livres signés.

### 4.1 Pièges relevés (17/10/2026)

- ⚠️ **Le compteur d'`ItemStack` est plafonné à 99 par son codec** (un
  `bipush 99` dans le `<clinit>` d'`ItemStack`). Un `ItemStack` de 999 ne
  survit donc pas à `ItemStack.CODEC` ni à `OPTIONAL_CODEC`. Le coffre doit
  sauvegarder **le compteur à part** (`{slot, item, count}`) avec une pile de
  comptage 1, et reconstruire au chargement. Côté réseau, aucun
  plafond : `OPTIONAL_STREAM_CODEC` écrit un `VarInt` (§7.4).
- `Container.getMaxStackSize(ItemStack)` existe : c'est le point d'entrée du
  plafond. `Slot.getMaxStackSize(ItemStack)` aussi : l'écran a besoin d'un
  `Slot` maison qui y renvoie le plafond du conteneur.
- ⚠️ **Double coffre** (relevé à l'étape 3) : `CompoundContainer` ne surcharge
  que `getMaxStackSize()` sans argument (il renvoie celui de la première
  moitié) ; `getMaxStackSize(ItemStack)` y est le défaut de `Container`,
  `min(getMaxStackSize(), stack.getMaxStackSize())`. Un plafond posé sur
  `getMaxStackSize(ItemStack)` du block entity est donc **ignoré** par
  `ChestBlock.getContainer` d'un double coffre, celui des entonnoirs et du
  comparateur. Étape 7 : rien à contourner — le plafond vit dans le menu et le
  comparateur, et entonnoirs et golem restent au stack vanilla (ci-dessous).
- Retirer l'amélioration alors qu'un emplacement dépasse le plafond inférieur :
  🔷 refusé (`mayPickup` faux), message dans l'écran.
- À la casse ✅ (Jérôme, 17/10/2026, étape 7) : la découpe vanilla, `Containers.dropItemStack`, qui coupe toute pile en entités de 10 à 30 objets — jamais une `ItemEntity` de 999. Des piles strictement vanilla (1 potion par entité) feraient jusqu'à 27 000 entités pour un coffre plein ; une entité de 30 potions se ramasse correctement, une par case (`Inventory.add`).
- Sortir des objets ✅ : un geste (souris ou shift-clic) sort **un stack vanilla** de l'objet : 64, 16 ou 1. Les potions sortent une à une.
- Entonnoirs et golem ✅ (Jérôme, 17/10/2026, étape 7) : **stack vanilla par emplacement**, aucun mixin. Ils lisent `ItemStack.getMaxStackSize()`, pas le conteneur, et ne touchent jamais une pile qui le dépasse (`canMergeItems` : `count <= max` ; golem : `count < max`). Seul le joueur remplit au-delà, par le menu.

**Implémentation (étape 7)** :

- **Plafond** : `SmartChestBlockEntity.maxStackSize(amélioration, pile)` = `max(palier, stack vanilla)`, stack vanilla sans amélioration, pour un objet durable ou du tag. Lu des deux côtés par les emplacements du menu, et par le comparateur. Le block entity ne surcharge **pas** `getMaxStackSize(ItemStack)` : seul le comparateur le lirait, et `CompoundContainer` l'ignore.
- ⚠️ **`setItem` sans `limitSize`** : `BaseContainerBlockEntity.setItem` et `SimpleContainer.setItem` tronquent la pile à `getMaxStackSize(stack)`. `Slot.safeInsert` fait `grow` puis `set` : sans surcharge, poser un objet sur une pile de 999 la ramènerait à 64. Les plafonds sont tenus par les gestes, jamais par une troncature. Côté client, le miroir du coffre ne limite pas non plus (à l'ouverture, les emplacements du coffre arrivent avant celui de l'amélioration).
- **NBT** : `Items` vanilla pour les piles ≤ 99 ; au-delà, liste `large_items` de `{Slot, item (pile de 1, ItemStack.CODEC), count}`. Les grandes piles sont retirées du champ `items` le temps de `ChestBlockEntity.saveAdditional` (qui le lit directement), puis remises (les mêmes objets, dans un `finally`). Clé absente si vide : un coffre sans grande pile se sauve comme à l'étape 6. Au chargement, compteur jamais réduit (config baissée : rien n'est perdu). `removeComponentsFromTag` retire `large_items` avec `Items`. Avec une table de loot en attente, ni l'un ni l'autre n'est lu ni écrit, comme vanilla. **Copie créative** : vanilla emporte le contenu dans le composant `CONTAINER`, qui refuse une pile au-delà du stack de l'objet (`ItemStackTemplate.validate`) ; `collectImplicitComponents` y met un stack vanilla de chaque pile.
- **Réseau** : rien. `OPTIONAL_STREAM_CODEC` écrit le compteur en `VarInt`, `HashedStack` (paquet de clic) aussi. ⚠️ La config n'est **pas** synchronisée : chaque côté lit son `smartchest.json` (§6). Des paliers différents entre client et serveur dédié faussent la prédiction des clics (corrigée par le serveur, sans perte), l'infobulle et le décompte d'un glisser.
- **Comparateur** : `SmartChestBlock.getAnalogOutputSignal` (combinateur comme le menu) → `redstoneSignal(conteneur, amélioration de la moitié principale)`, formule vanilla, chaque emplacement compté plein au plus.
- **Menu** : les emplacements du coffre de `ChestMenu` (`addChestGrid`, privé) sont remplacés en place par des `ChestSlot` (plafond, `remove` limité au stack vanilla). `quickMoveStack` : un stack vanilla par shift-clic vers le joueur ; vers le coffre, fusion maison dans les piles identiques (`moveItemStackTo` ne fusionne que les objets `isStackable`), puis les vides. `clicked` : sur une pile au-delà du stack vanilla, PICKUP avec un autre objet porté refusé (vanilla échangerait la pile entière vers le curseur), SWAP vers une case vide = un stack vanilla, case occupée refusé, Ctrl+Q = un seul stack vanilla jeté (vanilla boucle tant que la pile reste : 999 entités pour 999 potions). Changer l'amélioration de capacité prévient aussi le conteneur du coffre, donc les comparateurs des deux moitiés. Retrait : `mayPickup` de l'emplacement de capacité, avec le plafond de l'amélioration portée si le clic est un PICKUP qui l'échange (999 → 512 permis si tout tient dans 512), sinon le stack vanilla. Infobulle rouge dans l'écran (`container.smartchest.capacity_locked`).

Implémenté à l'étape : 7.

---

## 5. Amélioration de compression ✅

**L'amélioration est un `minecraft:crafter` vanilla** posé dans l'emplacement dédié ✅ : pas d'item custom. L'emplacement n'accepte que lui (`Slot.mayPlace`), et il y reste.

Quand un objet **ne tient plus dans un seul emplacement**, le coffre le compresse
en bloc.

**Règle (🔷 formalisation)** : soit `S` le plafond d'un emplacement pour cet
objet (stack vanilla, ou palier de capacité) et `N` le nombre d'objets de la
recette de compression. Quand le **total** de l'objet dans le coffre dépasse
`S` et vaut au moins `N`, le coffre retire `N` objets et insère 1 bloc,
et recommence tant que le total dépasse `S`.
Exemple : 64 émeraudes + 1 = 65 > 64 → 9 retirées, 1 bloc d'émeraude, il reste
56 émeraudes.

Le **total** et non « un emplacement plein » : le golem et les entonnoirs
posent le 65ᵉ objet dans un deuxième emplacement.

**Quelles recettes** : calculées à partir du `RecipeManager`, jamais écrites en
dur. Une recette de compression est valide si elle a **un seul ingrédient
répété `N` fois** (2×2 ou 3×3, ou sans forme) et **une sortie**, **et** s'il
existe la recette inverse (un seul ingrédient, la sortie ci-dessus, produisant
`N` fois l'objet de départ). Donc :

| Objet | Résultat |
| --- | --- |
| Émeraude, lingot de fer/d'or/de cuivre, diamant, redstone, lapis, charbon… | compressé |
| Pépite → lingot → bloc | en cascade, à chaque dépassement |
| Quartz | **jamais** : le bloc de quartz n'a pas de recette inverse |
| Objet sans recette de compression | rien |

Déclenchement : 🔷 **réactif**. `setChanged()` du block entity marque un drapeau
et le ticker du serveur traite au tick suivant (pas de récursion dans
`setItem`, pas de scan par tick). Aucun traitement côté client.

**Compatibilité avec d'autres mods** (ex. les caisses de Farmer's Delight) : automatique si la recette est une recette vanilla (`crafting_shaped`/`crafting_shapeless`) avec son inverse. Un type de recette maison est ignoré. À tester à l'étape 8 avec le mod installé.

**Plusieurs recettes pour le même objet** ✅ : la première trouvée. Rare en vanilla (aucun cas connu : à confirmer par un scan à l'étape 8, qui liste les ambiguïtés dans le log) ; avec des mods, l'ordre dépend du chargement, d'où le tag `#smartchest:no_compression` pour trancher.

Si le bloc obtenu ne tient pas dans le coffre, 🔷 **rien n'est retiré** (pas de
perte). Tag `#smartchest:no_compression` pour exclure une recette (données).

Implémenté à l'étape : 8.

---

## 6. Configuration 🔷

Format et rechargement : 🔷 JSON dans `config/smartchest.json`, lu au démarrage
(`FabricLoader.getInstance().getConfigDir()`), redémarrage requis. Classe
`SmartChestConfig`, lue par `SmartChestMod.onInitialize`, valeurs par
`SmartChestConfig.get()`.

| Clé | Défaut | Règle |
| --- | --- | --- |
| `capacity.tiers` | `[128, 256, 512, 999]` | 4 entiers **strictement** croissants 🔷, chacun de 2 à 999 |
| `compression.enabled` | `true` | booléen |

- Le fichier est **écrit seulement s'il manque**, avec toutes les clés et une clé
  `_comment` (le JSON n'a pas de commentaires ; le lecteur l'ignore).
- Une clé absente prend son défaut (`optionalFieldOf`).
- Fichier invalide (JSON cassé, valeur hors bornes, paliers non croissants, pas
  quatre paliers) : avertissement au log avec la raison, **tout** le fichier est
  remplacé par les défauts en mémoire 🔷 (pas de mélange partiel), et le fichier
  n'est **jamais réécrit**.
- ⚠️ Rien n'est synchronisé entre serveur et client : en multijoueur, `capacity.tiers` doit être identique des deux côtés (§4.1).
- Le log du démarrage donne le chemin et les valeurs retenues :
  `Config …smartchest.json: capacity tiers [...], compression enabled ...`.

---

## 7. Décisions techniques

### 7.1 Identité

| Élément | Valeur | Statut |
| --- | --- | --- |
| Mod id | `smartchest` | 🔷 |
| Nom affiché du mod | Smart Chest | ✅ |
| Package racine | `re.jerome.smartchest` | 🔷 |
| `group` Gradle | `re.jerome` | ✅ |
| Version | `0.1.0+26.3` | ✅ |
| Versions du jeu | 26.3 uniquement | ✅ |
| Licence | **MIT**, `Copyright (c) 2026 Jerome` | 🔷 |

### 7.2 Versions ✅

Relevées le 17/10/2026 sur le maven Fabric (mémo 26.3 §1) :

```properties
minecraft_version=26.3
loader_version=0.19.5
loom_version=1.17.21
fabric_api_version=0.163.0+26.3
```

Gradle `9.6.0`, JDK `25`, aucune mappings. Loom **1.17**, pas 1.18.
`0.19.5` est le seul loader marqué stable pour 26.3.

### 7.3 Architecture 🔷

- `SmartChestBlock extends ChestBlock`, `SmartChestBlockEntity extends
  ChestBlockEntity` : double coffre, couvercle, golem et entonnoirs à
  l'identique. Vérifier le constructeur de `ChestBlock` en 26.3 (supplier du
  `BlockEntityType` ; plus de `codec()` depuis 26.3).
- Améliorations dans un conteneur à part (§2.2) ; `MenuType` maison et un écran
  client ; `Slot` maison pour le plafond (§4.1).
- **Menu (étape 6)** : `SmartChestMenu extends ChestMenu`, parce que le compteur
  d'ouverture de `ChestBlockEntity` (`isOwnContainer`) ne reconnaît qu'un
  `ChestMenu` sur son propre conteneur et referme le couvercle sur tout autre
  menu. Emplacements : les rangées du coffre, l'inventaire du joueur (ceux de
  `ChestMenu`), puis les 2 améliorations (celles de la moitié principale pour un
  double coffre). `quickMoveStack` réécrit :
  du coffre ou d'une amélioration vers le joueur ; du joueur vers l'emplacement
  d'amélioration s'il le prend, sinon vers le coffre. Deux `MenuType` simples
  (`smart_chest`, `double_smart_chest`), comme `GENERIC_9x3`/`9x6`. Le double
  coffre passe par `SmartChestBlock.getMenuProvider`, avec la copie du
  combinateur privé de `ChestBlock` (verrou et nom compris ; nom par défaut
  « Grand coffre intelligent »).
- **Écran** : `SmartChestScreen`, dessiné comme `ContainerScreen`, plus un panneau
  de 2 emplacements à droite (x 178, 32 × 50) à côté des rangées 1-2, simple ou
  double. `imageWidth` = 210 pour que
  le panneau compte comme l'intérieur de l'écran. Fond, sprites et icônes
  d'emplacement vide par `tools/GenerateUpgrades.java` (méthode `sprite` à
  remplacer pour un autre dessin) ; il écrit aussi item, modèle et recette des 4
  améliorations. `GenerateWoods` ne contrôle plus que les fichiers de coffres
  dans les dossiers partagés.
- **Un seul mixin** : `CopperGolemAi` (§3). Le reste passe par l'héritage et
  les surcharges, plus un access widener pour l'item en main (ci-dessous).
- Variantes de bois : un bloc par bois, **générés par script** (modèles,
  blockstates, textures, recettes, tags) depuis une liste. Deux listes, une
  par côté : `SmartChestBlocks.ALL` (bois et planches) et `WOODS` dans
  `tools/GenerateWoods.java` (bois et nom français) ; le script vérifie
  qu'elles concordent. Ajouter un bois : une ligne dans chacune, relancer le
  script.
- Données avant Java : recettes, tags (`golem_destinations`,
  `no_capacity_upgrade`, `no_compression`) en JSON.
- **Rendu** : `SmartChestRenderer extends ChestRenderer` (étape 3) avec un
  modèle maison, `SmartChestLayers` : couvercle et caisse du coffre vanilla,
  loquet fait des cubes de `SmartChestLatch`, dans la partie `lock` que
  `ChestModel` fait tourner avec le couvercle. Couches enregistrées par
  `ModelLayerRegistry` (Fabric). Sur le double coffre, chaque moitié porte sa
  moitié du loquet, centrée sur la jonction.
- **Textures d'entité en 128×128** pour un `LayerDefinition` déclaré 64×64 : les
  UV sont divisés par la taille déclarée, une unité du modèle vaut 2 px.
  Dessinées entièrement par `tools/GenerateWoods.java` (méthode `texture`) :
  planches du bois agrandies avec veinage, cadre fer biseauté, équerres de
  cuivre, bande de jointure à rivets. Disposition des faces commentée dans le
  script.
- **Loquet généré depuis `tools/latch_front.png`** (10×11, notice
  `tools/latch_front.txt`) : pixel opaque = loquet, transparent = vide ; les
  cinq cuivres de la palette du script sont en relief (1 unité), les autres
  couleurs en creux (½ unité). Le script fusionne les pixels en cubes et écrit
  `SmartChestLatch.java` (classe générée, compilée avec le mod) ; il peint la
  face avant avec le dessin, les autres faces avec la couleur de bord assombrie.
- **Item en main et inventaire** : type de modèle spécial `smartchest:chest`
  (`SmartChestItemRenderer`), le `ChestSpecialRenderer` vanilla avec notre
  couche simple. Vanilla prend la sienne en dur (`ChestRenderer.LAYERS`) et
  Fabric n'a pas d'API pour ces types : enregistrement dans
  `SpecialModelRenderers.ID_MAPPER`, privé, ouvert par
  `smartchest.accesswidener`.

### 7.4 À vérifier avant d'écrire du code

- [x] Constructeur de `ChestBlock` en 26.3 : `protected (Supplier<BlockEntityType<?
      extends ChestBlockEntity>>, SoundEvent open, SoundEvent close, Properties)`.
      `ChestBlock.getContainer` d'un double coffre : `CompoundContainer`, dont la
      limite de pile ignore `getMaxStackSize(ItemStack)` (§4.1). Étape 3.
- [x] `HopperBlockEntity.addItem` : `ItemStack.getMaxStackSize()`. `tryMoveInItem`
      fusionne jusqu'au stack de l'objet, `canMergeItems` exige `count <= max`,
      `isFullContainer` compare au stack de l'objet ; l'extraction prend 1 objet
      (`removeItem(slot, 1)`). Étape 7.
- [x] `AbstractContainerMenu.getRedstoneSignalFromContainer` : Σ count /
      `Container.getMaxStackSize(ItemStack)`, puis `Mth.lerpDiscrete(f, 0, 15)` =
      `floor(f × 14) + (f > 0)`, **non borné** : 999 potions dans un double coffre
      donneraient 260. Surchargé au niveau du bloc. Étape 7.
- [x] `ItemStack.OPTIONAL_STREAM_CODEC` : `writeVarInt(count)` / `readVarInt`, sans
      plafond ; `ClientboundContainerSetSlot` et `SetContent` l'utilisent ;
      `HashedStack` (clic) écrit aussi un `VAR_INT`. Seuls les codecs « validés »
      (création) comparent au stack de l'objet. Étape 7.
- [x] `AbstractContainerMenu.doClick` / `quickMoveStack` : où se lit la limite
      de fusion (`Slot.getMaxStackSize(ItemStack)` ou `ItemStack`) ? Étape 6 :
      `moveItemStackTo`, `safeInsert` (PICKUP) et SWAP lisent
      `Slot.getMaxStackSize(ItemStack)` ; ⚠️ pour l'étape 7, QUICK_CRAFT (glisser)
      prend `min(ItemStack.getMaxStackSize(), Slot.getMaxStackSize(stack))` et
      PICKUP_ALL (double-clic) plafonne la pile portée à
      `ItemStack.getMaxStackSize()`. Le refus de retrait passera par `mayPickup`,
      que PICKUP, QUICK_MOVE, SWAP, THROW et PICKUP_ALL testent tous. Le
      double-clic ramasse aussi dans les emplacements d'amélioration
      (`canTakeItemForPickAll` vrai par défaut), comme le combustible d'un four.
      Étape 7 : ⚠️ PICKUP avec un autre objet porté **échange** (la pile entière du
      slot passe au curseur si l'objet porté tient dans le slot), SWAP met la pile
      entière dans la case (`Inventory.setItem`) ; la boucle QUICK_MOVE rappelle
      `quickMoveStack` tant qu'il rend une pile du même objet ; `moveItemStackTo`
      ne fusionne que si `isStackable()`.
- [x] `matchesLeavingItemsRequirement` du golem, lu en entier : coffre vide ou même objet déjà présent (§3). Étape 4.
- [x] `MenuType` : **`MenuType` simple**, un par taille. Son constructeur et
      `MenuScreens.register` sont privés en 26.3, mais ouverts par Fabric API
      (`fabric-transitive-access-wideners-v1`). L'équivalent Fabric s'appelle
      désormais `ExtendedMenuType` (`fabric-menu-api-v1`) ; inutile : le client
      n'a besoin que de la taille, donnée par le type. Étape 6.
- [ ] Format exact des recettes `crafting_shaped`/`shapeless` en 26.3 (les
      recettes sont un registre rechargeable depuis 26.3) et API du
      `RecipeManager` côté serveur.
- [x] Mixin `<clinit>` sur un `static final` privé : `@Mutable @Shadow`, puis
      export du bytecode (`-Dmixin.debug.export=true`) pour lire l'injection.
      Étape 4 : dans `run/.mixin.out`, le champ a perdu `final` et le handler
      est appelé après le second `putstatic`, juste avant le `return`.

---

## 8. Plan d'implémentation 🔷

Chaque étape se termine par un build vert et, quand c'est visible, un test en
jeu. Sous-agent de vérification avant chaque commit dans les catégories
obligatoires de `CLAUDE.md`.

| # | Étape | Contenu | Validé quand | État |
| --- | --- | --- | --- | --- |
| 1 | Squelette | wrapper Gradle, `fabric.mod.json`, licence, `.gitattributes`, build vide | le jeu se lance avec le mod listé, sans crash | ✅ faite |
| 2 | Configuration | `smartchest.json`, lecture au démarrage, valeurs par défaut | le fichier est généré et une valeur modifiée est lue (log) | ✅ faite |
| 3 | Coffre de base | `SmartChestBlock`/`BlockEntity` (chêne seul), double coffre, recette, onglet créatif, modèle et texture provisoires, contenu lâché à la casse | je le pose, le remplis, le fusionne en double, relance le monde : tout est là. Entonnoir et comparateur fonctionnent | ✅ faite |
| 4 | Golem de cuivre | mixin `CopperGolemAi`, tag `golem_destinations` | un golem prend dans un coffre de cuivre et dépose dans mon coffre, à plusieurs reprises, double coffre compris | ✅ faite |
| 5 | Variantes de bois | script de génération, tous les bois | chaque bois se pose, se fusionne avec le même bois seulement, golem compatible | ✅ faite |
| 5b | Texture finale | textures 128×128 dessinées par script : bois des planches de la variante avec veinage, cadre en fer sombre biseauté, petites équerres de cuivre avec vert-de-gris, bande de jointure à rivets ; modèle maison (`SmartChestLayers`) dont le loquet est généré depuis `tools/latch_front.png` (cuivre nuancé en relief, pierre redstone en creux) ; même modèle pour l'item. D'après les concepts de Jérôme (images non suivies, hors dépôt : Modrinth 6.2a) | les 12 bois portent la texture finale, en simple et en double coffre, en main et posés | ✅ faite |
| 6 | Emplacements d'amélioration | conteneur à part, `MenuType`, écran, `Slot` maison, NBT, lâchés à la casse ; les 4 items de capacité (recettes, sprites provisoires, sans effet) | l'écran montre 27 + 2 emplacements ; un objet y reste après relance ; golem et entonnoir ne s'y servent pas ; double coffre : seuls ceux de la moitié principale, réunis à la fusion ; casser lâche tout | ✅ faite |
| 7 | Amélioration de capacité | 4 items, plafonds, sauvegarde du compteur hors codec, réseau, entonnoir, comparateur | 999 potions dans un emplacement, relance du monde, tout est là ; retirer l'amélioration refusé si dépassement | ✅ faite |
| 8 | Compression | table de recettes, drapeau + ticker, cascade, `no_compression` | 64 émeraudes + 1 → un bloc ; quartz ne bouge jamais ; plein → rien n'est perdu | |
| 9 | Release | README (page Modrinth), version, tag, jar dans `_Publiés/` | feu vert de Jérôme | |

Le « Validé quand » est ce que Jérôme joue avant toute relecture et tout
commit. Il décrit un résultat visible en jeu, pas un état du code.

Relecture par sous-agent obligatoire : étapes 4 (mixin), 7 (persistance,
réseau, arithmétique), 8 (arithmétique, persistance).

---

## 9. Questions ouvertes ❓

| # | Question | Impact |
| --- | --- | --- |
| 1 | ~~Liste des bois~~ — 17/10/2026 : les 11, sans bambou. | §2 |
| 2 | ~~Recettes~~ — 17/10/2026 : toutes fixées. Recette de l'amélioration de capacité reçue en images. Observateur confirmé en haut au milieu (17/10/2026). Centre du 999 : fragment de netherite ✅. **Recette du coffre ✅** : 8 planches du bois de la variante autour d'un lingot de cuivre (une recette par bois). Question close. | §2, §4 |
| 3 | ~~Textures~~ — 17/10/2026 : texture dessinée par script d'après des croquis ; loquet généré depuis `tools/latch_front.png`, palette cuivre en relief ; aucune image d'IA dans le dépôt (les concepts restent hors dépôt, ignorés par `.gitignore`). Le bois vient des textures de planches du jar : zone grise Mojang à garder en tête avant publication. | §7.3, étape 5b |
| 4 | ~~Moitié principale~~ — 17/10/2026 : `LEFT`, 🔷 à confirmer en jeu. Étape 6 : en fait `ChestType.RIGHT`, la première du `CompoundContainer` (javap), à gauche vue de face. | §2.1 |
| 5 | ~~Exclusions~~ — 17/10/2026 : oui, shulker, sacs, livres signés. | §4 |
| 6 | ~~Sortie d'un emplacement~~ — 17/10/2026 : un stack vanilla par geste. | §4.1 |
| 7 | ~~Recette ambiguë~~ — 17/10/2026 : la première trouvée. | §5 |
| 8 | ~~Peuplier~~ — 17/10/2026 : ajouté, 12ᵉ variante (✅ Jérôme). Le jar 26.3 contient un bois **peuplier** (`poplar_chest_boat`, `textures/entity/chest_boat/poplar.png`), absent de la liste des 11 (§2). Ajouter une 12ᵉ variante ? Relevé à l'étape 3. C'est un bois complet en 26.3 (`Blocks.POPLAR_PLANKS`, étagère, pancarte). | §2 |

---

## 10. Journal des décisions

| Date | Décision |
| --- | --- |
| 17/10/2026 | Concept validé en deux séries de questions. Compteur 999, 4 items distincts, double coffre sur les emplacements du coffre principal, 27 + 2 emplacements, seuil de compression = dépassement du stack effectif, variantes de bois, entonnoirs, comparateur, double coffre. |
| 17/10/2026 | ⚠️ **Le golem ne dépose que dans `Blocks.CHEST` / `TRAPPED_CHEST`**, par identité de bloc, et pas dans un tag : « être un coffre » ne suffit pas, un mixin sur `CopperGolemAi` est inévitable. Relevé au `javap` sur le jar 26.3. |
| 17/10/2026 | ⚠️ **Un `ItemStack` plafonne à 99 dans son codec** : le 999 ne peut pas passer par `ItemStack.CODEC`. Sauvegarde du compteur à part. |
| 17/10/2026 | Étape 1 : squelette calqué sur le projet voisin (Master Sword 26.3). `splitEnvironmentSourceSets` gardé d'avance pour l'écran de l'étape 6. Point d'entrée `SmartChestMod` (main seulement, ni mixin ni entrée client). Pas d'icône : aucune image d'origine établie (règle Modrinth). `build` vert, `runClient` lance et logue « Smart Chest loaded ». |
| 17/10/2026 | Étape 2 : `SmartChestConfig`, deux codecs sur la même liste de champs (lecture `optionalFieldOf`, écriture `fieldOf`), comme Master Sword. **Différence voulue** : Master Sword réécrit son fichier normalisé à chaque chargement, ce qui écrase un fichier invalide par les défauts ; ici le fichier n'est écrit que s'il manque. `result()` plutôt que `resultOrPartial()` : un résultat partiel mêlerait valeurs du joueur et défauts sans que le log dise lesquels. Paliers en liste, taille et ordre contrôlés dans un `validate`. ⚠️ Pas `listOf(4, 4)` : DFU écarte d'abord l'élément hors bornes, puis le contrôle de taille masque l'erreur (`1000` donnait « List is too short: 3 »). Cas testés sous `runServer` : absent (généré), partiel, non croissant, hors bornes, trois paliers, JSON cassé — fichier jamais modifié. Validé en jeu par Jérôme : palier 1 passé à 100, relancé, le log affiche `[100, 256, 512, 999]`. Pas de relecture par sous-agent : hors catégories obligatoires de `CLAUDE.md`. Signatures DFU 10.0.21, Gson 2.14.0, `StrictJsonParser.parse(Reader)` et `FabricLoader.getConfigDir()` lues au `javap`. ⚠️ La borne 999 n'est pas celle du codec d'`ItemStack` (qui s'arrête à 99, §4.1) : c'est un choix du design. |
| 17/10/2026 | Étape 3 : `SmartChestBlock extends ChestBlock`, `SmartChestBlockEntity extends ChestBlockEntity`, un seul `BlockEntityType` pour tous les bois, bois passé en paramètre (`SmartChestBlocks.register("oak")`), id `oak_smart_chest`. Rien de surchargé côté sauvegarde ni casse : `items`, NBT et `BaseContainerBlockEntity.preRemoveSideEffects` (qui lâche le contenu) sont hérités. Fusion : `ChestBlock.chestCanConnectTo` est `state.is(this)` (identité du bloc) → jamais avec un coffre vanilla, ni avec un autre bois à l'étape 5, sans rien écrire. Seules surcharges : `newBlockEntity` (vanilla y crée un `ChestBlockEntity` en dur) et `getDefaultName` (« Coffre intelligent » ; un double coffre affiche encore « Grand coffre », repris par l'écran de l'étape 6). ⚠️ **`ChestRenderer` choisit sa texture dans un enum fermé** (`ChestRenderState.ChestMaterialType`, `Sheets.chooseSprite`) : pas de branchement possible par données. `SmartChestRenderer` étend `ChestRenderer`, garde son `extractRenderState` (type, orientation, couvercle, lumière du double) et refait seulement `submit` avec `Sheets.CHEST_MAPPER.apply(smartchest:<bois>)` → `smartchest:entity/chest/<bois>[_left\|_right].png`, que l'atlas `chests` ramasse (source `directory`, tous namespaces). Item en main : **données seules**, modèle spécial `minecraft:chest` avec `"texture": "smartchest:oak"`. Textures provisoires par `tools/RecolorChest.java` (bois assombri de 30 %, loquet cuivre). Propriétés du bloc relevées sur `Blocks.CHEST` ; item : `CONTAINER` vide comme `Items.CHEST`, mais 🔷 **pas combustible** (il contient un lingot de cuivre). Onglet « Blocs fonctionnels », après le coffre. `BlockEntityTypes` (pluriel) porte désormais les types vanilla. ⚠️ `runServer` s'arrête sur l'EULA (`run/eula.txt` à `false` dans ce projet, non accepté à la place de Jérôme) : données validées par le serveur intégré de `runClient --quickPlaySingleplayer`, aucune erreur au log. ⚠️ Pour l'étape 7 : `CompoundContainer` ignore `getMaxStackSize(ItemStack)` (§4.1). Validé en jeu par Jérôme (pose, remplissage, double coffre, relance, entonnoir, comparateur, casse, craft). Pas de relecture par sous-agent : ni mixin, ni sauvegarde surchargée, ni réseau, ni arithmétique (le calcul du couvercle est recopié de `ChestRenderer.submit`). |
| 17/10/2026 | Étape 4 : un seul mixin, `CopperGolemAiMixin`, `@Inject` en `TAIL` du `<clinit>` + `@Shadow @Final @Mutable` sur `TRANSPORT_ITEM_DESTINATION_BLOCK`, enveloppé par `.or(state.is(#smartchest:golem_destinations))` ; tag en données, qui contient `oak_smart_chest`. **Pourquoi cette forme** : une seule ancre (le nom du champ), le test vanilla reste intact et passe en premier, tout lecteur du champ en profite, et un autre mod qui fait de même ajoute simplement un `or`. Écarté : `@ModifyExpressionValue` sur le `getstatic` d'`initIdleActivity` (deux ancres) et `@ModifyReturnValue` sur `lambda$static$1` (nom synthétique). Vérifié au `javap` : le champ n'est lu que par `initIdleActivity()`, via `getActivities()`, appelée par le fournisseur de cerveau de `CopperGolem` à chaque golem, donc après le `<clinit>` ; le constructeur de `TransportItemsBetweenContainers` le copie dans `destinationBlockType`, lu par `isWantedBlock`. Aucune autre identité de bloc dans le comportement : la recherche teste `instanceof ChestBlockEntity`, le double coffre passe par `ChestBlock.TYPE`/`getConnectedBlockPos`/`getContainer`. Règle de dépôt lue en entier (§3) : coffre vide, ou même item déjà présent. Le TagKey est créé dans le handler, sans toucher `SmartChestBlocks` (dont l'initialisation enregistre les blocs). ⚠️ `CopperGolemAi` ne se charge qu'à la création du premier golem : l'export du bytecode (`run/.mixin.out`) est vide tant qu'aucun golem n'existe ; une fois un golem invoqué, il montre le champ sans `final` et le handler juste avant le `return` du `<clinit>`. Validé en jeu par Jérôme (simple et double coffre, plusieurs allers-retours). Relecture Opus : aucun défaut de code ; vérifié aussi que `TransportItemsBetweenContainers` n'est construit que par `CopperGolemAi`, et que les golems rechargés reconstruisent leur cerveau par le même chemin. §3 corrigée : filtres de sélection et filtres en route séparés, chat assis, liste des visitées, double coffre. Signalé et documenté sans correctif : un coffre de cuivre ajouté au tag par datapack ferait reposer sur place ; un bloc sans `ChestBlockEntity` dans le tag est ignoré. |
| 17/10/2026 | Étape 5 : les 12 bois (voir la ligne suivante pour le peuplier). `SmartChestBlocks.ALL`, une `List` de `register(bois, planches)` ; le `BlockEntityType` unique prend `Set.copyOf(ALL)`, l'onglet créatif `insertAfter(Items.CHEST, ALL…)` (signature `insertAfter(ItemLike, ItemLike...)` lue au `javap`). **Fusion** : rien à écrire, vérifié au `javap` : `ChestBlock.chestCanConnectTo` (pose, `updateShape`, `candidatePartnerFacing`) et `DoubleBlockCombiner.combineWithNeigbour` (conteneur, rendu) testent tous deux `state.is(bloc)`, donc deux bois différents ne s'unissent jamais, même avec un seul `BlockEntityType`. **Propriétés** : celles de `Blocks.CHEST`, plus couleur de carte (`defaultMapColor()`, comme les étagères vanilla), son (`getSoundType()`) et `ignitedByLava()` **lus sur les planches du bois** ; le chêne retombe exactement sur `Blocks.CHEST`. ⚠️ **Vanilla n'est pas uniforme** : tous les blocs en bois du Nether (planches, portes, trappes, panneaux, pancartes suspendues, barrières…) ne sont pas `ignitedByLava`, **sauf les étagères** (`CRIMSON_SHELF`, `WARPED_SHELF` le sont). On suit les planches, comme le reste. Le coffre vanilla n'est pas dans `FireBlock.bootStrap` (ne brûle pas au feu), nos coffres non plus ; leur item n'est pas un combustible (étape 3), le tag `non_flammable_wood` est donc sans objet. Son : planches du bois (`NETHER_WOOD`, `CHERRY_WOOD`…), `WOOD` pour le chêne comme `Blocks.CHEST`. **Données** : `tools/GenerateWoods.java` remplace `RecolorChest.java` et écrit 112 fichiers (par bois : blockstate, modèles bloc et item, item, recette, table de loot, 3 textures ; plus les 2 tags et les 2 langues, dont les autres clés sont gardées). Il relit chaque fichier à l'octet, contrôle les 64×64, le contenu des tags et des langues, l'absence de fichier orphelin, et la concordance avec `SmartChestBlocks.ALL` (même ordre, planches du même bois). Les 6 fichiers JSON du chêne ressortent identiques à l'octet à ceux de l'étape 3. **Textures provisoires** : le procédé de l'étape 3 (loquet cuivre), plus un décalage de teinte et des rapports de saturation et de luminosité entre la couleur moyenne des planches du chêne et celle des planches du bois (rapports de 1 pour le chêne). Assombrissement de 30 % retiré après le premier essai de Jérôme (coffres trop foncés à côté de leurs planches) : le loquet en cuivre suffit à distinguer un Smart Chest d'un coffre vanilla ; la texture du chêne change donc aussi. Noms français : ceux des étagères vanilla (`fr_fr` de l'index d'assets 26.3) : « sapin », « acajou », « carmin », « biscornu », et non « épicéa », « jungle », « cramoisi ». Validé en jeu par Jérôme au second essai (12 bois, teintes éclaircies). Pas de relecture par sous-agent : ni mixin, ni sauvegarde, ni réseau, ni arithmétique de jeu (le calcul de couleur ne tourne que dans le script) ; vérifié soi-même : compte des fichiers (112) et relecture à l'octet par le script, planche contact des 12 textures, recette et traductions du peuplier, données chargées sans erreur par le serveur intégré. ⚠️ `runServer` s'arrête toujours sur l'EULA, après l'initialisation du mod (les 12 blocs s'enregistrent sur serveur dédié). La texture finale devient l'étape 5b. |
| 17/10/2026 | ⚠️ **La liste des 11 bois omettait le peuplier, ajouté en 26.3** (relevé à l'étape 3, question 8). Ajouté au premier essai de l'étape 5 : 12ᵉ variante, entre chêne pâle et carmin comme dans les onglets vanilla (`CreativeModeTabs`). Il brûle comme le chêne : planches `ignitedByLava`, présentes dans `FireBlock.bootStrap`. Nom français vanilla « en peuplier » (« Étagère en peuplier ») : le `fr_fr.json` n'est pas dans le jar, seul `en_us` y est ; il se lit dans l'index d'assets de Loom (`C:/gradle-home/caches/fabric-loom/assets`, `indexes/26.3-34.json`). |
| 17/10/2026 | Étape 5b : texture finale et loquet. **Textures** : 128×128 pour des couches déclarées 64×64 — vérifié au `javap`, `ModelPart$Polygon` divise les UV par la taille *déclarée*, donc une image deux fois plus grande s'affiche à l'identique, en plus fin (une unité = 2 px, cubes possibles en demi-unités). Tout est dessiné par la méthode `texture` de `tools/GenerateWoods.java` : planches du bois agrandies avec veinage et joints affinés, cadre fer biseauté, petites équerres de cuivre en L avec un pixel de vert-de-gris, bande de jointure en relief à rivets. Disposition des faces relevée sur `ChestModel` et recoupée sur `normal*.png` : le coffre n'est pas retourné au rendu (`modelTransformation` = rotation en Y seule), les faces latérales sont stockées tournées d'un demi-tour ; couvercle et caisse se chevauchent d'une unité, leurs rangées communes doivent être identiques (contrôlé : 6912 px). **Modèle** : `SmartChestLayers`, couvercle et caisse vanilla, loquet en plusieurs cubes dans la partie `lock` que `ChestModel` fait tourner avec le couvercle ; enregistré par `ModelLayerRegistry`, branché dans `SmartChestRenderer` à la place de `ChestRenderer.LAYERS`. **Loquet** : défini par `tools/latch_front.png` (10×11, notice `latch_front.txt`), dessiné à la main : pixel opaque = loquet, transparent = vide ; les cinq cuivres de la palette de `copper_ingot.png` en relief (1 unité, comme vanilla), les autres couleurs en creux (½ unité). Le script fusionne les pixels en cubes et écrit la classe `SmartChestLatch`, compilée avec le mod : Fabric construit les couches dans un mixin sur `LayerDefinitions.createRoots`, appelé par `EntityModelSet.vanilla()` à chaque rechargement, sans gestionnaire de ressources — une classe générée est plus simple qu'un fichier de données lu à ce moment-là. Face avant = le dessin, autres faces = couleur de bord assombrie. Loquet final 6×8 px (dessiné par Claude à la demande de Jérôme ; le dessin précédent de Jérôme est gardé dans `run/latch_front.jerome.png`, hors dépôt). **Item en main** : ⚠️ le modèle spécial `minecraft:chest` prend `ChestRenderer.LAYERS` en dur (`ChestSpecialRenderer$Unbaked.bake`) et Fabric 0.163 n'a pas d'API pour les types de modèles spéciaux : type maison `smartchest:chest` (`SmartChestItemRenderer`, le `ChestSpecialRenderer` vanilla avec notre couche), enregistré dans `SpecialModelRenderers.ID_MAPPER`, privé, ouvert par un access widener d'une ligne (`accessible`, sans `mutable`). Toujours un seul mixin. **Ce qui s'est révélé faux en route** : l'étape 3 disait « item en main : données seules » — plus vrai, il faut Java et l'access widener ; la ligne 5b du plan disait « seule la méthode `texture` change » — il a fallu un modèle maison ; le contrôle « 64×64 » du script de l'étape 5 vérifie désormais du 128 ; la texture 64×64 à la résolution vanilla a été jugée trop peu détaillée ; un premier loquet en croix de 2 unités de profondeur était trop massif, les équerres trop longues ; la règle « forme fixée par les cubes, le gabarit ne fait que la peinture » remettait la peinture générée dans les zones transparentes du dessin de Jérôme — c'est le dessin qui fixe la forme ; le relief limité à une seule couleur (`#E77C56`) empêchait d'ombrer le cuivre — remplacé par une palette de cinq nuances. ⚠️ Écraser le dessin de Jérôme a été bloqué par la protection contre les destructions irréversibles tant qu'aucune copie n'existait : sauvegarder d'abord. Validé en jeu par Jérôme (12 bois, simple et double, couvercle ouvert, main et inventaire), sur plusieurs allers-retours. Relecture Opus : ni fuite ni coût par image (12 `ChestModel` par rechargement pour l'item, comme vanilla), access widener minimal et présent dans le jar, `ID_MAPPER.put` après `ClientBootstrap`, cubes et UV du loquet recalculés depuis le PNG et conformes ; corrigé : `SmartChestLatch.java` est écrit après les textures (un échec sur le jar ne laisse plus une classe neuve avec d'anciennes textures), et un cube qui finit sur la jonction sans la traverser garde sa face (trou latent avec un dessin asymétrique) — rendu inchangé, textures identiques à l'octet. |
| 17/10/2026 | Étape 6 : emplacements d'amélioration et les 4 items de capacité (sans effet). Conteneur à part dans le block entity (§2.2), `SmartChestMenu extends ChestMenu`, deux `MenuType` simples, `SmartChestScreen` avec un panneau à droite (§7.3). ⚠️ **Ce qui s'est révélé faux** : la moitié principale n'est pas `ChestType.LEFT` mais `RIGHT` — c'est elle que `ChestBlock.getBlockType` nomme `FIRST` et que le `CompoundContainer` met en premier (§2.1) ; l'intention de la spec (première du conteneur, à gauche vue de face) tenait, seul le nom d'enum était faux. ⚠️ **Un menu de coffre qui n'est pas un `ChestMenu` referme le couvercle** : `ChestBlockEntity$1.isOwnContainer` teste `instanceof ChestMenu` puis `getContainer() == this` ou `CompoundContainer.contains(this)`, d'où l'héritage. ⚠️ `MenuType(MenuSupplier, FeatureFlagSet)` et `MenuScreens.register` sont **privés** en 26.3 : Fabric les ouvre par ses access wideners transitifs ; l'ancien `ExtendedScreenHandlerType` est devenu `ExtendedMenuType`, inutile ici. Vérifié au `javap` : `moveItemStackTo` respecte `Slot.mayPlace` et `Slot.getMaxStackSize(ItemStack)` (= `min(getMaxStackSize(), pile)`), `BlockEntity.preRemoveSideEffects` ne lâche que le `Container` du block entity, `ChestBlockEntity.swapContents` ne sert qu'à la mise à niveau des vieux mondes. Compression : `Items.CRAFTER` directement (spec ✅ : le crafter seul), pas de tag. Onglet créatif : les 4 améliorations après les coffres. Recettes par script (même grille, centre fer / or / diamant / fragment de netherite). Données chargées sans erreur par le serveur intégré ; `runServer` s'arrête toujours sur l'EULA, après l'initialisation du mod. Premier essai de Jérôme : tout validé (écran, relance, golem, entonnoir, casse, moitié principale), puis changement demandé : un seul panneau sur le double coffre, et réunion des améliorations à la fusion (§2.1) — panneau grisé et seconde paire d'emplacements retirés. Second essai (fusion, deux sens de pose, emplacement libre ou occupé, séparation) : validé par Jérôme, rien de perdu. **Relecture Opus** (persistance, réseau du menu, fusion) : shift-clic, autres clics, indices du menu, casse, chargement, désynchro, signatures : rien à signaler. Corrigé : l'amélioration éjectée était perdue avec `blockDrops` à false (`popResourceFromFace` → `Containers.dropItemStack` devant la face) ; une copie créative emportait les améliorations et les rechargeait après la fusion, en écrasant la moitié principale (`removeComponentsFromTag` les retire) ; la fusion pouvait charger un chunk de façon synchrone et ne vérifiait pas le partenaire (`isLoaded`, même bloc, type opposé). Accepté et noté en §2.1 : le menu simple d'une moitié devenue secondaire. Pour l'étape 8 : un shift-clic sur une pile de crafters en met un dans l'emplacement de compression. Essai des correctifs validé par Jérôme ; il a relevé que deux coffres posés côte à côte ne fusionnent pas après coup, ce qui rend l'éjection inaccessible en jeu normal (§2.1). Pas de second tour de relecture : les correctifs sont ceux que la relecture proposait, signatures lues au `javap` (`Containers.dropItemStack`, `ValueOutput.discard`, `Level.isLoaded`, `Vec3.relative`, `ChestType.getOpposite`). |
| 17/10/2026 | Étape 7 : effet de l'amélioration de capacité (§4.1, « Implémentation »). Choix de Jérôme au plan : entonnoirs et golem au **stack vanilla** par emplacement, sans mixin ; casse par la **découpe vanilla** de 10 à 30 objets par entité (des piles strictement vanilla feraient 27 000 entités de potions). Points ouverts de §7.4 relevés au `javap` : entonnoir et golem lisent `ItemStack.getMaxStackSize()` ; le comparateur divise par `Container.getMaxStackSize(ItemStack)` et `lerpDiscrete` n'est pas borné ; `OPTIONAL_STREAM_CODEC` écrit un `VarInt` libre. ⚠️ **Ce qui s'est révélé faux** : « contourner `CompoundContainer` avant d'écrire l'étape 7 » — inutile, le plafond vit dans les emplacements du menu et dans le comparateur, et l'automatisation reste au stack vanilla ; « réseau » au plan — rien à faire ; la spec ignorait que **`setItem` tronque** (`BaseContainerBlockEntity` et `SimpleContainer` : `limitSize`), que `moveItemStackTo` ne fusionne pas les objets non empilables, et que PICKUP (échange), SWAP et Ctrl+Q sortent la pile entière ; §2.1 disait « vanilla n'emporte pas le contenu » dans une copie créative — il l'emporte, dans le composant `CONTAINER`. Validé en jeu par Jérôme (999 potions, relance, retrait refusé avec infobulle, entonnoir, comparateur, double coffre, golem, casse) ; il note que le comparateur monte plus vite avec un palier bas (remplissage rapporté au plafond), ce qui lui va. **Relecture Opus** (persistance, réseau, arithmétique) : aucune duplication ni perte en survie ; sauvegarde sur le thread serveur (`ChunkMap.save` → `SerializableChunkData.copyOf`, seul `write` est asynchrone), donc le retrait temporaire des grandes piles est sûr ; slots remplacés en place cohérents avec `lastSlots`/`remoteSlots`. Corrigé : Ctrl+Q bouclait (999 entités) ; la copie créative perdait les grandes piles (`ItemStackTemplate.validate`), elle reçoit désormais un stack vanilla de chacune ; le comparateur de la moitié secondaire n'était pas prévenu d'un changement d'amélioration ; `large_items` était lu et écrit malgré une table de loot en attente ; un SWAP pendant un glisser ne l'annulait pas ; commentaires périmés. Documenté sans correctif : la config n'est pas synchronisée (paliers identiques requis en multijoueur, §6) ; en multijoueur, un glisser sur un seul emplacement qu'un autre joueur remplit entre-temps peut mettre plus d'un stack sur le curseur (`doClick` interne, sans perte). Essai des correctifs validé par Jérôme. Pas de second tour de relecture : les correctifs sont ceux qu'elle proposait, signatures lues au `javap` (`Player.drop`, `handleCreativeModeItemDrop`, `canDropItems`, `DataComponentMap.Builder.set`, `ItemContainerContents.fromItems`). Mémo 26.3 §10 : pièges des piles au-delà du stack vanilla. |
