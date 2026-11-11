#!/usr/bin/env python3
"""Construit le pack de ressources de Crossfight UHC (sons et voix).

Usage :  python3 tools/build_pack.py

Lit      resourcepack/assets/crossfight/sounds/**.ogg   les sons (mono, Ogg Vorbis)
         resourcepack/abilities.txt                     "id_de_capacite = categorie" (bruitage d'une capacite)
Ecrit    resourcepack/assets/crossfight/sounds.json     la declaration des sons
         dist/CrossfightPack.zip                        le pack envoye aux joueurs
         src/main/resources/sounds.txt                  la liste des sons, embarquee dans le plugin
         src/main/resources/pack.sha1                   l'empreinte du pack, embarquee dans le plugin

Conventions de nommage, sous sounds/ :
  sfx/<categorie>/<n>.ogg     bibliotheque de bruitages : tous les fichiers d'un dossier sont des variantes
  ability/<id>.ogg            bruitage propre a une capacite (prioritaire sur abilities.txt)
  voice/<cle>.ogg             une replique ; <cle>_1.ogg, <cle>_2.ogg... = variantes tirees au hasard
  music/<piste>.ogg           une musique de combat (stereo) ; resourcepack/music.txt dit quelles
                              licences la declenchent : "licence_a + licence_b = piste"

Apres un changement : relancer ce script, recompiler le plugin, publier dist/.
"""
import hashlib
import json
import re
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "resourcepack"
SOUNDS = PACK / "assets" / "crossfight" / "sounds"
NS = "crossfight"


def variants(folder: Path):
    """Regroupe les fichiers d'un dossier : 'cle_1.ogg', 'cle_2.ogg' -> 'cle' ; 'cle.ogg' -> 'cle'."""
    groups = {}
    if not folder.is_dir():
        return groups
    for f in sorted(folder.glob("*.ogg")):
        m = re.fullmatch(r"(.+)_(\d+)", f.stem)
        key = m.group(1) if m else f.stem
        groups.setdefault(key, []).append(f)
    return groups


def ref(f: Path) -> str:
    return NS + ":" + f.relative_to(SOUNDS).with_suffix("").as_posix()


def main() -> int:
    events = {}

    # Bibliotheque : un evenement par dossier de categorie.
    library = {}
    sfx = SOUNDS / "sfx"
    if sfx.is_dir():
        for cat in sorted(p for p in sfx.iterdir() if p.is_dir()):
            files = sorted(cat.glob("*.ogg"))
            if files:
                library[cat.name] = files
                events["sfx." + cat.name] = files

    # Bruitages des capacites : la correspondance, puis les fichiers propres qui la remplacent.
    mapping = PACK / "abilities.txt"
    unknown = []
    if mapping.exists():
        for n, line in enumerate(mapping.read_text(encoding="utf-8").splitlines(), 1):
            line = line.split("#", 1)[0].strip()
            if not line:
                continue
            if "=" not in line:
                print(f"abilities.txt ligne {n} ignoree : {line}")
                continue
            ability, cat = (x.strip() for x in line.split("=", 1))
            if cat in ("", "-", "aucun"):
                continue
            if cat not in library:
                unknown.append(f"{ability} -> {cat}")
                continue
            events["ability." + ability] = library[cat]
    for key, files in variants(SOUNDS / "ability").items():
        events["ability." + key] = files
    for key, files in variants(SOUNDS / "voice").items():
        events["voice." + key] = files

    if unknown:
        print("Categories inconnues dans abilities.txt :", ", ".join(unknown))

    # Musiques de combat : une piste par fichier, lue en flux ; music.txt relie les licences aux pistes.
    tracks = {}
    music_dir = SOUNDS / "music"
    if music_dir.is_dir():
        for f in sorted(music_dir.glob("*.ogg")):
            r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(f)],
                               capture_output=True, text=True)
            try:
                millis = int(round(float(r.stdout.strip()) * 1000))
            except ValueError:
                print(f"duree illisible (ffprobe) : {f.name}, 180 s par defaut")
                millis = 180000
            tracks[f.stem] = millis
            events["music." + f.stem] = [f]
    pairs, missing = [], []
    music_map = PACK / "music.txt"
    if music_map.exists():
        for n, line in enumerate(music_map.read_text(encoding="utf-8").splitlines(), 1):
            line = line.split("#", 1)[0].strip()
            if not line:
                continue
            m = re.fullmatch(r"(\w+)\s*\+\s*(\w+)\s*=\s*([\w.-]+)", line)
            if not m:
                print(f"music.txt ligne {n} ignoree : {line}")
                continue
            a, b, track = m.groups()
            if track in tracks:
                pairs.append((a, b, track))
            else:
                missing.append(track)

    sounds_json = {}
    for name in sorted(events):
        if name.startswith("music."):
            sounds_json[name] = {"sounds": [{"name": ref(f), "stream": True} for f in events[name]]}
        else:
            sounds_json[name] = {"sounds": [ref(f) for f in events[name]]}
    out = PACK / "assets" / NS / "sounds.json"
    out.parent.mkdir(parents=True, exist_ok=True)
    out.write_text(json.dumps(sounds_json, indent=1, sort_keys=True) + "\n", encoding="utf-8")

    # Zip deterministe (ordre et dates fixes) : la meme entree donne la meme empreinte.
    dist = ROOT / "dist"
    dist.mkdir(exist_ok=True)
    zip_path = dist / "CrossfightPack.zip"
    used = {f for files in events.values() for f in files}
    members = [PACK / "pack.mcmeta", out] + sorted(used)
    credits = PACK / "CREDITS.md"
    if credits.exists():
        members.append(credits)
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for f in members:
            info = zipfile.ZipInfo(f.relative_to(PACK).as_posix(), date_time=(2025, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, f.read_bytes())
    sha1 = hashlib.sha1(zip_path.read_bytes()).hexdigest()

    res = ROOT / "src" / "main" / "resources"
    (res / "sounds.txt").write_text("# Genere par tools/build_pack.py\n" + "\n".join(sorted(events)) + "\n", encoding="utf-8")
    (res / "pack.sha1").write_text(sha1 + "\n", encoding="utf-8")
    lines = ["# Genere par tools/build_pack.py"]
    lines += [f"track {t} {s}" for t, s in sorted(tracks.items())]
    lines += [f"pair {a} {b} {t}" for a, b, t in pairs]
    (res / "music.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")

    kinds = {}
    for name in events:
        kinds[name.split(".", 1)[0]] = kinds.get(name.split(".", 1)[0], 0) + 1
    print(f"{len(events)} sons ({', '.join(f'{v} {k}' for k, v in sorted(kinds.items()))}), {len(used)} fichiers")
    print(f"{zip_path.relative_to(ROOT)} : {zip_path.stat().st_size // 1024} Ko, sha1 {sha1}")
    if music_map.exists():
        absent = sorted(set(missing))
        print(f"musiques : {len(tracks)} piste(s) presente(s), {len(pairs)} affrontement(s) couverts, {len(absent)} piste(s) attendue(s) mais absente(s)")
        for t in absent:
            print("  manque : sounds/music/" + t + ".ogg")
    if zip_path.stat().st_size > 100 * 1024 * 1024:
        print("ATTENTION : le pack depasse 100 Mo, la limite d'un fichier sur GitHub. Heberge-le ailleurs ou reduis les musiques.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
