#!/usr/bin/env python3
"""Refait le panneau de quête avec le pack d'interface acheté à Franuka (pack privé seulement).

Usage : python3 tools/build_quete_franuka.py

Même principe que tools/build_menu_franuka.py : les fichiers du « RPG UI pack » de Franuka restent dans
resourcepack-prive/ui-franuka/, et les deux images composées ici vont dans resourcepack-prive/assets/, où elles
remplacent dans le pack complet celles de tools/build_quete_panneau.py (mêmes noms, mêmes tailles, mêmes cases de
lettres : la police et le plugin ne changent pas). Le panneau : un cadre pêche, une grande bannière lavande à cheval
sur son bord, où « QUÊTE » est écrit en grand avec un contour bleu foncé, et en bas une jauge (un curseur du pack) qui dit où en est la quête (police quete_jauge). Les lettres : une police libre et grasse (la police de
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


BIG = False                      # « QUÊTE » deux fois plus gros, sur une grande banniere (essaye, puis remis a la taille d'origine)
TOP = 14 if BIG else 4           # rangs ajoutes en haut de l'image : pour la grande banniere, ou pour monter le mot de quatre pixels
NAVY = (44, 48, 92, 255)


def word(text, font, scale, fill, edge):
    """Un mot en grand : la police de titre du pack agrandie sans lissage, cernee d'un contour de `scale` pixels."""
    probe = ImageDraw.Draw(Image.new("L", (1, 1)))
    w = int(probe.textlength(text, font=font)) + 2
    mask = Image.new("L", (w, 24), 0)
    pen = ImageDraw.Draw(mask)
    pen.fontmode = "1"
    pen.text((1, 2), text, font=font, fill=255)
    mask = mask.crop(mask.getbbox()).resize((mask.getbbox()[2] - mask.getbbox()[0]) * scale and ((mask.getbbox()[2] - mask.getbbox()[0]) * scale, (mask.getbbox()[3] - mask.getbbox()[1]) * scale), Image.NEAREST)
    out = Image.new("RGBA", (mask.width + 2 * scale, mask.height + 2 * scale), (0, 0, 0, 0))
    ink = Image.new("RGBA", mask.size, edge)
    for dx in range(-scale, scale + 1):                                    # le contour : le mot decale dans toutes les directions
        for dy in range(-scale, scale + 1):
            out.paste(ink, (scale + dx, scale + dy), mask)
    out.paste(Image.new("RGBA", mask.size, fill), (scale, scale), mask)
    return out


def panel(title):
    im = Image.new("RGBA", (W, TOP + H + LIFT), (0, 0, 0, 0))
    # le cadre du pack a six pixels vides autour de lui : on le compose plus grand, et on les rogne
    im.alpha_composite(nine(sprite("Background boxes", "BGbox_01A"), W + 12, H - 6 + 12).crop((6, 6, W + 6, H)), (0, TOP + 6))
    banner = wide(sprite("Title banners", "BannerMedium_02B"), 156) if BIG else wide(sprite("Title banners", "BannerSmall_02B"), 92)
    im.alpha_composite(banner, ((W - banner.width) // 2, 0 if BIG else TOP))
    big = word("QUÊTE", title, 2 if BIG else 1, (255, 244, 226, 255), NAVY)
    im.alpha_composite(big, ((W - big.width) // 2, 0))
    return im


BAR_X, BAR_Y, BAR_W, STEPS = 14, 42, None, 10      # la jauge : a 14 points du bord, 42 sous le haut du cadre, sur toute la largeur


def gauges():
    """
    La jauge de la quete, a la place du filet du bas : la boite d'un curseur du pack allongee, et sa barre verte remplie
    de 0 a 10 dixiemes. Onze images de meme taille ; le plugin dessine celle qui dit ou en est la quete.
    """
    w = W - 2 * BAR_X
    box = wide(sprite("Sliders & Bars", "Slider01_Box"), w)
    fill = wide(sprite("Sliders & Bars", "Slider01_Bar04"), w)
    left, right = 4, w - 4                                               # la barre du pack commence a quatre points du bord de sa boite
    out = []
    for k in range(STEPS + 1):
        # (une image de police ne peut pas etre posee plus haut que sa propre hauteur : elle descend donc jusqu'a la ligne de base, la jauge en haut)
        im = Image.new("RGBA", (w, H + LIFT - BAR_Y), (0, 0, 0, 0))
        im.alpha_composite(box)
        end = left + round((right - left) * k / STEPS)
        if end > left:
            im.alpha_composite(fill.crop((0, 0, end, 16)))
        im.putpixel((w - 1, im.height - 1), (255, 255, 255, 2))                      # un point presque invisible : toutes les images avancent de la meme largeur
        out.append(im)
    return out


LINE1, LINE2 = 46, 36            # hauteur du haut de chaque ligne au-dessus de la ligne de base du titre


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
            if provider.get("file", "").endswith("quete_panneau.png"):           # l'image est plus haute : la banniere depasse en haut
                provider["height"] = provider["ascent"] = TOP + H + LIFT
        (fonts / (name + ".json")).write_text(json.dumps(font, ensure_ascii=True, indent=1), encoding="utf-8")
    # la jauge : onze images, posees a la hauteur du filet qu'elles remplacent
    bars = gauges()
    for k, im in enumerate(bars):
        im.save(OUT / f"quete_jauge_{k}.png")
    gauge = {"providers": [{"type": "bitmap", "file": f"crossfight:font/quete_jauge_{k}.png", "height": H + LIFT - BAR_Y, "ascent": H + LIFT - BAR_Y, "chars": [chr(0xE200 + k)]} for k in range(len(bars))]}
    (fonts / "quete_jauge.json").write_text(json.dumps(gauge, ensure_ascii=True, indent=1), encoding="utf-8")
    back = {"providers": [{"type": "space", "advances": {c: -w for c, w in advances.items()}}]}
    (fonts / "quete_recul.json").write_text(json.dumps(back, ensure_ascii=True, indent=1), encoding="utf-8")
    # pour centrer une ligne : les memes caracteres qui reculent, puis avancent, de la moitie de leur largeur
    for name, sign in (("quete_demi", -0.5), ("quete_demi2", 0.5)):
        (fonts / (name + ".json")).write_text(json.dumps({"providers": [{"type": "space", "advances": {c: w * sign for c, w in advances.items()}}]}, ensure_ascii=True, indent=1), encoding="utf-8")
    print("panneau", (W, TOP + H + LIFT), "lettres", atlas.size)
    return 0


if __name__ == "__main__":
    sys.exit(main())
