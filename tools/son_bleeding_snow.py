#!/usr/bin/env python3
"""Le bruitage de Bleeding Snow (Yuki) : l'epee arrache violemment la vie de Yuki.

Usage : python3 -I tools/son_bleeding_snow.py <sortie.wav>
Puis :  python3 tools/add_audio.py ability <sortie.wav> yuki_snow --prive   et   python3 tools/build_pack.py

Cale sur l'effet du plugin (character/solo/Yuki.java, BleedingSnow.aspirer) : quatre pulsations a 0 ; 0,25 ; 0,5 ;
0,75 s, l'eclat de la lame a 1 s. Fait de sons synthetises et de bruitages libres du pack (resourcepack/.../sfx,
CC0, voir resourcepack/CREDITS.md) :
  - une aspiration qui monte tout du long (un souffle filtre qui s'ouvre et enfle, un sort joue a l'envers) ;
  - a chaque pulsation, un battement de coeur (double, sature, avec une attaque claquante, pour s'entendre aussi sur
    un petit haut-parleur), la chair qui se dechire, une giclee humide et une lame ;
  - a la fin, le plus fort : la glace qui vole en eclats, une detonation, une dechirure et une lame.
"""
import subprocess
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
SFX = ROOT / "resourcepack" / "assets" / "crossfight" / "sounds" / "sfx"
SR = 44100
DUREE = 2.1
POULS = (0.0, 0.25, 0.5, 0.75)
FIN = 1.0
rng = np.random.default_rng(7)


def lire(cat, n):
    raw = subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-i", str(SFX / cat / f"{n}.ogg"),
                          "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"], capture_output=True, check=True).stdout
    return np.frombuffer(raw, dtype=np.float32).astype(np.float64)


def vitesse(x, k):
    """Rejoue x k fois plus vite (k < 1 : plus grave et plus long)."""
    n = int(len(x) / k)
    return np.interp(np.arange(n) * k, np.arange(len(x)), x)


def poser(piste, x, t, gain=1.0):
    i = int(t * SR)
    x = x[:max(0, len(piste) - i)]
    piste[i:i + len(x)] += gain * x


def enveloppe(n, attaque, decroissance):
    t = np.arange(n) / SR
    return np.minimum(1, t / max(attaque, 1e-4)) * np.exp(-t / decroissance)


def passe_bande(x, f0, f1, q=6.0):
    """Un filtre resonant dont la frequence glisse de f0 a f1 (Hz) sur la duree de x."""
    f = np.geomspace(f0, f1, len(x))
    y, b1, b2 = np.zeros_like(x), 0.0, 0.0
    for i in range(len(x)):
        w = 2 * np.sin(np.pi * f[i] / SR)
        hp = x[i] - b2 - b1 / q
        b1 += w * hp
        b2 += w * b1
        y[i] = b1
    return y


def battement(force):
    """Un coup de coeur : une note grave qui plonge (et sa seconde harmonique), une attaque claquante."""
    n = int(0.3 * SR)
    t = np.arange(n) / SR
    f = 75 * np.exp(-t * 6) + 38
    phase = 2 * np.pi * np.cumsum(f) / SR
    corps = (np.sin(phase) + 0.5 * np.sin(2 * phase) + 0.25 * np.sin(3 * phase)) * enveloppe(n, 0.003, 0.09)
    clic = passe_bande(rng.standard_normal(int(0.03 * SR)), 1800, 900, 3) * enveloppe(int(0.03 * SR), 0.001, 0.008)
    x = corps * 0.9
    x[:len(clic)] += 0.8 * clic / (np.abs(clic).max() + 1e-9)
    # Sature fort : des harmoniques dans les mediums, pour que le coup s'entende aussi sur un petit haut-parleur.
    return np.tanh(4.0 * force * x) * 0.8


def dechirure(duree=0.09):
    """La chair qui se dechire : un souffle aigu et rugueux, tres bref."""
    n = int(duree * SR)
    x = passe_bande(rng.standard_normal(n), 4500, 1200, 2.5)
    x *= enveloppe(n, 0.002, duree / 3) * (1 + 0.8 * np.sign(np.sin(np.arange(n) * 2 * np.pi * 70 / SR)))
    return x / (np.abs(x).max() + 1e-9)


def main():
    out = Path(sys.argv[1] if len(sys.argv) > 1 else "yuki_snow.wav")
    piste = np.zeros(int(DUREE * SR))

    # L'aspiration : un souffle qui s'ouvre (filtre de 250 a 3200 Hz) et enfle jusqu'a l'eclat, puis se coupe net.
    n = int(FIN * SR)
    souffle = passe_bande(rng.standard_normal(n), 250, 3200, 4)
    souffle *= np.linspace(0.15, 1.0, n) ** 1.6
    souffle[-int(0.02 * SR):] *= np.linspace(1, 0, int(0.02 * SR))
    poser(piste, souffle / np.abs(souffle).max(), 0.0, 0.3)
    sort = vitesse(lire("magic", 2)[::-1], 0.8)                    # un sort a l'envers, aspire vers la lame
    sort = sort[-n:] if len(sort) > n else sort
    poser(piste, sort / (np.abs(sort).max() + 1e-9), FIN - len(sort) / SR, 0.35)

    # Les pulsations : battement, giclee de sang, lame qui mord ; de plus en plus fort.
    for k, t in enumerate(POULS):
        force = 0.8 + 0.15 * k
        poser(piste, battement(force), t, 0.7)
        poser(piste, battement(force * 0.8), t + 0.11, 0.5)       # le second temps du coeur
        poser(piste, dechirure(), t + 0.005, 0.45 + 0.1 * k)
        giclee = vitesse(lire("water", 1 + k % 4), 0.75)[: int(0.35 * SR)]
        poser(piste, giclee * enveloppe(len(giclee), 0.002, 0.12), t + 0.01, 0.6)
        lame = vitesse(lire("slash", 1 + k % 3), 0.7 + 0.08 * k)
        poser(piste, lame, t + 0.02, 0.45)

    # L'eclat : la glace vole en eclats, une detonation sourde, une lame.
    poser(piste, vitesse(lire("crystal", 4), 0.8), FIN, 1.3)
    poser(piste, lire("crystal", 1), FIN + 0.03, 1.0)
    poser(piste, vitesse(lire("crystal", 2), 1.2), FIN + 0.08, 0.8)
    boum = vitesse(lire("big_explosion", 3), 1.15)
    poser(piste, boum * enveloppe(len(boum), 0.002, 0.4), FIN, 1.3)
    poser(piste, battement(1.5), FIN, 1.0)
    poser(piste, dechirure(0.14), FIN, 0.9)
    poser(piste, vitesse(lire("slash", 3), 0.8), FIN + 0.01, 0.9)

    piste = np.tanh(piste / (np.abs(piste).max() + 1e-9) * 1.1)
    piste *= 10 ** (-1 / 20) / np.abs(piste).max()
    fin = int(0.08 * SR)
    piste[-fin:] *= np.linspace(1, 0, fin)
    pcm = (piste * 32767).astype("<i2").tobytes()
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "s16le", "-ar", str(SR), "-ac", "1",
                    "-i", "-", str(out)], input=pcm, check=True)
    print(f"{out} : {len(piste) / SR:.2f} s")
    return 0


if __name__ == "__main__":
    sys.exit(main())
