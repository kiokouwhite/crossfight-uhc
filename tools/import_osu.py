#!/usr/bin/env python3
"""Importe une carte osu!mania a quatre touches dans la forge d'Anna (game/Rhythm.java).

Usage :  python3 tools/import_osu.py <carte.osz> <facile|moyen|difficile> [--difficulte <mot>] [--nom <nom>] [--decalage <ms>]

Une archive .osz contient la musique et une ou plusieurs cartes (.osu). Le script garde les cartes du mode mania a
quatre touches ; s'il y en a plusieurs, --difficulte en choisit une par un mot de son nom (sans lui, il les liste).
Il ecrit deux fichiers, jamais versionnes :
  resourcepack-prive/assets/crossfight/sounds/music/forge_<nom>.ogg   la musique, pour le pack complet
  prive/musiques/<nom>.txt                                           la carte, a deposer dans plugins/CrossfightUHC/musiques/
Ensuite : python3 tools/build_pack.py, deposer le pack et la carte sur le serveur, puis /cf rythme recharger.

Une note tenue devient une note simple (son debut). --decalage avance (negatif) ou retarde (positif) toutes les notes,
si la musique et les notes ne tombent pas ensemble en jeu. Trois musiques importees par difficulte au plus sont
proposees ; elles prennent la place de celles du plugin.

La musique et la carte appartiennent a leurs auteurs : elles restent dans le pack prive et sur le serveur.
"""
import argparse
import re
import subprocess
import sys
import tempfile
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOUNDS = ROOT / "resourcepack-prive" / "assets" / "crossfight" / "sounds" / "music"
CHARTS = ROOT / "prive" / "musiques"


def parse(text):
    """Les sections d'un fichier .osu : les couples cle/valeur des sections d'en-tete, et les lignes de [HitObjects]."""
    section, fields, hits = "", {}, []
    for line in text.splitlines():
        line = line.strip()
        if not line or line.startswith("//"):
            continue
        if line.startswith("[") and line.endswith("]"):
            section = line[1:-1]
        elif section == "HitObjects":
            hits.append(line)
        elif ":" in line and section in ("General", "Metadata", "Difficulty"):
            key, value = line.split(":", 1)
            fields[key.strip()] = value.strip()
    return fields, hits


def main():
    ap = argparse.ArgumentParser(description=__doc__.split("\n")[0])
    ap.add_argument("archive")
    ap.add_argument("niveau", choices=["facile", "moyen", "difficile"])
    ap.add_argument("--difficulte", default="")
    ap.add_argument("--nom", default="")
    ap.add_argument("--decalage", type=int, default=0)
    args = ap.parse_args()

    with zipfile.ZipFile(args.archive) as z:
        charts = []
        for name in z.namelist():
            if not name.lower().endswith(".osu"):
                continue
            fields, hits = parse(z.read(name).decode("utf-8", errors="replace"))
            if fields.get("Mode") != "3" or round(float(fields.get("CircleSize", "0"))) != 4:
                continue
            charts.append((fields, hits))
        if not charts:
            sys.exit("Aucune carte mania a quatre touches dans cette archive.")
        if args.difficulte:
            charts = [c for c in charts if args.difficulte.lower() in c[0].get("Version", "").lower()]
        if len(charts) != 1:
            print("Cartes a quatre touches (choisir avec --difficulte <mot>) :")
            for fields, hits in charts:
                print(f"  {fields.get('Version', '?')} : {len(hits)} notes")
            sys.exit(1 if not charts else 2)
        fields, hits = charts[0]
        audio = fields.get("AudioFilename", "")
        member = next((n for n in z.namelist() if n.lower() == audio.lower()), None)
        if member is None:
            sys.exit(f"Musique introuvable dans l'archive : {audio}")
        title = fields.get("TitleUnicode") or fields.get("Title") or Path(args.archive).stem
        name = re.sub(r"[^a-z0-9]+", "_", (args.nom or fields.get("Title") or Path(args.archive).stem).lower()).strip("_")[:32] or "musique"
        notes = []
        for line in hits:
            parts = line.split(",")
            if len(parts) < 4:
                continue
            notes.append((int(float(parts[2])) + args.decalage, min(3, max(0, int(float(parts[0])) * 4 // 512))))
        notes.sort()
        SOUNDS.mkdir(parents=True, exist_ok=True)
        CHARTS.mkdir(parents=True, exist_ok=True)
        with tempfile.TemporaryDirectory() as tmp:
            source = Path(tmp) / ("source" + Path(member).suffix.lower())
            source.write_bytes(z.read(member))
            out = SOUNDS / f"forge_{name}.ogg"
            done = subprocess.run(["ffmpeg", "-loglevel", "error", "-y", "-i", str(source), "-vn", "-c:a", "libvorbis", "-q:a", "4", str(out)])
            if done.returncode != 0:
                sys.exit("ffmpeg n'a pas pu convertir la musique.")
    lines = [f"# {fields.get('Artist', '?')} - {title} [{fields.get('Version', '?')}], carte de {fields.get('Creator', '?')}",
             f"titre: {title}", f"niveau: {args.niveau}", f"son: crossfight:music.forge_{name}"]
    lines += [f"{ms} {lane}" for ms, lane in notes]
    chart = CHARTS / f"{name}.txt"
    chart.write_text("\n".join(lines) + "\n", encoding="utf-8")
    seconds = notes[-1][0] / 1000 if notes else 0
    print(f"{title} [{fields.get('Version', '?')}] : {len(notes)} notes, {seconds:.0f} s ({len(notes) / max(1, seconds):.1f} par seconde)")
    print(f"  musique : {out.relative_to(ROOT)}")
    print(f"  carte   : {chart.relative_to(ROOT)}  -> a deposer dans plugins/CrossfightUHC/musiques/")
    print("Ensuite : python3 tools/build_pack.py, puis /cf rythme recharger sur le serveur.")


if __name__ == "__main__":
    main()
