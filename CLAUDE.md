# Smart Chest — instructions de travail

Mod Fabric pour Minecraft 26.3 : un coffre à emplacements d'amélioration (capacité par emplacement jusqu'à 999, compression automatique en blocs), compatible golem de cuivre.

## Les fichiers à lire

- **`SPEC.md`** — ce qu'on construit : specs confirmées, décisions techniques,
  plan d'implémentation, questions ouvertes. C'est la référence du projet.
- **`../SETUP-MC-MODDING-26.3.md`** — les pièges de cette machine et de
  Minecraft 26.3. À lire avant d'écrire du code Minecraft.
- **`../_Template mod/METHODE.md`** — la méthode de travail en détail, si une
  règle ci-dessous demande son contexte.

## Méthode de travail

- Passer par le **mode plan** pour la première session (la spec) et pour toute
  étape qui porte encore des ❓. Une étape ✅ ouverte avec `PROMPT-ETAPE.md` a
  déjà son plan.
- **Ne rien coder de mémoire** sur les API Minecraft ou Fabric. Vérifier à
  `javap` sur les jars du cache Loom, ou dans les sources de `genSources`, avant
  d'écrire. Le savoir issu des versions 1.x est largement faux depuis 26.1. Si
  une signature n'est pas sûre, le dire et vérifier ; jamais de nom de méthode
  inventé.
- **Référence vanilla : `ChestBlock` / `ChestBlockEntity`, et `CopperGolemAi` + `TransportItemsBetweenContainers` pour le golem.** On leur prend l'héritage et les points d'accès (`Container.getMaxStackSize(ItemStack)`, `ChestBlock.getContainer`), pas leur logique de recherche de cible.
- Sur ce qui n'est pas confirmé dans `SPEC.md`, **proposer plutôt qu'imposer**.
- **Chercher la voie « données » avant d'écrire du Java** : structures, tags,
  advancements, échanges, recettes sont du JSON.
- `build` puis `runServer` puis `runClient` : le premier vérifie le code, le
  deuxième **toutes** les données, le troisième seul montre le jeu réel.
- **Minimalisme** : pas de couche d'abstraction non demandée, pas d'interface
  pour une seule implémentation. Le code le plus court qui marche et se lit.

## Boucle d'une étape

1. Une étape du plan (`SPEC.md` §8) à la fois.
2. `runClient` doit compiler et lancer, puis **tu t'arrêtes** : je joue le
   « Validé quand » de l'étape avant toute relecture et tout commit.
3. En cas de crash : lis `run/logs/latest.log` et remonte la stacktrace
   pertinente, pas le fichier entier.
4. Après ma validation : relecture par sous-agent si l'étape la mérite (voir
   plus bas), correctifs, puis un commit pour l'étape.
5. Push, release, publication : jamais sans mon feu vert dans le chat.

## Licence et images

- Mods au concept voisin (coffres à améliorations : Sophisticated Storage, Iron Chests, etc.). **Interdit** de les lire ou de
  s'en inspirer ligne à ligne ; le concept est libre, le code ne l'est pas. Si
  je te demande de regarder l'un d'eux, refuse et rappelle-moi cette règle.
- Modrinth 6.2a interdit toute image d'IA générative sur la page projet (icône,
  galerie, description). Captures en jeu, textures produites par script et
  dessin à la main sont propres. Une image d'origine non établie reste hors du
  dépôt.

## Conventions

- Code, identifiants, commentaires et commits **en anglais** ; nos échanges en
  français.
- Textes joueur via `en_us.json` / `fr_fr.json`, jamais en dur.
- Valeurs de gameplay dans la config, pas en constantes dispersées.
- Ciblage par **tags** plutôt que par listes de blocs ou d'items codées en dur.
- `git add` par liste de fichiers explicite. Fichiers non suivis exprès :
  `run/`, `ref/`.

## Sous-agents : puissants et coûteux

Ils attrapent de vrais bugs, mais représentent l'essentiel de la consommation de
tokens : chacun repart de zéro.

