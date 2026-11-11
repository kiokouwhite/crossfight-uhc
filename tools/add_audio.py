#!/usr/bin/env python3
"""Ajoute un fichier audio au pack de ressources, au bon format et au bon endroit.

Usage :
  python3 tools/add_audio.py music <fichier> [nom_de_piste]     musique de combat (stereo)
  python3 tools/add_audio.py voice <fichier> <cle>              replique (mono), ex. netsu_ult
  python3 tools/add_audio.py ability <fichier> <id_capacite>    bruitage propre a une capacite (mono)

Sans nom de piste, la musique prend le nom du fichier, mis en minuscules, les espaces et la
ponctuation remplaces par "_" : c'est le nom a ecrire dans resourcepack/music.txt.
Le fichier source peut etre un mp3, wav, flac, m4a, ogg... (tout ce que lit ffmpeg).
Ensuite : python3 tools/build_pack.py
"""
import re
import subprocess
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SOUNDS = ROOT / "resourcepack" / "assets" / "crossfight" / "sounds"


def slug(text: str) -> str:
    return re.sub(r"_+", "_", re.sub(r"[^a-z0-9]+", "_", text.lower().replace("'", ""))).strip("_")


def main() -> int:
    if len(sys.argv) < 3 or sys.argv[1] not in ("music", "voice", "ability") or (sys.argv[1] != "music" and len(sys.argv) < 4):
        print(__doc__)
        return 1
    kind, src = sys.argv[1], Path(sys.argv[2])
    if not src.exists():
        print("Fichier introuvable :", src)
        return 1
    name = slug(sys.argv[3] if len(sys.argv) > 3 else src.stem)
    dst = SOUNDS / kind / (name + ".ogg")
    dst.parent.mkdir(parents=True, exist_ok=True)
    if kind == "music":
        # Stereo, niveau sonore uniformise entre les pistes, qualite 3 (environ 110 kbit/s).
        # Les silences de debut et de fin sont retires : la piste s'enchaine sur elle-meme sans blanc.
        trim = "silenceremove=start_periods=1:start_threshold=-60dB:start_silence=0"
        loop = f"{trim},areverse,{trim},areverse"
        audio = ["-ac", "2", "-ar", "44100", "-af", loop + ",loudnorm=I=-16:TP=-1.5:LRA=11", "-c:a", "libvorbis", "-q:a", "3"]
    else:
        # Mono : indispensable pour que le son soit entendu a l'endroit du personnage.
        audio = ["-ac", "1", "-ar", "44100", "-af", "loudnorm=I=-16:TP=-1.5:LRA=11", "-c:a", "libvorbis", "-q:a", "4"]
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(src), "-vn", "-map_metadata", "-1"] + audio + [str(dst)], check=True)
    print(f"{dst.relative_to(ROOT)} ({dst.stat().st_size // 1024} Ko)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
