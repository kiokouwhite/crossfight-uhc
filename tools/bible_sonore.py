#!/usr/bin/env python3
"""Les sons d'exemple de la bible sonore de Crossfight (« Nef ») : la chaine commune, le sceau des quatre voies, la
palette des matieres, la hierarchie (passif, actif, ultime) et deux capacites refaites (avant, apres).

Usage : python3 -I tools/bible_sonore.py <dossier>

Ecrit dans <dossier> un .wav et un .mp3 par son, et sons.json (nom, duree, crete de 72 tranches pour dessiner l'onde).
Tout est fait de sons synthetises et des bruitages libres du pack (resourcepack/.../sounds/sfx, CC0).

La chaine commune (ogive) : passe-haut a 40 Hz, un leger creux a 320 Hz et un peu de brillance au-dessus de 6 kHz,
une saturation douce, la reverberation d'une nef de pierre (la meme pour tous, plus ou moins selon le rang), le volume
cible du rang (crete du volume percu sur 400 ms, en LUFS), puis un limiteur a -1 dBFS. Tout est accorde en re.
"""
import json
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from scipy import signal

ROOT = Path(__file__).resolve().parent.parent
SFX = ROOT / "resourcepack" / "assets" / "crossfight" / "sounds" / "sfx"
SR = 44100
RE2, RE3, RE4, RE5 = 73.42, 146.83, 293.66, 587.33          # la tonalite commune : re
RANGS = {  # volume percu vise (crete sur 400 ms, LUFS), part de la nef
    "interface": (-22, 0.0), "passif": (-19, 0.12), "actif": (-15, 0.2), "ultime": (-12, 0.34)}
ESPACE = {"destruction": 1.0, "chasse": 0.35, "harmonie": 1.6, "nihilite": 1.25}   # la nef, plus ou moins, selon la voie
rng = np.random.default_rng(11)


# --------------------------------------------------------------------------- outils

def n_(d): return int(d * SR)
def t_(n): return np.arange(n) / SR


def lire(cat, n):
    raw = subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-i", str(SFX / cat / f"{n}.ogg"),
                          "-ac", "1", "-ar", str(SR), "-f", "f32le", "-"], capture_output=True, check=True).stdout
    x = np.frombuffer(raw, dtype=np.float32).astype(np.float64)
    return x / (np.abs(x).max() + 1e-9)


def vitesse(x, k):
    n = int(len(x) / k)
    return np.interp(np.arange(n) * k, np.arange(len(x)), x)


def env(n, a, d):
    t = t_(n)
    return np.minimum(1, t / max(a, 1e-4)) * np.exp(-t / d)


def piste(d): return np.zeros(n_(d))


def poser(p, x, t, g=1.0):
    """Pose x dans la piste p a t secondes ; sa fin s'eteint en 8 ms (une coupure nette claquerait)."""
    i = n_(t)
    x = x[:max(0, len(p) - i)].copy()
    f = min(len(x), n_(0.008))
    if f:
        x[-f:] *= np.linspace(1, 0, f)
    p[i:i + len(x)] += g * x
    return p


def norm(x): return x / (np.abs(x).max() + 1e-9)


def filtre(x, kind, f, ordre=2):
    sos = signal.butter(ordre, f, btype=kind, fs=SR, output="sos")
    return signal.sosfilt(sos, x)


def biquad_pic(x, f, gain_db, q):
    a = 10 ** (gain_db / 40)
    w = 2 * np.pi * f / SR
    al = np.sin(w) / (2 * q)
    b = [1 + al * a, -2 * np.cos(w), 1 - al * a]
    c = [1 + al / a, -2 * np.cos(w), 1 - al / a]
    return signal.lfilter(np.array(b) / c[0], np.array(c) / c[0], x)


