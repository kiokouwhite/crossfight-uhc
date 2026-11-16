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
Et, pour Valorant : un orbe (glyph.orbe, teint en jeu : les orbes de Sage), un anneau de vent (glyph.vent : les
bourrasques de Jett) et un couteau de lancer (glyph.kunai : sa Tempete de Lames).
"""
import json
import math
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageChops

ROOT = Path(__file__).resolve().parent.parent
SOURCES = ROOT / "resourcepack-prive" / "glyphes"
# Des images gardees telles quelles, en couleurs (une image officielle fournie : la porte blanche d'Omori...).
PHOTOS = ROOT / "resourcepack-prive" / "glyphes-couleur"
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


def orb() -> Image.Image:
    """Un orbe (pack public) : un coeur plein, un halo qui s'efface, un reflet. Blanc : il est teint en jeu
    (les orbes de Sage)."""
    import math
    size = 96
    img = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    px = img.load()
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            r = math.hypot(x - c, y - c) / (size / 2)
            if r >= 1:
                continue
            core = 1.0 if r < 0.42 else max(0.0, 1 - (r - 0.42) / 0.16)
            halo = max(0.0, 1 - r) ** 1.6 * 0.55
            ring = max(0.0, 1 - abs(r - 0.5) / 0.05) * 0.5
            a = min(1.0, max(core, halo + ring))
            # Le coeur s'assombrit vers son bord (un volume), le reflet reste blanc en haut a gauche.
            shade = 1.0 if r >= 0.42 else 0.72 + 0.28 * (1 - r / 0.42)
            glint = max(0.0, 1 - math.hypot(x - c + size * 0.13, y - c + size * 0.15) / (size * 0.1))
            v = int(255 * min(1.0, shade + glint))
            px[x, y] = (v, v, v, int(255 * a))
    return img


def gust() -> Image.Image:
    """Un anneau de vent (pack public) : trois arcs effiles qui tournent autour d'un centre vide. Blanc, teint
    en jeu (les bourrasques de Jett)."""
    import math
    size = 128
    img = Image.new("RGBA", (size, size), (255, 255, 255, 0))
    px = img.load()
    c = (size - 1) / 2
    for y in range(size):
        for x in range(size):
            dx, dy = x - c, y - c
            r = math.hypot(dx, dy) / (size / 2)
            if r >= 1 or r < 0.3:
                continue
            ang = math.atan2(dy, dx)
            best = 0.0
            for k in range(3):
                # Chaque arc : une spirale qui s'ecarte du centre en tournant, epaisse au depart, fine au bout.
                turn = ((ang - k * 2 * math.pi / 3) % (2 * math.pi)) / (2 * math.pi)
                if turn > 0.78:
                    continue
                radius = 0.42 + 0.48 * turn
                width = 0.085 * (1 - turn / 0.78) + 0.012
                best = max(best, max(0.0, 1 - abs(r - radius) / width) * (1 - 0.5 * turn))
            if best > 0:
                px[x, y] = (255, 255, 255, int(235 * best))
    return img


def kunai() -> Image.Image:
    """Un couteau de lancer (pack public), dessine debout : la pointe en haut, la lame en feuille, la poignee
    bandee, l'anneau en bas. Debout et symetrique, il se lit pareil des deux cotes de son image (les deux faces
    d'une image d'effet sont le miroir l'une de l'autre). Les couteaux de la Tempete de Lames de Jett."""
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    px = img.load()
    steel, light, dark, wrap, glow = (196, 208, 224), (240, 246, 255), (120, 132, 152), (40, 120, 132), (150, 240, 250)
    rows = [  # une ligne par pixel de haut, autour de l'axe (colonnes 7 et 8)
        "......ll......", ".....lssd.....", ".....lssd.....", "....llssdd....", "....lsssdd....", "....lsssdd....",
        ".....lssd.....", ".....lssd.....", "......sd......", "......wg......", "......gw......", "......wg......",
        ".....d..d.....", ".....s..s.....", ".....s..s.....", "......ss......",
    ]
    key = {"l": light, "s": steel, "d": dark, "w": wrap, "g": glow}
    for y, row in enumerate(rows):
        for x, c in enumerate(row):
            if c != ".":
                px[x + 1, y] = key[c] + (255,)
    return img


def photo(src: Path) -> Image.Image:
    """Une image fournie sur fond blanc, gardee en couleurs : on la recadre sur son sujet (tout ce qui n'est pas
    le blanc du fond), on rend le fond transparent autour de son rectangle, et on la centre dans un carre."""
    im = Image.open(src).convert("RGB")
    box = im.convert("L").point(lambda v: 255 if v < 246 else 0).getbbox()
    cut = im.crop(box).convert("RGBA")
    side = max(cut.size) + 8
    sq = Image.new("RGBA", (side, side), (0, 0, 0, 0))
    sq.paste(cut, ((side - cut.width) // 2, (side - cut.height) // 2))
    return sq


def white_door() -> Image.Image:
    """Une porte blanche a six panneaux, seule, sans mur (pack public) : la porte de l'Espace Blanc de l'evenement
    Omori. Elle doit avoir l'air d'une photo d'une vraie porte, un peu sale : panneaux moulures aux ombres
    douces, grain, taches grises, eraflures, crasse en bas et autour de la poignee, bords uses. Image originale,
    fabriquee ici de toutes pieces (aucune photo, aucune image du jeu)."""
    import random
    rnd = random.Random(7)
    S, K = 256, 3                       # image finale, et facteur de dessin (reduit ensuite : bords doux)
    W, H = S * K, S * K
    x0, x1, y0, y1 = 83 * K, 173 * K, 5 * K, 251 * K
    base = Image.new("L", (W, H), 0)
    d = ImageDraw.Draw(base)
    d.rectangle((x0, y0, x1, y1), fill=238)
    # Les panneaux : un creux plus sombre en haut et a gauche (l'ombre de la moulure), plus clair en bas a droite.
    def panel(px0, py0, px1, py1):
        for i, v in enumerate((176, 200, 222, 232)):
            d.rectangle((px0 + i * K, py0 + i * K, px1 - i * K, py1 - i * K), fill=v)
        d.rectangle((px0 + 4 * K, py0 + 4 * K, px1 - 4 * K, py1 - 4 * K), fill=241)
        d.line([(px0 + K, py1 - K), (px1 - K, py1 - K)], fill=250, width=K)
        d.line([(px1 - K, py0 + K), (px1 - K, py1 - K)], fill=247, width=K)
    for px0, px1 in ((x0 + 13 * K, x0 + 39 * K), (x1 - 39 * K, x1 - 13 * K)):
        panel(px0, y0 + 16 * K, px1, y0 + 100 * K)
        panel(px0, y0 + 118 * K, px1, y0 + 196 * K)
        panel(px0, y0 + 208 * K, px1, y0 + 234 * K)
    # La poignee ronde et son ombre portee.
    kx, ky = x1 - 9 * K, y0 + 108 * K
    d.ellipse((kx - 6 * K + 2 * K, ky - 6 * K + 3 * K, kx + 6 * K + 2 * K, ky + 6 * K + 3 * K), fill=188)
    d.ellipse((kx - 6 * K, ky - 6 * K, kx + 6 * K, ky + 6 * K), fill=226)
    d.ellipse((kx - 4 * K, ky - 5 * K, kx + 2 * K, ky + K), fill=250)
    base = base.filter(ImageFilter.GaussianBlur(K * 0.7))
    # La crasse : de grandes taches floues, plus denses en bas, sur les bords et autour de la poignee.
    dirt = Image.new("L", (W, H), 0)
    dd = ImageDraw.Draw(dirt)
    for _ in range(170):
        px, py = rnd.randint(x0, x1), int(y0 + (y1 - y0) * rnd.random() ** 0.55)
        r = rnd.randint(4 * K, 22 * K)
        dd.ellipse((px - r, py - r // 2, px + r, py + r // 2), fill=rnd.randint(10, 46))
    for _ in range(40):
        r = rnd.randint(3 * K, 10 * K)
        px, py = kx + rnd.randint(-16 * K, 4 * K), ky + rnd.randint(-18 * K, 18 * K)
        dd.ellipse((px - r, py - r, px + r, py + r), fill=rnd.randint(14, 40))
    dd.rectangle((x0, y1 - 14 * K, x1, y1), fill=44)
    dd.rectangle((x0, y0, x0 + 3 * K, y1), fill=36)
    dirt = dirt.filter(ImageFilter.GaussianBlur(K * 5))
    # Les eraflures : de fins traits sombres, et quelques eclats ou la peinture a saute.
    marks = Image.new("L", (W, H), 0)
    dm = ImageDraw.Draw(marks)
    for _ in range(46):
        px, py = rnd.randint(x0, x1), rnd.randint(y0, y1)
        ln, an = rnd.randint(6 * K, 34 * K), rnd.uniform(-0.5, 0.5) + (1.57 if rnd.random() < 0.7 else 0)
        dm.line([(px, py), (px + ln * math.cos(an), py + ln * math.sin(an))], fill=rnd.randint(40, 110), width=rnd.choice((1, 1, 2)))
    for _ in range(120):
        px, py = rnd.choice((rnd.randint(x0, x0 + 6 * K), rnd.randint(x1 - 6 * K, x1), rnd.randint(x0, x1))), rnd.randint(y0, y1)
        r = rnd.randint(1, 2 * K)
        dm.ellipse((px - r, py - r, px + r, py + r), fill=rnd.randint(50, 130))
    marks = marks.filter(ImageFilter.GaussianBlur(0.8))
    # Le grain d'une photo.
    noise = Image.effect_noise((W, H), 16).filter(ImageFilter.GaussianBlur(0.6))
    tone = ImageChops.subtract(ImageChops.subtract(base, dirt), marks.point(lambda v: v // 2))
    tone = ImageChops.add(ImageChops.subtract(tone, Image.new("L", (W, H), 22)), noise.point(lambda v: v // 6))
    # Un blanc un peu jauni, plus froid dans les ombres.
    rgb = Image.merge("RGB", (tone.point(lambda v: min(255, int(v * 1.0 + 4))), tone.point(lambda v: min(255, int(v * 0.99 + 3))), tone.point(lambda v: int(v * 0.955))))
    mask = Image.new("L", (W, H), 0)
    ImageDraw.Draw(mask).rectangle((x0, y0, x1, y1), fill=255)
    out = rgb.convert("RGBA")
    out.putalpha(mask)
    return out.resize((S, S), Image.LANCZOS)


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
    # Valorant : les orbes de Sage, les bourrasques et les couteaux de Jett.
    for name, img in (("orbe", orb()), ("vent", gust()), ("kunai", kunai())):
        print(write(name, img, PUBLIC).relative_to(ROOT))
    # L'evenement Omori : la porte blanche de l'Espace Blanc.
    print(write("porte_blanche", white_door(), PUBLIC).relative_to(ROOT))
    # Les images officielles fournies remplacent, dans le pack complet, le dessin public du meme nom.
    if PHOTOS.is_dir():
        for src in sorted(PHOTOS.iterdir()):
            if src.suffix.lower() in (".png", ".webp"):
                print(write(src.stem, photo(src)).relative_to(ROOT))
    if not SOURCES.is_dir():
        print(f"pas de {SOURCES} : rien a faire (les Glyphs gardent leurs particules)")
        return 0
    for src in sorted(SOURCES.iterdir()):
        if src.suffix.lower() in (".png", ".webp"):
            print(write(src.stem, texture(src)).relative_to(ROOT))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
