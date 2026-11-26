#!/usr/bin/env python3
"""Les bruitages du logo anime, synthetises ici (aucun son enregistre), cales sur tools/logo_anime.py.

Usage : python3 tools/logo_son.py <dossier>      ecrit <dossier>/crossfight_uhc_anime.wav (48 kHz, stereo)

Ensuite, node tools/logo_video.js <dossier> le pose sur le MP4 et le WebM.

  - chaque lettre de CROSSFIGHT qui apparait : un petit tintement, de plus en plus aigu, de gauche a droite ;
  - chaque barre : un souffle qui monte, puis l'impact (un grave qui tombe, un claquement, la resonance metallique
    d'une lame), plus fort pour la barre horizontale ;
  - UHC qui tombe : un souffle qui descend, puis un choc sourd ;
  - le reflet : des scintillements qui traversent de gauche a droite ;
  - la disparition : une montee (comme une cymbale a l'envers), l'implosion, les barres qui se retirent, les lettres
    qui s'envolent une a une, UHC qui remonte, puis le tintement de la derniere etincelle.
Le tout passe dans une reverberation, puis est limite en douceur.
"""
import sys
from pathlib import Path

import numpy as np
from scipy import signal
from scipy.io import wavfile

sys.path.insert(0, str(Path(__file__).resolve().parent))
import logo_anime as la  # noqa: E402

SR = 48000
TOTAL = la.DUREE + 0.25          # la video : l'animation et quelques images vides (tools/logo_video.js)
rng = np.random.default_rng(7)


def temps(d):
    return np.arange(int(d * SR)) / SR


def bruit(d):
    return rng.standard_normal(int(d * SR))


