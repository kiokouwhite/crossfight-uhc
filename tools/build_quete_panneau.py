#!/usr/bin/env python3
"""Dessine le panneau de quete affiche au-dessus du tableau de bord, et la police qui ecrit dessus.

Usage : python3 tools/build_quete_panneau.py

Le panneau est un parchemin dessine ici (rien n'est repris d'un jeu). Il est affiche comme un caractere du titre du
tableau de bord : une image de police ne peut pas monter plus haut que sa propre hauteur, donc l'image porte huit rangs
vides sous le parchemin, qui le soulevent au-dessus du logo. Pour ecrire dessus, il faut des lettres dessinees haut dans
une case haute (meme raison) : quete_lettres.png, une case de 8 x 64 par caractere, la lettre tout en haut. Chaque case
porte un point presque invisible dans sa sixieme colonne : toutes les lettres avancent ainsi de 7 pixels, et le
plugin sait reculer d'autant (game/ScoreboardManager.java).

Ecrit dans resourcepack/assets/crossfight/ : textures/font/quete_panneau.png, textures/font/quete_lettres.png,
font/quete.json (le panneau, les espaces, la premiere ligne), font/quete2.json (la deuxieme ligne),
font/quete_texte.json (les memes lettres a la hauteur d'un texte ordinaire, pour ecrire ailleurs que sur le panneau),
font/quete_recul.json (les memes caracteres, sans image : chacun recule de sa propre largeur ; le plugin reecrit le
texte dans cette police pour revenir au debut de la ligne, sans avoir a connaitre la largeur des lettres).
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent / "resourcepack" / "assets" / "crossfight"
W, H, LIFT = 172, 58, 8          # le parchemin, et les rangs vides dessous
CELL_W, CELL_H = 8, 64
FONT = "/usr/share/fonts/truetype/dejavu/DejaVuSansMono.ttf"   # une police libre, a chasse fixe, qui connait les accents
ACCENTS = "éèêëàâçùûîïôœÉÈÊÀÇ’…"            # ce que le français demande en plus (game/ScoreboardManager.java a la même liste)
LINE1, LINE2 = 41, 30            # hauteur du haut de chaque ligne au-dessus de la ligne de base du titre


def panel():
    im = Image.new("RGBA", (W, H + LIFT), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # le parchemin : un degrade chaud, plus sombre vers les bords
    for y in range(H):
        for x in range(W):
            edge = min(x, W - 1 - x, y, H - 1 - y)
            t = min(1.0, edge / 14.0)
            g = 0.5 + 0.5 * (1 - abs(y - H / 2) / (H / 2)) * 0.3
            r_, g_, b_ = 236 * (0.82 + 0.18 * t), 205 * (0.78 + 0.22 * t) * (0.9 + 0.1 * g), 150 * (0.7 + 0.3 * t)
            n = ((x * 37 + y * 91) % 7 - 3) * 1.5          # un grain leger
            im.putpixel((x, y), (int(max(0, min(255, r_ + n))), int(max(0, min(255, g_ + n))), int(max(0, min(255, b_ + n))), 255))
    # le cadre : brun sombre, filet dore
    dark, gold, light = (58, 34, 14, 255), (196, 150, 52, 255), (240, 205, 110, 255)
    d.rectangle([0, 0, W - 1, H - 1], outline=dark)
    d.rectangle([1, 1, W - 2, H - 2], outline=gold)
    d.rectangle([2, 2, W - 3, H - 3], outline=dark)
    for cx, cy in ((2, 2), (W - 3, 2), (2, H - 3), (W - 3, H - 3)):          # les coins
        d.rectangle([cx - 2, cy - 2, cx + 2, cy + 2], fill=dark)
        d.rectangle([cx - 1, cy - 1, cx + 1, cy + 1], fill=light)
    # le bandeau du haut, avec le mot QUETE
    d.rectangle([34, 4, W - 35, 15], fill=(120, 78, 22, 255), outline=dark)
    d.rectangle([35, 5, W - 36, 5], fill=gold)
    font = ImageFont.truetype(FONT, 10)
    d.fontmode = "1"                                          # des pixels nets, sans lissage
    word = "QUÊTE"
    tw = d.textlength(word, font=font)
    d.text(((W - tw) / 2, 3), word, font=font, fill=(250, 226, 150, 255))
    # un filet sous les deux lignes d'ecriture
    d.line([10, H - 6, W - 11, H - 6], fill=(150, 110, 50, 255))
    return im


def letters():
    font = ImageFont.truetype(FONT, 10)
    chars = [chr(c) for c in range(32, 127)] + list(ACCENTS)
    cols = 16
    rows = (len(chars) + cols - 1) // cols
    im = Image.new("RGBA", (cols * CELL_W, rows * CELL_H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    table = []
    for i in range(rows):
        table.append("".join(chars[i * cols:(i + 1) * cols]).ljust(cols, "\u0000"))
    for i, c in enumerate(chars):
        x, y = (i % cols) * CELL_W, (i // cols) * CELL_H
        cell = Image.new("RGBA", (CELL_W, 12), (0, 0, 0, 0))
        pen = ImageDraw.Draw(cell)
        pen.fontmode = "1"
        pen.text((0, 0), c, font=font, fill=(255, 255, 255, 255))
        cell = cell.crop((0, 0, 6, 12))                       # six colonnes d'encre au plus
        im.alpha_composite(cell, (x, y))
        im.putpixel((x + 5, y + 11), (255, 255, 255, max(1, im.getpixel((x + 5, y + 11))[3])))   # le point qui fixe l'avance a 7
    return im, table


def main():
    (ROOT / "textures" / "font").mkdir(parents=True, exist_ok=True)
    (ROOT / "font").mkdir(parents=True, exist_ok=True)
    panel().save(ROOT / "textures" / "font" / "quete_panneau.png")
    atlas, table = letters()
    atlas.save(ROOT / "textures" / "font" / "quete_lettres.png")
    rows = table
    spaces = {}
    for k in range(9):
        spaces[chr(0xE101 + k)] = -(1 << k)
        spaces[chr(0xE111 + k)] = 1 << k
    def line(ascent):
        return {"type": "bitmap", "file": "crossfight:font/quete_lettres.png", "height": CELL_H, "ascent": ascent, "chars": rows}
    main_font = {"providers": [{"type": "space", "advances": spaces},
                               {"type": "bitmap", "file": "crossfight:font/quete_panneau.png", "height": H + LIFT, "ascent": H + LIFT, "chars": [""]},
                               line(LINE1)]}
    (ROOT / "font" / "quete.json").write_text(json.dumps(main_font, ensure_ascii=True, indent=1), encoding="utf-8")
    (ROOT / "font" / "quete2.json").write_text(json.dumps({"providers": [line(LINE2)]}, ensure_ascii=True, indent=1), encoding="utf-8")
    # les memes lettres, a la hauteur d'un texte ordinaire : pour ecrire ailleurs que sur le panneau (la forge : game/Rhythm.java)
    (ROOT / "font" / "quete_texte.json").write_text(json.dumps({"providers": [line(10)]}, ensure_ascii=True, indent=1), encoding="utf-8")
    back = {c: -7 for row in rows for c in row if c != "\u0000"}
    (ROOT / "font" / "quete_recul.json").write_text(json.dumps({"providers": [{"type": "space", "advances": back}]}, ensure_ascii=True, indent=1), encoding="utf-8")
    # pour centrer une ligne : les memes caracteres qui reculent, puis avancent, de la moitie de leur largeur
    for name, sign in (("quete_demi", 0.5), ("quete_demi2", -0.5)):
        (ROOT / "font" / (name + ".json")).write_text(json.dumps({"providers": [{"type": "space", "advances": {c: w * sign for c, w in back.items()}}]}, ensure_ascii=True, indent=1), encoding="utf-8")
    print("panneau", (W, H + LIFT), "lettres", atlas.size)


if __name__ == "__main__":
    main()
