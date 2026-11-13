#!/usr/bin/env python3
"""Prepare les images de l'ecran de selection en 3D (game/SelectionStage.java).

Usage :  python3 tools/build_selection.py [--apercu apercu.png [licence] [personnage_survole]]

Chaque personnage est un panneau incline fait de plusieurs couches (des objets du pack affiches
par des ItemDisplay) :
  plaque      fond sombre incline, commun a tous           public  textures/item/select/plate.png
  lueur       halo blanc, teinte a la couleur du perso     public  textures/item/select/glow.png
  bandeau     bande coloree + nom du personnage            public  textures/item/select/banner/<id>.png
  illustration                                             PRIVE   textures/item/select/art/<id>.png
  icone de voie                                            PRIVE   textures/item/select/voie/<voie>.png
  logo de licence (au-dessus des panneaux)                 PRIVE   textures/item/select/logo/<licence>.png

Sources privees (jamais versionnees) : resourcepack-prive/selection/{art,voie,logo}/<nom>.(png|webp|jpg)
Noms et couleurs : resourcepack/selection.txt. Les dimensions (en blocs) sont les memes que dans
SelectionStage.java : les modifier des deux cotes.

Le plugin sait ce qui existe grace a tools/build_pack.py, qui declare "select.<type>.<nom>" pour
chaque texture trouvee (comme "skin.<perso>" pour les modeles articules) : sans illustration, le
panneau montre le personnage avec son skin.
"""
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFilter, ImageFont

ROOT = Path(__file__).resolve().parent.parent
PUBLIC = ROOT / "resourcepack" / "assets" / "crossfight"
PRIVATE = ROOT / "resourcepack-prive" / "assets" / "crossfight"
SOURCES = ROOT / "resourcepack-prive" / "selection"
FONT = "/usr/share/fonts/opentype/inter/Inter-BlackItalic.otf"

# Geometrie d'un panneau, en blocs (voir SelectionStage).
QW, PH = 2.97, 5.0            # quad de la plaque
PW, SLANT = 2.52, 0.45         # parallelogramme : largeur, decalage du haut vers la droite
PLATE_PX = (304, 512)
BANNER_PX = (304, 192)         # la bande occupe les 144 px du bas, le nom peut deborder au-dessus
BAND_PX = 144
ART_PX = (384, 512)            # quad de 3.3 x 4.4 blocs


def save(img: Image.Image, base: Path, rel: str):
    out = base / "textures" / "item" / "select" / (rel + ".png")
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, optimize=True)
    model(base, rel, tinted=rel == "glow")
    return out


def model(base: Path, rel: str, tinted: bool):
    """Un carre plat texture, sans ombrage, et l'objet qui l'affiche. Il a ses deux faces (chacune se lit a
    l'endroit de son cote) : les panneaux tournent pour faire face au joueur, et le jeu retourne les
    objets affiches d'un demi-tour, on ne sait donc pas laquelle lui fait face."""
    tex = f"crossfight:item/select/{rel}"
    face = {"uv": [0, 0, 16, 16], "texture": "#0"}
    if tinted:
        face["tintindex"] = 0
    m = {"textures": {"0": tex, "particle": tex},
         "elements": [{"from": [0, 0, 8], "to": [16, 16, 8], "shade": False, "faces": {"south": face, "north": dict(face)}}]}
    mp = base / "models" / "select" / (rel + ".json")
    mp.parent.mkdir(parents=True, exist_ok=True)
    mp.write_text(json.dumps(m, indent=1), encoding="utf-8")
    item = {"type": "minecraft:model", "model": f"crossfight:select/{rel}"}
    if tinted:
        item["tints"] = [{"type": "minecraft:custom_model_data", "index": 0, "default": -1}]
    ip = base / "items" / "select" / (rel + ".json")
    ip.parent.mkdir(parents=True, exist_ok=True)
    ip.write_text(json.dumps({"model": item}, indent=1), encoding="utf-8")


def roster():
    """(id, nom, couleur) de resourcepack/selection.txt. Une ligne qui commence par # est un commentaire
    (pas de commentaire en fin de ligne : les couleurs commencent aussi par #)."""
    out = []
    for line in (ROOT / "resourcepack" / "selection.txt").read_text(encoding="utf-8").splitlines():
        parts = line.split()
        if len(parts) >= 3 and not parts[0].startswith("#") and parts[-1].startswith("#"):
            out.append((parts[0], " ".join(parts[1:-1]), parts[-1]))
    return out


def hexrgb(h: str):
    h = h.lstrip("#")
    return tuple(int(h[i:i + 2], 16) for i in (0, 2, 4))


# ------------------------------------------------------------------ public

