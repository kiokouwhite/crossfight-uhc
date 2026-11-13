#!/usr/bin/env python3
"""Prepare les images de l'ecran de selection en 3D (game/SelectionStage.java).

Usage :  python3 tools/build_selection.py [--apercu apercu.png [licence] [personnage_survole]]

Chaque personnage est une grande carte inclinee, collee a ses voisines, comme sur une affiche : son
illustration recadree dans la carte, une bande a sa couleur avec son nom en capitales italiques, un
bandeau sombre et l'icone de sa voie a cheval sur les deux. Des objets du pack affiches par des ItemDisplay :
  carte       tout cela, d'une piece                       PRIVE   textures/item/select/card/<id>.png
  icone de voie (cartes sans illustration)                 PRIVE   textures/item/select/voie/<voie>.png
  logo de licence (en haut a gauche)                       PRIVE   textures/item/select/logo/<licence>.png
  carte vide  fond, bande blanche teinte a la couleur      public  textures/item/select/card_plate.png
  lueur       halo de la carte visee, teint                public  textures/item/select/card_glow.png
  fond        mur sombre a eclats derriere les cartes      public  textures/item/select/backdrop.png
Un personnage sans illustration a la carte vide, son nom en texte et son skin.

Sources privees (jamais versionnees) : resourcepack-prive/selection/{art,voie,logo}/<nom>.(png|webp|jpg)
Noms et couleurs : resourcepack/selection.txt ; voie et cadrage de chaque illustration :
resourcepack/selection_cartes.txt. Les dimensions (en blocs) sont les memes que dans
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

# Geometrie d'une carte, en blocs (voir SelectionStage).
PH = 6.2                              # hauteur
SLANT = 0.96                          # le haut est decale d'autant vers la droite
CARD_PX = (512, 992)                  # image de la carte : sa largeur couvre tout le parallelogramme
QW = PH * CARD_PX[0] / CARD_PX[1]     # 3.2 : largeur du carre affiche
PW = QW - SLANT                       # 2.24 : largeur de la carte a une hauteur donnee
GLOW_K = (1.16, 1.07)                 # le halo deborde de la carte
GLOW_PX = (592, 1056)
BAND_TOP, STRIP_TOP = 0.70, 0.885     # la bande de couleur, puis le bandeau sombre (fractions de hauteur)
FACE_Y = 0.20                         # ou se place le visage du personnage (fraction de hauteur)
BACKDROP_PX = (1024, 512)

def save(img: Image.Image, base: Path, rel: str):
    out = base / "textures" / "item" / "select" / (rel + ".png")
    out.parent.mkdir(parents=True, exist_ok=True)
    img.save(out, optimize=True)
    model(base, rel, tinted=rel in ("card_plate", "card_glow"))
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


# ------------------------------------------------------------------ cartes

def shape(w, h):
    """Le parallelogramme de la carte dans son image : en bas de 0 a la largeur, en haut decale a droite."""
    sl, pw = SLANT / QW * w, PW / QW * w
    return [(0, h), (pw, h), (w, 0), (sl, 0)]


def center_x(y, w, h):
    """Le milieu de la carte a la hauteur y (pixels depuis le haut)."""
    sl, pw = SLANT / QW * w, PW / QW * w
    return sl * (1 - y / h) + pw / 2


def mask_of(poly, size):
    m = Image.new("L", size, 0)
    ImageDraw.Draw(m).polygon(poly, fill=255)
    return m


def shards(size, seed, base=(20, 20, 24), light=(46, 46, 54), n=14):
    """Un fond sombre a eclats anguleux (comme une vitre brisee), dessine ici."""
    import random
    rnd = random.Random(seed)
    w, h = size
    img = Image.new("RGBA", size, base + (255,))
    d = ImageDraw.Draw(img)
    for _ in range(n):
        x, y = rnd.uniform(-0.2, 1.2) * w, rnd.uniform(-0.2, 1.2) * h
        a = rnd.uniform(0, math.pi)
        L, T = rnd.uniform(0.3, 0.9) * max(w, h), rnd.uniform(0.04, 0.14) * max(w, h)
        dx, dy = math.cos(a), math.sin(a)
        pts = [(x - dx * L / 2, y - dy * L / 2), (x + dx * L / 2 + dy * T * 0.3, y + dy * L / 2 - dx * T * 0.3),
               (x + dx * L * 0.3 + dy * T, y + dy * L * 0.3 - dx * T)]
        k = rnd.uniform(0.25, 1.0)
        col = tuple(round(base[i] + (light[i] - base[i]) * k) for i in range(3))
        d.polygon(pts, fill=col + (255,))
    for _ in range(4):
        x0, y0 = rnd.uniform(0, w), rnd.uniform(0, h)
        a = rnd.uniform(0, math.pi)
        d.line([(x0 - math.cos(a) * w, y0 - math.sin(a) * w), (x0 + math.cos(a) * w, y0 + math.sin(a) * w)], fill=light + (90,), width=2)
    return img


def name_label(text, max_w):
    """Le nom en capitales grasses italiques, blanc, contour noir et ombre portee, legerement incline."""
    text = text.upper()
    size = 84
    while size > 24:
        font = ImageFont.truetype(FONT, size)
        bbox = font.getbbox(text, stroke_width=5)
        if bbox[2] - bbox[0] <= max_w:
            break
        size -= 2
    tw, th = bbox[2] - bbox[0], bbox[3] - bbox[1]
    canvas = Image.new("RGBA", (tw + 48, th + 48), (0, 0, 0, 0))
    shadow = Image.new("RGBA", canvas.size, (0, 0, 0, 0))
    ImageDraw.Draw(shadow).text((24 - bbox[0] + 6, 24 - bbox[1] + 7), text, font=font, fill=(0, 0, 0, 200), stroke_width=5, stroke_fill=(0, 0, 0, 200))
    canvas.alpha_composite(shadow.filter(ImageFilter.GaussianBlur(1.5)))
    ImageDraw.Draw(canvas).text((24 - bbox[0], 24 - bbox[1]), text, font=font, fill=(255, 255, 255, 255), stroke_width=5, stroke_fill=(12, 12, 14, 255))
    return canvas.rotate(7, resample=Image.BICUBIC, expand=True)


def card(art, name, color, voie_img, framing, plate_only=False):
    """Une carte : fond a eclats, illustration recadree (le visage au milieu, a 20 % de la hauteur), bande a la
    couleur du personnage avec son nom, bandeau sombre et icone de voie a cheval sur les deux, liseres."""
    w, h = CARD_PX
    poly = shape(w, h)
    mask = mask_of(poly, (w, h))
    inside = shards((w, h), seed=len(name) * 7 + 3, base=(22, 22, 27), light=(44, 44, 52), n=9)
    if art is not None:
        fx, fy, zoom = framing
        k = zoom * h / art.height
        a = art.resize((max(1, round(art.width * k)), max(1, round(art.height * k))), Image.LANCZOS)
        ty = FACE_Y * h
        tx = center_x(ty, w, h)
        inside.alpha_composite(a, (round(tx - fx * a.width), round(ty - fy * a.height)))
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    out.paste(inside, (0, 0), mask)
    # La bande de couleur (le haut horizontal), plus sombre vers le bas, puis le bandeau sombre.
    band = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    bd = ImageDraw.Draw(band)
    y0, y1 = round(BAND_TOP * h), round(STRIP_TOP * h)
    col = (255, 255, 255) if plate_only else color
    for y in range(y0, y1):
        t = (y - y0) / (y1 - y0)
        c = tuple(round(v * (1 - 0.22 * t)) for v in col)
        bd.line([(0, y), (w, y)], fill=c + (228,))
    bd.line([(0, y0), (w, y0)], fill=tuple(min(255, v + 60) for v in col) + (255,), width=3)
    bd.rectangle((0, y1, w, h), fill=(14, 14, 16, 246))
    out.alpha_composite(Image.composite(band, Image.new("RGBA", (w, h)), mask))
    d = ImageDraw.Draw(out)
    # Liseres : un trait clair sur le bord gauche (comme les separations de l'affiche), un contour sombre.
    d.line(poly + [poly[0]], fill=(0, 0, 0, 200), width=3)
    d.line([poly[0], poly[3]], fill=(225, 225, 232, 230), width=4)
    if plate_only:
        return out
    label = name_label(name, PW / QW * w * 1.02)
    ly = (BAND_TOP + 0.42 * (STRIP_TOP - BAND_TOP)) * h
    out.alpha_composite(label, (round(center_x(ly, w, h) - label.width / 2), round(ly - label.height / 2)))
    if voie_img is not None:
        r = round(0.052 * h)
        cy = round(STRIP_TOP * h + 0.012 * h)
        cx = round(center_x(cy, w, h))
        disc = Image.new("RGBA", (w, h), (0, 0, 0, 0))
        ImageDraw.Draw(disc).ellipse((cx - r, cy - r, cx + r, cy + r), fill=(24, 24, 28, 255), outline=(70, 70, 78, 255), width=3)
        out.alpha_composite(disc)
        ic = voie_img.resize((round(r * 1.5), round(r * 1.5)), Image.LANCZOS)
        out.alpha_composite(ic, (cx - ic.width // 2, cy - ic.height // 2))
    return out


def card_glow():
    """Le halo de la carte visee : sa forme, floutee, blanche (teinte en jeu a la couleur du personnage)."""
    w, h = GLOW_PX
    cw, ch = round(w / GLOW_K[0]), round(h / GLOW_K[1])
    ox, oy = (w - cw) / 2, (h - ch) / 2
    poly = [(x + ox, y + oy) for x, y in shape(cw, ch)]
    m = mask_of(poly, (w, h))
    halo = m.filter(ImageFilter.GaussianBlur(26)).point(lambda v: round(min(255, v * 1.6)))
    edge = Image.new("L", (w, h), 0)
    ImageDraw.Draw(edge).line(poly + [poly[0]], fill=255, width=10)
    edge = edge.filter(ImageFilter.GaussianBlur(5))
    from PIL import ImageChops
    alpha = ImageChops.lighter(halo.point(lambda v: round(v * 0.75)), edge)
    img = Image.new("RGBA", (w, h), (255, 255, 255, 0))
    img.putalpha(alpha)
    return img


def backdrop():
    """Le mur derriere les cartes : sombre, a grands eclats, plus fonce sur les bords."""
    w, h = BACKDROP_PX
    img = shards((w, h), seed=11, base=(17, 17, 21), light=(40, 40, 47), n=26)
    vign = Image.new("L", (w, h), 0)
    px = vign.load()
    for y in range(h):
        for x in range(w):
            r = math.hypot((x - w / 2) / (w / 2), (y - h / 2) / (h / 2))
            px[x, y] = round(255 * min(1.0, max(0.0, (r - 0.55) / 0.6)) * 0.85)
    dark = Image.new("RGBA", (w, h), (6, 6, 8, 255))
    return Image.composite(dark, img, vign)


def framings():
    """voie et cadrage de chaque illustration (resourcepack/selection_cartes.txt)."""
    out = {}
    f = ROOT / "resourcepack" / "selection_cartes.txt"
    if f.exists():
        for line in f.read_text(encoding="utf-8").splitlines():
            t = line.split()
            if len(t) >= 5 and not t[0].startswith("#"):
                out[t[0]] = (t[1], (float(t[2]), float(t[3]), float(t[4])))
    return out


def auto_framing(art):
    """Sans cadrage donne : le haut des pixels visibles pour la tete, le personnage sur 1.15 carte."""
    box = art.getchannel("A").point(lambda a: 255 if a > 8 else 0).getbbox() or (0, 0, art.width, art.height)
    bh = box[3] - box[1]
    return ((box[0] + box[2]) / 2 / art.width, (box[1] + 0.08 * bh) / art.height, 1.15 * art.height / bh)


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
    made.append(save(card(None, "", (255, 255, 255), None, None, plate_only=True), PUBLIC, "card_plate"))
    made.append(save(card_glow(), PUBLIC, "card_glow"))
    made.append(save(backdrop(), PUBLIC, "backdrop"))
    voies = {}
    for f in sources("voie"):
        voies[f.stem] = icon(load(f))
        made.append(save(voies[f.stem], PRIVATE, f"voie/{f.stem}"))
    for f in sources("logo"):
        made.append(save(logo(load(f)), PRIVATE, f"logo/{f.stem}"))
    names = {cid: (name, hexrgb(color)) for cid, name, color in roster()}
    frames = framings()
    for f in sources("art"):
        cid = f.stem
        name, color = names.get(cid, (cid.capitalize(), (200, 200, 200)))
        art = load(f)
        voie, framing = frames.get(cid, (None, None))
        made.append(save(card(art, name, color, voies.get(voie), framing or auto_framing(art)), PRIVATE, f"card/{cid}"))
    for p in made:
        print(p.relative_to(ROOT))
    # Le plugin lit les memes noms et couleurs (lueur au survol, cartes sans illustration).
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
    """Vue de face (sans perspective) de l'ecran de selection, 1920 x 1080, pour verifier la composition."""
    ids = [cid for cid, _, _ in roster()]
    order = {"fire_emblem": ["marth", "corrin", "takumi", "byleth"]}.get(licence, ids)
    colors = {cid: hexrgb(c) for cid, _, c in roster()}
    W, H = 1920, 1080
    ppb = H / 7.7                    # le champ de vision vertical a 5.5 blocs : environ 7.7 blocs
    step = PW + 0.08
    canvas = Image.new("RGBA", (W, H), (10, 10, 12, 255))

    def tex(base, rel):
        p = base / "textures" / "item" / "select" / (rel + ".png")
        return Image.open(p).convert("RGBA") if p.exists() else None

    def put(img, cx, cy, w, h, tint=None):
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
        canvas.alpha_composite(im, (round(W / 2 + cx * ppb - im.width / 2), round(H / 2 - cy * ppb - im.height / 2)))

    put(tex(PUBLIC, "backdrop"), 0, 0.3, 18, 9)
    n = len(order)
    cy0 = 0.38
    for i, cid in enumerate(order):
        cx = (i - (n - 1) / 2) * step
        hov = cid == hovered
        k = 1.06 if hov else 1.0
        if hov:
            put(tex(PUBLIC, "card_glow"), cx, cy0, QW * GLOW_K[0] * k, PH * GLOW_K[1] * k, tint=colors.get(cid, (255, 255, 255)))
        c = tex(PRIVATE, f"card/{cid}")
        if c is None:
            put(tex(PUBLIC, "card_plate"), cx, cy0, QW, PH, tint=colors.get(cid, (200, 200, 200)))
        else:
            put(c, cx, cy0, QW * k, PH * k)
    lg = tex(PRIVATE, f"logo/{licence}")
    if lg:
        # En haut a gauche, par-dessus le haut de la premiere carte, comme sur l'affiche.
        put(lg, -(n - 1) / 2 * step - 0.15, cy0 + PH / 2 - 0.42, 3.4, 3.4 * lg.height / lg.width)
    canvas.convert("RGB").save(out)


if __name__ == "__main__":
    sys.exit(main())