def plateau_aigu(x, f, gain_db):
    a = 10 ** (gain_db / 40)
    w = 2 * np.pi * f / SR
    al = np.sin(w) / 2 * np.sqrt(2)
    cs = np.cos(w)
    b = [a * ((a + 1) + (a - 1) * cs + 2 * np.sqrt(a) * al), -2 * a * ((a - 1) + (a + 1) * cs),
         a * ((a + 1) + (a - 1) * cs - 2 * np.sqrt(a) * al)]
    c = [(a + 1) - (a - 1) * cs + 2 * np.sqrt(a) * al, 2 * ((a - 1) - (a + 1) * cs), (a + 1) - (a - 1) * cs - 2 * np.sqrt(a) * al]
    return signal.lfilter(np.array(b) / c[0], np.array(c) / c[0], x)


def balayage(n, f0, f1, q=4.0):
    """Un souffle : du bruit dans un filtre resonant qui glisse de f0 a f1 (Hz)."""
    x = rng.standard_normal(n)
    f = np.geomspace(f0, f1, n)
    y, b1, b2 = np.zeros(n), 0.0, 0.0
    for i in range(n):
        w = 2 * np.sin(np.pi * f[i] / SR)
        hp = x[i] - b2 - b1 / q
        b1 += w * hp
        b2 += w * b1
        y[i] = b1
    return norm(y)


# --------------------------------------------------------------------------- matieres

def cloche(f0, d=1.6, eclat=1.0):
    """Une cloche : les partiels d'une cloche d'eglise (bourdon, fondamentale, tierce mineure, quinte, nominale...)."""
    n = n_(d)
    t = t_(n)
    parts = ((0.5, 0.5, 1.0), (1.0, 1.0, 0.8), (1.183, 0.45, 0.6), (1.506, 0.35, 0.5), (2.0, 0.6, 0.4),
             (2.514, 0.25 * eclat, 0.25), (3.011, 0.2 * eclat, 0.18), (4.166, 0.15 * eclat, 0.12))
    x = sum(a * np.sin(2 * np.pi * f0 * r * t + r) * np.exp(-3.2 * t / (d * k)) for r, a, k in parts)
    return norm(x * np.minimum(1, t / 0.002))


def acier(d=0.9, f=1150):
    """Le metal qui sonne : des partiels inharmoniques aigus qui s'eteignent vite."""
    n = n_(d)
    t = t_(n)
    x = sum(a * np.sin(2 * np.pi * f * r * t) * np.exp(-t / k) for r, a, k in
            ((1.0, 1.0, 0.25), (2.76, 0.6, 0.14), (5.40, 0.4, 0.08), (8.93, 0.25, 0.05), (13.3, 0.15, 0.03)))
    return norm(x * np.minimum(1, t / 0.001))


def lumiere(f0, d=0.9, montee=False):
    """La lumiere : une modulation de frequence propre et des grains de scintillement."""
    n = n_(d)
    t = t_(n)
    indice = (t / d) * 3 if montee else 3 * np.exp(-t / (d / 3))
    x = np.sin(2 * np.pi * f0 * t + indice * np.sin(2 * np.pi * f0 * 3.5 * t))
    for _ in range(int(14 * d)):
        g = n_(0.06)
        i = rng.integers(0, max(1, n - g))
        x[i:i + g] += 0.35 * np.sin(2 * np.pi * rng.uniform(3000, 8000) * t_(g)) * np.hanning(g)
    e = np.linspace(0, 1, n) ** 2 if montee else env(n, 0.005, d / 5)
    return norm(x * e)


def grave(d=0.8, f_haut=62, f_bas=34):
    """Le grave : une note qui plonge, saturee pour s'entendre aussi sur un petit haut-parleur."""
    n = n_(d)
    t = t_(n)
    f = f_bas + (f_haut - f_bas) * np.exp(-t * 7)
    x = np.sin(2 * np.pi * np.cumsum(f) / SR) * env(n, 0.004, d / 5)
    return norm(np.tanh(3.5 * x))