def plate():
    w, h = PLATE_PX
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bottom_r = round(PW / QW * w)
    shift = round(SLANT / QW * w)
    poly = [(0, h), (bottom_r, h), (w, 0), (shift, 0)]
    # Degrade sombre, plus clair en haut, avec un liseré clair sur les bords.
    grad = Image.new("RGBA", (w, h))
    gd = ImageDraw.Draw(grad)
    for y in range(h):
        t = y / h
        c = round(46 - 26 * t)
        gd.line([(0, y), (w, y)], fill=(c, c, c + 2, 235))
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).polygon(poly, fill=255)
    img.paste(grad, (0, 0), mask)
    d = ImageDraw.Draw(img)
    d.line(poly + [poly[0]], fill=(120, 120, 128, 255), width=3)
    return img


def glow():
    s = 256
    img = Image.new("RGBA", (s, s), (255, 255, 255, 0))
    px = img.load()
    for y in range(s):
        for x in range(s):
            r = math.hypot((x - s / 2) / (s / 2), (y - s / 2) / (s / 2))
            a = max(0.0, 1.0 - r) ** 1.6
            px[x, y] = (255, 255, 255, round(210 * a))
    return img


def banner(name: str, color):
    w, h = BANNER_PX
    img = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bottom_r = round(PW / QW * w)
    band_shift = round(SLANT * (BAND_PX / PLATE_PX[1]) / QW * w)
    top = h - BAND_PX
    band = [(0, h), (bottom_r, h), (bottom_r + band_shift, top), (band_shift, top)]
    layer = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    ImageDraw.Draw(layer).polygon(band, fill=color + (225,))
    # Un leger degrade vers le bas, plus sombre.
    shade = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sd = ImageDraw.Draw(shade)
    for y in range(top, h):
        sd.line([(0, y), (w, y)], fill=(0, 0, 0, round(70 * (y - top) / BAND_PX)))
    mask = Image.new("L", (w, h), 0)
    ImageDraw.Draw(mask).polygon(band, fill=255)
    layer = Image.alpha_composite(layer, Image.composite(shade, Image.new("RGBA", (w, h)), mask))
    img = Image.alpha_composite(img, layer)

    # Le nom, en capitales grasses italiques, contour noir et ombre portee, legerement incline.
    text = name.upper()
    size = 72
    while size > 20:
        font = ImageFont.truetype(FONT, size)
        bbox = font.getbbox(text, stroke_width=4)
        if bbox[2] - bbox[0] <= w * 0.86:
            break
        size -= 2
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    canvas = Image.new("RGBA", (tw + 40, th + 40), (0, 0, 0, 0))
    cd = ImageDraw.Draw(canvas)
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).text((20 - bbox[0] + 5, 20 - bbox[1] + 6), text, font=font, fill=(0, 0, 0, 170), stroke_width=4, stroke_fill=(0, 0, 0, 170))
    shadow = shadow.filter(ImageFilter.GaussianBlur(2))
    canvas = Image.alpha_composite(canvas, shadow)
    cd = ImageDraw.Draw(canvas)
    cd.text((20 - bbox[0], 20 - bbox[1]), text, font=font, fill=(255, 255, 255, 255), stroke_width=4, stroke_fill=(15, 15, 18, 255))
    canvas = canvas.rotate(6, resample=Image.BICUBIC, expand=True)
    cx = (bottom_r + band_shift) / 2 + band_shift * 0.2
    cy = top + BAND_PX * 0.30
    img.alpha_composite(canvas, (round(cx - canvas.width / 2), max(0, round(cy - canvas.height / 2))))
    return img


# ------------------------------------------------------------------ prive

def load(src: Path) -> Image.Image:
    return Image.open(src).convert("RGBA")


