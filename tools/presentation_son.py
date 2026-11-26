#!/usr/bin/env python3
"""La bande son de la video de presentation d'un personnage (tools/presentation_perso.py) : des bruitages libres et sa
replique d'arrivee, poses sur la video.

Usage : python3 -I tools/presentation_son.py <personnage> <dossier> [--voix N]

Lit <dossier>/<personnage>.mp4 (deja rendu), ecrit <dossier>/<personnage>.wav et remet la video avec ce son dans
<dossier>/<personnage>.mp4 (l'image n'est pas reencodee).

Les bruitages sont ceux du logo (packs CC0 d'OpenGameArt, telecharges par tools/logo_son.py ; credits dans
tools/logo/CREDITS.md) et quelques sons CC0 de la bibliotheque du pack de ressources (resourcepack/CREDITS.md). La voix
est la replique d'arrivee du personnage (<personnage>_selection_<N>.ogg du pack prive ; la plus longue par defaut).
  - le fond s'allume : un gong grave ;
  - il surgit du bas de l'ecran : un souffle qui monte avec lui, puis un choc sourd quand il se pose ;
  - il reste blanc : un souffle au ralenti qui monte jusqu'a l'explosion ;
  - l'explosion lui rend ses couleurs : le choc de deux lames, un bouclier, un canon, un scintillement magique ;
  - sa voix, juste apres (les bruitages baissent sous elle) ;
  - le cartouche qui se deplie et celui du surnom : un souffle ; chaque lettre du nom qui tombe : un cliquetis ;
  - les signes de la voie qui s'allument : des tintements de cristal ; le halo qui se forme : un scintillement.
"""
import subprocess
import sys
from pathlib import Path

import numpy as np
from scipy.io import wavfile

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import logo_son as ls  # noqa: E402
import presentation_perso as pp  # noqa: E402

SR = ls.SR
VOIX = ROOT / "resourcepack-prive" / "assets" / "crossfight" / "sounds" / "voice"


def option(argv, name, default):
    return argv[argv.index(name) + 1] if name in argv else default


def main():
    if len(sys.argv) < 3:
        print(__doc__)
        return 1
    cid, out = sys.argv[1], Path(sys.argv[2]).resolve()
    video = out / f"{cid}.mp4"
    if not video.exists():
        sys.exit(f"Pas de video : {video} (python3 tools/presentation_perso.py {cid} {sys.argv[2]})")
    duree = float(subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(video)],
                                 capture_output=True, text=True, check=True).stdout)
    ls.telecharger()
    import build_selection as bs
    name = {i: n for i, n, c in bs.roster()}[cid]
    X = pp.EXPLOSION
    sfx = np.zeros((2, int(duree * SR)))
    trouve, charger, poser, filtre = ls.trouve, ls.charger, ls.poser, ls.filtre
    S = lambda n: trouve("souffles", f"swish-{n}.wav")

    # Le fond s'allume : un gong grave.
    poser(sfx, filtre(charger(ls.BIBLIO / "time" / "1.ogg", 0.8), "lowpass", 3000), 0.0, gain=0.3, cale=False)
    # Il surgit du bas de l'ecran (0,3 a 1,2 s) et se pose.
    poser(sfx, charger(trouve("vent", "qubodup-slomo1.wav")), 1.18, gain=0.32)
    poser(sfx, charger(trouve("vent", "qubodup-megaswosh1.wav"), 0.8), 1.1, gain=0.4)
    poser(sfx, charger(trouve("metal", "thud2.wav")), 1.2, gain=0.4)
    poser(sfx, charger(trouve("boucliers", "impact.8.ogg")), 1.2, gain=0.22)
    # Il reste blanc : la montee jusqu'a l'explosion.
    poser(sfx, charger(trouve("vent", "qubodup-slomo1.wav")), X, gain=0.5)
    # L'explosion : les lames, le bouclier, le canon, et un scintillement pour les couleurs qui reviennent.
    poser(sfx, charger(trouve("chocs_epees", "sword_clash.2.ogg")), X, gain=0.7)
    poser(sfx, charger(trouve("boucliers", "impact.10.ogg")), X, gain=0.45)
    poser(sfx, filtre(charger(ls.BIBLIO / "big_explosion" / "2.ogg"), "lowpass", 2500), X + 0.01, gain=0.75)
    poser(sfx, charger(ls.BIBLIO / "magic" / "4.ogg"), X + 0.05, gain=0.35, cale=False)
    # Les signes de la voie s'allument au passage de l'onde.
    for k, (t, pan) in enumerate(((X + 0.12, -0.6), (X + 0.2, 0.6), (X + 0.3, -0.3))):
        poser(sfx, charger(ls.BIBLIO / "crystal" / f"{k + 1}.ogg", 1.1 + 0.1 * k), t, pan=pan, gain=0.16)
    # Le cartouche du nom se deplie, les lettres tombent, puis le surnom.
    poser(sfx, charger(S(5)), X + 0.18, gain=0.3)
    lettres = [c for c in name.upper() if not c.isspace()]
    for n in range(len(lettres)):
        clink = trouve("metal", f"clink{(n % 3) + 1}{'_0' if n % 3 == 0 else ''}.wav")
        poser(sfx, charger(clink, 1.0 + 0.06 * n), X + 0.3 + n * 0.08 + 0.3, pan=-0.3 + 0.6 * n / max(1, len(lettres) - 1), gain=0.14)
    poser(sfx, charger(S(3)), X + 1.05, gain=0.25)
    poser(sfx, charger(trouve("metal", "bing1.wav")), X + 1.2, gain=0.1)
    # Le halo se forme : un scintillement leger.
    poser(sfx, charger(ls.BIBLIO / "magic" / "5.ogg"), X + 1.5, gain=0.18, cale=False)

    # La voix d'arrivee, juste apres l'explosion ; les bruitages baissent sous elle.
    voix = sorted(VOIX.glob(f"{cid}_selection_*.ogg"), key=lambda f: -len(charger(f)))
    choix = option(sys.argv, "--voix", "")
    voix = [VOIX / f"{cid}_selection_{choix}.ogg"] if choix else voix
    piste_voix = np.zeros_like(sfx)
    if voix:
        v = charger(voix[0])
        t_voix = X + 0.15
        poser(piste_voix, v, t_voix, gain=1.0, cale=False)
        debut, fin = int((t_voix - 0.05) * SR), int((t_voix + len(v) / SR + 0.15) * SR)
        baisse = np.ones(sfx.shape[1])
        rampe = int(0.08 * SR)
        baisse[debut:fin] = 0.45
        baisse = np.convolve(baisse, np.ones(rampe) / rampe, mode="same")
        sfx *= baisse

    mix = ls.reverb(sfx) + ls.reverb(piste_voix, humide=0.1)
    mix = np.tanh(1.15 * mix / (np.abs(mix).max() + 1e-9))
    mix *= 0.89 / np.abs(mix).max()
    fondu = int(0.4 * SR)
    mix[:, -fondu:] *= np.linspace(1, 0, fondu)
    wav = out / f"{cid}.wav"
    wavfile.write(wav, SR, (mix.T * 32767).astype(np.int16))
    tmp = out / f"{cid}.son.mp4"
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(video), "-i", str(wav), "-map", "0:v",
                    "-map", "1:a", "-c:v", "copy", "-c:a", "aac", "-b:a", "192k", "-shortest", str(tmp)], check=True)
    tmp.replace(video)
    print(video, "voix :", voix[0].name if voix else "aucune")


if __name__ == "__main__":
    sys.exit(main())
