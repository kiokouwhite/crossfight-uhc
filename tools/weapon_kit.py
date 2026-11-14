#!/usr/bin/env python3
"""Boîte à outils pour modéliser des armes en pavés (style Blockbench) à partir d'une description courte.

Usage :  python3 tools/weapon_kit.py <definitions.py> [--apercu planche.png] [--seul nom[,nom...]]

Un fichier de définitions (privé : resourcepack-prive/modeles/definitions/) crée des objets Arme et les
range dans une liste ARMES. Chaque arme est décrite par son PROFIL : des pièces rectangulaires posées dans
un plan (u vers la droite, v vers le haut), avec une épaisseur, comme on dessinerait l'arme de côté ; des
pavés libres en 3D complètent au besoin. Le script écrit pour chacune un projet Blockbench (format Java
Block/Item, texture incluse) dans resourcepack-prive/modeles/brouillons/<nom>.bbmodel, et une planche
d'aperçu. Rien d'ici ne dessine une arme précise : les dessins restent dans les fichiers privés.

Genres (comment le profil est posé dans le modèle, et où la main tient l'arme) :
  lame   épée, couteau, faux, lance, batte... : debout, le profil vu de face (u = x, v = y depuis le bas),
         épaisseur le long de z. La main tient la poignée en `prise` (par défaut u = 0, v = 2.5).
  feu    pistolet, fusil : couché, la bouche du canon vers le nord (u = vers le canon, v = y depuis le
         centre), épaisseur le long de x. Le profil est la vue du côté droit de l'arme.
  arc    arc : debout, vu de côté, le ventre de l'arc vers le nord (u), la corde vers le sud.
  objet  gantelet, drone... : pavés libres autour du centre du modèle.

Une image détourée vue de côté peut aussi être décalquée (Arme.trace) : la silhouette devient des pavés,
plus épais au coeur de l'arme. C'est ainsi que sont faites les armes à feu.

Ces projets sont des brouillons à retoucher dans Blockbench. Ils ne sont pas encore dans le pack : seule la
Falchion de Marth l'est (tools/build_falchion.py).
"""
import base64
import io
import json
import math
import runpy
import sys
import uuid
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
DRAFTS = ROOT / "resourcepack-prive" / "modeles" / "brouillons"
ORIGIN = {"lame": (8.0, 0.0, 8.0), "feu": (8.0, 8.0, 8.0), "arc": (8.0, 8.0, 8.0), "objet": (8.0, 8.0, 8.0)}
LIMITS = (-16.0, 32.0)


def rgb(c):
    if isinstance(c, str):
        c = c.lstrip("#")
        return tuple(int(c[i:i + 2], 16) for i in (0, 2, 4))
    return tuple(c)


def tone(c, k):
    """Éclaircit (k > 1) ou assombrit (k < 1) une couleur, en gardant sa teinte."""
    if k >= 1:
        return tuple(min(255, round(v + (255 - v) * (k - 1))) for v in c)
    return tuple(max(0, round(v * k)) for v in c)


