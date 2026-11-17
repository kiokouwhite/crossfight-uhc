#!/usr/bin/env python3
"""Habille le menu d'enchantement d'Anna avec le pack d'interface acheté à Franuka (pack privé seulement).

Usage : python3 tools/build_menu_franuka.py

Le « RPG UI pack » de Franuka (https://franuka.itch.io/rpg-ui-pack) est un pack payant : ses fichiers restent dans
resourcepack-prive/ui-franuka/ (jamais versionné) et l'image composée ici va dans resourcepack-prive/assets/, où elle
remplace, dans le pack complet, la carte dessinée par tools/build_menu_anna.py (même nom, même taille, mêmes cases :
le plugin n'a rien à savoir). Sans les fichiers du pack, ce script ne fait rien et le pack garde la carte publique.

Deux images de 176 x 222, la taille d'un coffre de six rangées, sur une fenêtre lavande. La première : à gauche la
couverture (« ANNA », les prix), au milieu la case où poser l'objet, à droite les quatre pièces d'armure portées. La
seconde : l'objet en haut, et dessous les trois niveaux proposés, chacun avec son prix. En bas, l'inventaire.
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "resourcepack-prive" / "ui-franuka"
ONE = SRC / "Individual files" / "1x"
OUT = ROOT / "resourcepack-prive" / "assets" / "crossfight" / "textures" / "font" / "menu_anna.png"
W, H = 176, 222
BROWN, CREAM = (92, 58, 64, 255), (255, 240, 220, 255)


def sprite(folder, name):
    return Image.open(ONE / folder / (name + ".png")).convert("RGBA")


def nine(box, w, h, t=16):
    """Étire un cadre de 3 x 3 tuiles (t pixels chacune) à w x h : les coins tels quels, les bords et le centre répétés."""
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    def tile(sx, sy, x1, y1, x2, y2):
        piece = box.crop((sx, sy, sx + t, sy + t))
        for y in range(y1, y2, t):
            for x in range(x1, x2, t):
                out.alpha_composite(piece.crop((0, 0, min(t, x2 - x), min(t, y2 - y))), (x, y))
    tile(t, t, t, t, w - t, h - t)
    tile(t, 0, t, 0, w - t, t); tile(t, 2 * t, t, h - t, w - t, h)
    tile(0, t, 0, t, t, h - t); tile(2 * t, t, w - t, t, w, h - t)
    for sx, sy, x, y in ((0, 0, 0, 0), (2 * t, 0, w - t, 0), (0, 2 * t, 0, h - t), (2 * t, 2 * t, w - t, h - t)):
        out.alpha_composite(box.crop((sx, sy, sx + t, sy + t)), (x, y))
    return out


def wide(banner, w, t=16):
    """Allonge une bannière de trois tuiles en répétant celle du milieu."""
    out = Image.new("RGBA", (w, banner.height), (0, 0, 0, 0))
    for x in range(t, w - t, t):
        out.alpha_composite(banner.crop((t, 0, 2 * t, banner.height)).crop((0, 0, min(t, w - t - x), banner.height)), (x, 0))
    out.alpha_composite(banner.crop((0, 0, t, banner.height)), (0, 0))
    out.alpha_composite(banner.crop((2 * t, 0, 3 * t, banner.height)), (w - t, 0))
    return out


def write(im, cx, y, text, font, fill, shadow=None):
    """Écrit text centré sur cx, sans lissage (les polices du pack sont faites pour ça)."""
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    x = cx - round(d.textlength(text, font=font) / 2)
    if shadow:
        d.text((x, y + 1), text, font=font, fill=shadow)
    d.text((x, y), text, font=font, fill=fill)


NAVY = (62, 66, 110, 255)


def window(mine):
    """La fenetre lavande, avec les cases de l'inventaire du joueur en bas."""
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    im.alpha_composite(nine(sprite("Background boxes", "BGbox_01B"), W, H))
    for col in range(9):
        for y in (140, 158, 176, 198):
            im.alpha_composite(mine, (8 + 18 * col, y))
    return im


def framed(im, slot, col, row):
    """Une case mise en valeur : un cadre ouvrage autour d'elle (le cadre du pack a six pixels vides autour de lui)."""
    x, y = 8 + 18 * col, 18 + 18 * row
    im.alpha_composite(nine(sprite("Background boxes", "BGbox_03A"), 44, 44), (x + 8 - 22, y + 8 - 22))
    im.alpha_composite(slot, (x, y))


def main():
    if not ONE.is_dir():
        print("pack de Franuka absent (resourcepack-prive/ui-franuka/) : rien a faire, le pack garde les cartes publiques")
        return 0
    title = ImageFont.truetype(str(SRC / "Fonts" / "FantasyRPGtitle (size 17).ttf"), 17)
    text = ImageFont.truetype(str(SRC / "Fonts" / "FantasyRPGtext (size 12).ttf"), 12)
    slot, mine = sprite("Item slots", "Slot_01_Empty"), sprite("Item slots", "Slot_02_Empty")
    OUT.parent.mkdir(parents=True, exist_ok=True)

    # 1. Poser l'objet : la couverture a gauche, la case au milieu de la page, les pieces portees a droite.
    im = window(mine)
    im.alpha_composite(nine(sprite("Background boxes", "BGbox_01A"), 78, 114), (5, 13))
    for k, name in enumerate(("Icon_09", "Icon_13", "Icon_10")):          # une epee, une baguette, un bouclier
        im.alpha_composite(sprite("Mini icons", name), (18 + 18 * k, 22))
    write(im, 44, 41, "ANNA", title, (236, 170, 60, 255), BROWN)
    im.alpha_composite(sprite("Dividers", "Divider_05"), (20, 56))
    write(im, 44, 74, "Enchantements", text, BROWN)
    im.alpha_composite(wide(sprite("Title banners", "BannerSmall_03C_Normal"), 58), (15, 94))
    write(im, 44, 96, "1 - 5 niv.", text, BROWN)
    write(im, 106, 22, "Pose ton", text, NAVY)
    write(im, 106, 32, "objet ici", text, NAVY)
    framed(im, slot, 5, 2)                                                 # la case 23 du coffre
    write(im, 106, 92, "ou clique", text, NAVY)
    write(im, 106, 102, "une pièce", text, NAVY)
    write(im, 106, 112, "portée ->", text, NAVY)
    for row in range(1, 5):                                                # les quatre pieces d'armure portees
        im.alpha_composite(slot, (8 + 18 * 7, 18 + 18 * row))
    im.save(OUT)

    # 2. Choisir le niveau : l'objet en haut, trois niveaux dessous, chacun avec son prix.
    im = window(mine)
    im.alpha_composite(wide(sprite("Title banners", "BannerSmall_02B"), 116), (30, 10))
    write(im, 88, 12, "Choisis le niveau", text, (255, 244, 226, 255))
    framed(im, slot, 4, 1)                                                 # la case 13 : l'objet
    for k, (col, price) in enumerate(((2, "1 niv."), (4, "2 niv."), (6, "5 niv.")), 1):
        x = 8 + 18 * col
        im.alpha_composite(nine(sprite("Background boxes", "BGbox_01A"), 46, 70), (x + 8 - 23, 56))
        im.alpha_composite(slot, (x, 72))                                  # les cases 29, 31 et 33
        write(im, x + 8, 89, "I" * k, title, (236, 170, 60, 255), BROWN)
        write(im, x + 8, 102, price, text, BROWN)
    im.save(OUT.with_name("menu_anna2.png"))
    print(OUT.relative_to(ROOT), "et menu_anna2.png", im.size)
    return 0


if __name__ == "__main__":
    sys.exit(main())
