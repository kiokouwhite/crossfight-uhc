#!/usr/bin/env python3
"""Fait d'une musique une boucle de jeu video : elle n'a plus de fin, elle retombe sur elle-meme sans qu'on l'entende.

Usage :
  python3 tools/boucle_musique.py analyse <fichier>
      cherche ou la piste se repete (le meme enregistrement, rejoue plus loin) et propose un point de boucle
  python3 tools/boucle_musique.py <fichier> <piste> --debut <secondes> --longueur <echantillons>
                                  [--tours 8] [--suite 4] [--fondu 300] [--relais 250] [--niveau -16] [--prive]
      ecrit la piste en deux fichiers, dans sounds/music/ :
        <piste>.ogg          l'introduction (tout ce qui precede --debut), puis <tours> tours de boucle
        <piste>_boucle.ogg   <suite> tours de boucle, pour continuer apres le premier fichier, autant de fois qu'il faut

Le principe. Une musique de jeu est une introduction jouee une fois, puis une section qui tourne. Sur un disque, cette
section est en general jouee deux fois avant un fondu final : le second tour est le meme enregistrement que le premier,
a l'echantillon pres. "analyse" retrouve ce decalage exact ; la boucle va alors de --debut a --debut + --longueur, et
ce qui suit sa fin est, sur le disque meme, ce qui suit son debut : le raccord est celui du compositeur.
Les derniers --fondu millisecondes d'un tour glissent du second passage vers le premier (les deux sont alignes a
l'echantillon pres : aucun flottement, seuls le souffle et les effets qui different d'un tour a l'autre sont lisses).

Minecraft ne sait pas boucler un son : le serveur doit relancer un fichier, a un vingtieme de seconde pres. Dans un
fichier, en revanche, les tours s'enchainent a l'echantillon pres. D'ou les deux fichiers : le premier contient deja
plusieurs tours (dix minutes de musique sans aucun raccord fait par le serveur), le second prend la suite. A la fin de
chaque fichier, le debut du tour suivant est ajoute en fondu sortant (--relais ms), et le fichier suivant commence en
fondu entrant : meme si le relais tombe 50 ms trop tot ou trop tard, il n'y a ni blanc ni claquement.
Chaque fichier dit lui-meme quand et par quoi il est relaye (etiquettes CROSSFIGHT_RELAIS et CROSSFIGHT_SUITE, lues par
tools/build_pack.py) : le plugin n'a rien a savoir de plus.

--niveau est le niveau vise, en LUFS : une piste calme qui en precede une autre se regle plus bas qu'elle. Une section
qui n'est pas rejouee a l'identique peut tourner quand meme, avec un long --fondu (plusieurs secondes) : sa fin se fond
dans ce qui precede son debut.

Avec --prive, les fichiers vont dans resourcepack-prive/ (musiques sous droits, jamais sur le depot).
Demande numpy et ffmpeg. Ensuite : python3 tools/build_pack.py
"""
import json
import math
import re
import subprocess
import sys
from pathlib import Path

import numpy as np

ROOT = Path(__file__).resolve().parent.parent
SR = 44100
SUFFIX = "_boucle"


def decode(src: Path) -> np.ndarray:
    """La piste en 44,1 kHz stereo, flottants entre -1 et 1 : un tableau (echantillons, 2)."""
    r = subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-i", str(src), "-vn", "-ac", "2", "-ar", str(SR),
                        "-f", "f32le", "-"], capture_output=True, check=True)
    return np.frombuffer(r.stdout, dtype=np.float32).reshape(-1, 2)


def ncc(p: np.ndarray, q: np.ndarray) -> float:
    return float(np.dot(p, q)) / (math.sqrt(float(np.dot(p, p)) * float(np.dot(q, q))) + 1e-12)


