# Architecture

```
com.kiokouwhite.crossfight
├── CrossfightPlugin        point d'entrée
├── game/                   machine à états, horloge, événements, scoreboard
├── team/                   équipes, tailles, attribution des licences
├── player/                 état des joueurs, vies et réapparition
├── ability/                moteur de capacités (passif, actif, activable, intervertible, ultime)
├── character/              licences, personnages, menu de sélection
│   ├── fireemblem/         Fire Emblem : Soutien, Marth, Corrin, Takumi, Byleth
│   ├── rwby/               RWBY : Ultimes en Duo, Ruby, Yang, Weiss, Blake
│   ├── valorant/           Valorant : crédits, armes à feu, Neon, Sage, Sova, Jett
│   ├── snk/                Shingeki no Kyojin : équipement 3D, Eren, Mikasa, Livaï, Armin
│   ├── unordinary/         unOrdinary : épées d'Aura, Arlo, John, Remi, Seraphina
│   ├── earthdelta/         Earth Delta : mutations, Kishi, Kura, Taka, Hano et ses 28 tomes
│   ├── duo/                Synergie : second ultime commun aux groupes duo (jauge à 200 %)
│   ├── gunvolt/            Armed Blue Gunvolt : Gunvolt, Acura, Kirin, Asimov, six Synergies
│   ├── shoja/              Shoja's Memories : Hiro, Pinkou, Aïda, Crystal, six Synergies
│   ├── bloodysnow/         Bloody Snow : Netsu, Kaze, Hannah, Eirika, Marques de Sang, six Synergies
│   └── solo/               Personnages solitaires : Burst (jauge à 1000 %), Yuki, le Drifter, Shoja et son arbre de 59 capacités
├── util/                   Sounds (bruitages et voix du pack), Music (musiques de combat), effets, menus…
├── listener/               PvP, charge d'ultime, mort, restrictions d'équipement
└── command/                /cf et /vivre
```