class Arme:
    """Une arme : ses couleurs, ses pièces, et où la main la tient."""

    def __init__(self, nom, genre="lame", couleurs=None, prise=None, titre=None, densite=2, relief=True):
        if genre not in ORIGIN:
            raise ValueError(f"genre inconnu : {genre}")
        self.nom, self.genre, self.titre = nom, genre, titre or nom
        self.couleurs = {k: rgb(v) for k, v in (couleurs or {}).items()}
        self.prise = tuple(prise) if prise else ((0.0, 2.5, 0.0) if genre == "lame" else (0.0, 0.0, 0.0))
        self.densite, self.relief = densite, relief
        self.pieces = []

    # ------------------------------------------------------------ pièces

    def p(self, nom, couleur, u, v, w, h, t=1.0, rot=0.0, off=0.0, groupe=None, relief=None):
        """Une pièce du profil : centre (u, v), largeur w (le long de u), hauteur h (le long de v), épaisseur t,
        tournée de `rot` degrés dans le plan du profil (sens inverse des aiguilles d'une montre), décalée de
        `off` le long de l'épaisseur."""
        while rot > 45:
            rot, w, h = rot - 90, h, w
        while rot < -45:
            rot, w, h = rot + 90, h, w
        if self.genre == "lame":
            c, s, axis, angle = (u, v, off), (w, h, t), "z", rot
        elif self.genre in ("feu", "arc"):
            c, s, axis, angle = (off, v, -u), (t, h, w), "x", rot
        else:
            c, s, axis, angle = (u, v, off), (w, h, t), "z", rot
        self._add(nom, couleur, c, s, (axis, angle) if abs(angle) > 1e-6 else None, groupe, relief)
        return self

    def sym(self, nom, couleur, u, v, w, h, t=1.0, rot=0.0, off=0.0, groupe=None, relief=None):
        """La même pièce des deux côtés de l'axe (u et -u, rotations opposées)."""
        self.p(nom + "_d", couleur, u, v, w, h, t, rot, off, groupe, relief)
        self.p(nom + "_g", couleur, -u, v, w, h, t, -rot, off, groupe, relief)
        return self

    def ligne(self, nom, couleur, points, w, t=1.0, off=0.0, groupe=None, relief=None, recouvre=0.3, sym=False):
        """Une ligne brisée dans le plan du profil (une courbe : branche d'arc, griffe, croissant) : un pavé de
        largeur w entre chaque paire de points (u, v) qui se suivent. `sym` : aussi son reflet (u -> -u)."""
        for side in ((1, "") if not sym else (1, "_d"), (-1, "_g")) if sym else ((1, ""),):
            k, suffix = side
            pts = [(k * u, v) for u, v in points]
            for i in range(len(pts) - 1):
                (u0, v0), (u1, v1) = pts[i], pts[i + 1]
                du, dv = u1 - u0, v1 - v0
                length = math.hypot(du, dv)
                if length < 1e-6:
                    continue
                self.p(f"{nom}{suffix}{i + 1}", couleur, (u0 + u1) / 2, (v0 + v1) / 2, w, length + recouvre, t,
                       math.degrees(math.atan2(-du, dv)), off, groupe or nom, relief)
        return self

    def trace(self, image, longueur, ancre, cellule=0.5, teintes=8, miroir=False, epaisseur=(0.6, 1.7), seuil=110, prefixe="t"):
        """Décalque la silhouette d'une image détourée (fond transparent) vue de côté : elle est découpée en
        cases de `cellule` unités, les cases voisines de même teinte sont réunies en pavés, et chaque pavé est
        d'autant plus épais qu'il est loin du bord de la silhouette (le corps est épais, le canon fin).

        longueur : la longueur de l'arme dans le modèle (la largeur de l'image) ; ancre : le point de l'image
        (fractions de sa largeur depuis la gauche et de sa hauteur depuis le haut, après le miroir éventuel) qui
        vient sur la prise de l'arme ; miroir : retourne l'image (pour que le canon pointe vers u positif)."""
        import numpy as np
        im = Image.open(image).convert("RGBA")
        im = im.crop(im.getbbox())
        if miroir:
            im = im.transpose(Image.FLIP_LEFT_RIGHT)
        nx = max(2, round(longueur / cellule))
        ny = max(2, round(im.height / im.width * longueur / cellule))
        small = np.array(im.convert("RGBa").resize((nx, ny), Image.BOX), dtype=float)   # couleurs prémultipliées
        alpha = small[:, :, 3]
        solid = alpha >= seuil
        colors = np.zeros((ny, nx, 3))
        colors[solid] = small[:, :, :3][solid] / alpha[solid][:, None] * 255.0
        # Réduit à quelques teintes : les cases voisines de même teinte se réunissent en grands pavés.
        cells = [tuple(int(v) for v in colors[y, x]) for y in range(ny) for x in range(nx) if solid[y, x]]
        strip = Image.new("RGB", (len(cells), 1))
        strip.putdata(cells)
        q = strip.quantize(colors=teintes, method=Image.Quantize.MEDIANCUT, dither=Image.Dither.NONE)
        pal = q.getpalette()
        idx = iter(q.getdata())
        index = -np.ones((ny, nx), dtype=int)
        for y in range(ny):
            for x in range(nx):
                if solid[y, x]:
                    index[y, x] = next(idx)
        for k in sorted(set(index[solid].tolist())):
            self.couleurs[f"{prefixe}{k}"] = tuple(pal[3 * k:3 * k + 3])
        # Distance de chaque case au bord de la silhouette (en cases).
        dist = np.zeros((ny, nx), dtype=int)
        cur = solid.copy()
        while cur.any():
            dist[cur] += 1
            pad = np.pad(cur, 1, constant_values=False)
            cur = cur & pad[:-2, 1:-1] & pad[2:, 1:-1] & pad[1:-1, :-2] & pad[1:-1, 2:]
        # Réunion gloutonne en rectangles de même teinte.
        used = ~solid
        fx, fy = ancre
        n = 0
        for y in range(ny):
            for x in range(nx):
                if used[y, x]:
                    continue
                k = index[y, x]
                w = 1
                while x + w < nx and not used[y, x + w] and index[y, x + w] == k:
                    w += 1
                h = 1
                while y + h < ny and not used[y + h, x:x + w].any() and (index[y + h, x:x + w] == k).all():
                    h += 1
                used[y:y + h, x:x + w] = True
                depth = dist[y:y + h, x:x + w].max() * cellule
                t = min(epaisseur[1], epaisseur[0] + 0.75 * (depth - cellule))
                n += 1
                u = (x + w / 2 - fx * nx) * cellule + self.prise[0]
                v = (fy * ny - (y + h / 2)) * cellule + self.prise[1]
                self.p(f"{prefixe}{n:03d}", f"{prefixe}{k}", u, v, w * cellule, h * cellule, round(max(epaisseur[0], t), 2),
                       groupe=f"{prefixe}{k}", relief=False)
        return self

    def c(self, nom, couleur, cx, cy, cz, sx, sy, sz, rot=None, groupe=None, relief=None):
        """Un pavé libre : centre (x, y, z) par rapport à l'origine du genre, tailles, rotation (axe, degrés)."""
        self._add(nom, couleur, (cx, cy, cz), (sx, sy, sz), rot if rot and abs(rot[1]) > 1e-6 else None, groupe, relief)
        return self

    def _add(self, nom, couleur, c, s, rot, groupe, relief):
        if couleur not in self.couleurs:
            raise ValueError(f"{self.nom} : couleur inconnue '{couleur}' ({nom})")
        o = ORIGIN[self.genre]
        center = [o[i] + c[i] for i in range(3)]
        self.pieces.append({"nom": nom, "couleur": couleur, "centre": center, "taille": [max(0.05, v) for v in s],
                            "rot": rot, "groupe": groupe or nom.split("_")[0],
                            "relief": self.relief if relief is None else relief})

    # ------------------------------------------------------------ modèle

    def grip(self):
        """Le point tenu par la main, dans le repère du modèle."""
        o, g = ORIGIN[self.genre], self.prise
        if self.genre == "lame":
            return (o[0] + g[0], o[1] + g[1], o[2] + g[2])
        if self.genre in ("feu", "arc"):
            return (o[0] + g[2], o[1] + g[1], o[2] - g[0])
        return (o[0] + g[0], o[1] + g[1], o[2] + g[2])

    def elements(self):
        """Les pavés au format des modèles de Minecraft (sans les faces), avec un avertissement s'ils débordent."""
        out = []
        for p in self.pieces:
            c, s = p["centre"], p["taille"]
            el = {"name": p["nom"], "from": [round(c[i] - s[i] / 2, 3) for i in range(3)],
                  "to": [round(c[i] + s[i] / 2, 3) for i in range(3)]}
            if p["rot"]:
                el["rotation"] = {"angle": round(p["rot"][1], 2), "axis": p["rot"][0], "origin": [round(v, 3) for v in c]}
            for v in el["from"] + el["to"]:
                if not LIMITS[0] <= v <= LIMITS[1]:
                    print(f"attention : {self.nom}/{p['nom']} sort des limites du modele ({v})")
            out.append(el)
        return out

    def atlas(self):
        """La texture : un rectangle par face, à plat, avec un léger relief (bord clair en haut à gauche, sombre
        en bas à droite). Rend l'image et, pour chaque pièce, les uv de ses six faces (en unités 0-16)."""
        d = self.densite
        rects = []
        for i, p in enumerate(self.pieces):
            sx, sy, sz = p["taille"]
            for face, (a, b) in (("north", (sx, sy)), ("south", (sx, sy)), ("east", (sz, sy)), ("west", (sz, sy)),
                                 ("up", (sx, sz)), ("down", (sx, sz))):
                rects.append([i, face, max(1, round(a * d)), max(1, round(b * d))])
        rects.sort(key=lambda r: (-r[3], -r[2]))
        size = 16
        while True:
            pos, x, y, row, ok = {}, 0, 0, 0, True
            for i, face, w, h in rects:
                if x + w > size:
                    x, y, row = 0, y + row, 0
                if y + h > size or w > size:
                    ok = False
                    break
                pos[(i, face)] = (x, y, w, h)
                x, row = x + w, max(row, h)
            if ok:
                break
            size *= 2
        img = Image.new("RGBA", (size, size), (0, 0, 0, 0))
        dr = ImageDraw.Draw(img)
        shade = {"north": 1.0, "south": 1.0, "east": 0.94, "west": 0.94, "up": 1.06, "down": 0.86}
        uvs = [dict() for _ in self.pieces]
        k = 16 / size
        for (i, face), (x, y, w, h) in pos.items():
            p = self.pieces[i]
            base = tone(self.couleurs[p["couleur"]], shade[face])
            dr.rectangle((x, y, x + w - 1, y + h - 1), fill=base + (255,))
            if p["relief"] and w >= 3 and h >= 3:
                dr.line((x, y, x + w - 1, y), fill=tone(base, 1.22) + (255,))
                dr.line((x, y, x, y + h - 1), fill=tone(base, 1.22) + (255,))
                dr.line((x, y + h - 1, x + w - 1, y + h - 1), fill=tone(base, 0.8) + (255,))
                dr.line((x + w - 1, y, x + w - 1, y + h - 1), fill=tone(base, 0.8) + (255,))
            uvs[i][face] = [round(x * k, 4), round(y * k, 4), round((x + w) * k, 4), round((y + h) * k, 4)]
        return img, uvs

    def model(self):
        """Le modèle complet (pavés et faces) et sa texture."""
        img, uvs = self.atlas()
        els = self.elements()
        for el, uv in zip(els, uvs):
            el["faces"] = {f: {"uv": uv[f], "texture": "#0"} for f in ("north", "south", "east", "west", "up", "down")}
        return els, img

    # ------------------------------------------------------------ Blockbench

    def bbmodel(self):
        els, img = self.model()
        buf = io.BytesIO()
        img.save(buf, "PNG")
        out_els, groups = [], {}
        for el, p in zip(els, self.pieces):
            u = str(uuid.uuid4())
            rot = [0, 0, 0]
            if "rotation" in el:
                rot["xyz".index(el["rotation"]["axis"])] = el["rotation"]["angle"]
            out_els.append({
                "name": el["name"], "box_uv": False, "rescale": False, "locked": False, "light_emission": 0,
                "render_order": "default", "allow_mirror_modeling": True,
                "from": el["from"], "to": el["to"], "autouv": 0, "color": 0,
                "rotation": rot, "origin": [round(v, 3) for v in p["centre"]],
                "faces": {k: {"uv": f["uv"], "texture": 0} for k, f in el["faces"].items()},
                "type": "cube", "uuid": u,
            })
            groups.setdefault(p["groupe"], []).append(u)
        g = self.grip()
        outliner = [{"name": name, "origin": [round(v, 3) for v in g], "color": 0, "uuid": str(uuid.uuid4()),
                     "export": True, "mirror_uv": False, "isOpen": False, "locked": False, "visibility": True,
                     "autouv": 0, "children": ids} for name, ids in groups.items()]
        texture = {
            "path": "", "name": f"{self.nom}.png", "folder": "item", "namespace": "crossfight", "id": "0",
            "width": img.width, "height": img.height, "uv_width": 16, "uv_height": 16, "particle": True,
            "render_mode": "default", "render_sides": "auto", "visible": True, "internal": True, "saved": False,
            "uuid": str(uuid.uuid4()), "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode(),
        }
        return {
            "meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False},
            "name": self.nom, "parent": "", "ambientocclusion": True, "front_gui_light": True,
            "visible_box": [1, 1, 0], "resolution": {"width": 16, "height": 16},
            "elements": out_els, "outliner": outliner, "textures": [texture],
        }

    def write(self, folder: Path = DRAFTS):
        folder.mkdir(parents=True, exist_ok=True)
        f = folder / f"{self.nom}.bbmodel"
        f.write_text(json.dumps(self.bbmodel(), indent=1) + "\n", encoding="utf-8")
        return f


