#!/usr/bin/env python3
"""Les icones de bonus/malus sur les bords de l'ecran (util/EffectHud.java).

Usage :  python3 tools/build_overlays.py [--apercu image.png] [--minecraft client.jar]

--minecraft : les memes images avec les vraies icones d'effets de Minecraft (lues dans le client.jar), ecrites
              dans resourcepack-prive/, dossier overlay_mc/ : un dossier a part, pour que le pack public envoye par le
              serveur (prioritaire sur celui du joueur) ne les recouvre pas. Le plugin les prefere quand le pack
              complet les contient ("overlay.minecraft"). Jamais versionnees.

Minecraft dessine en plein ecran l'image "camera_overlay" d'un objet porte (comme la vue a travers une
citrouille). Le plugin glisse a chaque joueur, pour lui seul, un objet invisible dans sa case de casque
(les bonus) et dans celle de jambieres (les malus) : sa vraie armure ne change pas. Chaque image couvre
tout l'ecran et ne dessine que son bord : a gauche les icones des bonus, a droite celles des malus (un ou
deux effets par cote), sans bande ni trait de couleur.

Les icones ne sont pas fixes : nues (sans pastille), elles montent sur trois files le long du bord, sortent
du bas de l'ecran et s'effacent a mi-hauteur. Une image de plein ecran ne peut pas etre animee par le pack : chaque boucle est donc une suite de
FRAMES images que le plugin fait defiler (une toutes les 2 ticks).

Ecrit    resourcepack/assets/crossfight/textures/overlay/bonus_<a>[_<b>]_<image>.png   (et .png.mcmeta : lissage)
         resourcepack/assets/crossfight/textures/overlay/malus_<a>[_<b>]_<image>.png
Les noms des effets, leur ordre et leur couleur sont ceux de EffectHud.java. Dessins originaux.
"""
import itertools
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "resourcepack" / "assets" / "crossfight" / "textures" / "overlay"
W, H = 1024, 576
BADGE_R = 25
SLOTS_Y = (392, 318)          # premiere icone, puis la seconde au-dessus
# L'animation : les icones montent le long du bord en file, apparaissent en bas et s'effacent en haut.
FRAMES = 8                    # images par boucle (le plugin en change tous les 2 ticks)
COPIES = 12                   # icones de chaque effet a l'ecran en meme temps (un multiple de STEP : une file par rang)
STEP = 3                      # en une boucle, chaque icone prend la place de celle qui est STEP rangs plus haut ; autant de files
RISE = (H + 34, 320)          # hauteur de depart (sous le bas de l'ecran : elles en sortent) et d'arrivee (mi-hauteur)
LANES = (30, 76, 122)         # distance au bord de chaque file
SIZE = (512, 288)             # taille des images ecrites (dessinees en 1024 x 576 puis reduites)
EDGE_X = 62                   # centre des icones, depuis le bord

BONUS = [("vitesse", (57, 208, 255)), ("force", (255, 106, 61)), ("resistance", (106, 168, 255)),
         ("regeneration", (255, 111, 177)), ("invisibilite", (201, 194, 255)), ("absorption", (255, 211, 77)),
         ("saut", (141, 255, 122))]
MALUS = [("lenteur", (125, 147, 184)), ("faiblesse", (170, 170, 170)), ("poison", (123, 209, 59)),
         ("cecite", (140, 100, 190)), ("wither", (110, 96, 86)), ("feu", (255, 138, 31)), ("entrave", (159, 232, 255))]


# ------------------------------------------------------------------ icones (blanc, 192 px, reduites ensuite)

