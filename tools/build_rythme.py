#!/usr/bin/env python3
"""Dessine le tableau du jeu de rythme de la forge (game/Rhythm.java), pour le pack public.

Usage : python3 tools/build_rythme.py

Le tableau est fait d'images de police, que le plugin montre dans des textes flottants devant le joueur, agrandis 1,4
fois (un pixel d'image = 0,035 bloc) :
  le tableau, 132 x 214 : quatre couloirs de 27 pixels (le premier commence a 12 pixels du bord gauche), chacun teinte
  a la couleur de ses notes, la ligne de
  frappe a 40 pixels du bas, et sous elle une grande touche par couloir, marquee 1, 2, 3, 4 ;
  quatre notes, 24 x 16, une couleur par couloir ;
  l'eclat d'une note frappee, 32 x 32 (une etoile blanche qui grossit et disparait) ;
  quatre touches enfoncees, 32 x 32, que le plugin pose un instant sur celles du tableau quand le joueur appuie ;
  le panneau de resultat, de la taille du tableau, et l'anneau du score (64 x 64, 51 images, de vide a plein, avec une
  encoche a chaque palier : 55, 75 et 90 centiemes) ;
  le faisceau d'un couloir, 25 x 160 : de la lumiere qui monte de la ligne et s'eteint vers le haut, blanche, que le
  plugin teinte a la couleur du couloir.
Ici, tout est dessine pixel par pixel ; dans le pack complet, tools/build_rythme_franuka.py compose les memes images
(memes noms, memes tailles) avec le pack d'interface achete.
Ecrit resourcepack/assets/crossfight/font/rythme.json et textures/font/rythme_*.png.
"""
import json
import math
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent / "resourcepack" / "assets" / "crossfight"
W, H = 132, 214
LEFT, LANE, LINE, TOP = 12, 27, 40, 8           # le premier couloir, sa largeur, la ligne (depuis le bas), la marge du haut
NOTE_W, NOTE_H = 24, 16
COLORS = [(240, 80, 110), (90, 170, 250), (110, 220, 130), (250, 200, 70)]
DIGITS = "/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf"


def lanes(im, dark, light):
    """Les quatre couloirs, la ligne de frappe, et le chiffre de chaque touche (le fond et les touches sont deja la)."""
    d = ImageDraw.Draw(im)
    for k in range(4):
        x = LEFT + LANE * k
        tint = tuple(round(dark[i] * 0.72 + COLORS[k][i] * 0.28) for i in range(3)) + (dark[3],)      # chaque couloir a la couleur de ses notes, en sombre
        d.rectangle([x + 1, TOP, x + LANE - 2, H - LINE - 1], fill=tint)
    d.rectangle([LEFT - 2, H - LINE - 2, LEFT + 4 * LANE + 1, H - LINE + 1], fill=(40, 36, 60, 255))
    d.rectangle([LEFT - 1, H - LINE - 1, LEFT + 4 * LANE, H - LINE], fill=light)


def digits(im, color, y, dx=0, size=14):
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    font = ImageFont.truetype(DIGITS, size)
    for k in range(4):
        cx = LEFT + LANE * k + LANE // 2
        d.text((cx - round(d.textlength(str(k + 1), font=font) / 2) + dx, y), str(k + 1), font=font, fill=color)


NEEDED = (55, 75, 90)            # le score qu'il faut pour un C, un B, un A (game/Rhythm.java a les memes)


def rings(out):
    """L'anneau du score, 64 x 64, en 51 images : il se remplit dans le sens des aiguilles, et change de couleur a chaque palier."""
    for k in range(51):
        value = k * 2
        ring = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
        d = ImageDraw.Draw(ring)
        d.arc([2, 2, 61, 61], 0, 360, fill=(24, 20, 28, 255), width=9)
        d.arc([3, 3, 60, 60], 0, 360, fill=(70, 66, 92, 255), width=7)
        color = (240, 80, 110) if value < NEEDED[0] else (250, 200, 70) if value < NEEDED[1] else (110, 220, 130) if value < NEEDED[2] else (120, 220, 250)
        if value > 0:
            d.arc([3, 3, 60, 60], -90, -90 + 360 * value / 100, fill=color + (255,), width=7)
        for mark in NEEDED:                                   # les trois paliers : une encoche claire en travers de l'anneau
            a = math.radians(-90 + 360 * mark / 100)
            d.line([(31.5 + math.cos(a) * 21, 31.5 + math.sin(a) * 21), (31.5 + math.cos(a) * 31, 31.5 + math.sin(a) * 31)], fill=(255, 244, 226, 255), width=2)
        ring.putpixel((63, 63), (255, 255, 255, 2))           # largeur fixe
        ring.save(out / f"rythme_anneau_{k:02d}.png")