# ---------------------------------------------------------------- aperçu

def rot_matrix(axis, deg):
    import numpy as np
    a = math.radians(deg)
    c, s = math.cos(a), math.sin(a)
    if axis == "x":
        return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])
    if axis == "y":
        return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def quads(els, textures):
    """Les faces du modèle pour le rendu de tools/pose_preview.py : coins en unités de modèle autour de (8, ., 8),
    uv en pixels. `textures` : une image, ou un dictionnaire clé de texture -> image."""
    import numpy as np
    sys.path.insert(0, str(ROOT / "tools"))
    import pose_preview as pp
    if not isinstance(textures, dict):
        textures = {"0": textures}
    arrays = {k: np.array(im.convert("RGBA")) for k, im in textures.items()}
    out = []
    for el in els:
        a, b = el["from"], el["to"]
        rot = el.get("rotation")
        if rot:
            R, o = rot_matrix(rot["axis"], rot["angle"]), np.array(rot["origin"], dtype=float)
        for face, f in el["faces"].items():
            tex = arrays[f["texture"].lstrip("#")]
            k = tex.shape[1] / 16
            pts = []
            for pt in pp.FACES[face](a, b):
                q = np.array(pt, dtype=float)
                if rot:
                    q = o + R @ (q - o)
                pts.append(np.array([q[0] - 8, q[1], q[2] - 8]))
            u1, v1, u2, v2 = (x * k for x in f["uv"])
            out.append((pts, [(u1, v1), (u2, v1), (u1, v2), (u2, v2)], tex))
    return out


