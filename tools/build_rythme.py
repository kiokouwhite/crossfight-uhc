#!/usr/bin/env python3
"""Dessine le tableau du jeu de rythme de la forge (game/Rhythm.java), pour le pack public.

Usage : python3 tools/build_rythme.py

Le tableau est fait d'images de police, que le plugin montre dans des textes flottants devant le joueur, agrandis 1,4
fois (un pixel d'image = 0,035 bloc) :
  le tableau, 132 x 200 : quatre couloirs de 27 pixels (le premier commence a 12 pixels du bord gauche), la ligne de
  frappe a 26 pixels du bas, et sous elle une touche par couloir, marquee 1, 2, 3, 4 ;
  quatre notes, 24 x 16, une couleur par couloir.
Ici, tout est dessine pixel par pixel ; dans le pack complet, tools/build_rythme_franuka.py compose les memes images
(memes noms, memes tailles) avec le pack d'interface achete.
Ecrit resourcepack/assets/crossfight/font/rythme.json et textures/font/rythme_*.png.
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent / "resourcepack" / "assets" / "crossfight"
W, H = 132, 200
LEFT, LANE, LINE, TOP = 12, 27, 26, 8           # le premier couloir, sa largeur, la ligne (depuis le bas), la marge du haut
NOTE_W, NOTE_H = 24, 16
COLORS = [(240, 80, 110), (90, 170, 250), (110, 220, 130), (250, 200, 70)]
DIGITS = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def lanes(im, dark, light):
    """Les quatre couloirs, la ligne de frappe, et le chiffre de chaque touche (le fond et les touches sont deja la)."""
    d = ImageDraw.Draw(im)
    for k in range(4):
        x = LEFT + LANE * k
        d.rectangle([x + 1, TOP, x + LANE - 2, H - LINE - 1], fill=dark if k % 2 == 0 else tuple(min(255, c + 10) for c in dark[:3]) + (dark[3],))
    d.rectangle([LEFT - 2, H - LINE - 2, LEFT + 4 * LANE + 1, H - LINE + 1], fill=(40, 36, 60, 255))
    d.rectangle([LEFT - 1, H - LINE - 1, LEFT + 4 * LANE, H - LINE], fill=light)


def digits(im, color, y, dx=0):
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    font = ImageFont.truetype(DIGITS, 9)
    for k in range(4):
        cx = LEFT + LANE * k + LANE // 2
        d.text((cx - 2 + dx, y), str(k + 1), font=font, fill=color)


def write_font(folder):
    providers = [{"type": "bitmap", "file": "crossfight:font/rythme_tableau.png", "height": H, "ascent": H, "chars": [""]}]
    providers += [{"type": "bitmap", "file": f"crossfight:font/rythme_note_{k}.png", "height": NOTE_H, "ascent": NOTE_H, "chars": [chr(0xE401 + k)]} for k in range(4)]
    (folder / "rythme.json").write_text(json.dumps({"providers": providers}, ensure_ascii=True, indent=1), encoding="utf-8")


def main():
    out = ROOT / "textures" / "font"
    out.mkdir(parents=True, exist_ok=True)
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.rounded_rectangle([0, 0, W - 1, H - 1], radius=5, fill=(232, 184, 62, 255), outline=(24, 20, 28, 255))
    d.rounded_rectangle([4, 4, W - 5, H - 5], radius=3, fill=(52, 48, 68, 255), outline=(24, 20, 28, 255))
    lanes(im, (34, 32, 48, 255), (250, 226, 150, 255))
    for k in range(4):                                   # les touches : des carres sombres sous la ligne
        x = LEFT + LANE * k + LANE // 2 - 7
        d.rectangle([x, H - 21, x + 13, H - 7], fill=(76, 72, 92, 255), outline=(24, 20, 28, 255))
    digits(im, (244, 244, 248, 255), H - 20)
    im.save(out / "rythme_tableau.png")
    for k, c in enumerate(COLORS):
        note = Image.new("RGBA", (NOTE_W, NOTE_H), (0, 0, 0, 0))
        n = ImageDraw.Draw(note)
        n.rounded_rectangle([1, 2, NOTE_W - 2, NOTE_H - 3], radius=3, fill=c + (255,), outline=(24, 20, 28, 255))
        n.line([(4, 4), (NOTE_W - 5, 4)], fill=tuple(min(255, v + 70) for v in c) + (255,))
        note.putpixel((NOTE_W - 1, NOTE_H - 1), (255, 255, 255, 2))        # largeur fixe
        note.save(out / f"rythme_note_{k}.png")
    write_font(ROOT / "font")
    print("tableau", (W, H), "et quatre notes", (NOTE_W, NOTE_H))


if __name__ == "__main__":
    main()
