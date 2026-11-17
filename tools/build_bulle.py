#!/usr/bin/env python3
"""Dessine la bulle de dialogue des personnages de quête (Anna), pour le pack public.

Usage : python3 tools/build_bulle.py

La bulle est une image de police : le plugin écrit, sur la première ligne d'un texte flottant (game/Places.java), le
caractère de la bulle qui convient au nombre de lignes, puis le texte sur les lignes suivantes, centré. L'image monte de
7 : son haut tombe sur le haut de la première ligne, qui sert de marge. Une image par nombre de lignes (1 à 14), large de
216 : dix pixels de marge en haut, dix par ligne, dix en bas, puis huit pour la pointe. Le plugin coupe lui-même le texte
en lignes de 176 pixels au plus. Ici, un cadre blanc à bord sombre, dessiné pixel par pixel ; dans le pack complet,
tools/build_bulle_franuka.py le remplace par un cadre du pack d'interface acheté (mêmes noms, mêmes tailles).
Écrit resourcepack/assets/crossfight/font/bulle.json et textures/font/bulle_*.png.
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / "resourcepack" / "assets" / "crossfight"
W, LINES, TAIL = 216, 14, 8


def height(n):
    return 10 + 10 * n + 10 + TAIL


def tail(im, fill, edge):
    """La pointe, en bas au milieu : elle ouvre le bord du cadre et descend de huit pixels."""
    d = ImageDraw.Draw(im)
    y, cx = im.height - TAIL - 1, im.width // 2
    d.polygon([(cx - 7, y), (cx + 7, y), (cx, y + 7)], fill=fill)
    d.line([(cx - 7, y), (cx, y + 7)], fill=edge)
    d.line([(cx + 7, y), (cx, y + 7)], fill=edge)
    d.line([(cx - 6, y), (cx + 6, y)], fill=fill)


def frame(n):
    im = Image.new("RGBA", (W, height(n)), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    fill, edge, shade = (250, 250, 255, 255), (40, 36, 50, 255), (214, 214, 228, 255)
    d.rounded_rectangle([0, 0, W - 1, im.height - TAIL - 1], radius=4, fill=fill, outline=edge)
    d.line([(3, im.height - TAIL - 3), (W - 4, im.height - TAIL - 3)], fill=shade)
    tail(im, fill, edge)
    return im


def write_font(folder, ascent=7):
    providers = [{"type": "bitmap", "file": f"crossfight:font/bulle_{n:02d}.png", "height": height(n), "ascent": ascent, "chars": [chr(0xE300 + n)]} for n in range(1, LINES + 1)]
    (folder / "bulle.json").write_text(json.dumps({"providers": providers}, ensure_ascii=True, indent=1), encoding="utf-8")


def main():
    (ROOT / "textures" / "font").mkdir(parents=True, exist_ok=True)
    for n in range(1, LINES + 1):
        frame(n).save(ROOT / "textures" / "font" / f"bulle_{n:02d}.png")
    write_font(ROOT / "font")
    print(LINES, "bulles de", W, "pixels de large")


if __name__ == "__main__":
    main()
