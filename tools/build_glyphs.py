#!/usr/bin/env python3
"""Les signes de Glyph de Weiss (pack prive) : images blanches teintees en jeu a la couleur du Dust.

Usage :  python3 tools/build_glyphs.py

Lit      resourcepack-prive/glyphes/<nom>.png|webp     un signe blanc sur fond transparent (le halo colore
                                                       eventuel devient blanc : c'est la teinte qui colore)
Ecrit    resourcepack-prive/assets/crossfight/textures/item/glyph/<nom>.png   512 x 512, centre
         resourcepack-prive/assets/crossfight/models/glyph/<nom>.json         un carre plat a deux faces, teinte
         resourcepack-prive/assets/crossfight/items/glyph/<nom>.json          la teinte vient de custom_model_data
Le plugin le connait ensuite sous le nom "glyph.<nom>" (tools/build_pack.py) et l'affiche avec un
ItemDisplay (character/rwby/Glyph.java). Sans ces fichiers, les Glyphs gardent leurs seules particules.
Il dessine aussi, dans le pack public, la flaque d'ombre de Blake (glyph.flaque_noire, Discretion).
"""
import json
from pathlib import Path

from PIL import Image, ImageFilter, ImageChops

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "resourcepack-prive" / "glyphes"
OUT = ROOT / "resourcepack-prive" / "assets" / "crossfight"
PUBLIC = ROOT / "resourcepack" / "assets" / "crossfight"
SIZE = 512


def texture(src: Path) -> Image.Image:
    im = Image.open(src).convert("RGBA")
    im = im.crop(im.getbbox())
    side = max(im.size)
    sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    sq.paste(im, ((side - im.width) // 2, (side - im.height) // 2))
    sq = sq.resize((SIZE - 24, SIZE - 24), Image.LANCZOS)
    alpha = Image.new("L", (SIZE, SIZE), 0)
    alpha.paste(sq.split()[3], (12, 12))
    # Un leger halo autour du trait, pour qu'il se lise de loin et qu'il ait l'air lumineux.
    halo = alpha.filter(ImageFilter.GaussianBlur(7)).point(lambda v: int(v * 0.55))
    alpha = ImageChops.lighter(alpha, halo)
    white = Image.new("RGBA", (SIZE, SIZE), (255, 255, 255, 0))
    white.putalpha(alpha)
    return white


def puddle() -> Image.Image:
    """La flaque d'ombre de Blake (dessinee ici, pack public) : une tache noire aux bords irreguliers,
    un reflet violet sur le pourtour."""
    import math
    import random
    rnd = random.Random(7)
    size = 256
    mask = Image.new("L", (size, size), 0)
    from PIL import ImageDraw
    d = ImageDraw.Draw(mask)
    c = size / 2
    # Le contour : un cercle deforme par quelques ondulations, plus des gouttes autour.
    pts = []
    for i in range(180):
        a = 2 * math.pi * i / 180
        r = 92 + 9 * math.sin(3 * a + 0.4) + 6 * math.sin(5 * a + 1.3) + 4 * math.sin(9 * a + 2.1)
        pts.append((c + r * math.cos(a), c + r * math.sin(a)))
    d.polygon(pts, fill=255)
    for _ in range(9):
        a = rnd.uniform(0, 2 * math.pi)
        r = rnd.uniform(100, 116)
        s_ = rnd.uniform(5, 11)
        x, y = c + r * math.cos(a), c + r * math.sin(a)
        d.ellipse((x - s_, y - s_, x + s_, y + s_), fill=255)
    mask = mask.filter(ImageFilter.GaussianBlur(2.2))
    rim = ImageChops.subtract(mask, mask.filter(ImageFilter.GaussianBlur(9)).point(lambda v: min(255, int(v * 1.15))))
    img = Image.new("RGBA", (size, size), (10, 6, 16, 0))
    px = img.load()
    m, rm = mask.load(), rim.load()
    for y in range(size):
        for x in range(size):
            a = m[x, y]
            if a == 0:
                continue
            g = rm[x, y] / 255
            px[x, y] = (int(10 + 70 * g), int(6 + 20 * g), int(16 + 110 * g), int(a * 0.96))
    return img


def write(name: str, img: Image.Image, out: Path = OUT):
    tex = out / "textures" / "item" / "glyph" / f"{name}.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    img.save(tex, optimize=True)
    face = {"uv": [0, 0, 16, 16], "texture": "#0", "tintindex": 0}
    model = {"textures": {"0": f"crossfight:item/glyph/{name}", "particle": f"crossfight:item/glyph/{name}"},
             "elements": [{"from": [0, 0, 8], "to": [16, 16, 8], "shade": False,
                           "faces": {"south": face, "north": dict(face)}}]}
    mp = out / "models" / "glyph" / f"{name}.json"
    mp.parent.mkdir(parents=True, exist_ok=True)
    mp.write_text(json.dumps(model, indent=1), encoding="utf-8")
    item = {"model": {"type": "minecraft:model", "model": f"crossfight:glyph/{name}",
                      "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": -1}]}}
    ip = out / "items" / "glyph" / f"{name}.json"
    ip.parent.mkdir(parents=True, exist_ok=True)
    ip.write_text(json.dumps(item, indent=1), encoding="utf-8")
    return tex


def main() -> int:
    print(write("flaque_noire", puddle(), PUBLIC).relative_to(ROOT))
    if not SOURCES.is_dir():
        print(f"pas de {SOURCES} : rien a faire (les Glyphs gardent leurs particules)")
        return 0
    for src in sorted(SOURCES.iterdir()):
        if src.suffix.lower() in (".png", ".webp"):
            print(write(src.stem, texture(src)).relative_to(ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
