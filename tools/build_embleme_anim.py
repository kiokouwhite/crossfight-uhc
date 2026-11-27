#!/usr/bin/env python3
"""L'animation de l'Emblème de Feu, à l'écran de Marth : la Falchion apparaît, l'Emblème vient toucher sa garde, elle
s'entoure d'un halo doré (un reflet parcourt la lame, des étincelles), un éclair blanc, et la Sainte-Falchion apparaît
dans un halo bleu, puis tout s'efface.

Usage : python3 tools/build_embleme_anim.py [--apercu dossier]

Les images (IMAGES, une par tick) sont rendues d'après le modèle de la Falchion du pack privé
(tools/build_falchion.py, ses textures « t » et « sainte_t »), penchée comme son icône, et d'après l'icône de
l'Emblème (tools/build_icones.py : icone/marth_embleme). Elles vont dans la police crossfight:embleme du pack privé
(un caractère par image, à partir de U+E000) ; le plugin les montre en titre (game/Lootbox.java, eveiller), le texte
en sous-titre. Le modèle et l'icône ne sont pas à nous : tout reste dans resourcepack-prive/.
--apercu : une planche de toutes les images et une animation GIF, dans ce dossier.
"""
import json
import math
import random
import sys
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

ROOT = Path(__file__).resolve().parent.parent
sys.path.insert(0, str(ROOT / "tools"))
import pose_preview as pp  # noqa: E402

PRIVE = ROOT / "resourcepack-prive" / "assets" / "crossfight"
TAILLE = 256                 # une image (la plus grande que la police de Minecraft accepte pour un caractère)
SUR = 2                      # rendu deux fois plus grand, puis réduit (bords lisses)
HAUTEUR = 24                 # la hauteur du caractère dans la police (le titre est dessiné 4 fois plus grand)
MONTEE = 24                  # au-dessus de la ligne de base : l'image monte au-dessus du sous-titre
PREMIER = 0xE000

# Le déroulé, en images (une par tick, 20 par seconde). Le plugin change le sous-titre et transforme l'arme à ECLAIR
# (game/Lootbox.java : ANIM_IMAGES et ANIM_ECLAIR, à garder égaux à IMAGES et ECLAIR).
APPARITION = 8               # la Falchion apparaît
VOL = 12                     # l'Emblème vole jusqu'à la garde
HALO = 16                    # la Falchion brille
ECLAIR = APPARITION + VOL + HALO          # 36 : l'éclair blanc
BLANC = 5                    # l'éclair
REVELE = 7                   # la Sainte-Falchion sort du blanc
TENUE = 16                   # elle brille de bleu
FONDU = 8                    # tout s'efface
IMAGES = ECLAIR + BLANC + REVELE + TENUE + FONDU       # 72

OR = np.array([255, 214, 110]) / 255
BLEU = np.array([110, 210, 255]) / 255
BLANC_PUR = np.array([1.0, 1.0, 1.0])


# --------------------------------------------------------------------------- le rendu de l'épée

def modele():
    return json.loads((PRIVE / "models" / "item" / "falchion.json").read_text(encoding="utf-8"))


def placer(model):
    """La Falchion penchée comme son icône (poignée en bas à gauche, pointe en haut à droite), un peu tournée pour
    montrer son épaisseur : une fonction modèle -> repère du rendu, centrée et à la taille de l'image."""
    tilt = pp.rot(0, 0, -45)
    c = np.array([8, 8, 8], dtype=float)
    pts = []
    for el in model["elements"]:
        a, b = el["from"], el["to"]
        for x in (a[0], b[0]):
            for y in (a[1], b[1]):
                for z in (a[2], b[2]):
                    pts.append(c + tilt @ (np.array([x, y, z], dtype=float) - c))
    pts = np.array(pts)
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    milieu = (lo + hi) / 2
    # La plus grande diagonale tient dans 72 % de l'image : de la place pour le halo.
    etendue = max(hi[0] - lo[0], hi[1] - lo[1])
    echelle = TAILLE * SUR * 0.72 / etendue

    def f(q):
        q = c + tilt @ (np.array(q, dtype=float) - c) - milieu
        return q
    return f, echelle


