#!/usr/bin/env python3
"""Les bruitages du logo anime : des sons libres (CC0) d'OpenGameArt, places et mixes ici, cales sur tools/logo_anime.py.

Usage : python3 -I tools/logo_son.py <dossier>    ecrit <dossier>/crossfight_uhc_anime.wav (48 kHz, stereo)

Ensuite, node tools/logo_video.js <dossier> le pose sur le MP4 et le WebM.

Les packs (SOURCES, tous CC0) sont telecharges une fois dans build/vendor/sons_logo/, chacun dans son dossier ; s'y
ajoutent des explosions de la bibliotheque du pack de ressources (resourcepack/assets/crossfight/sounds/sfx, CC0 elles
aussi : resourcepack/CREDITS.md).
  - chaque lettre de CROSSFIGHT : un petit souffle d'arme et un cliquetis, de plus en plus aigus, de gauche a droite ;
  - chaque barre : un grand souffle, puis le choc de deux lames, le coup sourd d'un bouclier et le grondement d'un canon ;
  - UHC qui tombe : un souffle court, puis un choc sourd ;
  - le reflet : une lame qui glisse, de gauche a droite, et un tintement ;
  - la disparition : un souffle au ralenti qui monte, coupe net par l'implosion (lames, bouclier, explosion), un souffle
    pour les barres, un pour chaque lettre qui s'envole, un pour UHC, puis une cloche pour la derniere etincelle.
"""
import subprocess
import sys
from pathlib import Path

import numpy as np
from scipy import signal
from scipy.io import wavfile

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import logo_anime as la  # noqa: E402

SR = 48000
TOTAL = la.DUREE + 0.25          # la video : l'animation et quelques images vides (tools/logo_video.js)
VENDOR = ROOT / "build" / "vendor" / "sons_logo"
BIBLIO = ROOT / "resourcepack" / "assets" / "crossfight" / "sounds" / "sfx"
OGA = "https://opengameart.org/sites/default/files/"
# Les packs : (dossier, fichier a telecharger, auteur, page). Tous en CC0 (domaine public).
SOURCES = [
    ("epees", "sword_-_starninjas_1.zip", "StarNinjas", "https://opengameart.org/content/20-sword-sound-effects-attacks-and-clashes"),
    ("chocs_epees", "sword_clash_-_starninjas_0.zip", "StarNinjas", "https://opengameart.org/content/20-sword-sound-effects-attacks-and-clashes"),
    ("boucliers", "impact_-_starninjas.zip", "StarNinjas", "https://opengameart.org/content/10-impactshield-blocks"),
    ("souffles", "swishes.zip", "artisticdude", "https://opengameart.org/content/swishes-sound-pack"),
    ("vent", "qubodup-timehitwind.zip", "qubodup", "https://opengameart.org/content/wind-hit-time-morph"),
    ("chutes", "sfx_breaking_and_falling.zip", "rubberduck", "https://opengameart.org/content/75-cc0-breaking-falling-hit-sfx"),
    ("cloches", "bell_ding4.wav", "PWL", "https://opengameart.org/content/bell-dingschimes"),
    ("metal", "bing1.wav", "BMacZero", "https://opengameart.org/content/metal-impact-sounds"),
    ("metal", "clink1_0.wav", "BMacZero", "https://opengameart.org/content/metal-impact-sounds"),
    ("metal", "clink2.wav", "BMacZero", "https://opengameart.org/content/metal-impact-sounds"),
    ("metal", "clink3.wav", "BMacZero", "https://opengameart.org/content/metal-impact-sounds"),
    ("metal", "thud2.wav", "BMacZero", "https://opengameart.org/content/metal-impact-sounds"),
]