def fit_bottom(img: Image.Image, size, overflow=1.3):
    """Detoure (boite des pixels visibles), puis remplit la hauteur de size, centre en bas. Une illustration
    tres large (flammes, ailes) peut deborder de 30 % sur les cotes : ses bords sont coupes plutot que de
    rapetisser le personnage."""
    alpha = img.getchannel("A").point(lambda a: 255 if a > 8 else 0)
    box = alpha.getbbox()
    if box:
        img = img.crop(box)
    w, h = size
    k = min(h / img.height, w * overflow / img.width)
    img = img.resize((max(1, round(img.width * k)), max(1, round(img.height * k))), Image.LANCZOS)
    out = Image.new("RGBA", size, (0, 0, 0, 0))
    out.alpha_composite(img, ((w - img.width) // 2, h - img.height))
    return out


def icon(img: Image.Image):
    alpha = img.getchannel("A").point(lambda a: 255 if a > 8 else 0)
    box = alpha.getbbox()
    if box:
        img = img.crop(box)
    return img.resize((128, 128), Image.LANCZOS)


def logo(img: Image.Image):
    """Le logo en blanc (son alpha garde), recadre, 512 px de large, hauteur multiple de 16."""
    alpha = img.getchannel("A")
    # Un logo noir sur fond transparent devient blanc ; un logo deja clair garde ses couleurs.
    lum = img.convert("L")
    a_px, l_px = list(alpha.tobytes()), list(lum.tobytes())
    dark = sum(1 for a, l in zip(a_px, l_px) if a > 128 and l < 100)
    opaque = sum(1 for a in a_px if a > 128) or 1
    if dark / opaque > 0.5:
        white = Image.new("RGBA", img.size, (255, 255, 255, 0))
        white.putalpha(alpha)
        img = white
    box = img.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox()
    if box:
        img = img.crop(box)
    w = 512
    h = max(16, round(img.height * w / img.width))
    img = img.resize((w, h), Image.LANCZOS)
    hh = (h + 15) // 16 * 16
    out = Image.new("RGBA", (w, hh), (0, 0, 0, 0))
    out.alpha_composite(img, (0, (hh - h) // 2))
    return out


def sources(kind: str):
    folder = SOURCES / kind
    if not folder.is_dir():
        return []
    return sorted(f for f in folder.iterdir() if f.suffix.lower() in (".png", ".webp", ".jpg", ".jpeg"))


def main() -> int:
    made = []
    made.append(save(plate(), PUBLIC, "plate"))
    made.append(save(glow(), PUBLIC, "glow"))
    for cid, name, color in roster():
        made.append(save(banner(name, hexrgb(color)), PUBLIC, f"banner/{cid}"))
    for f in sources("art"):
        made.append(save(fit_bottom(load(f), ART_PX), PRIVATE, f"art/{f.stem}"))
    for f in sources("voie"):
        made.append(save(icon(load(f)), PRIVATE, f"voie/{f.stem}"))
    for f in sources("logo"):
        made.append(save(logo(load(f)), PRIVATE, f"logo/{f.stem}"))
    for p in made:
        print(p.relative_to(ROOT))
    # Le plugin lit les memes noms et couleurs (lueur au survol, panneaux sans bandeau).
    res = ROOT / "src" / "main" / "resources" / "selection.txt"
    res.write_text((ROOT / "resourcepack" / "selection.txt").read_text(encoding="utf-8"), encoding="utf-8")
    if "--apercu" in sys.argv:
        i = sys.argv.index("--apercu")
        out = Path(sys.argv[i + 1])
        rest = sys.argv[i + 2:]
        preview(out, rest[0] if rest else "fire_emblem", rest[1] if len(rest) > 1 else None)
        print("apercu :", out)
    return 0


# ---------------------------------------------------------------- apercu

def preview(out: Path, licence: str, hovered):
    """Vue de face (sans perspective) de l'ecran de selection, pour verifier la composition."""
    ids = [cid for cid, _, _ in roster()]
    order = {"fire_emblem": ["marth", "corrin", "takumi", "byleth"]}.get(licence, ids)
    colors = {cid: hexrgb(c) for cid, _, c in roster()}
    voies = {"marth": "harmonie", "byleth": "harmonie", "corrin": "destruction", "takumi": "chasse"}
    ppb = 110  # pixels par bloc
    step = 2.85
    n = len(order)
    width_b = n * step + 2.5
    W, H = round(width_b * ppb), round(8.6 * ppb)
    canvas = Image.new("RGBA", (W, H), (16, 14, 22, 255))

    def tex(base, rel):
        p = base / "textures" / "item" / "select" / (rel + ".png")
        return Image.open(p).convert("RGBA") if p.exists() else None

    def put(img, cx, cy, w, h, alpha=1.0, tint=None):
        if img is None:
            return
        im = img.resize((max(1, round(w * ppb)), max(1, round(h * ppb))), Image.LANCZOS)
        if tint:
            r, g, b = tint
            px = im.load()
            for y in range(im.height):
                for x in range(im.width):
                    pr, pg, pb, pa = px[x, y]
                    px[x, y] = (pr * r // 255, pg * g // 255, pb * b // 255, pa)
        if alpha < 1:
            a = im.getchannel("A").point(lambda v: round(v * alpha))
            im.putalpha(a)
        canvas.alpha_composite(im, (round(cx * ppb - im.width / 2), round(H - cy * ppb - im.height / 2)))

    cy0 = 3.4
    lg = tex(PRIVATE, f"logo/{licence}")
    if lg:
        put(lg, width_b / 2, cy0 + PH / 2 + 0.95, 4.2, 4.2 * lg.height / lg.width)
    for i, cid in enumerate(order):
        cx = 1.25 + step * (i + 0.5)
        hov = cid == hovered
        k = 1.08 if hov else 1.0
        put(tex(PUBLIC, "plate"), cx, cy0, QW, PH)
        if hov:
            put(tex(PUBLIC, "glow"), cx, cy0 + 0.3, 3.8, 4.6, tint=colors.get(cid, (255, 255, 255)))
        put(tex(PRIVATE, f"art/{cid}"), cx, cy0 + 0.3 + (0.05 if hov else 0), 3.3 * k, 4.4 * k)
        bqh = BANNER_PX[1] / BANNER_PX[0] * QW
        put(tex(PUBLIC, f"banner/{cid}"), cx, cy0 - PH / 2 + bqh / 2, QW, bqh)
        put(tex(PRIVATE, f"voie/{voies.get(cid, 'harmonie')}"), cx - 0.187, cy0 - PH / 2 + 0.42, 0.72, 0.72)
    canvas.convert("RGB").save(out)


if __name__ == "__main__":
    sys.exit(main())