**Explorer soi-même par défaut.** Un sous-agent d'exploration seulement quand la
question résiste à quelques `javap`, au mémo et aux sources décompilées.

**Demander des conclusions, pas des dumps.** La signature exacte et la réponse à
la question posée, jamais « le code intégral ». Un agent d'exploration rend la
**ligne `javap` brute**, jamais un résumé en prose.

**Faire relire ce qui le mérite.** Relecture avant commit **obligatoire** pour :
mixins, arithmétique, réseau, persistance, worldgen, durabilité, dégâts.
**Inutile** pour une texture, de la documentation, une traduction ou une clé de
config triviale — dans ces cas, vérifier soi-même et **le dire explicitement**
dans le message de rapport.

**Choisir le modèle selon l'enjeu.** Exploration, signature, inventaire :
**Sonnet**. Relecture avant commit dans les catégories obligatoires : **Opus**.

**Une seule passe par étape, après mon essai en jeu**, en lecture seule : il ne
modifie rien et ne commite rien. Pour un second tour, **rappeler le même agent**
(`SendMessage`) plutôt que d'en lancer un neuf.

Le prompt de relecture donne : le diff à lire (`git diff` + `--staged` +
fichiers non suivis), ce fichier, **ce qui est déjà vérifié** (signatures lues,
versions, essai en jeu — pour ne pas le refaire), des questions numérotées et
falsifiables, le chemin du jar à passer à `javap`, et la forme attendue
(français, `fichier:ligne`, pas de dump). Ordre de recherche : justesse
(client/serveur, coût par tick, NBT), signatures vérifiées et non supposées,
commentaires périmés, prose des docs en dernier.

Tu me remontes ce qu'il trouve, « rien à signaler » compris.

## Sessions

Repartir d'une **session neuve à chaque étape** du plan, ouverte avec
`../_Template mod/PROMPT-ETAPE.md` rempli. Une conversation longue renvoie tout
son contexte à chaque tour : `SPEC.md` est écrit précisément pour qu'une session
neuve reprenne sans rien perdre. Ce qui doit survivre et que le dépôt ne dit pas
(état d'une release, préférences) va dans ta mémoire, mise à jour en fin de
session.

## Tenue des fichiers

- `SPEC.md` se met à jour **au fil du projet** : marquer les étapes faites dans
  le plan (§8), fermer les questions tranchées (§9) en reportant la réponse dans
  la section concernée, noter les décisions structurantes dans le journal (§10),
  y compris **ce qui s'est révélé faux**.
- `CLAUDE.md` (ce fichier) reste court : uniquement des instructions
  permanentes. Tout ce qui décrit le mod va dans `SPEC.md`.
- Ce qu'on découvre sur Minecraft 26.3 ou sur la machine et qui vaudrait
  pour **n'importe quel mod** va dans `../SETUP-MC-MODDING-26.3.md`.
- Une étape finie mais non consignée est une étape à refaire : plan, question,
  journal et commit avant de fermer la session.
- Le `README.md` est la page Modrinth : il suit le comportement à chaque
  release.

## Pièges connus sur ce projet

- **Client vs serveur** : `level.isClientSide()` sur tout accès au monde.
- **Coût par tick** : jamais de scan de blocs à chaque tick ; cache, et
  rafraîchissement périodique.
- **NBT** : tout l'état doit survivre à un reload du monde. Tester en relançant,
  pas seulement en jeu.
- **Le golem de cuivre ne dépose que dans `Blocks.CHEST`/`TRAPPED_CHEST` par identité** : notre bloc n'est destination que via le mixin de `CopperGolemAi` (SPEC §3). Il écrit par `setItem` et ignore le plafond du conteneur : tout traitement d'insertion (compression) réagit à `setChanged`, jamais à un chemin d'insertion.
- **Le compteur d'`ItemStack` est plafonné à 99 par `ItemStack.CODEC`** : ne jamais sauvegarder une pile de 999 par ce codec (SPEC §4.1).
- **Les améliorations ne sont pas dans `items`** : sinon golem et entonnoirs les remplissent.
- <pièges propres au mod, ajoutés au fil des étapes>