def telecharger(sources=None, vendor=None):
    """Les packs, une fois : chacun dans son dossier de vendor (build/vendor/sons_logo/ par defaut), les archives
    (zip, 7z) y sont decompressees."""
    from urllib.parse import quote
    for dossier, fichier, *_ in (sources or SOURCES):
        d = (vendor or VENDOR) / dossier
        cible = d / fichier
        if cible.exists():
            continue
        d.mkdir(parents=True, exist_ok=True)
        subprocess.run(["curl", "-sSfL", "--max-time", "120", "-o", str(cible), OGA + quote(fichier)], check=True)
        if fichier.endswith(".zip"):
            subprocess.run(["unzip", "-q", "-o", str(cible), "-d", str(d)], check=True)
        elif fichier.endswith(".7z"):
            subprocess.run(["7z", "x", "-y", f"-o{d}", str(cible)], check=True, stdout=subprocess.DEVNULL)


def charger(chemin, vitesse=1.0):
    """Un son en mono a 48 kHz (decode par ffmpeg), normalise ; vitesse > 1 le rend plus court et plus aigu."""
    r = subprocess.run(["ffmpeg", "-v", "error", "-i", str(chemin), "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"],
                       capture_output=True, check=True)
    x = np.frombuffer(r.stdout, dtype=np.float32).astype(float)
    if vitesse != 1.0:
        x = np.interp(np.arange(0, len(x) - 1, vitesse), np.arange(len(x)), x)
    return x / (np.abs(x).max() + 1e-9)


def trouve(dossier, nom, vendor=None):
    """Le fichier nom dans le dossier d'un pack (les archives ont parfois un sous-dossier)."""
    return next(p for p in ((vendor or VENDOR) / dossier).rglob(nom) if "__MACOSX" not in str(p))


def pic(x):
    """L'instant (secondes) ou le son est le plus fort : c'est lui qu'on cale sur l'evenement."""
    env = np.convolve(np.abs(x), np.ones(480) / 480, mode="same")
    return env.argmax() / SR


def filtre(x, kind, f):
    return signal.sosfilt(signal.butter(2, f, btype=kind, fs=SR, output="sos"), x)


def poser(piste, son, t, pan=0.0, gain=1.0, cale=True, pan_fin=None):
    """Ajoute un son a l'instant t (son pic cale sur t, sauf cale=False), place de -1 (gauche) a 1 (droite) ; avec
    pan_fin, il glisse de pan a pan_fin pendant sa duree."""
    i = int(round((t - (pic(son) if cale else 0)) * SR))
    if i < 0:
        son, i = son[-i:], 0
    son = son[: max(0, piste.shape[1] - i)] * gain
    p = np.full(len(son), pan) if pan_fin is None else np.linspace(pan, pan_fin, len(son))
    a = (p + 1) * np.pi / 4
    piste[0, i:i + len(son)] += son * np.cos(a) * np.sqrt(2)
    piste[1, i:i + len(son)] += son * np.sin(a) * np.sqrt(2)