def icon(name: str) -> Image.Image:
    S = 192
    im = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(im)
    c = S / 2
    if name == "vitesse":
        for dx in (-46, -6, 34):
            d.line([(c + dx - 22, c - 44), (c + dx + 18, c), (c + dx - 22, c + 44)], fill=255, width=20, joint="curve")
    elif name == "force":
        d.polygon([(c, 18), (c + 14, 40), (c + 14, 118), (c - 14, 118), (c - 14, 40)], fill=255)
        d.rounded_rectangle((c - 42, 118, c + 42, 134), 6, fill=255)
        d.rectangle((c - 8, 134, c + 8, 166), fill=255)
        d.ellipse((c - 14, 160, c + 14, 184), fill=255)
    elif name == "resistance":
        d.polygon([(c, 18), (c + 62, 40), (c + 56, 104), (c, 176), (c - 56, 104), (c - 62, 40)], fill=255)
        d.polygon([(c, 44), (c + 38, 58), (c + 34, 100), (c, 146)], fill=0)
    elif name in ("regeneration", "absorption"):
        heart = [(c + 70 * (16 * math.sin(t) ** 3) / 17, c - 6 - 70 * (13 * math.cos(t) - 5 * math.cos(2 * t) - 2 * math.cos(3 * t) - math.cos(4 * t)) / 17)
                 for t in (i * math.pi / 90 for i in range(180))]
        d.polygon(heart, fill=255)
        if name == "regeneration":
            d.rectangle((c - 7, c - 34, c + 7, c + 22), fill=0)
            d.rectangle((c - 28, c - 13, c + 28, c + 1), fill=0)
        else:
            inner = [(c + (x - c) * 0.62, c - 2 + (y - c) * 0.62) for x, y in heart]
            d.polygon(inner, fill=0)
            for sx, sy in ((c + 62, 34), (c - 66, 150)):
                d.polygon([(sx, sy - 16), (sx + 5, sy - 5), (sx + 16, sy), (sx + 5, sy + 5), (sx, sy + 16), (sx - 5, sy + 5), (sx - 16, sy), (sx - 5, sy - 5)], fill=255)
    elif name == "invisibilite":
        d.ellipse((c - 78, c - 44, c + 78, c + 44), fill=255)
        d.ellipse((c - 62, c - 30, c + 62, c + 30), fill=0)
        d.ellipse((c - 24, c - 24, c + 24, c + 24), fill=255)
        d.line([(c - 70, c + 70), (c + 70, c - 70)], fill=0, width=30)
        d.line([(c - 70, c + 70), (c + 70, c - 70)], fill=255, width=14)
    elif name == "saut":
        d.polygon([(c, 14), (c + 52, 72), (c + 18, 72), (c + 18, 118), (c - 18, 118), (c - 18, 72), (c - 52, 72)], fill=255)
        d.rounded_rectangle((c - 60, 136, c + 60, 150), 7, fill=255)
        d.rounded_rectangle((c - 40, 162, c + 40, 176), 7, fill=255)
    elif name == "lenteur":
        d.rounded_rectangle((c - 56, 16, c + 56, 32), 6, fill=255)
        d.rounded_rectangle((c - 56, 160, c + 56, 176), 6, fill=255)
        d.polygon([(c - 44, 32), (c + 44, 32), (c + 8, c), (c + 44, 160), (c - 44, 160), (c - 8, c)], fill=255)
        d.polygon([(c - 30, 44), (c + 30, 44), (c, 84)], fill=0)
        d.polygon([(c - 4, c + 10), (c + 4, c + 10), (c + 30, 148), (c - 30, 148)], fill=0)
        d.polygon([(c, 118), (c + 22, 148), (c - 22, 148)], fill=255)
    elif name == "faiblesse":
        d.polygon([(c + 10, 16), (c + 22, 36), (c + 22, 74), (c - 6, 82), (c - 6, 36)], fill=255)
        d.polygon([(c - 6, 96), (c + 22, 88), (c + 22, 118), (c - 6, 118)], fill=255)
        d.rounded_rectangle((c - 34, 118, c + 50, 134), 6, fill=255)
        d.rectangle((c, 134, c + 16, 164), fill=255)
        d.ellipse((c - 6, 158, c + 22, 184), fill=255)
    elif name == "poison":
        d.polygon([(c, 14), (c + 46, 98), (c - 46, 98)], fill=255)
        d.ellipse((c - 52, 62, c + 52, 166), fill=255)
        d.ellipse((c - 26, 96, c - 4, 118), fill=0)
        d.ellipse((c + 8, 120, c + 22, 134), fill=0)
    elif name == "cecite":
        d.arc((c - 80, c - 70, c + 80, c + 40), 20, 160, fill=255, width=16)
        for k in (-2, -1, 0, 1, 2):
            a = math.radians(90 + k * 28)
            x0, y0 = c + 72 * math.cos(a), c - 15 + 52 * math.sin(a)
            d.line([(x0, y0), (x0 + 24 * math.cos(a), y0 + 24 * math.sin(a))], fill=255, width=13)
    elif name == "wither":
        d.ellipse((c - 62, 16, c + 62, 132), fill=255)
        d.rounded_rectangle((c - 38, 112, c + 38, 168), 10, fill=255)
        d.ellipse((c - 44, 62, c - 10, 98), fill=0)
        d.ellipse((c + 10, 62, c + 44, 98), fill=0)
        d.polygon([(c, 104), (c + 9, 122), (c - 9, 122)], fill=0)
        for x in (-20, 0, 20):
            d.rectangle((c + x - 4, 140, c + x + 4, 168), fill=0)
    elif name == "feu":
        d.polygon([(c, 12), (c + 30, 62), (c + 58, 96), (c + 60, 132), (c + 36, 168), (c, 180), (c - 36, 168), (c - 60, 132), (c - 56, 96), (c - 22, 70)], fill=255)
        d.polygon([(c + 4, 84), (c + 28, 122), (c + 26, 150), (c, 164), (c - 26, 150), (c - 24, 124)], fill=0)
        d.polygon([(c + 2, 112), (c + 14, 136), (c, 152), (c - 14, 138)], fill=255)
    elif name == "entrave":
        d.arc((c - 40, 22, c + 40, 106), 180, 360, fill=255, width=20)
        d.rectangle((c - 40, 62, c - 20, 92), fill=255)
        d.rectangle((c + 20, 62, c + 40, 92), fill=255)
        d.rounded_rectangle((c - 58, 88, c + 58, 176), 14, fill=255)
        d.ellipse((c - 12, 110, c + 12, 134), fill=0)
        d.polygon([(c - 7, 126), (c + 7, 126), (c + 10, 156), (c - 10, 156)], fill=0)
    return im