def rendre(model, prefixe, f, echelle, lacet=14):
    """L'épée (textures « t » ou « sainte_t ») en RGBA, sur fond transparent, à la taille de l'image."""
    tdir = PRIVE / "textures" / "item" / "falchion"
    texs = {k: np.array(Image.open(tdir / f"{prefixe}{k}.png").convert("RGBA")) for k in model["textures"] if k != "particle"}
    quads = []
    for el in model["elements"]:
        a, b = el["from"], el["to"]
        rot = el.get("rotation")
        if rot:
            ang = {"x": (rot["angle"], 0, 0), "y": (0, rot["angle"], 0), "z": (0, 0, rot["angle"])}[rot["axis"]]
            R, o = pp.rot(*ang), np.array(rot["origin"], dtype=float)
        for face, fa in el["faces"].items():
            tex = texs[fa["texture"][1:]]
            k = tex.shape[1] / 16
            pts = []
            for pt in pp.FACES[face](a, b):
                q = np.array(pt, dtype=float)
                if rot:
                    q = o + R @ (q - o)
                pts.append(f(q))
            u1, v1, u2, v2 = (x * k for x in fa["uv"])
            quads.append((pts, [(u1, v1), (u2, v1), (u1, v2), (u2, v2)], tex))
    n = TAILLE * SUR
    # pose_preview.render : x à droite, y vers le haut depuis 70 px au-dessus du bas ; on centre.
    decale = np.array([0, (n / 2 - 70) / echelle, 0])
    quads = [([p + decale for p in pts], uv, tex) for pts, uv, tex in quads]
    im = Image.fromarray(pp.render(quads, lacet, size=(n, n), scale=echelle, alpha=True), "RGBA")
    return np.asarray(im.resize((TAILLE, TAILLE), Image.LANCZOS)).astype(np.float32) / 255


def point(f, echelle, q, lacet=14):
    """Où tombe un point du modèle dans l'image (en pixels de l'image finale)."""
    p = f(q)
    a = math.radians(lacet)
    right = np.array([math.cos(a), 0, -math.sin(a)])
    n = TAILLE * SUR
    sx = n / 2 + (p @ right) * echelle
    sy = n - 70 - (p[1] + (n / 2 - 70) / echelle) * echelle
    return sx / SUR, sy / SUR


# --------------------------------------------------------------------------- les effets

def sur(dst, src):
    """src par-dessus dst (deux images RGBA en flottants, couleurs non prémultipliées)."""
    a_s, a_d = src[..., 3:4], dst[..., 3:4]
    a = a_s + a_d * (1 - a_s)
    rgb = (src[..., :3] * a_s + dst[..., :3] * a_d * (1 - a_s)) / np.maximum(a, 1e-6)
    return np.concatenate([rgb, a], axis=-1)


def flou(masque, rayon):
    im = Image.fromarray((np.clip(masque, 0, 1) * 255).astype(np.uint8), "L").filter(ImageFilter.GaussianBlur(rayon))
    return np.asarray(im).astype(np.float32) / 255


def halo(masque, couleur, rayon, force):
    """Une lueur de cette couleur autour de la forme."""
    g = np.clip(flou(masque, rayon) * 2.2 * force, 0, 1)
    out = np.zeros((TAILLE, TAILLE, 4), np.float32)
    out[..., :3] = couleur
    out[..., 3] = g
    return out


def blanchir(img, k, couleur=BLANC_PUR):
    out = img.copy()
    out[..., :3] = out[..., :3] * (1 - k) + couleur * k
    return out


def opacite(img, k):
    out = img.copy()
    out[..., 3] *= k
    return out


def reflet(img, t, largeur=0.09):
    """Une bande de lumière en travers de la lame, à la position t (0 : la poignée, 1 : la pointe), seulement sur l'épée."""
    yy, xx = np.mgrid[0:TAILLE, 0:TAILLE].astype(np.float32)
    # La lame va du bas à gauche au haut à droite : la position le long de l'épée est (x - y).
    s = ((xx - yy) / TAILLE + 1) / 2
    bande = np.clip(1 - np.abs(s - (0.18 + 0.7 * t)) / largeur, 0, 1) ** 1.5
    out = img.copy()
    out[..., :3] = out[..., :3] + (1 - out[..., :3]) * (bande * 0.85)[..., None]
    return out


def etoile(dessin_rgba, x, y, r, couleur, force):
    """Une étincelle à quatre branches, en flottants, ajoutée à l'image."""
    yy, xx = np.mgrid[0:TAILLE, 0:TAILLE].astype(np.float32)
    dx, dy = np.abs(xx - x), np.abs(yy - y)
    branche = np.clip(1 - (dx / r + dy / (r * 0.12)), 0, 1) + np.clip(1 - (dy / r + dx / (r * 0.12)), 0, 1)
    coeur = np.clip(1 - np.hypot(dx, dy) / (r * 0.35), 0, 1)
    a = np.clip((branche + coeur) * force, 0, 1)
    couche = np.zeros_like(dessin_rgba)
    couche[..., :3] = couleur * 0.4 + 0.6
    couche[..., 3] = a
    return sur(dessin_rgba, couche)