def view(qs, yaw, size, pitch=0.0):
    """Une vue du modèle, cadrée au plus juste : yaw 0 = de face (côté sud), 90 = côté droit (est)."""
    import numpy as np
    sys.path.insert(0, str(ROOT / "tools"))
    import pose_preview as pp
    if pitch:
        R = rot_matrix("x", pitch)
        # On penche le modèle vers la caméra : rotation autour de l'axe horizontal de l'écran.
        Ry, Ryi = rot_matrix("y", yaw), rot_matrix("y", -yaw)
        qs = [([Ry @ (R @ (Ryi @ p)) for p in pts], uv, tex) for pts, uv, tex in qs]
    a = math.radians(yaw)
    right = np.array([math.cos(a), 0, -math.sin(a)])
    xs = [float(p @ right) for pts, _, _ in qs for p in pts]
    ys = [float(p[1]) for pts, _, _ in qs for p in pts]
    w, h = size
    span = max(max(xs) - min(xs), 1e-3), max(max(ys) - min(ys), 1e-3)
    scale = min((w - 24) / span[0], (h - 24) / span[1])
    cx, cy = (max(xs) + min(xs)) / 2, (max(ys) + min(ys)) / 2
    # Le rendu place x = 0 au milieu et y = 0 à 70 px du bas : on recentre le modèle en conséquence.
    shift = np.array([-cx, 0, 0]) if abs(math.sin(a)) < 1e-9 else -cx * right
    dy = (h / 2 - 70) / scale - cy
    moved = [([p + shift + np.array([0, dy, 0]) for p in pts], uv, tex) for pts, uv, tex in qs]
    return Image.fromarray(pp.render(moved, yaw, size=size, scale=scale))


