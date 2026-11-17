#!/usr/bin/env python3
"""Refait le panneau de quête avec le pack d'interface acheté à Franuka (pack privé seulement).

Usage : python3 tools/build_quete_franuka.py

Même principe que tools/build_menu_franuka.py : les fichiers du « RPG UI pack » de Franuka restent dans
resourcepack-prive/ui-franuka/, et les deux images composées ici vont dans resourcepack-prive/assets/, où elles
remplacent dans le pack complet celles de tools/build_quete_panneau.py (mêmes noms, mêmes tailles, mêmes cases de
lettres : la police et le plugin ne changent pas). Le panneau : un cadre pêche, une bannière lavande « QUÊTE » à cheval
sur son bord, un filet en bas. Les lettres : une police libre et grasse (la police de
texte du pack, toute petite, se lisait mal), chacune à sa largeur (font/quete_recul.json dit de combien reculer pour
chacune). Les polices du pack public sont recopiées avec les lignes à la hauteur de ce cadre.
"""
import json
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_quete_panneau as base
from build_menu_franuka import ONE, SRC, nine, sprite, wide, write

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "resourcepack-prive" / "assets" / "crossfight" / "textures" / "font"
W, H, LIFT = base.W, base.H, base.LIFT
BROWN = (92, 58, 64, 255)


def panel(title):
    im = Image.new("RGBA", (W, H + LIFT), (0, 0, 0, 0))
    # le cadre du pack a six pixels vides autour de lui : on le compose plus grand, et on les rogne
    im.alpha_composite(nine(sprite("Background boxes", "BGbox_01A"), W + 12, H - 6 + 12).crop((6, 6, W + 6, H)), (0, 6))
    im.alpha_composite(wide(sprite("Title banners", "BannerSmall_02B"), 84), ((W - 84) // 2, 0))
    write(im, W // 2, 0, "QUÊTE", title, (255, 244, 226, 255), (62, 66, 110, 255))
    line = sprite("Dividers", "Divider_02")
    for x in range(14, W - 14, 48):
        im.alpha_composite(line.crop((0, 0, min(48, W - 14 - x), 16)), (x, 40))
    return im


LINE1, LINE2 = 44, 33            # hauteur du haut de chaque ligne au-dessus de la ligne de base du titre


BODY = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"   # une police libre, grasse : la police de texte du pack, toute petite, se lisait mal
CELL = 12                        # largeur d'une case de lettre (le panneau public en a 8 : cette police est plus large)


def letters():
    """
    Le même tableau de caractères que le panneau public, dans des cases plus larges (12 x 64, la lettre en haut à
    gauche), et chaque lettre garde sa propre largeur : le jeu avance de la largeur de l'encre plus un. Rend l'image et
    l'avance de chaque caractère.
    """
    font = ImageFont.truetype(BODY, 10)
    chars = [chr(c) for c in range(32, 127)] + list(base.ACCENTS)
    cols = 16
    im = Image.new("RGBA", (cols * CELL, ((len(chars) + cols - 1) // cols) * base.CELL_H), (0, 0, 0, 0))
    advances = {}
    for i, c in enumerate(chars):
        x, y = (i % cols) * CELL, (i // cols) * base.CELL_H
        cell = Image.new("RGBA", (24, 12), (0, 0, 0, 0))
        pen = ImageDraw.Draw(cell)
        pen.fontmode = "1"
        pen.text((2, 0), c, font=font, fill=(255, 255, 255, 255))
        box = cell.getbbox()
        if box:
            ink = cell.crop((box[0], 0, min(box[2], box[0] + CELL - 1), 12))
            im.alpha_composite(ink, (x, y))
            advances[c] = ink.width + 1
        else:
            im.putpixel((x + 2, y + 11), (255, 255, 255, 1))          # l'espace : un point presque invisible, avance de 4
            advances[c] = 4
    return im, advances


def main():
    if not ONE.is_dir():
        print("pack de Franuka absent (resourcepack-prive/ui-franuka/) : rien a faire, le pack garde le panneau public")
        return 0
    title = ImageFont.truetype(str(SRC / "Fonts" / "FantasyRPGtitle (size 17).ttf"), 17)
    OUT.mkdir(parents=True, exist_ok=True)
    panel(title).save(OUT / "quete_panneau.png")
    atlas, advances = letters()
    atlas.save(OUT / "quete_lettres.png")
    # les polices : celles du pack public, avec les lignes a la hauteur de ce cadre et le recul de chaque lettre
    public, fonts = ROOT / "resourcepack" / "assets" / "crossfight" / "font", OUT.parent.parent / "font"
    fonts.mkdir(parents=True, exist_ok=True)
    for name, ascent in (("quete", LINE1), ("quete2", LINE2)):
        font = json.loads((public / (name + ".json")).read_text(encoding="utf-8"))
        for provider in font["providers"]:
            if provider.get("file", "").endswith("quete_lettres.png"):
                provider["ascent"] = ascent
        (fonts / (name + ".json")).write_text(json.dumps(font, ensure_ascii=True, indent=1), encoding="utf-8")
    back = {"providers": [{"type": "space", "advances": {c: -w for c, w in advances.items()}}]}
    (fonts / "quete_recul.json").write_text(json.dumps(back, ensure_ascii=True, indent=1), encoding="utf-8")
    print("panneau", (W, H + LIFT), "lettres", atlas.size)
    return 0


if __name__ == "__main__":
    sys.exit(main())