def etincelles(img, masque, graine, n, couleur, force, rayon=7):
    """Des étincelles posées sur l'épée, qui naissent et s'éteignent (graine : une par image)."""
    r = random.Random(graine)
    ys, xs = np.nonzero(masque > 0.5)
    if len(xs) == 0:
        return img
    for _ in range(n):
        i = r.randrange(len(xs))
        img = etoile(img, xs[i] + r.uniform(-4, 4), ys[i] + r.uniform(-4, 4), rayon * r.uniform(0.6, 1.2), couleur, force * r.uniform(0.5, 1))
    return img


def eclat(t, couleur):
    """Un grand rond de lumière au centre (l'éclair), d'intensité t."""
    yy, xx = np.mgrid[0:TAILLE, 0:TAILLE].astype(np.float32)
    d = np.hypot(xx - TAILLE / 2, yy - TAILLE / 2) / (TAILLE / 2)
    out = np.zeros((TAILLE, TAILLE, 4), np.float32)
    out[..., :3] = couleur
    out[..., 3] = np.clip((1 - d) * 1.4, 0, 1) ** 1.6 * t
    return out


def emblème(taille):
    ic = PRIVE / "textures" / "item" / "icone" / "marth_embleme.png"
    if not ic.exists():
        return None
    im = Image.open(ic).convert("RGBA")
    return im.resize((taille, taille), Image.NEAREST)


def poser(img, sprite, cx, cy, alpha=1.0):
    couche = Image.new("RGBA", (TAILLE, TAILLE), (0, 0, 0, 0))
    couche.paste(sprite, (int(round(cx - sprite.width / 2)), int(round(cy - sprite.height / 2))))
    c = np.asarray(couche).astype(np.float32) / 255
    c[..., 3] *= alpha
    return sur(img, c)


def doux(t):
    t = min(1.0, max(0.0, t))
    return t * t * (3 - 2 * t)


# --------------------------------------------------------------------------- les images

def images():
    model = modele()
    f, echelle = placer(model)
    falchion = rendre(model, "t", f, echelle)
    sainte = rendre(model, "sainte_t", f, echelle)
    masque = falchion[..., 3]
    # La garde : le milieu du pavé "garde" (ou de la poignée), là où l'Emblème vient se poser.
    garde = next((el for el in model["elements"] if el["name"] == "garde"), model["elements"][0])
    gx, gy = point(f, echelle, [(garde["from"][k] + garde["to"][k]) / 2 for k in range(3)])
    sprite = emblème(44)
    vide = np.zeros((TAILLE, TAILLE, 4), np.float32)
    out = []
    for i in range(IMAGES):
        img = vide.copy()
        if i < APPARITION:                                     # la Falchion apparaît
            t = doux((i + 1) / APPARITION)
            img = sur(img, halo(masque, OR, 10, 0.25 * t))
            img = sur(img, opacite(falchion, t))
        elif i < APPARITION + VOL:                             # l'Emblème vole jusqu'à la garde
            t = (i - APPARITION + 1) / VOL
            img = sur(img, halo(masque, OR, 10, 0.25 + 0.15 * t))
            img = sur(img, falchion)
            if sprite is not None:
                u = doux(t)
                # Du bas à droite vers la garde, sur un arc ; il rétrécit en approchant.
                sx = TAILLE * 0.86 + (gx - TAILLE * 0.86) * u
                sy = TAILLE * 0.9 + (gy - TAILLE * 0.9) * u - math.sin(u * math.pi) * 30
                s = sprite.resize((max(8, int(44 - 16 * u)),) * 2, Image.NEAREST)
                img = sur(img, eclat(0.0, OR))
                img = poser(img, s, sx, sy)
                if i == APPARITION + VOL - 1:                  # le contact : une petite gerbe
                    img = etoile(img, gx, gy, 26, OR, 1.0)
        elif i < ECLAIR:                                       # la Falchion brille
            t = (i - APPARITION - VOL + 1) / HALO
            img = sur(img, halo(masque, OR, 8 + 10 * t, 0.4 + 0.6 * t))
            epee = blanchir(falchion, 0.35 * t, np.array([1.0, 0.93, 0.7]))
            epee = reflet(epee, (t * 2) % 1.0)                 # deux passages du reflet
            img = sur(img, epee)
            if sprite is not None and t < 0.5:                 # l'Emblème se fond dans l'épée
                img = poser(img, sprite.resize((28, 28), Image.NEAREST), gx, gy, 1 - t * 2)
            img = etincelles(img, masque, i, 2 + int(4 * t), OR, 0.9)
        elif i < ECLAIR + BLANC:                               # l'éclair
            t = (i - ECLAIR + 1) / BLANC
            img = sur(img, halo(masque, BLANC_PUR, 16, 1.0))
            img = sur(img, blanchir(falchion, 0.6 + 0.4 * t))
            img = sur(img, eclat(0.55 + 0.45 * math.sin(t * math.pi), BLANC_PUR))
        elif i < ECLAIR + BLANC + REVELE:                      # la Sainte-Falchion sort du blanc
            t = (i - ECLAIR - BLANC + 1) / REVELE
            img = sur(img, halo(sainte[..., 3], BLEU, 18 - 6 * t, 1.0))
            img = sur(img, blanchir(sainte, 1 - doux(t)))
            img = sur(img, eclat(0.6 * (1 - t), BLANC_PUR))
        elif i < IMAGES - FONDU:                               # elle brille de bleu
            t = (i - ECLAIR - BLANC - REVELE) / TENUE
            pouls = 0.75 + 0.25 * math.sin(t * math.pi * 3)
            img = sur(img, halo(sainte[..., 3], BLEU, 12, pouls))
            img = sur(img, reflet(sainte, t) if t < 0.6 else sainte)
            img = etincelles(img, sainte[..., 3], 1000 + i, 4, BLEU, 0.9)
        else:                                                  # tout s'efface
            t = (i - (IMAGES - FONDU) + 1) / FONDU
            k = 1 - doux(t)
            img = sur(img, halo(sainte[..., 3], BLEU, 12, 0.75 * k))
            img = sur(img, opacite(sainte, k))
        out.append(Image.fromarray((np.clip(img, 0, 1) * 255).astype(np.uint8), "RGBA"))
    return out


