"""Outils d'analyse des volumes extraits par extrait.py (un lieu = un tableau de blocs [y, z, x]).

    import sys; sys.path.insert(0, "<ce dossier>")
    import socle_lib as sl
    v = sl.load("garreg_mach")          # v.ids [y,z,x] uint16, v.names, v.meta (dict), v.H, v.D, v.W
    air = v.mask("air")                 # tableau de booleens [y,z,x] : la propriete du bloc de chaque case
    solid = v.mask("solid")             # proprietes : air, solid, occluding, collidable, sturdy_up, sturdy_down, light (>0), liquid
    leaves = v.tagged("LEAVES")         # les blocs d'une etiquette du jeu (LEAVES, LOGS, DIRT, BASE_STONE_OVERWORLD, SAND, SLABS, STAIRS, ...)
    is_x = v.named(r"glass|pane")       # les blocs dont le nom contient cette expression reguliere
    low = sl.lowest(solid)              # [z,x] : indice y du plus bas bloc vrai de chaque colonne (-1 s'il n'y en a pas)
    sl.section(v, z=120, path="coupe.png")      # coupe verticale (x en abscisse, y vers le haut), aux couleurs de carte des blocs
    sl.topdown(v, path="dessus.png")            # vue de dessus
    sl.heat(arr2d, path="carte.png")            # une carte [z,x] de valeurs en fausses couleurs (les -1 en noir)

Reperes : y relatif = y du monde d'origine - meta["y1"] ; le sol de la fiche est a l'indice v.sol = meta["sol"] - meta["y1"].
Le plugin pose le lieu de sorte que l'indice v.sol tombe au niveau du terrain de la map : tout ce qui est a un indice
>= v.sol est pose au-dessus du terrain (apres l'avoir degage), tout ce qui est en dessous est pose dans le sol.
Proprietes des blocs : celles du serveur (Paper 1.21.10), lues dans materiaux.txt.
"""
import json, os, re
import numpy as np
from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
DATA = os.environ.get("SOCLE_DONNEES", os.path.join(HERE, "donnees"))
ALIASES = {"grass": "short_grass", "grass_path": "dirt_path", "chain": "iron_chain", "sign": "oak_sign", "wall_sign": "oak_wall_sign"}


def table():
    props = {}
    for line in open(os.path.join(HERE, "materiaux.txt"), encoding="utf-8"):
        p = line.split()
        if len(p) < 10:
            continue
        props[p[0]] = {"air": p[1] == "1", "solid": p[2] == "1", "occluding": p[3] == "1", "collidable": p[4] == "1", "sturdy_up": p[5] == "1",
                       "sturdy_down": p[6] == "1", "light": int(p[7]), "color": tuple(int(p[8][i:i + 2], 16) for i in (0, 2, 4)),
                       "tags": set() if p[9] == "-" else set(p[9].split(","))}
    return props


class Vol:
    def __init__(self, place, data=None):
        z = np.load(os.path.join(data or DATA, place + ".npz"), allow_pickle=False)
        self.ids, self.names, self.meta = z["ids"], [str(n) for n in z["names"]], json.loads(str(z["meta"]))
        self.H, self.D, self.W = self.ids.shape
        self.sol = self.meta["sol"] - self.meta["y1"]
        t = table()
        self.unknown = []
        self.props = []
        for n in self.names:
            base, double = (n[:-7], True) if n.endswith("#double") else (n, False)
            p = t.get(base) or t.get(ALIASES.get(base, ""))
            if p is None:
                self.unknown.append(n)
                p = {"air": False, "solid": True, "occluding": True, "collidable": True, "sturdy_up": True, "sturdy_down": True, "light": 0, "color": (255, 0, 255), "tags": set()}
            p = dict(p)
            if double:
                p.update(occluding=True, sturdy_up=True, sturdy_down=True)
            p["liquid"] = base in ("water", "lava", "bubble_column")
            self.props.append(p)

    def lut(self, f):
        return np.array([bool(f(p, n)) for p, n in zip(self.props, self.names)])

    def mask(self, prop):
        if prop == "light":
            return self.lut(lambda p, n: p["light"] > 0)[self.ids]
        return self.lut(lambda p, n: p[prop])[self.ids]

    def tagged(self, tag):
        return self.lut(lambda p, n: tag in p["tags"])[self.ids]

    def named(self, pattern):
        r = re.compile(pattern)
        return self.lut(lambda p, n: r.search(n) is not None)[self.ids]

    def colors(self):
        return np.array([p["color"] for p in self.props], dtype=np.uint8)


def load(place, data=None):
    return Vol(place, data)


def lowest(mask):
    """[z,x] : l'indice y du plus bas bloc vrai de chaque colonne, -1 s'il n'y en a pas."""
    has = mask.any(axis=0)
    return np.where(has, np.argmax(mask, axis=0), -1)


def highest(mask):
    has = mask.any(axis=0)
    return np.where(has, mask.shape[0] - 1 - np.argmax(mask[::-1], axis=0), -1)


def section(v, z=None, x=None, path="coupe.png", scale=3, mark=None):
    """Coupe verticale a z (ou a x) donne : une image, y vers le haut. `mark` : un tableau de booleens [y,z,x] dessine en rouge."""
    col = v.colors()
    sl = v.ids[:, z, :] if z is not None else v.ids[:, :, x]
    img = col[sl].astype(np.uint8)
    img[sl == 0] = (135, 170, 220)
    if mark is not None:
        m = mark[:, z, :] if z is not None else mark[:, :, x]
        img[m] = (255, 40, 40)
    line = v.sol
    if 0 <= line < v.H:
        img[line - 1, ::8] = (255, 255, 0)          # le niveau du sol de la fiche : un pointille jaune juste sous l'indice sol
    im = Image.fromarray(img[::-1])
    im = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
    im.save(path)
    return path


def topdown(v, path="dessus.png", scale=2):
    notair = v.ids != 0
    top = highest(notair)
    col = v.colors()
    zz, xx = np.indices(top.shape)
    img = col[v.ids[np.maximum(top, 0), zz, xx]].astype(np.float64)
    shade = 0.6 + 0.4 * (top - top[top >= 0].min()) / max(1, top.max() - top[top >= 0].min()) if (top >= 0).any() else 1
    img = img * np.atleast_3d(shade)
    img[top < 0] = 0
    im = Image.fromarray(np.clip(img, 0, 255).astype(np.uint8))
    im.resize((im.width * scale, im.height * scale), Image.NEAREST).save(path)
    return path


def heat(a, path="carte.png", scale=2, lo=None, hi=None):
    """Une carte [z,x] en fausses couleurs (bleu = bas, rouge = haut) ; les valeurs negatives en noir."""
    a = np.asarray(a, dtype=np.float64)
    ok = a >= 0
    lo = a[ok].min() if lo is None and ok.any() else (lo or 0)
    hi = a[ok].max() if hi is None and ok.any() else (hi or 1)
    t = np.clip((a - lo) / max(1e-9, hi - lo), 0, 1)
    img = np.stack([255 * t, 255 * (1 - np.abs(2 * t - 1)), 255 * (1 - t)], axis=-1)
    img[~ok] = 0
    im = Image.fromarray(img.astype(np.uint8))
    im.resize((im.width * scale, im.height * scale), Image.NEAREST).save(path)
    return path