# --minecraft : les vraies icones d'effets de Minecraft (images de Mojang, lues dans le client.jar donne et
# jamais versionnees : les images faites ainsi vont dans le pack prive). Sans icone chez Minecraft (le feu,
# l'entrave), le dessin de ce script reste.
VANILLA = {"vitesse": "speed", "force": "strength", "resistance": "resistance", "regeneration": "regeneration",
           "invisibilite": "invisibility", "absorption": "absorption", "saut": "jump_boost", "lenteur": "slowness",
           "faiblesse": "weakness", "poison": "poison", "cecite": "blindness", "wither": "wither"}
MOJANG = {}
_BADGES = {}


def load_minecraft(jar: Path):
    import io
    import zipfile
    with zipfile.ZipFile(jar) as z:
        for ours, theirs in VANILLA.items():
            MOJANG[ours] = Image.open(io.BytesIO(z.read(f"assets/minecraft/textures/mob_effect/{theirs}.png"))).convert("RGBA")


def badge(name: str, color, size: int) -> Image.Image:
    """L'icone dans sa pastille : fond sombre translucide, anneau et halo de la couleur de l'effet."""
    key = (name, tuple(color), size, name in MOJANG)
    if key not in _BADGES:
        _BADGES[key] = _badge(name, color, size)
    return _BADGES[key].copy()