def ecrire(frames):
    dossier = PRIVE / "textures" / "font" / "embleme"
    dossier.mkdir(parents=True, exist_ok=True)
    for old in dossier.glob("*.png"):
        old.unlink()
    providers = []
    for i, im in enumerate(frames):
        im.save(dossier / f"f{i:02d}.png", optimize=True)
        providers.append({"type": "bitmap", "file": f"crossfight:font/embleme/f{i:02d}.png",
                          "height": HAUTEUR, "ascent": MONTEE, "chars": [chr(PREMIER + i)]})
    police = PRIVE / "font" / "embleme.json"
    police.parent.mkdir(parents=True, exist_ok=True)
    police.write_text(json.dumps({"providers": providers}, indent=1, ensure_ascii=True) + "\n", encoding="utf-8")
    return dossier, police


def apercu(frames, dossier: Path):
    dossier.mkdir(parents=True, exist_ok=True)
    fond = (34, 36, 44, 255)
    cases = []
    for im in frames:
        c = Image.new("RGBA", im.size, fond)
        c.alpha_composite(im)
        cases.append(c.convert("RGB"))
    cols = 12
    rangs = (len(cases) + cols - 1) // cols
    k = 128
    planche = Image.new("RGB", (cols * k, rangs * k), fond[:3])
    d = ImageDraw.Draw(planche)
    for i, c in enumerate(cases):
        planche.paste(c.resize((k, k), Image.LANCZOS), ((i % cols) * k, (i // cols) * k))
        d.text(((i % cols) * k + 3, (i // cols) * k + 2), str(i), fill=(200, 200, 200))
    planche.save(dossier / "embleme_planche.png")
    cases[0].save(dossier / "embleme_animation.gif", save_all=True, append_images=cases[1:] + [cases[-1]] * 10, duration=50, loop=0)
    return dossier / "embleme_planche.png", dossier / "embleme_animation.gif"


def main() -> int:
    if not (PRIVE / "models" / "item" / "falchion.json").exists():
        print("pas de Falchion dans le pack privé (tools/build_falchion.py d'abord) : rien à faire")
        return 0
    frames = images()
    dossier, police = ecrire(frames)
    print(f"{len(frames)} images -> {dossier.relative_to(ROOT)}, police {police.relative_to(ROOT)}")
    if "--apercu" in sys.argv:
        for p in apercu(frames, Path(sys.argv[sys.argv.index("--apercu") + 1])):
            print("apercu :", p)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