def filtre(x, kind, f):
    sos = signal.butter(2 if kind != "bandpass" else 2, f, btype=kind, fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def balayage(d, f0, f1, largeur=0.35, env=None):
    """Un souffle : du bruit dont seule une bande passe, et cette bande glisse de f0 a f1 (en Hz)."""
    x = bruit(d)
    f, t, z = signal.stft(x, SR, nperseg=1024, noverlap=768)
    centre = f0 * (f1 / f0) ** np.clip(t / d, 0, 1)
    masque = np.exp(-0.5 * (np.log(np.maximum(f[:, None], 1) / centre[None, :]) / largeur) ** 2)
    _, y = signal.istft(z * masque, SR, nperseg=1024, noverlap=768)
    y = y[: len(x)]
    y = np.pad(y, (0, len(x) - len(y)))
    if env is not None:
        y *= env
    return y / (np.abs(y).max() + 1e-9)


def tintement(f, d=0.14):
    t = temps(d)
    ton = np.sin(2 * np.pi * f * t) * np.exp(-t / 0.03)
    clic = filtre(bruit(d), "highpass", 3000) * np.exp(-t / 0.004) * 0.25
    corps = np.sin(2 * np.pi * f / 2 * t) * np.exp(-t / 0.05) * 0.35
    return ton * 0.6 + clic + corps


def impact(f_metal, force=1.0, grave=45.0):
    d = 1.6
    t = temps(d)
    freq = grave + 120 * np.exp(-t / 0.06)
    boum = np.tanh(2.2 * np.sin(2 * np.pi * np.cumsum(freq) / SR)) * np.exp(-t / 0.3)
    claque = filtre(bruit(d), "highpass", 1400) * np.exp(-t / 0.03)
    corps = filtre(bruit(d), "lowpass", 420) * np.exp(-t / 0.12) * 2
    metal = np.zeros_like(t)
    for k, (r, dec, a) in enumerate(((1, .9, .22), (2.32, .65, .2), (4.25, .45, .15), (6.63, .3, .10), (9.38, .2, .07))):
        metal += a * np.sin(2 * np.pi * f_metal * r * t + k) * np.exp(-t / dec)
    attaque = np.minimum(1, t / 0.002)
    return force * attaque * (boum * 1.0 + claque * 0.55 + corps * 0.45 + metal * 0.5)


def choc_sourd():
    d = 0.6
    t = temps(d)
    freq = 38 + 70 * np.exp(-t / 0.04)
    boum = np.sin(2 * np.pi * np.cumsum(freq) / SR) * np.exp(-t / 0.2)
    bois = np.sin(2 * np.pi * 230 * t) * np.exp(-t / 0.05) * 0.35
    clic = filtre(bruit(d), "bandpass", (900, 4000)) * np.exp(-t / 0.008) * 0.3
    return boum + bois + clic


def cloche(f, d=2.0):
    t = temps(d)
    s = np.zeros_like(t)
    for r, dec, a in ((1, 1.5, .5), (2.0, 1.0, .25), (2.76, .8, .2), (4.07, .5, .12), (5.4, .3, .08)):
        s += a * np.sin(2 * np.pi * f * r * t) * np.exp(-t / dec)
    return s * np.minimum(1, t / 0.004)


def poser(piste, son, t, pan=0.0, gain=1.0):
    """Ajoute un son (mono) a l'instant t (secondes), place de -1 (gauche) a 1 (droite)."""
    i = int(round(t * SR))
    if i >= piste.shape[1]:
        return
    son = son[: piste.shape[1] - i] * gain
    a = (pan + 1) * np.pi / 4
    piste[0, i:i + len(son)] += son * np.cos(a) * np.sqrt(2)
    piste[1, i:i + len(son)] += son * np.sin(a) * np.sqrt(2)


def reverb(piste, duree=1.6, humide=0.22):
    t = temps(duree)
    sortie = np.zeros_like(piste)
    for c in range(2):
        ir = filtre(bruit(duree), "lowpass", 5500) * np.exp(-t / 0.33)
        ir /= np.sqrt((ir ** 2).sum())
        # Pas de graves dans la salle : ils brouilleraient les impacts.
        sortie[c] = signal.fftconvolve(filtre(piste[c], "highpass", 180), ir)[: piste.shape[1]]
    return piste + humide * sortie


def main():
    out = Path(sys.argv[1]) if len(sys.argv) > 1 else None
    if out is None:
        print(__doc__)
        return 1
    n = int(TOTAL * SR)
    p = np.zeros((2, n))
    V, H, U, F = la.V, la.H, la.U, la.FIN

    # Les lettres : une gamme pentatonique qui monte, de gauche a droite.
    gamme = [0, 2, 4, 7, 9, 12, 14, 16, 19, 21]
    for i in range(10):
        t0 = la.LETTRE0 + i * la.LETTRE_PAS + 0.07
        poser(p, tintement(660 * 2 ** (gamme[i] / 12)), t0, pan=-0.6 + 1.2 * i / 9, gain=0.28)
        poser(p, balayage(0.09, 2500, 6000, env=np.hanning(int(0.09 * SR))), t0 - 0.05, pan=-0.6 + 1.2 * i / 9, gain=0.06)

    # Les barres : un souffle qui monte jusqu'a l'impact.
    for t_imp, f_metal, force, grave in ((V, 415, 0.85, 48), (H, 330, 1.0, 40)):
        d = 0.32
        env = np.linspace(0, 1, int(d * SR)) ** 2.5
        poser(p, balayage(d, 350, 5200, env=env), t_imp - d, gain=0.32)
        poser(p, impact(f_metal, force, grave), t_imp, gain=0.9)

    # UHC tombe de la barre : un souffle qui descend, puis le choc.
    d = 0.2
    poser(p, balayage(d, 3200, 500, env=np.hanning(int(d * SR)) ** 0.7), U - d, gain=0.2)
    poser(p, choc_sourd(), U, gain=0.55)

    # Le reflet : des scintillements de gauche a droite, et un souffle tres aigu qui les porte.
    r0, r1 = la.REFLET
    for k in range(28):
        u = (k + rng.uniform(0, 1)) / 28
        f = rng.uniform(2600, 7200)
        dd = rng.uniform(0.06, 0.18)
        t = temps(dd)
        ping = np.sin(2 * np.pi * f * t) * np.exp(-t / (dd / 3))
        poser(p, ping, r0 + u * (r1 - r0), pan=-0.85 + 1.7 * u, gain=0.05)
    d = r1 - r0
    air = filtre(bruit(d), "highpass", 6500) * np.hanning(int(d * SR))
    for k in range(8):
        a, b = int(k * len(air) / 8), int((k + 1) * len(air) / 8)
        poser(p, air[a:b], r0 + a / SR, pan=-0.8 + 1.6 * k / 7, gain=0.04)

    # La disparition. La montee, comme une cymbale a l'envers, coupee net par l'implosion.
    d = 0.44
    t = temps(d)
    montee = filtre(bruit(d), "highpass", 2200) * (t / d) ** 3
    montee += 0.25 * np.sin(2 * np.pi * np.cumsum(200 * (4.5 ** (t / d))) / SR) * (t / d) ** 2
    poser(p, montee, F + 0.14 - d, gain=0.28)
    imp = impact(370, 1.0, 42)
    t = temps(0.55)
    tombe = np.sin(2 * np.pi * np.cumsum(320 * (40 / 320) ** (t / 0.55)) / SR) * np.exp(-t / 0.3) * 0.5
    poser(p, imp, F + 0.14, gain=0.85)
    poser(p, tombe, F + 0.14, gain=0.4)
    # Les barres se retirent, les lettres s'envolent, UHC remonte.
    poser(p, balayage(0.2, 4200, 700, env=np.hanning(int(0.2 * SR))), F + 0.15, gain=0.22)
    for i in range(10):
        poser(p, balayage(0.13, 3000, 8500, env=np.hanning(int(0.13 * SR))), F + 0.15 + i * 0.026,
              pan=-0.6 + 1.2 * i / 9, gain=0.07)
    poser(p, balayage(0.22, 500, 3200, env=np.hanning(int(0.22 * SR))), F + 0.2, gain=0.14)
    # La derniere etincelle : une cloche claire, et quelques eclats autour.
    poser(p, cloche(1568), F + 0.64, gain=0.22)
    for k in range(6):
        f = 3136 * 2 ** (rng.integers(0, 12) / 12)
        t = temps(0.25)
        poser(p, np.sin(2 * np.pi * f * t) * np.exp(-t / 0.06), F + 0.64 + k * 0.045, pan=rng.uniform(-0.4, 0.4), gain=0.035)

    # La salle, puis une limite douce, et un fondu sur les derniers instants.
    p = reverb(p)
    p = np.tanh(1.3 * p / (np.abs(p).max() + 1e-9))
    p *= 0.89 / np.abs(p).max()
    fondu = int(0.15 * SR)
    p[:, -fondu:] *= np.linspace(1, 0, fondu)
    out.mkdir(parents=True, exist_ok=True)
    wavfile.write(out / "crossfight_uhc_anime.wav", SR, (p.T * 32767).astype(np.int16))
    print(out / "crossfight_uhc_anime.wav", f"{TOTAL:.2f} s")


if __name__ == "__main__":
    sys.exit(main())
