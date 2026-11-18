#!/usr/bin/env python3
"""L'orbe de l'ultime, en bas à gauche de l'écran, avec le pack d'interface acheté à Franuka (pack privé seulement).

Usage : python3 tools/build_ultime_franuka.py [--apercu image.png]

Le jeu dessine en plein écran l'image « camera_overlay » d'un objet porté (hud/EffectHud.java) : ici, des images
de 960 x 540, vides sauf leur coin bas gauche, où se trouve le cadre au dragon du pack (Resource orbs,
DecoratedFrame2_Left) et, dans son anneau, l'orbe doré rempli selon la charge de l'ultime : 21 images de 0 à 100 %
par pas de 5 (ult_00 à ult_20), et deux pour l'ultime prêt, qui alternent (ult_pret_0, ult_pret_1 : l'orbe plein,
plus clair, avec des éclats). Elles vont dans resourcepack-prive/assets/crossfight/textures/overlay_ult/.
Sans les fichiers du pack de Franuka, ce script ne fait rien et le jeu n'affiche pas d'orbe.
"""
import sys
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_menu_franuka import ONE, ROOT, sprite  # noqa: E402

OUT = ROOT / "resourcepack-prive" / "assets" / "crossfight" / "textures" / "overlay_ult"
W, H = 960, 540
MARGIN = 8                       # du bord de l'ecran, en points de l'image
ORB_AT = (33, 13)                # ou poser l'orbe (48 x 48) dans le cadre (80 x 80) : au milieu de son anneau
STEPS = 20


def orb(level, ready=0):
    """Le cadre et son orbe rempli a level / STEPS (par le bas) ; ready : 1 ou 2, l'orbe plein et eclatant."""
    frame = sprite("Resource orbs", "DecoratedFrame2_Left")
    full = sprite("Resource orbs", "Orb_Energy")
    box = full.getbbox()
    # le fond de l'orbe : sa forme, dans la couleur sombre du cadre vide du pack
    dark = Image.new("RGBA", full.size, (0, 0, 0, 0))
    dark.paste(Image.new("RGBA", full.size, (72, 44, 60, 255)), (0, 0), full.getchannel("A"))
    top = box[3] - round((box[3] - box[1]) * level / STEPS)
    inside = dark.copy()
    if top < box[3]:
        inside.alpha_composite(full.crop((0, top, full.width, full.height)), (0, top))
        if 0 < level < STEPS:                                             # la surface : un trait clair
            for x in range(full.width):
                if full.getpixel((x, top))[3]:
                    inside.putpixel((x, top), (255, 244, 200, 255))
    if ready:
        px = inside.load()
        for y in range(inside.height):
            for x in range(inside.width):
                r, g, b, a = px[x, y]
                if a:
                    k = 40 if ready == 1 else 70
                    px[x, y] = (min(255, r + k), min(255, g + k), min(255, b + k), a)
    out = Image.new("RGBA", frame.size, (0, 0, 0, 0))
    out.alpha_composite(inside, ORB_AT)
    out.alpha_composite(frame)
    if ready:                                                              # des eclats autour, qui changent d'une image a l'autre
        for x, y in (((14, 44), (70, 6), (74, 62)) if ready == 1 else ((20, 60), (62, 2), (78, 40))):
            for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= x + dx < 80 and 0 <= y + dy < 80:
                    out.putpixel((x + dx, y + dy), (255, 250, 210, 255))
    return out


def screen(piece):
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    im.alpha_composite(piece, (MARGIN, H - piece.height - 1))            # tout en bas : sous la derniere ligne du tchat
    return im


def main():
    if not ONE.is_dir():
        print("pack de Franuka absent (resourcepack-prive/ui-franuka/) : rien a faire")
        return 0
    OUT.mkdir(parents=True, exist_ok=True)
    for k in range(STEPS + 1):
        screen(orb(k)).save(OUT / f"ult_{k:02d}.png")
    for k in (1, 2):
        screen(orb(STEPS, k)).save(OUT / f"ult_pret_{k - 1}.png")
    if "--apercu" in sys.argv:
        sheet = Image.new("RGBA", (6 * 84, 84), (60, 70, 90, 255))
        for i, piece in enumerate((orb(0), orb(5), orb(10), orb(15), orb(20), orb(20, 2))):
            sheet.alpha_composite(piece, (i * 84 + 2, 2))
        sheet.resize((sheet.width * 3, sheet.height * 3), Image.NEAREST).save(sys.argv[sys.argv.index("--apercu") + 1])
    print("overlay_ult :", STEPS + 3, "images", (W, H))
    return 0


if __name__ == "__main__":
    sys.exit(main())
