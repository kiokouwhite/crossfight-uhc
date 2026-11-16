#!/usr/bin/env python3
"""Range dans le pack prive les bruitages du jeu OMORI que joue l'evenement Omori.

Usage :  python3 tools/build_omori_sons.py <dossier "Sound Effects" du jeu>

Les fichiers du jeu ne sont jamais versionnes : ils vont dans resourcepack-prive/ (pack complet seulement), en
mono (un son en stereo ne s'attenue pas avec la distance dans Minecraft), sous le nom que le plugin attend :
assets/crossfight/sounds/ability/omori_<cle>.ogg, joue par game/Omori.java (evenement "ability.omori_<cle>").
Sans ces sons dans le pack, le plugin joue a la place des bruitages de Minecraft. Ensuite : tools/build_pack.py.
"""
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "resourcepack-prive" / "assets" / "crossfight" / "sounds" / "ability"

# cle du plugin -> fichier du jeu (sans .ogg). Les attaques d'Omori portent, dans le jeu, le nom de leur bruitage.
SOUNDS = {
    "porte": "SE_Door_Enter",                 # on ouvre la porte blanche
    "coup": "GEN_hit",                        # un coup porte a Omori
    "phase": "SE_bs_realization",             # Omori change de phase
    "basique": "BA_basic_attack_omori",       # Attaque basique : le coup de couteau
    "attrape": "BA_grab",                     # Poignarder : il attrape sa victime
    "poignarde": "BA_stab",                   # Poignarder : le couteau s'enfonce
    "rafale": "BA_hack_away",                 # Trancher (Hack Away) : la rafale de coups
    "mains": "SE_red_hands",                  # Effacement et Annihilation (Red Hands) : les mains rouges
    "jette": "GEN_throw",                     # Effacement : les mains jettent le joueur
    "ecrase": "GEN_smash",                    # Annihilation : la main s'ecrase au sol
    "charge": "BA_headbutt",                  # Traumatisme : il percute sa cible
    "enveloppe": "BA_something_engulf",       # Suicide : la surface rouge se referme
    "eclate": "BA_pierce_heart",              # Suicide : elle eclate en fils
    "fixe": "SE_whispers",                    # "Je ne peux pas le faire" : il fixe les joueurs
    "cheveux": "BA_tangle",                   # "Je ne peux pas le faire" : les cheveux tombent
    "vaincu": "SE_enemy_defeat1",             # Omori est vaincu
    "pardon": "GEN_complete",                 # on se pardonne
}


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    src = Path(sys.argv[1])
    OUT.mkdir(parents=True, exist_ok=True)
    missing = []
    for key, name in SOUNDS.items():
        f = src / f"{name}.ogg"
        if not f.is_file():
            missing.append(name)
            continue
        subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(f), "-ac", "1", "-ar", "44100", "-c:a", "libvorbis", "-q:a", "5",
                        str(OUT / f"omori_{key}.ogg")], check=True)
    print(f"{len(SOUNDS) - len(missing)} sons dans {OUT.relative_to(ROOT)}" + (f" ; absents : {', '.join(missing)}" if missing else ""))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