def souffle(d=0.5, f0=400, f1=2400, retombe=True):
    n = n_(d)
    x = balayage(n, f0, f1, 3)
    e = np.sin(np.linspace(0, np.pi, n)) ** (1.5 if retombe else 0.8)
    if not retombe:
        e = np.linspace(0, 1, n) ** 2
    return norm(x * e)


def dechirure(d=0.1):
    n = n_(d)
    x = balayage(n, 4500, 1200, 2.5) * env(n, 0.002, d / 3)
    return norm(x * (1 + 0.8 * np.sign(np.sin(t_(n) * 2 * np.pi * 70))))


# --------------------------------------------------------------------------- la chaine commune

def nef(rt60=2.4):
    """La reponse d'une nef de pierre : reflexions precoces puis une queue qui s'assombrit."""
    n = n_(rt60)
    t = t_(n)
    queue = rng.standard_normal(n) * np.exp(-6.91 * t / rt60)
    queue = filtre(queue, "lowpass", 2600) * (t > 0.03) + filtre(queue, "lowpass", 7000) * (t <= 0.03) * 0.5
    ir = np.zeros(n)
    for d, a in ((0.021, 0.7), (0.034, 0.5), (0.049, 0.42), (0.066, 0.33), (0.083, 0.25)):
        ir[n_(d)] += a
    ir[n_(0.02):] += 0.35 * queue[:n - n_(0.02)]
    return ir / np.sqrt(np.sum(ir ** 2))


NEF = nef()


def mesure(x):
    """La crete du volume percu sur 400 ms (LUFS), mesuree par ffmpeg (EBU R128)."""
    with tempfile.NamedTemporaryFile(suffix=".wav") as f:
        ecrire_wav(np.concatenate([x, np.zeros(n_(0.5))]), Path(f.name))
        out = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-i", f.name, "-af",
                              "ebur128=metadata=1,ametadata=print:key=lavfi.r128.M", "-f", "null", "-"],
                             capture_output=True, text=True).stderr
    vals = [float(l.split("=")[1]) for l in out.splitlines() if "lavfi.r128.M=" in l and "inf" not in l]
    return max(vals) if vals else -70.0


