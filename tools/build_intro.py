#!/usr/bin/env python3
"""Fait d'une video une suite d'images du pack, a jouer plein ecran en jeu (Minecraft ne lit pas de video).

Usage :  python3 tools/build_intro.py <video> <nom> [--prive]      puis  python3 tools/build_pack.py

Ecrit, dans le pack (prive avec --prive : une video sous droits n'est jamais versionnee) :
  assets/crossfight/textures/font/<nom>/f000.png ...   dix images par seconde, 256 x 144 (la plus grande image qu'accepte une police), en niveaux de gris
  assets/crossfight/font/<nom>.json                    une police : un caractere par image (a partir de U+E001),
                                                       et U+E000, un fond noir qui couvre tout l'ecran
  assets/crossfight/sounds/ability/<nom>.ogg           le son de la video
Le plugin affiche le fond noir en titre et l'image en sous-titre, renouveles toutes les deux ticks
(Omori.java : "espace_blanc", la video d'entree dans l'Espace Blanc). Le sous-titre est dessine a l'echelle 2 :
une image de 135 de haut y fait 270 pixels d'interface sur 480, ce qui tient sur tous les ecrans.
"""
import json
import subprocess
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
FPS, W, H = 10, 256, 144


def main() -> int:
    private = "--prive" in sys.argv
    argv = [a for a in sys.argv if a != "--prive"]
    if len(argv) != 3:
        print(__doc__)
        return 1
    src, name = Path(argv[1]), argv[2]
    base = ROOT / ("resourcepack-prive" if private else "resourcepack") / "assets" / "crossfight"
    frames = base / "textures" / "font" / name
    frames.mkdir(parents=True, exist_ok=True)
    for old in frames.glob("*.png"):
        old.unlink()
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(src), "-vf", f"fps={FPS},scale={W}:{H}", "-pix_fmt", "gray",
                    str(frames / "f%03d.png")], check=True)
    files = sorted(frames.glob("f*.png"))
    # ffmpeg numerote a partir de 1 : on repart de 0.
    for i, f in enumerate(files):
        f.rename(frames / f"f{i:03d}.png")
    Image.new("RGBA", (32, 18), (0, 0, 0, 255)).save(frames / "noir.png")
    providers = [{"type": "bitmap", "file": f"crossfight:font/{name}/noir.png", "ascent": 125, "height": 256, "chars": [""]}]
    for i in range(len(files)):
        providers.append({"type": "bitmap", "file": f"crossfight:font/{name}/f{i:03d}.png", "ascent": 79, "height": 135, "chars": [chr(0xE001 + i)]})
    (base / "font").mkdir(parents=True, exist_ok=True)
    (base / "font" / f"{name}.json").write_text(json.dumps({"providers": providers}) + "\n", encoding="utf-8")
    sound = base / "sounds" / "ability" / f"{name}.ogg"
    sound.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(src), "-vn", "-map_metadata", "-1", "-ac", "2", "-ar", "44100",
                    "-c:a", "libvorbis", "-q:a", "4", str(sound)], check=True)
    size = sum(f.stat().st_size for f in frames.glob("*.png")) // 1024
    print(f"{name} : {len(files)} images ({size} Ko), {len(files) / FPS:.1f} s, son {sound.stat().st_size // 1024} Ko")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
