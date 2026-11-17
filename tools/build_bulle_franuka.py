#!/usr/bin/env python3
"""Refait la bulle de dialogue avec le pack d'interface acheté à Franuka (pack privé seulement).

Usage : python3 tools/build_bulle_franuka.py

Même principe que tools/build_menu_franuka.py : les fichiers du « RPG UI pack » de Franuka restent dans
resourcepack-prive/ui-franuka/, et les images composées ici vont dans resourcepack-prive/assets/, où elles remplacent
dans le pack complet celles de tools/build_bulle.py (mêmes noms, mêmes tailles : la police et le plugin ne changent
pas). La bulle : le cadre clair du pack, étiré à la taille voulue, et une pointe ajoutée ici à ses couleurs.
"""
import sys
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_bulle as base
from build_menu_franuka import ONE, nine, sprite

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "resourcepack-prive" / "assets" / "crossfight" / "textures" / "font"


def main():
    if not ONE.is_dir():
        print("pack de Franuka absent (resourcepack-prive/ui-franuka/) : rien a faire, le pack garde la bulle publique")
        return 0
    OUT.mkdir(parents=True, exist_ok=True)
    box = sprite("Background boxes", "BGbox_04C")
    fill, edge = box.getpixel((24, 24)), box.getpixel((7, 24))
    for n in range(1, base.LINES + 1):
        h = base.height(n) - base.TAIL
        im = Image.new("RGBA", (base.W, base.height(n)), (0, 0, 0, 0))
        # le cadre du pack a quelques pixels vides autour de lui (3 sur les cotes, 4 en haut et en bas) : on les rogne
        im.alpha_composite(nine(box, base.W + 6, h + 8).crop((3, 4, base.W + 3, h + 4)), (0, 0))
        # son bord du bas, au milieu, pour y accrocher la pointe
        cx = base.W // 2
        bottom = max(y for y in range(h) if im.getpixel((cx, y))[3] > 0)
        tip = Image.new("RGBA", (base.W, bottom + 1 + base.TAIL), (0, 0, 0, 0))
        tip.alpha_composite(im.crop((0, 0, base.W, bottom + 1)))
        base.tail(tip, fill, edge)
        im.paste(tip, (0, 0))
        im.save(OUT / f"bulle_{n:02d}.png")
    print(base.LINES, "bulles composees avec le pack")
    return 0


if __name__ == "__main__":
    sys.exit(main())