def sheet(armes, out: Path, cell=(300, 400), compact=False):
    """Une planche : pour chaque arme, sa vue principale et une vue de trois quarts (compact : la seconde seule)."""
    from PIL import ImageFont
    if compact:
        return compact_sheet(armes, out)
    cols = min(len(armes), 3)
    rows = (len(armes) + cols - 1) // cols
    w, h = cell
    img = Image.new("RGB", (cols * 2 * w, rows * (h + 22)), (235, 235, 235))
    dr = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("/usr/share/fonts/opentype/inter/Inter-BlackItalic.otf", 15)
    except OSError:
        font = None
    for i, arme in enumerate(armes):
        els, tex = arme.model()
        qs = quads(els, tex)
        main = 0 if arme.genre in ("lame", "objet") else 90
        x, y = (i % cols) * 2 * w, (i // cols) * (h + 22)
        img.paste(view(qs, main, cell), (x, y + 22))
        img.paste(view(qs, main + 40, cell, pitch=18), (x + w, y + 22))
        dr.text((x + 6, y + 3), f"{arme.titre}  ({arme.nom}, {len(arme.pieces)} paves)", fill=(30, 30, 30), font=font)
    img.save(out)


def compact_sheet(armes, out: Path, cell=(210, 380)):
    """Une vue de trois quarts par arme, jusqu'à huit par ligne : pour passer en revue beaucoup d'armes."""
    from PIL import ImageFont
    cols = min(len(armes), 8)
    if armes and all(a.genre == "feu" for a in armes):
        cell, cols = (440, 230), min(len(armes), 4)     # les armes à feu sont longues : des cases larges
    rows = (len(armes) + cols - 1) // cols
    w, h = cell
    img = Image.new("RGB", (cols * w, rows * (h + 20)), (235, 235, 235))
    dr = ImageDraw.Draw(img)
    try:
        font = ImageFont.truetype("/usr/share/fonts/opentype/inter/Inter-BlackItalic.otf", 13)
    except OSError:
        font = None
    for i, arme in enumerate(armes):
        els, tex = arme.model()
        main = 0 if arme.genre in ("lame", "objet") else 90
        x, y = (i % cols) * w, (i // cols) * (h + 20)
        img.paste(view(quads(els, tex), main + 30, cell, pitch=12), (x, y + 20))
        dr.text((x + 5, y + 3), arme.nom, fill=(30, 30, 30), font=font)
    img.save(out)


def main() -> int:
    if len(sys.argv) < 2:
        print(__doc__)
        return 1
    scope = runpy.run_path(sys.argv[1])
    armes = scope.get("ARMES", [])
    if "--seul" in sys.argv:
        wanted = set(sys.argv[sys.argv.index("--seul") + 1].split(","))
        armes = [a for a in armes if a.nom in wanted]
    for a in armes:
        print(a.write().relative_to(ROOT), f"({len(a.pieces)} paves)")
    if "--apercu" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--apercu") + 1])
        sheet(armes, out, compact="--compact" in sys.argv)
        print("apercu :", out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