def _badge(name: str, color, size: int) -> Image.Image:
    S = size * 4
    out = Image.new("RGBA", (S, S), (0, 0, 0, 0))
    ring = Image.new("L", (S, S), 0)
    dr = ImageDraw.Draw(ring)
    m = S * 0.12
    dr.ellipse((m, m, S - m, S - m), outline=255, width=int(S * 0.045))
    glow = ring.filter(ImageFilter.GaussianBlur(S * 0.05))
    disk = Image.new("L", (S, S), 0)
    ImageDraw.Draw(disk).ellipse((m, m, S - m, S - m), fill=150)
    out.paste(Image.new("RGBA", (S, S), (8, 10, 18, 255)), (0, 0), disk)
    out.paste(Image.new("RGBA", (S, S), color + (255,)), (0, 0), glow)
    out.paste(Image.new("RGBA", (S, S), tuple(min(255, int(v * 0.7 + 255 * 0.3)) for v in color) + (255,)), (0, 0), ring)
    if name in MOJANG:
        # L'icone de Minecraft, en gros pixels nets, posee sur la pastille reduite.
        out = out.resize((size, size), Image.LANCZOS)
        side = max(18, int(size * 0.62))
        ic = MOJANG[name].resize((side, side), Image.NEAREST)
        out.alpha_composite(ic, ((size - side) // 2, (size - side) // 2))
        return out
    ic = icon(name).resize((int(S * 0.5), int(S * 0.5)), Image.LANCZOS)
    off = ((S - ic.width) // 2, (S - ic.height) // 2)
    out.paste(Image.new("RGBA", ic.size, (255, 255, 255, 255)), off, ic)
    return out.resize((size, size), Image.LANCZOS)


_SPRITES = {}


def sprite(name: str, color, size: int) -> Image.Image:
    """L'icone seule, sans pastille : celle de Minecraft en gros pixels nets, ou le dessin de ce script a la
    couleur de l'effet ; une ombre douce dessous pour qu'elle se lise sur n'importe quel fond."""
    key = (name, tuple(color), size, name in MOJANG)
    if key in _SPRITES:
        return _SPRITES[key].copy()
    pad = max(4, size // 6)
    out = Image.new("RGBA", (size + 2 * pad, size + 2 * pad), (0, 0, 0, 0))
    if name in MOJANG:
        ic = MOJANG[name].resize((size, size), Image.NEAREST)
    else:
        mask = icon(name).resize((size, size), Image.LANCZOS)
        ic = Image.new("RGBA", (size, size), tuple(min(255, int(v * 0.45 + 255 * 0.55)) for v in color) + (0,))
        ic.putalpha(mask)
    shadow = Image.new("RGBA", out.size, (0, 0, 0, 0))
    shadow.paste(Image.new("RGBA", ic.size, (6, 8, 16, 255)), (pad, pad + max(1, size // 18)), ic.getchannel("A"))
    shadow = shadow.filter(ImageFilter.GaussianBlur(max(1.5, size / 14)))
    shadow.putalpha(shadow.getchannel("A").point(lambda v: int(v * 0.75)))
    out.alpha_composite(shadow)
    out.alpha_composite(ic, (pad, pad))
    _SPRITES[key] = out
    return out.copy()


def arrow(up: bool, color, size: int) -> Image.Image:
    S = size * 4
    im = Image.new("L", (S, S), 0)
    d = ImageDraw.Draw(im)
    pts = [(S / 2, S * 0.15), (S * 0.9, S * 0.8), (S * 0.1, S * 0.8)] if up else [(S / 2, S * 0.85), (S * 0.9, S * 0.2), (S * 0.1, S * 0.2)]
    d.polygon(pts, fill=255)
    out = Image.new("RGBA", (S, S), color + (0,))
    out.putalpha(im)
    return out.resize((size, size), Image.LANCZOS)


def overlay(effects, palette, left: bool, frame: int = 0) -> Image.Image:
    img = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    colors = [dict(palette)[e] for e in effects]
    # Les icones en file montante : chaque effet a COPIES icones, espacees regulierement ; d'une image a la
    # suivante elles montent d'un cran, et au bout de la boucle chacune a pris la place de la suivante.
    stream = []
    for j in range(COPIES):
        # Avec deux effets, leurs icones alternent dans la file : il y a toujours COPIES icones en tout.
        n = j % len(effects)
        t = (j / COPIES + frame * STEP / (FRAMES * COPIES)) % 1.0
        stream.append((t, j % STEP, effects[n], colors[n]))
    for t, lane, e, color in sorted(stream):
        fade = min(1.0, (1 - t) / 0.42)
        size = int(58 * (1.05 - 0.32 * t))
        b = sprite(e, color, size)
        b.putalpha(b.getchannel("A").point(lambda v: int(v * fade)))
        # Des files qui ondulent, de plus en plus loin du bord.
        off = LANES[lane % len(LANES)] + int(10 * math.sin(t * math.pi * 2 + lane * 2.1))
        cx = off if left else W - off
        cy = int(RISE[0] + (RISE[1] - RISE[0]) * t)
        img.alpha_composite(b, (cx - b.width // 2, cy - b.height // 2))
    return img


def main() -> int:
    global OUT
    if "--minecraft" in sys.argv:
        load_minecraft(Path(sys.argv[sys.argv.index("--minecraft") + 1]))
        OUT = ROOT / "resourcepack-prive" / "assets" / "crossfight" / "textures" / "overlay_mc"
    OUT.mkdir(parents=True, exist_ok=True)
    for old in list(OUT.glob("*.png")) + list(OUT.glob("*.mcmeta")):
        old.unlink()
    made = 0
    for kind, palette, left in (("bonus", BONUS, True), ("malus", MALUS, False)):
        names = [n for n, _ in palette]
        combos = [(n,) for n in names] + list(itertools.combinations(names, 2))
        for combo in combos:
            for frame in range(FRAMES):
                img = overlay(list(combo), palette, left, frame).resize(SIZE, Image.LANCZOS)
                f = OUT / f"{kind}_{'_'.join(combo)}_{frame}.png"
                # En couleurs pleines : une palette de 256 couleurs ferait des marches dans le degrade de la bande.
                img.save(f, optimize=True)
                (OUT / (f.name + ".mcmeta")).write_text(json.dumps({"texture": {"blur": True}}) + "\n", encoding="utf-8")
                made += 1
    print(f"{made} images dans {OUT.relative_to(ROOT)}")
    if "--apercu" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--apercu") + 1])
        bg = Image.new("RGBA", (W, H), (70, 90, 120, 255))
        for y in range(H):
            ImageDraw.Draw(bg).line([(0, y), (W, y)], fill=(70 + y // 8, 95 + y // 10, 130 - y // 12, 255))
        bg.alpha_composite(overlay(["vitesse", "force"], BONUS, True))
        bg.alpha_composite(overlay(["poison", "entrave"], MALUS, False))
        bg.convert("RGB").save(out)
        # La boucle, image par image (le bord gauche avec deux bonus, le bord droit avec un malus).
        strip = Image.new("RGBA", (FRAMES * 190, H), (60, 80, 110, 255))
        for fr in range(FRAMES):
            strip.alpha_composite(overlay(["vitesse", "force"], BONUS, True, fr).crop((0, 0, 95, H)), (fr * 190, 0))
            strip.alpha_composite(overlay(["feu"], MALUS, False, fr).crop((W - 95, 0, W, H)), (fr * 190 + 95, 0))
        strip.convert("RGB").save(out.with_name(out.stem + "_boucle.png"))
        sheet = Image.new("RGBA", (64 * 7 + 16, 64 * 2 + 16), (30, 34, 44, 255))
        for i, (n, col) in enumerate(BONUS):
            sheet.alpha_composite(badge(n, col, 60), (8 + i * 64, 8))
        for i, (n, col) in enumerate(MALUS):
            sheet.alpha_composite(badge(n, col, 60), (8 + i * 64, 72))
        sheet = sheet.resize((sheet.width * 2, sheet.height * 2), Image.LANCZOS)
        sheet.convert("RGB").save(out.with_name(out.stem + "_icones.png"))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
