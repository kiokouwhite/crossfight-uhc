#!/usr/bin/env python3
"""Dessine l'habillage du menu d'enchantement d'Anna (Garreg Mach) : une carte en pixel art.

Usage : python3 tools/build_menu_anna.py

Une image de 176 x 222, la taille exacte d'un coffre de six rangées, un pixel d'image par pixel d'interface : une carte
dorée ouverte, à gauche la couverture (un grimoire, « ANNA », « ENCHANTE », le prix), à droite la page où viennent les
pièces à enchanter (quatre colonnes de six cases : les colonnes 5 à 8 du coffre), en bas l'inventaire du joueur. Le
plugin la pose par le titre du coffre (game/Places.java, Enchants) : un caractère d'espace recule de 8 pixels, puis le
caractère de l'image, haut de 222 et monté de 13, la cale sur le coin du coffre. Tout est dessiné ici, pixel par pixel.
Écrit resourcepack/assets/crossfight/font/menu.json et textures/font/menu_anna.png.
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / "resourcepack" / "assets" / "crossfight"
W, H = 176, 222
INK = (24, 20, 28, 255)
GOLD, GOLD_DARK, GOLD_LIGHT, GOLD_DEEP = (232, 184, 62, 255), (222, 160, 52, 255), (250, 226, 150, 255), (190, 124, 40, 255)
SLATE, SLATE_DARK, SLATE_LIGHT = (76, 72, 92, 255), (62, 58, 76, 255), (98, 94, 118, 255)
WHITE, SHADOW = (244, 244, 248, 255), (40, 36, 50, 255)

# Des lettres de 5 x 7, dessinées pour ce menu.
LETTERS = {
    "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "N": ["#...#", "##..#", "#.#.#", "#.#.#", "#..##", "#...#", "#...#"],
    "E": ["#####", "#....", "#....", "####.", "#....", "#....", "#####"],
    "C": [".####", "#....", "#....", "#....", "#....", "#....", ".####"],
    "H": ["#...#", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
    "I": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "#####"],
    "V": ["#...#", "#...#", "#...#", "#...#", ".#.#.", ".#.#.", "..#.."],
    "U": ["#...#", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "X": ["#...#", "#...#", ".#.#.", "..#..", ".#.#.", "#...#", "#...#"],
    "5": ["#####", "#....", "####.", "....#", "....#", "#...#", ".###."],
    " ": ["....."] * 7,
}


def text(im, x, y, s, scale=1, color=WHITE):
    """Écrit s, le coin en (x, y) ; chaque lettre porte une ombre, en bas."""
    for ch in s:
        for dy, row in enumerate(LETTERS[ch]):
            for dx, c in enumerate(row):
                if c != "#":
                    continue
                for a in range(scale):
                    for b in range(scale):
                        px, py = x + dx * scale + a, y + dy * scale + b
                        if im.getpixel((px, py + scale)) != color:
                            im.putpixel((px, py + scale), SHADOW)
                        im.putpixel((px, py), color)
        x += 6 * scale


def width(s, scale=1):
    return (6 * len(s) - 1) * scale


def panel(d, x1, y1, x2, y2, cut):
    """Un panneau d'ardoise cerné de noir, les coins coupés de `cut` pixels."""
    pts = [(x1 + cut, y1), (x2 - cut, y1), (x2, y1 + cut), (x2, y2 - cut), (x2 - cut, y2), (x1 + cut, y2), (x1, y2 - cut), (x1, y1 + cut)]
    d.polygon(pts, fill=SLATE, outline=INK)
    inner = [(x1 + cut + 1, y1 + 1), (x2 - cut - 1, y1 + 1), (x2 - 1, y1 + cut + 1), (x2 - 1, y2 - cut - 1), (x2 - cut - 1, y2 - 1), (x1 + cut + 1, y2 - 1), (x1 + 1, y2 - cut - 1), (x1 + 1, y1 + cut + 1)]
    d.line(inner + [inner[0]], fill=SLATE_DARK)


def slot(d, x, y):
    """Une case : le coin de l'objet en (x, y), seize pixels de côté."""
    d.rectangle([x - 1, y - 1, x + 16, y + 16], fill=SLATE_DARK, outline=INK)
    d.line([(x, y + 16), (x + 16, y + 16), (x + 16, y)], fill=SLATE_LIGHT)


