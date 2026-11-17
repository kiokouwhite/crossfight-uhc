#!/usr/bin/env python3
"""Dessine l'habillage du menu d'enchantement d'Anna (Garreg Mach) : une carte en pixel art.

Usage : python3 tools/build_menu_anna.py

Deux images de 176 x 222, la taille exacte d'un coffre de six rangées, un pixel d'image par pixel d'interface, sur une
carte dorée. La première : à gauche la couverture (un grimoire, « ANNA », « ENCHANTE », les prix), à droite la case où
poser l'objet (case 23 du coffre) et les quatre pièces d'armure portées (cases 16, 25, 34, 43). La seconde : l'objet en
haut (case 13), les trois niveaux dessous (cases 29, 31, 33) avec leur prix. En bas, l'inventaire du joueur. Le plugin
les pose par le titre du coffre (game/Places.java, Enchants et Levels) : un caractère d'espace recule de 8 pixels, puis
le caractère de l'image, haut de 222 et monté de 13, la cale sur le coin du coffre. Tout est dessiné ici, pixel par pixel.
Écrit resourcepack/assets/crossfight/font/menu.json, textures/font/menu_anna.png et menu_anna2.png.
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
    "R": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
    "P": ["####.", "#...#", "#...#", "####.", "#....", "#....", "#...."],
    "O": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "S": [".####", "#....", "#....", ".###.", "....#", "....#", "####."],
    "B": ["####.", "#...#", "#...#", "####.", "#...#", "#...#", "####."],
    "J": ["..###", "...#.", "...#.", "...#.", "...#.", "#..#.", ".##.."],
    "1": ["..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."],
    "2": [".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"],
    "-": [".....", ".....", ".....", ".###.", ".....", ".....", "....."],
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


def card():
    """Le fond commun des deux menus : la carte doree, et l'inventaire du joueur en bas."""
    im = Image.new("RGBA", (W, H), GOLD)
    d = ImageDraw.Draw(im)
    d.rectangle([0, 0, W - 1, 127], fill=GOLD, outline=INK)
    d.rectangle([1, 2, W - 2, 3], fill=GOLD_LIGHT)
    for cx, cy in ((3, 6), (W - 4, 6), (3, 124), (W - 4, 124)):          # des coins ouvrages
        d.rectangle([cx - 1, cy - 1, cx + 1, cy + 1], fill=GOLD_DEEP)
    # l'inventaire du joueur : son nom est ecrit par le jeu sur le bandeau dore, les cases dessous
    d.rectangle([0, 127, W - 1, H - 1], fill=GOLD, outline=INK)
    panel(d, 4, 138, 171, 217, 2)
    for row in range(3):
        for col in range(9):
            slot(d, 8 + 18 * col, 140 + 18 * row)
    for col in range(9):
        slot(d, 8 + 18 * col, 198)
    return im, d


def main():
    (ROOT / "textures" / "font").mkdir(parents=True, exist_ok=True)
    violet, green = (214, 170, 255, 255), (190, 240, 130, 255)

    # 1. Poser l'objet : la couverture a gauche, la case ou poser l'objet, les quatre pieces d'armure portees.
    im, d = card()
    d.rectangle([1, 4, 87, 126], fill=GOLD_DARK)
    d.rectangle([87, 4, 88, 126], fill=GOLD_DEEP)
    panel(d, 6, 14, 81, 124, 6)
    book(im, d, 30, 30)
    text(im, 44 - width("ANNA", 2) // 2, 60, "ANNA", 2)
    text(im, 44 - width("ENCHANTE") // 2, 82, "ENCHANTE", 1, violet)
    d.line([(20, 94), (68, 94)], fill=SLATE_LIGHT)
    text(im, 44 - width("1 - 5 NIV") // 2, 100, "1 - 5 NIV", 1, green)
    panel(d, 93, 14, 171, 125, 2)
    text(im, 110 - width("POSE") // 2, 22, "POSE", 1)
    text(im, 110 - width("ICI") // 2, 32, "ICI", 1)
    d.rectangle([94, 50, 118, 74], fill=GOLD, outline=INK)               # le cadre dore de la case 23
    slot(d, 98, 54)
    text(im, 110 - width("OU") // 2, 92, "OU", 1, violet)
    text(im, 111 - width("PORTE") // 2, 104, "PORTE", 1, violet)
    for row in range(1, 5):                                               # les cases 16, 25, 34, 43
        slot(d, 8 + 18 * 7, 18 + 18 * row)
    im.save(ROOT / "textures" / "font" / "menu_anna.png")

    # 2. Choisir le niveau : l'objet en haut (case 13), les trois niveaux dessous (cases 29, 31, 33) et leur prix.
    im, d = card()
    panel(d, 6, 14, 169, 124, 6)
    text(im, 88 - width("CHOISIS") // 2, 20, "CHOISIS", 1)
    d.rectangle([76, 32, 100, 56], fill=GOLD, outline=INK)
    slot(d, 80, 36)
    for k, (col, price) in enumerate(((2, "1 NIV"), (4, "2 NIV"), (6, "5 NIV")), 1):
        x = 8 + 18 * col
        slot(d, x, 72)
        text(im, x + 8 - width("I" * k) // 2, 93, "I" * k, 1, (255, 220, 110, 255))
        text(im, x + 8 - width(price) // 2, 106, price, 1, green)
    im.save(ROOT / "textures" / "font" / "menu_anna2.png")

    font = {"providers": [
        {"type": "space", "advances": {"\ue100": -8}},
        {"type": "bitmap", "file": "crossfight:font/menu_anna.png", "height": H, "ascent": 13, "chars": ["\ue000"]},
        {"type": "bitmap", "file": "crossfight:font/menu_anna2.png", "height": H, "ascent": 13, "chars": ["\ue001"]},
    ]}
    (ROOT / "font" / "menu.json").write_text(json.dumps(font, ensure_ascii=True, indent=1), encoding="utf-8")
    print("menu_anna.png, menu_anna2.png", im.size)


if __name__ == "__main__":
    main()
