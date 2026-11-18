#!/usr/bin/env python3
"""Refait le tableau du jeu de rythme avec le pack d'interface achete a Franuka (pack prive seulement).

Usage : python3 tools/build_rythme_franuka.py

Meme principe que tools/build_menu_franuka.py : les fichiers du « RPG UI pack » de Franuka restent dans
resourcepack-prive/ui-franuka/, et les images composees ici vont dans resourcepack-prive/assets/, ou elles remplacent
dans le pack complet celles de tools/build_rythme.py (memes noms, memes tailles). Le tableau : la fenetre lavande du
pack, quatre couloirs sombres, la ligne de frappe, et les touches du pack marquees 1 a 4. Les notes : un bouton du
pack, recolore en quatre teintes.
"""
import colorsys
import sys
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_rythme as base
from build_menu_franuka import ONE, nine, sprite

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "resourcepack-prive" / "assets" / "crossfight" / "textures" / "font"


def tinted(im, turn):
    """La meme image, les teintes tournees de `turn` (0 a 1) : le rouge du bouton devient bleu, vert ou or."""
    out = im.copy()
    px = out.load()
    for y in range(out.height):
        for x in range(out.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            h, s, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
            if s > 0.25:                                  # les contours sombres et les reflets clairs restent
                r, g, b = (round(c * 255) for c in colorsys.hsv_to_rgb((h + turn) % 1, s, v))
            px[x, y] = (r, g, b, a)
    return out


def main():
    if not ONE.is_dir():
        print("pack de Franuka absent (resourcepack-prive/ui-franuka/) : rien a faire, le pack garde le tableau public")
        return 0
    OUT.mkdir(parents=True, exist_ok=True)
    W, H = base.W, base.H
    # la fenetre du pack a six pixels vides autour d'elle : on la compose plus grande, et on les rogne
    im = nine(sprite("Background boxes", "BGbox_01B"), W + 12, H + 12).crop((6, 6, W + 6, H + 6))
    base.lanes(im, (44, 46, 82, 235), (255, 244, 226, 255))
    # les touches du pack, deux fois plus grandes (des pixels doubles, nets)
    key = sprite("Buttons", "Input_Key_Normal").resize((32, 32), Image.NEAREST)
    for k in range(4):
        im.alpha_composite(key, (base.LEFT + base.LANE * k + base.LANE // 2 - 16, H - 40))
    base.digits(im, (255, 244, 226, 255), H - 33, -1, 13)
    im.save(OUT / "rythme_tableau.png")
    # les touches enfoncees : le fond de la fenetre (pour cacher la touche au repos), la touche enfoncee du pack, son chiffre a la couleur du couloir
    from PIL import ImageDraw, ImageFont
    pressed = sprite("Buttons", "Input_Key_Pressed").resize((32, 32), Image.NEAREST)
    fill = im.getpixel((base.LEFT + 2, H - 6))
    for k in range(4):
        one = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        d = ImageDraw.Draw(one)
        d.rectangle([0, 2, 31, 31], fill=fill)
        one.alpha_composite(pressed)
        d.fontmode = "1"
        font = ImageFont.truetype(base.DIGITS, 13)
        d.text((16 - round(d.textlength(str(k + 1), font=font) / 2) - 1, 10), str(k + 1), font=font, fill=tuple(base.COLORS[k]) + (255,))
        one.save(OUT / f"rythme_touche_{k}.png")
    # le panneau de resultat : la meme fenetre, un fond sombre dedans (comme les couloirs), le rond de l'anneau et les trois lignes
    from PIL import ImageDraw as _Draw
    res = nine(sprite("Background boxes", "BGbox_01B"), W + 12, H + 12).crop((6, 6, W + 6, H + 6))
    _Draw.Draw(res).rectangle([10, 8, W - 11, H - 12], fill=(44, 46, 82, 255))
    base.result_lines(res, (30, 32, 62, 255))
    res.save(OUT / "rythme_resultat.png")
    button = sprite("Buttons", "Button_02B_Normal").crop((7, 1, 25, 16))        # 18 x 15 : le bouton sans ses marges
    for k, turn in enumerate((0.95, 0.60, 0.35, 0.12)):                        # rose, bleu, vert, or
        note = Image.new("RGBA", (base.NOTE_W, base.NOTE_H), (0, 0, 0, 0))
        note.alpha_composite(tinted(button, turn), ((base.NOTE_W - 18) // 2, 0))
        note.putpixel((base.NOTE_W - 1, base.NOTE_H - 1), (255, 255, 255, 2))   # largeur fixe
        note.save(OUT / f"rythme_note_{k}.png")
    print("tableau et notes composes avec le pack")
    return 0


if __name__ == "__main__":
    sys.exit(main())
