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
"""
import json
from pathlib import Path

from PIL import Image, ImageFilter, ImageChops

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "resourcepack-prive" / "glyphes"
OUT = ROOT / "resourcepack-prive" / "assets" / "crossfight"
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


def write(name: str, img: Image.Image):
    tex = OUT / "textures" / "item" / "glyph" / f"{name}.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    img.save(tex, optimize=True)
    face = {"uv": [0, 0, 16, 16], "texture": "#0", "tintindex": 0}
    model = {"textures": {"0": f"crossfight:item/glyph/{name}", "particle": f"crossfight:item/glyph/{name}"},
             "elements": [{"from": [0, 0, 8], "to": [16, 16, 8], "shade": False,
                           "faces": {"south": face, "north": dict(face)}}]}
    mp = OUT / "models" / "glyph" / f"{name}.json"
    mp.parent.mkdir(parents=True, exist_ok=True)
    mp.write_text(json.dumps(model, indent=1), encoding="utf-8")
    item = {"model": {"type": "minecraft:model", "model": f"crossfight:glyph/{name}",
                      "tints": [{"type": "minecraft:custom_model_data", "index": 0, "default": -1}]}}
    ip = OUT / "items" / "glyph" / f"{name}.json"
    ip.parent.mkdir(parents=True, exist_ok=True)
    ip.write_text(json.dumps(item, indent=1), encoding="utf-8")
    return tex


def main() -> int:
    if not SOURCES.is_dir():
        print(f"pas de {SOURCES} : rien a faire (les Glyphs gardent leurs particules)")
        return 0
    for src in sorted(SOURCES.iterdir()):
        if src.suffix.lower() in (".png", ".webp"):
            print(write(src.stem, texture(src)).relative_to(ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