def ogive(x, rang, voie="destruction"):
    """La chaine commune : le meme traitement pour tous les sons, a la dose de leur rang (et de leur voie pour la nef)."""
    cible, envoi = RANGS[rang]
    envoi *= ESPACE[voie]
    x = filtre(norm(x), "highpass", 40)
    x = biquad_pic(x, 320, -2.5, 1.0)
    x = plateau_aigu(x, 6000, 2.0)
    x = np.tanh(1.6 * norm(x)) / np.tanh(1.6)
    if envoi:
        mouille = signal.fftconvolve(x, NEF)
        sec = np.concatenate([x, np.zeros(len(mouille) - len(x))])
        x = sec + envoi * mouille * (np.sqrt(np.mean(sec ** 2)) / (np.sqrt(np.mean(mouille ** 2)) + 1e-9)) * 1.6
    seuil = 10 ** (-42 / 20) * np.abs(x).max()
    fin = len(x) - np.argmax(np.abs(x[::-1]) > seuil)
    x = x[:min(len(x), fin + n_(0.05))]
    f = min(len(x) // 3, n_(0.4))
    x[-f:] *= np.linspace(1, 0, f) ** 1.5
    x *= 10 ** ((cible - mesure(x)) / 20)
    x = np.where(np.abs(x) > 0.75, np.sign(x) * (0.75 + 0.14 * np.tanh((np.abs(x) - 0.75) / 0.14)), x)
    f = n_(0.03)
    x[-f:] *= np.linspace(1, 0, f)
    return x


# --------------------------------------------------------------------------- le sceau des quatre voies

def sceau(voie):
    """La signature : un eclat de cloche et de lumiere, accorde en re, de la couleur de la voie."""
    if voie == "destruction":                 # grave et quinte, sature, un coup sourd dessous
        p = piste(1.0)
        poser(p, cloche(RE3, 0.9, 0.6), 0, 0.8)
        poser(p, cloche(RE3 * 1.5, 0.7, 0.6), 0, 0.45)
        poser(p, grave(0.5), 0, 0.7)
        poser(p, lumiere(RE5, 0.3), 0, 0.25)
        return np.tanh(2.2 * norm(p))
    if voie == "chasse":                      # aigu, tres bref, une pointe de souffle
        p = piste(0.45)
        poser(p, cloche(RE5, 0.35, 1.4), 0.03, 0.8)
        poser(p, souffle(0.08, 2500, 6000), 0, 0.4)
        poser(p, lumiere(RE5 * 2, 0.2), 0.03, 0.3)
        return p
    if voie == "harmonie":                    # accord majeur qui sonne longtemps
        p = piste(1.6)
        for r, g in ((1.0, 0.7), (1.25, 0.5), (1.5, 0.5), (2.0, 0.3)):
            poser(p, cloche(RE4 * r, 1.5, 0.8), 0, g)
        poser(p, lumiere(RE5, 1.0), 0, 0.3)
        return p
    if voie == "nihilite":                    # un triton, aspire a l'envers, qui retombe
        p = piste(1.5)
        envers = (cloche(RE4, 0.5, 1.3) + cloche(RE4 * 1.414, 0.5, 1.3))[::-1]
        poser(p, norm(envers), 0, 0.9)
        coup = cloche(RE4, 1.0, 1.2) + 0.8 * cloche(RE4 * 1.414, 1.0, 1.2)
        coup = vitesse(coup, 1.0)
        n = len(coup)
        coup = np.interp(np.arange(n) * np.linspace(1, 0.94, n), np.arange(n), coup)   # la hauteur qui descend
        poser(p, norm(coup), 0.5, 0.8)
        poser(p, grave(0.6, 50, 30), 0.5, 0.35)
        return p
    raise ValueError(voie)


# --------------------------------------------------------------------------- les exemples

def matieres():
    m = {}
    p = piste(1.0); poser(p, vitesse(lire("slash", 2), 0.9), 0, 0.7); poser(p, acier(0.8), 0.01, 0.8); m["acier"] = p
    p = piste(1.0); poser(p, filtre(lire("earth", 1), "lowpass", 2500), 0, 0.8); poser(p, grave(0.4, 80, 40), 0, 0.6); m["pierre"] = p
    p = piste(1.6); poser(p, cloche(RE4, 1.5), 0, 0.8); poser(p, lire("crystal", 1), 0.02, 0.5); m["cloche_verre"] = p
    p = piste(0.8); poser(p, souffle(0.6, 300, 2600), 0, 0.9); poser(p, lire("whoosh", 1), 0.25, 0.4); m["souffle"] = p
    m["lumiere"] = lumiere(RE5, 1.0)
    m["grave"] = grave(1.0)
    p = piste(0.7); poser(p, vitesse(lire("water", 1), 0.75), 0, 0.7); poser(p, dechirure(0.12), 0.005, 0.6); m["sang"] = p
    return m


def frappe(rang):
    """La meme frappe de la Voie de la Destruction, a trois tailles."""
    if rang == "passif":
        return sceau("destruction")[: n_(0.6)]
    if rang == "actif":
        p = piste(1.3)
        poser(p, souffle(0.18, 600, 2400, False), 0, 0.6)
        poser(p, acier(0.7), 0.18, 0.7)
        poser(p, filtre(lire("earth", 2), "lowpass", 3000), 0.18, 0.6)
        poser(p, sceau("destruction"), 0.18, 0.7)
        return p
    p = piste(3.2)                               # ultime : une montee d'une seconde, puis l'impact
    poser(p, souffle(1.0, 200, 3000, False), 0, 0.6)
    poser(p, lumiere(RE4, 1.0, montee=True), 0, 0.4)
    poser(p, np.linspace(0, 1, n_(1.0)) ** 2 * np.sin(2 * np.pi * RE2 * t_(n_(1.0))), 0, 0.5)
    poser(p, acier(1.0, 900), 1.0, 0.8)
    poser(p, filtre(lire("big_explosion", 1), "lowpass", 4000), 1.0, 0.9)
    poser(p, grave(1.2, 70, 30), 1.0, 1.0)
    for r, g in ((1.0, 0.5), (1.5, 0.4), (2.0, 0.3)):
        poser(p, cloche(RE3 * r, 2.0, 0.7), 1.0, g)
    poser(p, sceau("destruction"), 1.0, 0.8)
    return p


def avant_apres():
    a = {}
    a["insaisissable_avant"] = lire("whoosh", 1)
    p = piste(0.8)                                # Yuki (Chasse) : un dash bref, la lame fine, elle disparait
    poser(p, souffle(0.22, 900, 4000), 0, 0.8)
    poser(p, vitesse(lire("slash", 1), 1.3), 0.04, 0.4)
    poser(p, sceau("chasse"), 0.02, 0.7)
    disparait = lumiere(RE5, 0.5)[::-1]
    poser(p, disparait, 0.15, 0.3)
    a["insaisissable_apres"] = p
    a["sage_soin_avant"] = lire("heal", 1)
    p = piste(2.0)                                # Sage (Harmonie) : une lumiere qui monte, l'accord qui s'ouvre
    poser(p, lumiere(RE4, 0.6, montee=True), 0, 0.5)
    poser(p, souffle(0.6, 300, 1500, False), 0, 0.25)
    poser(p, sceau("harmonie"), 0.55, 0.9)
    poser(p, lire("heal", 2), 0.6, 0.25)
    a["sage_soin_apres"] = p
    return a


def ecrire_wav(x, chemin):
    pcm = (np.clip(x, -1, 1) * 32767).astype("<i2").tobytes()
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "s16le", "-ar", str(SR), "-ac", "1",
                    "-i", "-", str(chemin)], input=pcm, check=True)