def clock(seconds: float) -> str:
    return "%d:%05.2f" % (int(seconds // 60), seconds % 60)


# ------------------------------------------------------------------------------------------------ analyse

def repeats(mono: np.ndarray):
    """Les decalages (en echantillons) auxquels un extrait de la piste se retrouve plus loin, presque a l'identique."""
    q = 4                                                   # recherche grossiere a 11 kHz, affinee ensuite
    lo = mono[: len(mono) // q * q].reshape(-1, q).mean(axis=1)
    srl = SR // q
    length = 3 * srl
    size = 1 << int(math.ceil(math.log2(len(lo) + length)))
    spectrum = np.fft.rfft(lo, size)
    c = np.concatenate(([0.0], np.cumsum(lo ** 2)))
    energy = c[length:] - c[:-length]
    found = []
    t = 0.1 * len(lo) / srl
    while t < len(lo) / srl - 6:
        a = int(t * srl)
        seg = lo[a:a + length]
        corr = np.fft.irfft(spectrum * np.conj(np.fft.rfft(seg, size)), size)[: len(lo) - length + 1]
        score = corr / (np.sqrt(energy * float(np.dot(seg, seg))) + 1e-12)
        score[max(0, a - 2 * srl): a + 2 * srl] = 0
        # Un meme passage peut revenir plusieurs fois : on garde ses meilleurs echos, pas seulement le premier.
        for _ in range(4):
            i = int(np.argmax(score))
            if score[i] < 0.75:
                break
            found.append(abs(i - a))
            score[max(0, i - srl): i + srl] = 0
        t += 4.0
    # Regroupe les decalages voisins, puis les affine a l'echantillon pres sur la piste entiere.
    groups = []
    for lag in sorted(found):
        if groups and lag - groups[-1][-1] <= 2:
            groups[-1].append(lag)
        else:
            groups.append([lag])
    out = []
    for g in groups:
        if len(g) < 3:
            continue
        coarse = int(np.median(g)) * q
        # Un extrait d'une seconde, pris la ou la correspondance tient le mieux : on essaie les decalages voisins.
        starts = list(range(0, len(mono) - coarse - 2 * SR, 2 * SR))
        if not starts:
            continue
        start = max(starts, key=lambda a: ncc(mono[a:a + SR], mono[a + coarse:a + coarse + SR]))
        p = mono[start:start + SR]
        best = (-2.0, coarse)
        for d in range(-q - 2, q + 3):
            if start + coarse + d + SR > len(mono):
                continue
            s = ncc(p, mono[start + coarse + d:start + coarse + d + SR])
            if s > best[0]:
                best = (s, coarse + d)
        if best[0] >= 0.75:
            out.append(best[1])
    return sorted(set(out))


def run(mono: np.ndarray, lag: int):
    """Ou la correspondance a ce decalage tient : (debut, fin, fin avant le fondu final, ressemblance moyenne)."""
    win, step = SR // 2, SR // 4
    marks = []
    for a in range(0, len(mono) - lag - win, step):
        p, q = mono[a:a + win], mono[a + lag:a + lag + win]
        ratio = 10 * math.log10((float(np.dot(q, q)) + 1e-12) / (float(np.dot(p, p)) + 1e-12))
        marks.append((a, ncc(p, q), ratio))
    # La plus longue suite de fenetres qui se ressemblent (un trou d'une fenetre est tolere).
    best, cur, gap = [], [], 0
    for m in marks:
        if m[1] >= 0.7:
            cur.append(m)
            gap = 0
        elif cur and gap < 1:
            gap += 1
        else:
            if len(cur) > len(best):
                best = cur
            cur, gap = [], 0
    if len(cur) > len(best):
        best = cur
    if not best:
        return None
    # Le fondu final : le second passage devient durablement plus faible que le premier.
    clean = best[-1][0] + win
    for k in range(len(best) - 8):
        if all(m[2] < -1.0 for m in best[k:k + 8]):
            clean = best[k][0]
            break
    return best[0][0], best[-1][0] + win, clean, float(np.mean([m[1] for m in best]))


def calm_spots(mono: np.ndarray, lag: int, first: int, last: int, count: int = 3):
    """Dans [first, last], les instants ou raccorder : les deux passages s'y ressemblent le plus, et rien n'y claque."""
    hop, n = SR // 100, 2048
    window = np.hanning(n)
    flux, prev = [], None
    for a in range(first - SR // 2, last + SR // 2, hop):
        seg = mono[max(0, a - n // 2): max(0, a - n // 2) + n]
        if len(seg) < n:
            flux.append(0.0)
            continue
        mag = np.log1p(100 * np.abs(np.fft.rfft(seg * window)))
        flux.append(0.0 if prev is None else float(np.sum(np.maximum(0, mag - prev))))
        prev = mag
    flux = np.array(flux)
    base = first - SR // 2
    rows = []
    for s in range(first, last, hop):
        k = (s - base) // hop
        around = float(np.max(flux[max(0, k - 30): k + 10]))            # de 300 ms avant a 100 ms apres
        w = int(0.3 * SR)
        sim = ncc(mono[s - w:s], mono[s - w + lag:s + lag])
        level = 10 * math.log10(float(np.mean(mono[s - w:s] ** 2)) + 1e-12)
        rows.append((s, sim, around, level))
    if not rows:
        return []
    sims = np.array([r[1] for r in rows])
    good = [r for r in rows if r[1] >= np.percentile(sims, 80)]
    good.sort(key=lambda r: r[2])
    picks = []
    for r in good:
        if all(abs(r[0] - p[0]) > SR for p in picks):
            picks.append(r)
        if len(picks) >= count:
            break
    return picks


def analyse(src: Path) -> int:
    x = decode(src)
    mono = x.mean(axis=1).astype(np.float64)
    print(f"{src.name} : {clock(len(mono) / SR)} ({len(mono)} echantillons a {SR} Hz)")
    lags = repeats(mono)
    if not lags:
        print("Aucun passage rejoue a l'identique : cette piste n'a pas de boucle toute faite.")
        print("Il faut alors choisir a l'oreille une section qui tourne bien, et la donner avec --debut et --longueur.")
        return 1
    found = []
    for lag in lags:
        r = run(mono, lag)
        if r and r[1] - r[0] > 4 * SR:
            found.append((lag,) + r)
    found.sort(key=lambda f: f[1])
    for lag, a, b, clean, sim in found:
        print(f"\nDecalage de {lag} echantillons ({lag / SR:.4f} s), ressemblance {sim:.2f} :")
        print(f"  le passage de {clock(a / SR)} a {clock(b / SR)} est rejoue de {clock((a + lag) / SR)} a {clock((b + lag) / SR)}")
        if clean < b:
            print(f"  le second passage s'efface (fondu final) a partir de {clock((clean + lag) / SR)}")
        margin = int(0.5 * SR)
        spots = calm_spots(mono, lag, a + margin, max(a + margin + 1, clean - margin))
        for s, sim_s, _, level in spots:
            print(f"  raccord possible : --debut {s / SR:.2f} --longueur {lag}   (ressemblance {sim_s:.2f}, niveau {level:.0f} dB ;"
                  f" introduction {clock(s / SR)}, boucle {lag / SR:.1f} s)")
    if found:
        print("\nLa boucle la plus tardive laisse entendre tout le morceau avant de tourner : c'est en general la bonne.")
    return 0


# ------------------------------------------------------------------------------------------------ construction

def loudness(x: np.ndarray):
    """Le niveau percu (LUFS) et la crete vraie (dB) d'un signal, mesures par ffmpeg."""
    r = subprocess.run(["ffmpeg", "-hide_banner", "-nostats", "-f", "f32le", "-ar", str(SR), "-ac", "2", "-i", "-",
                        "-af", "loudnorm=I=-16:TP=-1.5:LRA=11:print_format=json", "-f", "null", "-"],
                       input=x.astype(np.float32).tobytes(), capture_output=True)
    m = re.search(r"\{[^{}]*\"input_i\"[^{}]*\}", r.stderr.decode("utf-8", "replace"))
    if not m:
        return None
    info = json.loads(m.group(0))
    return float(info["input_i"]), float(info["input_tp"])


def encode(x: np.ndarray, dst: Path, relay_ms: int, follow: str):
    dst.parent.mkdir(parents=True, exist_ok=True)
    cmd = ["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-f", "f32le", "-ar", str(SR), "-ac", "2", "-i", "-",
           "-map_metadata", "-1", "-metadata", f"CROSSFIGHT_RELAIS={relay_ms}", "-metadata", f"CROSSFIGHT_SUITE={follow}",
           "-c:a", "libvorbis", "-q:a", "3", str(dst)]
    p = subprocess.Popen(cmd, stdin=subprocess.PIPE)
    data = np.clip(x, -1.0, 1.0).astype(np.float32)
    for i in range(0, len(data), SR * 30):
        p.stdin.write(data[i:i + SR * 30].tobytes())
    p.stdin.close()
    if p.wait() != 0:
        raise SystemExit("ffmpeg n'a pas pu ecrire " + str(dst))


def seconds_of(f: Path) -> float:
    r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(f)],
                       capture_output=True, text=True)
    return float(r.stdout.strip())


def slug(text: str) -> str:
    return re.sub(r"_+", "_", re.sub(r"[^a-z0-9]+", "_", text.lower().replace("'", ""))).strip("_")


def option(argv, name, default):
    if name in argv:
        i = argv.index(name)
        value = argv[i + 1]
        del argv[i:i + 2]
        return value
    return default


def build(argv) -> int:
    private = "--prive" in argv
    argv = [a for a in argv if a != "--prive"]
    start_s = option(argv, "--debut", None)
    length = option(argv, "--longueur", None)
    turns = int(option(argv, "--tours", 8))
    after = int(option(argv, "--suite", 4))
    fade = int(int(option(argv, "--fondu", 300)) * SR / 1000)
    relay = int(int(option(argv, "--relais", 250)) * SR / 1000)
    level = float(option(argv, "--niveau", -16))
    if len(argv) != 3 or start_s is None or length is None:
        print(__doc__)
        return 1
    src, name = Path(argv[1]), slug(argv[2])
    if not src.exists():
        print("Fichier introuvable :", src)
        return 1
    x = decode(src)
    s, n = int(round(float(start_s) * SR)), int(length)
    if s - fade < 0 or s + n + relay > len(x):
        print(f"La boucle ({clock(s / SR)} + {n / SR:.2f} s) sort de la piste ({clock(len(x) / SR)}).")
        return 1
    mono = x.mean(axis=1).astype(np.float64)
    w = int(0.3 * SR)
    before = ncc(mono[s - w:s], mono[s + n - w:s + n])
    behind = ncc(mono[s:s + w], mono[s + n:s + n + w])
    print(f"ressemblance des deux passages au raccord : {before:.2f} avant, {behind:.2f} apres")
    if min(before, behind) < 0.6:
        print("ATTENTION : ce qui suit la fin de la boucle ne ressemble pas a ce qui suit son debut ; le raccord s'entendra.")

    # Le silence du debut est retire : la musique part des la premiere note.
    loud = np.nonzero(np.abs(mono[:s]) > 10 ** (-60 / 20))[0]
    head = max(0, int(loud[0]) - SR // 100) if len(loud) else 0
    intro = x[head:s]
    # Un tour : la piste de s a s + n ; ses derniers instants glissent du second passage vers le premier,
    # pour que la fin du tour soit exactement ce qui precede son debut.
    lap = x[s:s + n].copy()
    ramp = (np.arange(1, fade + 1, dtype=np.float32) / (fade + 1))[:, None]
    lap[n - fade:] = (1 - ramp) * x[s + n - fade:s + n] + ramp * x[s - fade:s]
    # Le relais entre deux fichiers : le debut d'un tour, en fondu sortant a la fin de l'un, entrant au debut de l'autre.
    down = (1 - np.arange(1, relay + 1, dtype=np.float32) / (relay + 1))[:, None]
    tail = lap[:relay] * down
    first = np.concatenate([intro] + [lap] * turns + [tail])
    more = np.concatenate([lap] * after + [tail])
    more[:relay] *= (1 - down)

    # Meme niveau pour les deux fichiers : un gain fixe (un niveau qui varierait s'entendrait au raccord).
    measure = loudness(np.concatenate([intro, lap]))
    gain = 1.0
    if measure:
        db = min(level - measure[0], -1.5 - measure[1])
        gain = 10 ** (db / 20)
        print(f"niveau : {measure[0]:.1f} LUFS, crete {measure[1]:.1f} dB -> gain de {db:+.1f} dB")
    sounds = ROOT / ("resourcepack-prive" if private else "resourcepack") / "assets" / "crossfight" / "sounds" / "music"
    a, b = sounds / (name + ".ogg"), sounds / (name + SUFFIX + ".ogg")
    relay_first = int(round((len(intro) + turns * n) * 1000 / SR))
    relay_more = int(round(after * n * 1000 / SR))
    encode(first * gain, a, relay_first, name + SUFFIX)
    encode(more * gain, b, relay_more, name + SUFFIX)
    for f, samples, r in ((a, len(first), relay_first), (b, len(more), relay_more)):
        got = seconds_of(f)
        ok = "" if abs(got - samples / SR) < 0.01 else f"  ATTENTION : {got:.3f} s lus, {samples / SR:.3f} s attendus"
        print(f"{f.relative_to(ROOT)} : {clock(samples / SR)}, {f.stat().st_size // 1024} Ko, relaye a {clock(r / 1000)}{ok}")
    print(f"introduction {clock(len(intro) / SR)}, puis un tour de {n / SR:.2f} s : {turns} dans le premier fichier, {after} dans la suite")
    print("Ensuite : python3 tools/build_pack.py")
    return 0


def main() -> int:
    if len(sys.argv) >= 3 and sys.argv[1] == "analyse":
        src = Path(sys.argv[2])
        if not src.exists():
            print("Fichier introuvable :", src)
            return 1
        return analyse(src)
    return build(list(sys.argv))


if __name__ == "__main__":
    sys.exit(main())
