#!/usr/bin/env python3
"""Habille le menu d'enchantement d'Anna avec le pack d'interface acheté à Franuka (pack privé seulement).

Usage : python3 tools/build_menu_franuka.py

Le « RPG UI pack » de Franuka (https://franuka.itch.io/rpg-ui-pack) est un pack payant : ses fichiers restent dans
resourcepack-prive/ui-franuka/ (jamais versionné) et l'image composée ici va dans resourcepack-prive/assets/, où elle
remplace, dans le pack complet, la carte dessinée par tools/build_menu_anna.py (même nom, même taille, mêmes cases :
le plugin n'a rien à savoir). Sans les fichiers du pack, ce script ne fait rien et le pack garde la carte publique.

Deux images sur une fenêtre lavande qui déborde de dix pixels autour du coffre de six rangées (196 x 242 : les cases
de l'inventaire ne touchent pas son bord ; la police du pack privé, font/menu.json, cale l'image en conséquence). La première : à gauche la
couverture (« ANNA », les prix), à droite la case où poser l'objet. La
seconde : l'objet en haut, et dessous les trois niveaux proposés, chacun avec son prix. En bas, l'inventaire.

Une image retouchée à la main et déposée dans resourcepack-prive/ui-retouches/ sous le même nom (menu_anna.png,
menu_anna2.png), à la même taille (196 x 242), remplace celle que ce script compose.
"""
import json
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


MARGIN = 10                      # la fenetre deborde du coffre de dix pixels de chaque cote : les cases ne touchent plus son bord


def window(mine):
    """Le contenu d'un menu, aux coordonnees du coffre (176 x 222) : d'abord les cases de l'inventaire du joueur."""
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    for col in range(9):
        for y in (140, 158, 176, 198):
            im.alpha_composite(mine, (8 + 18 * col, y))
    return im


def framed(im, slot, col, row):
    """Une case mise en valeur : un cadre ouvrage autour d'elle (le cadre du pack a six pixels vides autour de lui)."""
    x, y = 8 + 18 * col, 18 + 18 * row
    im.alpha_composite(nine(sprite("Background boxes", "BGbox_03A"), 44, 44), (x + 8 - 22, y + 8 - 22))
    im.alpha_composite(slot, (x, y))


def keep(content, path):
    """
    Pose le contenu sur la fenêtre lavande, plus grande que le coffre, et enregistre l'image ; sauf si une retouche
    faite à la main existe sous le même nom : elle passe devant.
    """
    im = nine(sprite("Background boxes", "BGbox_01B"), W + 2 * MARGIN, H + 2 * MARGIN)
    im.alpha_composite(content, (MARGIN, MARGIN))
    own = ROOT / "resourcepack-prive" / "ui-retouches" / path.name
    if own.is_file():
        mine = Image.open(own).convert("RGBA")
        if mine.size == im.size:
            mine.save(path)
            print("  retouche reprise :", own.relative_to(ROOT))
            return
        print("  retouche ignoree (il faut", im.size, "et non", mine.size, ") :", own.relative_to(ROOT))
    im.save(path)


def main():
    if not ONE.is_dir():
        print("pack de Franuka absent (resourcepack-prive/ui-franuka/) : rien a faire, le pack garde les cartes publiques")
        return 0
    title = ImageFont.truetype(str(SRC / "Fonts" / "FantasyRPGtitle (size 17).ttf"), 17)
    text = ImageFont.truetype(str(SRC / "Fonts" / "FantasyRPGtext (size 12).ttf"), 12)
    slot, mine = sprite("Item slots", "Slot_01_Empty"), sprite("Item slots", "Slot_02_Empty")
    OUT.parent.mkdir(parents=True, exist_ok=True)

    # 1. Poser l'objet : la couverture a gauche, la case a droite.
    im = window(mine)
    im.alpha_composite(nine(sprite("Background boxes", "BGbox_01A"), 78, 114), (5, 13))
    for k, name in enumerate(("Icon_09", "Icon_13", "Icon_10")):          # une epee, une baguette, un bouclier
        im.alpha_composite(sprite("Mini icons", name), (18 + 18 * k, 22))
    write(im, 44, 41, "ANNA", title, (236, 170, 60, 255), BROWN)
    im.alpha_composite(sprite("Dividers", "Divider_05"), (20, 56))
    write(im, 44, 74, "Enchants.", text, BROWN)
    im.alpha_composite(wide(sprite("Title banners", "BannerSmall_03C_Normal"), 58), (15, 94))
    write(im, 44, 96, "1 - 5 niv.", text, BROWN)
    write(im, 124, 41, "Pose ton", text, NAVY)
    write(im, 124, 51, "objet ici", text, NAVY)
    framed(im, slot, 6, 3)                                                 # la case 33 du coffre
    keep(im, OUT)

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
    keep(im, OUT.with_name("menu_anna2.png"))
    # la police : celle du pack public, calee pour une image plus grande que le coffre
    font = {"providers": [
        {"type": "space", "advances": {"\ue100": -(8 + MARGIN)}},
        {"type": "bitmap", "file": "crossfight:font/menu_anna.png", "height": H + 2 * MARGIN, "ascent": 13 + MARGIN, "chars": ["\ue000"]},
        {"type": "bitmap", "file": "crossfight:font/menu_anna2.png", "height": H + 2 * MARGIN, "ascent": 13 + MARGIN, "chars": ["\ue001"]},
    ]}
    fonts = OUT.parent.parent.parent / "font"
    fonts.mkdir(parents=True, exist_ok=True)
    (fonts / "menu.json").write_text(json.dumps(font, ensure_ascii=True, indent=1), encoding="utf-8")
    print(OUT.relative_to(ROOT), "et menu_anna2.png", (W + 2 * MARGIN, H + 2 * MARGIN))
    return 0


if __name__ == "__main__":
    sys.exit(main())
