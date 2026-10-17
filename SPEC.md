# Smart Chest — spécification

Document vivant. Il décrit **ce qu'on construit** et **les décisions prises**.
Les instructions de travail permanentes sont dans `CLAUDE.md`, les pièges de la
machine et de Minecraft 26.3 dans `../SETUP-MC-MODDING-26.3.md` (et §1 et §4 de
`../SETUP-MC-MODDING-26.2.md`).

Statut : étapes 1 à 4 faites. Prochaine session : étape 5.
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
| Variantes | les 11 bois vanilla (chêne, épicéa, bouleau, jungle, acacia, chêne noir, palétuvier, cerisier, chêne pâle, cramoisi, biscornu), **pas de bambou** | ✅ |
| Golem de cuivre | dépose des objets dedans (voir §3) | ✅ |

### 2.1 Double coffre ✅

Les emplacements d'amélioration du **coffre principal** s'appliquent aux 54
emplacements. Ceux de l'autre moitié sont désactivés (grisés dans l'écran).
**Moitié principale : la moitié `ChestType.LEFT`** 🔷 (c'est la première du `CompoundContainer` en vanilla ; en jeu, celle qui est à gauche quand on regarde la face du coffre). Poser un 2ᵉ coffre contre un coffre existant ne change donc pas toujours la moitié principale : à vérifier à l'étape 6. Les améliorations posées dans la moitié inactive y restent, grisées mais retirables.

### 2.2 Où vivent les améliorations 🔷

**Hors de la liste `items`** du `BlockEntity`, dans leur propre conteneur
sauvegardé à part. Sinon le golem, les entonnoirs et le comparateur les
verraient comme des emplacements de stockage et y rangeraient des objets.
Les améliorations sont rendues par l'écran seulement, et lâchées à la casse.

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
  comptage 1, et reconstruire au chargement. Même question côté réseau :
  `OPTIONAL_STREAM_CODEC` — à lire (§7.4).
- `Container.getMaxStackSize(ItemStack)` existe : c'est le point d'entrée du
  plafond. `Slot.getMaxStackSize(ItemStack)` aussi : l'écran a besoin d'un
  `Slot` maison qui y renvoie le plafond du conteneur.
- ⚠️ **Double coffre** (relevé à l'étape 3) : `CompoundContainer` ne surcharge
  que `getMaxStackSize()` sans argument (il renvoie celui de la première
  moitié) ; `getMaxStackSize(ItemStack)` y est le défaut de `Container`,
  `min(getMaxStackSize(), stack.getMaxStackSize())`. Un plafond posé sur
  `getMaxStackSize(ItemStack)` du block entity est donc **ignoré** par
  `ChestBlock.getContainer` d'un double coffre, celui des entonnoirs et du
  comparateur. À traiter avant d'écrire l'étape 7.
- Retirer l'amélioration alors qu'un emplacement dépasse le plafond inférieur :
  🔷 refusé (`mayPickup` faux), message dans l'écran.
- À la casse, le contenu est lâché en piles de **taille vanilla** (jamais une
  `ItemEntity` de 999).
- Sortir des objets ✅ : un geste (souris ou shift-clic) sort **un stack vanilla** de l'objet : 64, 16 ou 1. Les potions sortent une à une.

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
- **Un seul mixin** : `CopperGolemAi` (§3). Le reste passe par l'héritage et
  les surcharges.
- Variantes de bois : un bloc par bois, **générés par script** (modèles,
  blockstates, textures, recettes, tags) depuis une liste.
- Données avant Java : recettes, tags (`golem_destinations`,
  `no_capacity_upgrade`, `no_compression`) en JSON.

### 7.4 À vérifier avant d'écrire du code

- [x] Constructeur de `ChestBlock` en 26.3 : `protected (Supplier<BlockEntityType<?
      extends ChestBlockEntity>>, SoundEvent open, SoundEvent close, Properties)`.
      `ChestBlock.getContainer` d'un double coffre : `CompoundContainer`, dont la
      limite de pile ignore `getMaxStackSize(ItemStack)` (§4.1). Étape 3.
- [ ] `HopperBlockEntity.addItem` : consulte-t-il `Container.getMaxStackSize
      (ItemStack)` ou `ItemStack.getMaxStackSize()` ?
- [ ] `AbstractContainerMenu.getRedstoneSignalFromContainer` : même question
      pour le comparateur.
- [ ] `ItemStack.OPTIONAL_STREAM_CODEC` : le compteur est-il un `VarInt`
      plafonné, ou un entier libre ? (`ClientboundContainerSetSlot` d'un écran
      avec 999.)
- [ ] `AbstractContainerMenu.doClick` / `quickMoveStack` : où se lit la limite
      de fusion (`Slot.getMaxStackSize(ItemStack)` ou `ItemStack`) ?
- [x] `matchesLeavingItemsRequirement` du golem, lu en entier : coffre vide ou même objet déjà présent (§3). Étape 4.
- [ ] `MenuType` : choisir entre `ExtendedScreenHandlerType` (Fabric) et un
      `MenuType` simple + `ContainerData`.
- [ ] Format exact des recettes `crafting_shaped`/`shapeless` en 26.3 (les
      recettes sont un registre rechargeable depuis 26.3) et API du
      `RecipeManager` côté serveur.
- [x] Mixin `<clinit>` sur un `static final` privé : `@Mutable @Shadow`, puis
      export du bytecode (`-Dmixin.debug.export=true`) pour lire l'injection.
      Étape 4 : dans `run/.mixin.out`, le champ a perdu `final` et le handler
      est appelé après le second `putstatic`, juste avant le `return`.
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
| 5 | Variantes de bois | script de génération, tous les bois | chaque bois se pose, se fusionne avec le même bois seulement, golem compatible | |
| 6 | Emplacements d'amélioration | conteneur à part, `MenuType`, écran, `Slot` maison, NBT, lâchés à la casse | l'écran montre 27 + 2 emplacements ; un objet y reste après relance ; golem et entonnoir ne s'y servent pas | |
| 7 | Amélioration de capacité | 4 items, plafonds, sauvegarde du compteur hors codec, réseau, entonnoir, comparateur | 999 potions dans un emplacement, relance du monde, tout est là ; retirer l'amélioration refusé si dépassement | |
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
| 3 | Textures : 🔷 par script depuis les textures vanilla du coffre, avec une bordure propre au mod (métal ou couleur, à choisir sur croquis de Jérôme). Aucun outil Blockbench branché sur cette session : tout passe par `tools/`. Les textures dérivées de Mojang sont dans le dépôt : zone grise à garder en tête avant publication. | §7.3, étape 3 |
| 4 | ~~Moitié principale~~ — 17/10/2026 : `LEFT`, 🔷 à confirmer en jeu. | §2.1 |
| 5 | ~~Exclusions~~ — 17/10/2026 : oui, shulker, sacs, livres signés. | §4 |
| 6 | ~~Sortie d'un emplacement~~ — 17/10/2026 : un stack vanilla par geste. | §4.1 |
| 7 | ~~Recette ambiguë~~ — 17/10/2026 : la première trouvée. | §5 |
| 8 | ❓ Le jar 26.3 contient un bois **peuplier** (`poplar_chest_boat`, `textures/entity/chest_boat/poplar.png`), absent de la liste des 11 (§2). Ajouter une 12ᵉ variante ? Relevé à l'étape 3, à trancher avant l'étape 5. | §2, étape 5 |

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
