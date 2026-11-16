#!/usr/bin/env python3
"""Essai d'icones en pixel art pour les objets de capacites (ici celles de Marth).

Usage :  python3 tools/build_icones_test.py

Chaque icone est une grille de 16 x 16 ecrite a la main, un caractere par pixel (palette ci-dessous).
Ecrit    maquettes/icones_marth/<capacite>.png   l'icone en 16 x 16, fond transparent
         maquettes/icones_marth/apercu.png       les icones agrandies, dans une case d'inventaire
Dessins originaux (symboles : une epee, une banniere, un diademe), pas des images du jeu d'origine.
"""
from pathlib import Path

from PIL import Image, ImageDraw

OUT = Path(__file__).resolve().parent.parent / "maquettes" / "icones_marth"

PALETTE = {
    ".": None,
    "k": (24, 22, 40),      # contour
    "w": (250, 250, 255),   # eclat
    "s": (206, 216, 232),   # acier clair
    "a": (146, 160, 188),   # acier
    "d": (92, 104, 138),    # acier sombre
    "g": (255, 208, 74),    # or
    "o": (214, 148, 38),    # or sombre
    "y": (255, 240, 150),   # or clair
    "b": (64, 112, 220),    # bleu
    "c": (120, 176, 255),   # bleu clair
    "n": (34, 60, 140),     # bleu nuit
    "r": (226, 62, 70),     # rouge
    "m": (150, 30, 52),     # rouge sombre
    "p": (255, 150, 150),   # rose
    "t": (112, 74, 44),     # bois
}

ICONS = {
    # Contre : la lame en garde, l'eclat de la parade qui part de la garde.
    "marth_contre": [
        "..............kk",
        ".............kwk",
        "....c.......kwsk",
        "...ccc.....kwsk.",
        "..cc.cc...kwsk..",
        ".cc...c..kwsk...",
        ".c......kwsk....",
        ".c.....kwsk.....",
        ".cc...kwsk......",
        "..c..ksak.......",
        "..okkkak...c....",
        ".kgogkk...cc....",
        "..kogok.ccc.....",
        ".ktkkgk.........",
        "ktk..k..........",
        "kk..............",
    ],
    # Charisme : la banniere du prince qui claque au vent, les allies suivent.
    "marth_charisme": [
        "..kk............",
        ".kyyk...........",
        ".kggkkkkkkkk....",
        "..ktkccccccbkk..",
        "..ktkcbbbbbbbbk.",
        "..ktkbbgbbbbnk..",
        "..ktkbgggbbnk...",
        "..ktkbbgbbbbnk..",
        "..ktkbbbbbbbbnk.",
        "..ktknnnnnnnnk..",
        "..ktkkkkkkkkk...",
        "..ktk...........",
        "..ktk..w...w....",
        "..ktk.w...w...w.",
        "..ktk..w...w....",
        "..kkk...........",
    ],
    # Esprit du Leader : le diademe, et la flamme de la Force au-dessus.
    "marth_leader": [
        ".......kk.......",
        "......krrk......",
        ".....krprrk.....",
        ".....krrrmk.....",
        "......kmmk......",
        ".......kk.......",
        "..k....kk....k..",
        ".kgk..kyyk..kgk.",
        ".kgokkkggkkkogk.",
        ".kggggkbbkggggk.",
        ".kgyggkcbkggogk.",
        ".kgggggkkgggogk.",
        ".koggggggggoook.",
        "..kkkkkkkkkkkk..",
        "................",
        "................",
    ],
    # Coup Critique : l'estoc qui fend l'air, l'etoile de l'impact au bout.
    "marth_ult": [
        "...........w....",
        "...........w....",
        "........w.kwk.w.",
        ".........kywyk..",
        "........wwwwwww.",
        ".........kywyk..",
        "........wkswk.w.",
        ".......ksakw....",
        "......ksak.w....",
        ".r...ksak.......",
        "..r.ksak........",
        ".okkkak..r......",
        "kgogkk..r.......",
        ".kogok.r........",
        "ktkkgk..........",
        "kk..k...........",
    ],
}


def render(rows):
    assert len(rows) == 16 and all(len(r) == 16 for r in rows), [len(r) for r in rows]
    im = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    for y, row in enumerate(rows):
        for x, ch in enumerate(row):
            if PALETTE[ch]:
                im.putpixel((x, y), PALETTE[ch] + (255,))
    return im


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    zoom, pad = 12, 18
    names = list(ICONS)
    cell = 16 * zoom + 2 * pad
    sheet = Image.new("RGB", (cell * len(names), cell * 2 + 40), (40, 40, 46))
    d = ImageDraw.Draw(sheet)
    for i, name in enumerate(names):
        im = render(ICONS[name])
        im.save(OUT / f"{name}.png")
        big = im.resize((16 * zoom, 16 * zoom), Image.NEAREST)
        # En haut : dans une case d'inventaire (gris de Minecraft) ; en bas : sur fond sombre, taille reelle a cote.
        for row, bg in enumerate(((139, 139, 139), (28, 28, 34))):
            x0, y0 = i * cell, row * cell
            d.rectangle((x0 + 6, y0 + 6, x0 + cell - 7, y0 + cell - 7), fill=bg)
            sheet.paste(big, (x0 + pad, y0 + pad), big)
            sheet.paste(im.resize((32, 32), Image.NEAREST), (x0 + cell - 44, y0 + cell - 44), im.resize((32, 32), Image.NEAREST))
        d.text((i * cell + pad, cell * 2 + 12), name, fill=(230, 230, 230))
    sheet.save(OUT / "apercu.png")
    print(len(names), "icones dans", OUT)


if __name__ == "__main__":
    main()