def result_lines(im, dark):
    """Sur le panneau de resultat : le rond ou se pose l'anneau, et trois lignes pour les recompenses."""
    d = ImageDraw.Draw(im)
    d.ellipse([W // 2 - 34, H - 184, W // 2 + 33, H - 117], fill=dark)
    for row in range(3):
        d.rectangle([14, H - 92 + 16 * row, W - 15, H - 92 + 16 * row + 13], fill=dark)


def write_font(folder):
    providers = [{"type": "bitmap", "file": "crossfight:font/rythme_tableau.png", "height": H, "ascent": H, "chars": [""]}]
    providers += [{"type": "bitmap", "file": f"crossfight:font/rythme_note_{k}.png", "height": NOTE_H, "ascent": NOTE_H, "chars": [chr(0xE401 + k)]} for k in range(4)]
    # une touche enfoncee par couloir : elle recouvre un instant la touche du tableau
    providers += [{"type": "bitmap", "file": f"crossfight:font/rythme_touche_{k}.png", "height": 32, "ascent": 32, "chars": [chr(0xE407 + k)]} for k in range(4)]
    # le panneau de resultat, et l'anneau du score : une image par deux centiemes, de vide a plein
    providers.append({"type": "bitmap", "file": "crossfight:font/rythme_resultat.png", "height": H, "ascent": H, "chars": ["\ue40b"]})
    providers += [{"type": "bitmap", "file": f"crossfight:font/rythme_anneau_{k:02d}.png", "height": 64, "ascent": 64, "chars": [chr(0xE500 + k)]} for k in range(51)]
    providers.append({"type": "bitmap", "file": "crossfight:font/rythme_eclat.png", "height": 32, "ascent": 32, "chars": ["\ue405"]})
    providers.append({"type": "bitmap", "file": "crossfight:font/rythme_faisceau.png", "height": 160, "ascent": 160, "chars": ["\ue406"]})
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
        x = LEFT + LANE * k + LANE // 2 - 11
        d.rectangle([x, H - 33, x + 22, H - 9], fill=(76, 72, 92, 255), outline=(24, 20, 28, 255))
        d.line([(x + 1, H - 10), (x + 21, H - 10)], fill=(52, 48, 68, 255))
    digits(im, (244, 244, 248, 255), H - 30)
    im.save(out / "rythme_tableau.png")
    for k, c in enumerate(COLORS):
        note = Image.new("RGBA", (NOTE_W, NOTE_H), (0, 0, 0, 0))
        n = ImageDraw.Draw(note)
        n.rounded_rectangle([1, 2, NOTE_W - 2, NOTE_H - 3], radius=3, fill=c + (255,), outline=(24, 20, 28, 255))
        n.line([(4, 4), (NOTE_W - 5, 4)], fill=tuple(min(255, v + 70) for v in c) + (255,))
        note.putpixel((NOTE_W - 1, NOTE_H - 1), (255, 255, 255, 2))        # largeur fixe
        note.save(out / f"rythme_note_{k}.png")
    # les touches enfoncees, 32 x 32 : le fond du tableau (pour cacher la touche au repos), puis la touche, plus basse et plus sombre
    for k in range(4):
        key = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
        kd = ImageDraw.Draw(key)
        kd.rectangle([0, 2, 31, 31], fill=(52, 48, 68, 255))
        kd.rectangle([5, 10, 27, 31], fill=(58, 54, 72, 255), outline=(24, 20, 28, 255))
        kd.fontmode = "1"
        font = ImageFont.truetype(DIGITS, 14)
        kd.text((16 - round(kd.textlength(str(k + 1), font=font) / 2), 13), str(k + 1), font=font, fill=tuple(COLORS[k]) + (255,))
        key.save(out / f"rythme_touche_{k}.png")
    # l'eclat : une etoile a huit branches, blanche au coeur, doree au bord
    burst = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    b = ImageDraw.Draw(burst)
    for k in range(8):
        a = math.pi * k / 4
        length = 15 if k % 2 == 0 else 10
        b.line([(16, 16), (16 + round(math.cos(a) * length), 16 + round(math.sin(a) * length))], fill=(255, 226, 120, 255), width=3)
        b.line([(16, 16), (16 + round(math.cos(a) * (length - 3)), 16 + round(math.sin(a) * (length - 3)))], fill=(255, 255, 255, 255), width=1)
    b.ellipse([10, 10, 22, 22], fill=(255, 255, 255, 255), outline=(255, 236, 160, 255))
    burst.save(out / "rythme_eclat.png")
    # le faisceau : blanc, plein en bas, eteint en haut, un peu plus pale sur les bords
    beam = Image.new("RGBA", (25, 160), (0, 0, 0, 0))
    for y in range(160):
        up = (159 - y) / 159                                 # 0 en bas, 1 en haut
        for x in range(25):
            edge = 1 - abs(x - 12) / 12 * 0.45
            beam.putpixel((x, y), (255, 255, 255, max(1 if x == 24 and y == 159 else 0, round(190 * (1 - up) ** 1.6 * edge))))
    beam.putpixel((24, 159), (255, 255, 255, max(2, beam.getpixel((24, 159))[3])))
    beam.save(out / "rythme_faisceau.png")
    rings(out)
    res = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    r = ImageDraw.Draw(res)
    r.rounded_rectangle([0, 0, W - 1, H - 1], radius=5, fill=(232, 184, 62, 255), outline=(24, 20, 28, 255))
    r.rounded_rectangle([4, 4, W - 5, H - 5], radius=3, fill=(52, 48, 68, 255), outline=(24, 20, 28, 255))
    result_lines(res, (34, 32, 48, 255))
    res.save(out / "rythme_resultat.png")
    write_font(ROOT / "font")
    print("tableau", (W, H), "et quatre notes", (NOTE_W, NOTE_H))


if __name__ == "__main__":
    main()