def main():
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    out = Path(sys.argv[1])
    out.mkdir(parents=True, exist_ok=True)
    sons = {}
    for voie in ("destruction", "chasse", "harmonie", "nihilite"):
        sons[f"sceau_{voie}"] = ogive(sceau(voie), "passif", voie)
    for nom, x in matieres().items():
        sons[f"matiere_{nom}"] = ogive(x, "actif")
    for rang in ("passif", "actif", "ultime"):
        sons[f"frappe_{rang}"] = ogive(frappe(rang), rang)
    voies = {"insaisissable": "chasse", "sage_soin": "harmonie"}
    for nom, x in avant_apres().items():
        if nom.endswith("avant"):                 # le son actuel, au meme volume, sans autre traitement
            sons[nom] = x * min(10 ** ((-15 - mesure(x)) / 20), 0.89 / (np.abs(x).max() + 1e-9))
        else:
            sons[nom] = ogive(x, "actif", voies[nom.rsplit("_", 1)[0]])
    infos = {}
    for nom, x in sons.items():
        ecrire_wav(x, out / f"{nom}.wav")
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(out / f"{nom}.wav"),
                        "-c:a", "libmp3lame", "-b:a", "128k", str(out / f"{nom}.mp3")], check=True)
        k = max(1, len(x) // 72)
        cretes = [round(float(np.abs(x[i:i + k]).max()), 3) for i in range(0, k * 72, k)]
        infos[nom] = {"duree": round(len(x) / SR, 2), "cretes": cretes, "lufs": round(mesure(x), 1)}
        print(f"{nom:26} {len(x) / SR:5.2f} s  {infos[nom]['lufs']:6.1f} LUFS")
    (out / "sons.json").write_text(json.dumps(infos, ensure_ascii=False), encoding="utf-8")
    return 0


if __name__ == "__main__":
    sys.exit(main())