def book(im, d, x, y):
    """Le grimoire ouvert, vingt-huit pixels de large, et ses étincelles."""
    violet, violet_dark, page, page_dark = (150, 92, 214, 255), (104, 58, 160, 255), (246, 236, 208, 255), (214, 196, 160, 255)
    d.polygon([(x, y + 6), (x + 13, y + 3), (x + 14, y + 4), (x + 15, y + 3), (x + 28, y + 6), (x + 28, y + 20), (x + 15, y + 17), (x + 14, y + 18), (x + 13, y + 17), (x, y + 20)], fill=violet, outline=INK)
    d.polygon([(x + 2, y + 6), (x + 13, y + 4), (x + 13, y + 15), (x + 2, y + 17)], fill=page)
    d.polygon([(x + 26, y + 6), (x + 15, y + 4), (x + 15, y + 15), (x + 26, y + 17)], fill=page)
    d.line([(x + 14, y + 4), (x + 14, y + 18)], fill=INK)
    for k in range(4):
        d.line([(x + 4, y + 8 + 2 * k), (x + 11, y + 7 + 2 * k)], fill=page_dark)
        d.line([(x + 17, y + 7 + 2 * k), (x + 24, y + 8 + 2 * k)], fill=page_dark)
    d.line([(x + 1, y + 19), (x + 13, y + 16)], fill=violet_dark)
    d.line([(x + 27, y + 19), (x + 15, y + 16)], fill=violet_dark)
    for sx, sy, c in ((x + 3, y - 3, (255, 236, 130, 255)), (x + 23, y - 5, (190, 240, 255, 255)), (x + 14, y - 8, (255, 170, 240, 255)), (x + 30, y + 1, (255, 236, 130, 255)), (x - 3, y + 2, (190, 240, 255, 255))):
        d.line([(sx - 2, sy), (sx + 2, sy)], fill=c)
        d.line([(sx, sy - 2), (sx, sy + 2)], fill=c)
        im.putpixel((sx, sy), WHITE)


def main():
    im = Image.new("RGBA", (W, H), GOLD)
    d = ImageDraw.Draw(im)
    # la carte : un bord noir, un reflet en haut, la moitie gauche un peu plus sombre, la pliure au milieu
    d.rectangle([0, 0, W - 1, 127], fill=GOLD, outline=INK)
    d.rectangle([1, 1, 87, 126], fill=GOLD_DARK)
    d.rectangle([1, 2, W - 2, 3], fill=GOLD_LIGHT)
    d.rectangle([87, 4, 88, 126], fill=GOLD_DEEP)
    for cx, cy in ((3, 6), (W - 4, 6), (3, 124), (W - 4, 124)):          # des coins ouvrages
        d.rectangle([cx - 1, cy - 1, cx + 1, cy + 1], fill=GOLD_DEEP)
    # la couverture, a gauche
    panel(d, 6, 14, 81, 124, 6)
    book(im, d, 30, 30)
    text(im, 44 - width("ANNA", 2) // 2, 60, "ANNA", 2)
    text(im, 44 - width("ENCHANTE") // 2, 82, "ENCHANTE", 1, (214, 170, 255, 255))
    d.line([(20, 94), (68, 94)], fill=SLATE_LIGHT)
    d.ellipse([12, 100, 18, 106], fill=(130, 230, 80, 255), outline=INK)   # une bille d'experience
    im.putpixel((14, 102), (220, 255, 190, 255))
    text(im, 22, 100, "5 NIVEAUX", 1, (190, 240, 130, 255))
    # la page, a droite : quatre colonnes de six cases (colonnes 5 a 8 du coffre)
    panel(d, 93, 14, 171, 125, 2)
    for row in range(6):
        for col in range(5, 9):
            slot(d, 8 + 18 * col, 18 + 18 * row)
    # l'inventaire du joueur : son nom est ecrit par le jeu sur le bandeau dore, les cases dessous
    d.rectangle([0, 127, W - 1, H - 1], fill=GOLD, outline=INK)
    panel(d, 4, 138, 171, 217, 2)
    for row in range(3):
        for col in range(9):
            slot(d, 8 + 18 * col, 140 + 18 * row)
    for col in range(9):
        slot(d, 8 + 18 * col, 198)
    (ROOT / "textures" / "font").mkdir(parents=True, exist_ok=True)
    im.save(ROOT / "textures" / "font" / "menu_anna.png")
    font = {"providers": [
        {"type": "space", "advances": {"": -8}},
        {"type": "bitmap", "file": "crossfight:font/menu_anna.png", "height": H, "ascent": 13, "chars": [""]},
    ]}
    (ROOT / "font" / "menu.json").write_text(json.dumps(font, ensure_ascii=True, indent=1), encoding="utf-8")
    print("menu_anna.png", im.size)


if __name__ == "__main__":
    main()
