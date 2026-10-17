# Prompt de l'étape 1

```markdown
On attaque l'étape 1 de Smart Chest : squelette. Le design est dans SPEC.md
(§7.1 et §7.2, et la ligne 1 du plan en §8) : lis-le en entier avant de coder,
avec CLAUDE.md et la section 1 du mémo SETUP-MC-MODDING-26.2.md.

## État du dépôt
- Pas encore de dépôt git : `git init` fait partie de l'étape (adresse `noreply` avant le premier commit).
- Fichiers non suivis exprès : `run/`, `ref/`, `Craft upgrade*.png` (références de Jérôme).
- Prochaine version : 0.1.0+26.3.

## Déjà établi (ne pas re-vérifier)
- Versions (SPEC §7.2) relevées au maven Fabric le 17/10/2026.
- Jar vanilla pour javap : C:/gradle-home/caches/fabric-loom/26.3/minecraft-merged.jar (déjà exploité pour le golem, SPEC §3).
- Modèle de projet : copier le wrapper et la structure d'un projet voisin 26.3 (ex. `MOD MC THE MASTER SWORD-26.3`).

## Ce qu'il faut faire
1. Squelette Gradle, `fabric.mod.json` (`minecraft` et `fabricloader` en `depends`), licence MIT, `.gitattributes` et `.gitignore` selon la check-list §7 du mémo 26.2.
2. Un point d'entrée vide qui logge le chargement.
3. SPEC.md : étape 1 marquée dans le plan §8, journal §10.
4. ./gradlew build, puis runClient. Ensuite arrête-toi : je joue le « Validé quand » de l'étape.

## Après ma validation
- Pas de relecture par sous-agent (squelette) : dis-le et dis ce que tu as vérifié.
- Un commit pour l'étape.
- Mets à jour ta mémoire : où en est le projet, ce qui reste à faire.
```
