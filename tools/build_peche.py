#!/usr/bin/env python3
"""Dessine le cercle de la pêche (quête de Garreg Mach) : un anneau qui se referme sur une cible, au milieu de l'écran.

Usage : python3 tools/build_peche.py

Dix-sept images de 96 x 96 : la cible (un anneau fixe) et l'anneau mobile, de plus en plus petit ; les douze premières
le montrent qui approche, la treizième posé sur la cible, les quatre dernières passé au travers. Le plugin les affiche
en titre, donc centrées (game/Quests.java) : une image haute de 24 unités de police fait 96 pixels d'interface, un
pixel d'image par pixel d'écran. Tout est blanc : le plugin teinte l'image (blanc, jaune près du but, vert dessus).
Rien n'est repris d'un jeu. Écrit resourcepack/assets/crossfight/font/peche.json et textures/font/peche_*.png.
"""
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / "resourcepack" / "assets" / "crossfight"
SIZE, TARGET, STEP = 96, 14.0, 2.6
FIRST, LAST = 12, -4          # l'ecart a la cible, en crans


def ring(d, radius, width, alpha):
    c = SIZE / 2
    d.ellipse([c - radius, c - radius, c + radius, c + radius], outline=(255, 255, 255, alpha), width=width)


def main():
    (ROOT / "textures" / "font").mkdir(parents=True, exist_ok=True)
    chars = []
    for i, gap in enumerate(range(FIRST, LAST - 1, -1)):
        big = Image.new("RGBA", (SIZE * 4, SIZE * 4), (0, 0, 0, 0))
        d = ImageDraw.Draw(big)
        c = SIZE * 2
        # la cible : un anneau epais, a demi transparent, et un point au centre
        r = TARGET * 4
        d.ellipse([c - r, c - r, c + r, c + r], outline=(255, 255, 255, 150), width=12)
        d.ellipse([c - 6, c - 6, c + 6, c + 6], fill=(255, 255, 255, 200))
        # l'anneau mobile
        r = max(2.0, TARGET + STEP * gap) * 4
        d.ellipse([c - r, c - r, c + r, c + r], outline=(255, 255, 255, 255), width=8)
        im = big.resize((SIZE, SIZE), Image.LANCZOS)
        im.save(ROOT / "textures" / "font" / f"peche_{i:02d}.png")
        chars.append(chr(0xE200 + i))
    providers = [{"type": "bitmap", "file": f"crossfight:font/peche_{i:02d}.png", "height": 24, "ascent": 9, "chars": [ch]} for i, ch in enumerate(chars)]
    (ROOT / "font" / "peche.json").write_text(json.dumps({"providers": providers}, ensure_ascii=True, indent=1), encoding="utf-8")
    print(len(chars), "images")


if __name__ == "__main__":
    main()