def reverb(piste, duree=1.4, humide=0.18):
    rng = np.random.default_rng(3)
    t = np.arange(int(duree * SR)) / SR
    sortie = np.zeros_like(piste)
    for c in range(2):
        ir = filtre(rng.standard_normal(len(t)), "lowpass", 6000) * np.exp(-t / 0.3)
        ir /= np.sqrt((ir ** 2).sum())
        sortie[c] = signal.fftconvolve(filtre(piste[c], "highpass", 200), ir)[: piste.shape[1]]
    return piste + humide * sortie


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    out = Path(sys.argv[1])
    telecharger()
    p = np.zeros((2, int(TOTAL * SR)))
    V, H, U, F = la.V, la.H, la.U, la.FIN
    E = lambda n: trouve("epees", f"sword.{n}.ogg")
    C = lambda n: trouve("chocs_epees", f"sword_clash.{n}.ogg")
    B = lambda n: trouve("boucliers", f"impact.{n}.ogg")
    S = lambda n: trouve("souffles", f"swish-{n}.wav")

    # Les lettres : un petit souffle et un cliquetis, de plus en plus aigus, de gauche a droite.
    for i in range(10):
        t0 = la.LETTRE0 + i * la.LETTRE_PAS + 0.08
        pan = -0.6 + 1.2 * i / 9
        poser(p, charger(S(1 + i % 13), 1.0 + 0.04 * i), t0, pan=pan, gain=0.22)
        poser(p, charger(trouve("metal", f"clink{(i % 3) + 1}{'_0' if i % 3 == 0 else ''}.wav"), 1.0 + 0.05 * i), t0 + 0.02, pan=pan, gain=0.1)

    # Les barres : le grand souffle, puis les lames, le bouclier et le canon. L'horizontale frappe plus fort.
    for t_imp, souffle, lame, bouclier, canon, force in ((V, "qubodup-megaswosh1.wav", 4, 8, ("explosion", 3), 0.85),
                                                          (H, "qubodup-megaswosh2.wav", 7, 10, ("big_explosion", 2), 1.0)):
        poser(p, charger(trouve("vent", souffle)), t_imp - 0.05, gain=0.45 * force)
        poser(p, charger(C(lame)), t_imp, gain=0.75 * force)
        poser(p, charger(B(bouclier)), t_imp, gain=0.5 * force)
        poser(p, filtre(charger(BIBLIO / canon[0] / f"{canon[1]}.ogg"), "lowpass", 2500), t_imp + 0.01, gain=0.7 * force)

    # UHC tombe de la barre : un souffle court, puis le choc sourd.
    poser(p, charger(S(9), 0.85), U - 0.06, gain=0.25)
    poser(p, charger(trouve("metal", "thud2.wav")), U, gain=0.45)
    poser(p, charger(trouve("chutes", "bfh1_hit_02.ogg")), U, gain=0.4)

    # Le reflet : une lame qui glisse de gauche a droite, et un tintement au milieu.
    r0, r1 = la.REFLET
    poser(p, charger(E(3)), (r0 + r1) / 2, pan=-0.7, pan_fin=0.7, gain=0.35)
    poser(p, charger(trouve("metal", "bing1.wav")), r0 + 0.25, pan=-0.2, gain=0.18)

    # La disparition : le souffle au ralenti qui monte jusqu'a l'implosion.
    poser(p, charger(trouve("vent", "qubodup-slomo1.wav")), F + 0.14, gain=0.55)
    poser(p, charger(C(6)), F + 0.14, gain=0.8)
    poser(p, charger(B(4)), F + 0.14, gain=0.5)
    poser(p, filtre(charger(BIBLIO / "big_explosion" / "1.ogg"), "lowpass", 2500), F + 0.15, gain=0.8)
    # Les barres se retirent, les lettres s'envolent, UHC remonte.
    poser(p, charger(trouve("vent", "60009__qubodup__swosh_22.flac")), F + 0.24, gain=0.35)
    for i in range(10):
        poser(p, charger(S(1 + (i * 3) % 13), 1.15 + 0.03 * i), F + 0.2 + i * 0.026, pan=-0.6 + 1.2 * i / 9, gain=0.14)
    poser(p, charger(S(7), 1.3), F + 0.32, gain=0.18)
    # La derniere etincelle.
    poser(p, charger(trouve("cloches", "bell_ding4.wav")), F + 0.7, gain=0.28)

    # La salle, une limite douce, un fondu sur les derniers instants.
    p = reverb(p)
    p = np.tanh(1.2 * p / (np.abs(p).max() + 1e-9))
    p *= 0.89 / np.abs(p).max()
    fondu = int(0.15 * SR)
    p[:, -fondu:] *= np.linspace(1, 0, fondu)
    out.mkdir(parents=True, exist_ok=True)
    wavfile.write(out / "crossfight_uhc_anime.wav", SR, (p.T * 32767).astype(np.int16))
    print(out / "crossfight_uhc_anime.wav", f"{TOTAL:.2f} s")


if __name__ == "__main__":
    sys.exit(main())
