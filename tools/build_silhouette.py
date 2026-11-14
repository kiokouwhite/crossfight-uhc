#!/usr/bin/env python3
"""Fabrique le modele articule neutre "silhouette" du pack public : un personnage sans skin.

Usage :  python3 tools/build_silhouette.py

L'ecran de selection le montre, teint a la couleur du personnage, pour ceux qui n'ont pas encore de skin
dans le pack (game/SelectionStage.java). Le skin est dessine ici : blanc, un peu plus sombre vers le bas de
chaque partie, un point d'interrogation sur le visage. Les modeles sont ceux de tools/add_skin.py.
"""
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent

# Chaque partie dans un skin de 64x64 : (u, v, largeur, hauteur, profondeur).
BOXES = {"head": (0, 0, 8, 8, 8), "body": (16, 16, 8, 12, 4), "arm_r": (40, 16, 4, 12, 4), "arm_l": (32, 48, 4, 12, 4),
         "leg_r": (0, 16, 4, 12, 4), "leg_l": (16, 48, 4, 12, 4)}
QUESTION = [".XX.", "X..X", "..X.", ".X..", "....", ".X.."]


def skin() -> Image.Image:
    img = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    for u, v, w, h, d in BOXES.values():
        img.paste((255, 255, 255, 255), (u + d, v, u + d + 2 * w, v + d))            # dessus, dessous
        for y in range(h):                                                            # les quatre cotes, en degrade
            g = round(255 - 60 * y / max(1, h - 1))
            img.paste((g, g, g, 255), (u, v + d + y, u + 2 * d + 2 * w, v + d + y + 1))
    u, v, w, h, d = BOXES["head"]
    for row, line in enumerate(QUESTION):
        for col, ch in enumerate(line):
            if ch == "X":
                img.putpixel((u + d + 2 + col, v + d + 1 + row), (120, 120, 120, 255))
    return img


def main() -> int:
    with tempfile.TemporaryDirectory() as tmp:
        png = Path(tmp) / "silhouette.png"
        skin().save(png)
        return subprocess.call([sys.executable, str(ROOT / "tools" / "add_skin.py"), str(png), "silhouette"])


if __name__ == "__main__":
    sys.exit(main())
