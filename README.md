# XPert Guitare

Appli Android perso pour apprendre la **guitare électrique rythmique**, niveau grand débutant. Installée à la main (APK), sans Play Store.
Même famille que [XPert Drum](https://github.com/jeermi/xpertdrum) : charte et logo repris de [XPert](https://github.com/jeermi/xpert).

## Ce qu'il y a dedans

- **Prénom** demandé au premier lancement.
- **Les bases** : 8 leçons illustrées (ta guitare et ton ampli, s'installer, la main gauche, le médiator et la main droite,
  lire les accords et les rythmiques, s'accorder, étouffer les cordes, bien travailler). Les dessins montrent la posture,
  les doigts, la tenue du médiator, le geste du poignet ; les accords et rythmiques d'exemple s'écoutent.
- **Exercices** : 33 exercices sur 8 niveaux (premiers accords, enchaînements, gratter en rythme, croches et aller-retour,
  rythmique pop, power chords et palm mute, styles, accords barrés). Trois sortes :
  - un accord à la fois : l'appli écoute et dit si l'accord sonne juste, sinon quelle corde vérifier ;
  - changements d'accords en une minute ;
  - rythmiques avec métronome et grille d'accords : chaque coup de médiator est jugé (pile, en avance, en retard)
    **et** chaque accord est vérifié au micro. Tempo qui monte, mode « Écouter », bilan de fin.
- **Dictionnaire d'accords** : 41 positions (ouverts, septièmes, couleurs, power chords, barrés), diagramme, écoute,
  vérification au micro.
- **Accordeur** au micro (Mi La Ré Sol Si Mi), avec note de référence.
- **Métronome**, **réglage du micro** (1 min), **progression** (stockée sur le téléphone uniquement).

Il n'y a pas de pad à l'écran : l'appli écoute ta vraie guitare au micro du téléphone. Le son est analysé en direct, jamais enregistré.

## Reconnaissance des accords

`audio/ChordAnalyzer.kt`, pur Kotlin, testé sur les échantillons de l'appli :

1. sous-échantillonnage à ~12 kHz, FFT de 4096 points (≈ 0,34 s, résolution 2,9 Hz) ;
2. blanchiment spectral et recherche des pics (interpolation parabolique), compression racine carrée ;
3. estimation de l'accordage global de la guitare (une guitare accordée 30 cents trop bas reste reconnue) ;
4. profil chroma (12 notes), comparé par corrélation à des modèles d'accords qui incluent les harmoniques de chaque note ;
5. vérification contre les accords voisins et contre les erreurs de doigt courantes (une case à côté sur une corde),
   ce qui permet de dire quelle corde vérifier. Une corde un peu étouffée est tolérée.

Sur 1 148 accords simulés à partir des deux guitares (clair, micro de téléphone, désaccordé, grattés vers le haut, crunch, saturé) :
99,5 % des accords justes acceptés, 0,09 % de faux accords acceptés, 92 % des doigts mal placés détectés.

## Sons

Les sons de guitare viennent de **Black and Green Guitars** de Karoryfer Lecolds (Gretsch Anniversary et Höfner Club
enregistrées par Brian Wood), sous licence **CC0** : voir [CREDITS.md](CREDITS.md).

## Installer l'APK

1. Depuis le téléphone, ouvre la [dernière release](https://github.com/jeermi/xpertguitar/releases/latest) et télécharge
   `xpert-guitare.apk`. Chaque modification sur `main` en publie une nouvelle automatiquement.
2. Ouvre le fichier. Android demande d'autoriser l'installation depuis cette source : accepte.
3. Les mises à jour s'installent par-dessus, tant que l'APK est signé avec la même clé (voir plus bas).

## Compiler soi-même

Prérequis : JDK 17 et le SDK Android (Android Studio suffit).

```bash
./gradlew testDebugUnitTest   # tests (dont la reconnaissance d'accords sur les vrais échantillons)
./gradlew assembleRelease     # APK : app/build/outputs/apk/release/app-release.apk
```

Sans clé perso, l'APK est signé avec une clé de debug qui change à chaque build sur GitHub Actions : Android refusera
alors d'installer une mise à jour par-dessus (« conflit de signature ») et il faudra désinstaller, ce qui efface la progression.
Pour l'éviter, crée une clé une fois pour toutes et ajoute-la en secrets du dépôt :

```bash
keytool -genkeypair -keystore xpert.jks -alias xpertguitar -keyalg RSA -keysize 2048 -validity 10000
base64 -w0 xpert.jks   # à coller dans le secret XPERT_KEYSTORE_B64
```

puis le mot de passe (le même pour la clé et le magasin) dans le secret `XPERT_KEYSTORE_PASSWORD`
(Settings > Secrets and variables > Actions). Garde `xpert.jks` en lieu sûr, hors du dépôt.

## Architecture

- `audio/` : moteur audio basse latence (`AudioTrack`) qui joue les échantillons (une voix par corde, étouffement),
  micro (`MicListener`), attaques (`OnsetDetector`), accords (`ChordAnalyzer`, `ChordStream`), accordeur (YIN, `PitchDetector`).
- `music/` : notes, accords, positions sur le manche, dictionnaire.
- `practice/` : rythmiques, exercices, séance avec métronome, jugement des accords.
- `content/` : leçons et liste des illustrations.
- `data/` : progression et réglages (SharedPreferences).
- `ui/` : écrans Jetpack Compose, dessins (guitare, mains, médiator), diagrammes d'accords, thème XPert.
