#!/usr/bin/env python3
"""La bande son de la video de presentation d'un personnage (tools/presentation_perso.py) : des bruitages libres et sa
replique d'arrivee, poses sur la video.

Usage : python3 -I tools/presentation_son.py <personnage> <dossier> [--voix N] [--ambiance angelique|epique]

Lit <dossier>/<personnage>.mp4 (deja rendu), ecrit <dossier>/<personnage>.wav et remet la video avec ce son dans
<dossier>/<personnage>.mp4 (l'image n'est pas reencodee).

Deux ambiances. La voix est toujours la replique d'arrivee du personnage (<personnage>_selection_<N>.ogg du pack prive ;
la plus longue par defaut), juste apres l'explosion, et les bruitages baissent sous elle.

angelique (par defaut) : des sons libres d'OpenGameArt (SOURCES_ANGE, CC0 et CC-BY 3.0 ; credits a donner avec la video
dans tools/presentation_credits.md), telecharges une fois dans build/vendor/sons_ange/.
  - une nappe feerique tout du long ; un choeur de voix etirees qui monte avec le personnage ; une clochette quand il se
    pose ; une nappe de voix douces qui enfle jusqu'a l'explosion ;
  - l'explosion : un eclair de lumiere sacree (qui se charge avant), une lame tres legere, puis une harpe de soin quand
    les couleurs reviennent ;
  - les signes qui s'allument : des scintillements ; chaque lettre du nom : une clochette, de plus en plus aigue ; le
    surnom : un carillon ; le halo : un sort de soin, puis un dernier carillon tres doux.

epique : les bruitages du logo (packs CC0 d'OpenGameArt, tools/logo/CREDITS.md) et quelques sons CC0 de la bibliotheque
du pack de ressources (resourcepack/CREDITS.md).
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
VENDOR_ANGE = ROOT / "build" / "vendor" / "sons_ange"
# Les sons de l'ambiance angelique : (dossier, fichier, auteur, licence, page). Credits : tools/presentation_credits.md.
SOURCES_ANGE = [
    ("nappe", "Dreamscape Drone.wav", "jdagenet", "CC-BY 3.0", "https://opengameart.org/content/dreamscape-drone"),
    ("choeur", "qubodup-WisdomMagic_0.7z", "Iwan Gabovitch (qubodup)", "CC-BY 3.0", "https://opengameart.org/content/wisdom-magic"),
    ("choeur", "141695__copyc4t__levelup.wav", "qubodup", "CC-BY 3.0", "https://opengameart.org/content/levelup-sound-atmospheric"),
    ("sacre", "Magic Smite.wav", "spookymodem", "CC-BY 3.0", "https://opengameart.org/content/magic-smite"),
    ("scintillement", "shimmer_1.flac", "qubodup", "CC-BY 3.0", "https://opengameart.org/content/shimmer-glitter-magic"),
    ("clochettes", "life_pickup.flac", "Lamoot", "CC-BY 3.0", "https://opengameart.org/content/life-pickup-yo-frankie"),
    ("harpe", "health_restore.wav", "Spring Spring", "CC0", "https://opengameart.org/content/magic-words-healing-sound-effect"),
    ("soin", "curemagic.zip", "Someoneman", "CC0", "https://opengameart.org/content/cure-magic"),
    ("carillons", "mousebyte_chimeyui.7z", "MouseBYTE", "CC0", "https://opengameart.org/content/chimey-ui-sounds"),
]


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
    import build_selection as bs
    name = {i: n for i, n, c in bs.roster()}[cid]
    X = pp.EXPLOSION
    sfx = np.zeros((2, int(duree * SR)))
    if option(sys.argv, "--ambiance", "angelique") == "epique":
        ls.telecharger()
        epique(sfx, X, name)
    else:
        ls.telecharger()
        ls.telecharger(SOURCES_ANGE, VENDOR_ANGE)
        angelique(sfx, X, name)
    voix_et_mixage(sfx, cid, out, video)


def lettres_tombent(name, X):
    """Les instants ou chaque lettre du nom se pose, et sa place de gauche a droite."""
    lettres = [c for c in name.upper() if not c.isspace()]
    return [(X + 0.3 + n * 0.08 + 0.3, -0.3 + 0.6 * n / max(1, len(lettres) - 1)) for n in range(len(lettres))]


def angelique(sfx, X, name):
    trouve, charger, poser, filtre = ls.trouve, ls.charger, ls.poser, ls.filtre
    A = lambda dossier, nom: trouve(dossier, nom, VENDOR_ANGE)
    duree = sfx.shape[1] / SR
    # La nappe feerique, tout du long : un morceau de la nappe, qui monte puis s'eteint.
    # (les graves des nappes et des choeurs sont coupes : l'ensemble reste aerien)
    aerien = lambda x: filtre(x, "highpass", 160)
    nappe = aerien(filtre(charger(A("nappe", "Dreamscape Drone.wav")), "lowpass", 7000))[int(6.0 * SR):int((6.0 + duree) * SR)]
    env = np.minimum(1, np.arange(len(nappe)) / (0.8 * SR)) * np.minimum(1, (len(nappe) - np.arange(len(nappe))) / (1.2 * SR))
    poser(sfx, nappe * env, 0.0, gain=0.2, cale=False)
    # Il surgit : un choeur de voix etirees qui monte avec lui, un souffle tres leger, une clochette quand il se pose.
    poser(sfx, aerien(charger(A("choeur", "qubodup-WisdomMagic3.flac"))), 0.25, gain=0.5, cale=False)
    poser(sfx, charger(trouve("vent", "qubodup-slomo1.wav")), 1.18, gain=0.14)
    poser(sfx, charger(A("clochettes", "life_pickup.flac")), 1.2, gain=0.3)
    poser(sfx, charger(trouve("metal", "thud2.wav")), 1.2, gain=0.12)
    # Il reste blanc : un choeur grave dessous, la nappe de voix douces qui enfle jusqu'a l'explosion.
    poser(sfx, aerien(charger(A("choeur", "qubodup-WisdomMagic8.flac"))), 1.0, gain=0.35, cale=False)
    poser(sfx, aerien(charger(A("choeur", "141695__copyc4t__levelup.wav"))), X + 0.05, gain=0.4)
    # L'explosion : l'eclair de lumiere sacree (il se charge avant), une lame tres legere, puis la harpe de soin.
    poser(sfx, charger(A("sacre", "Magic Smite.wav")), X, gain=0.8)
    poser(sfx, charger(trouve("chocs_epees", "sword_clash.2.ogg")), X, gain=0.22)
    poser(sfx, charger(A("harpe", "health_restore.wav")), X + 0.35, gain=0.5)
    # Les signes s'allument : des scintillements de part et d'autre.
    poser(sfx, charger(A("scintillement", "shimmer_1.flac")), X + 0.15, pan=-0.5, gain=0.3)
    poser(sfx, charger(A("scintillement", "shimmer_1.flac"), 1.12), X + 0.28, pan=0.5, gain=0.25)
    # Le cartouche se deplie (un souffle doux), chaque lettre tombe (une clochette, de plus en plus aigue), le surnom.
    poser(sfx, charger(trouve("souffles", "swish-5.wav")), X + 0.18, gain=0.14)
    for n, (t, pan) in enumerate(lettres_tombent(name, X)):
        poser(sfx, charger(A("clochettes", "life_pickup.flac"), 1.0 + 0.1 * n), t, pan=pan, gain=0.18)
    poser(sfx, charger(A("carillons", "Chime_Confirm.mp3")), X + 1.05, gain=0.3)
    # Le halo se forme : un sort de soin, puis un dernier carillon tres doux.
    poser(sfx, charger(A("soin", "Cure6.wav")), X + 1.45, gain=0.3, cale=False)
    poser(sfx, charger(A("carillons", "Chime_Sleep.mp3")), X + 2.7, gain=0.16)
    # Rien de lourd sous l'ensemble : les graves sous 110 Hz sont coupes (la voix n'est pas touchee).
    for c in range(2):
        sfx[c] = filtre(filtre(sfx[c], "highpass", 110), "highpass", 110)


def epique(sfx, X, name):
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
    for n, (t, pan) in enumerate(lettres_tombent(name, X)):
        clink = trouve("metal", f"clink{(n % 3) + 1}{'_0' if n % 3 == 0 else ''}.wav")
        poser(sfx, charger(clink, 1.0 + 0.06 * n), t, pan=pan, gain=0.14)
    poser(sfx, charger(S(3)), X + 1.05, gain=0.25)
    poser(sfx, charger(trouve("metal", "bing1.wav")), X + 1.2, gain=0.1)
    # Le halo se forme : un scintillement leger.
    poser(sfx, charger(ls.BIBLIO / "magic" / "5.ogg"), X + 1.5, gain=0.18, cale=False)


def voix_et_mixage(sfx, cid, out, video):
    charger, poser = ls.charger, ls.poser
    X = pp.EXPLOSION
    # La voix d'arrivee, juste apres l'explosion ; les bruitages baissent sous elle.
    # <personnage>_selection_<N>.ogg, ou une seule replique : <personnage>_selection.ogg.
    voix = sorted([*VOIX.glob(f"{cid}_selection_*.ogg"), *VOIX.glob(f"{cid}_selection.ogg")], key=lambda f: -len(charger(f)))
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
