#!/usr/bin/env python3
"""Les armes retouchées dans Blockbench, mises en jeu (pack privé).

Usage :  python3 tools/build_armes.py [--apercu dossier]

Chaque arme de la table ARMES a son projet dans resourcepack-prive/modeles/<nom>.bbmodel : le projet
Blockbench retouché, déposé tel quel (pas besoin d'exporter). Le script en tire, dans le pack privé :

  assets/crossfight/textures/item/arme/<nom>/     ses textures peintes (et celles que le script anime)
  assets/crossfight/models/item/arme/<nom>.json   le modèle, avec ses réglages d'affichage
  assets/minecraft/items/<objet>.json             l'objet de Minecraft qu'elle remplace : le modèle n'est
                                                  montré qu'à l'arme marquée (custom_model_data "<nom>")
et, dans le plugin, src/main/resources/models.txt : comment le pantin des animations tient chaque modèle
(point de prise, inclinaison, taille). Le plugin marque l'arme quand le pack contient le modèle
(util/Models.java, nom "model.<nom>" dans la liste du pack).

Ce que le script fait de plus qu'une exportation de Blockbench :
  - Minecraft n'accepte qu'une rotation par pavé, sur un axe, entre -45 et 45 degrés. Un pavé tourné
    davantage est d'abord tourné lui-même d'un ou plusieurs quarts de tour (ses faces et leurs textures
    suivent), et ne garde que le reste. Une seconde rotation minuscule (moins de 2 degrés) est ignorée.
  - Un arc ("genre": "arc") : la corde brille. Sa texture est animée (un éclat qui remonte la corde, sur
    une lueur qui respire), elle éclaire d'elle-même (light_emission) et un halo translucide l'entoure.
    Trois modèles de plus montrent l'arc bandé (corde tirée en deux brins vers l'archer, flèche de lumière
    encochée), choisis par le jeu pendant qu'on vise comme les trois images de l'arc de Minecraft.
  - Le modèle reste droit dans le pack (corde verticale, ventre de l'arc vers le nord) : ce sont les réglages
    d'affichage qui le posent exactement comme l'image de l'arc de Minecraft, dans la main (à la première et à
    la troisième personne), au sol, dans l'inventaire. La poignée du modèle vient là où est celle de l'image.

Ensuite : python3 tools/build_pack.py, puis recompiler le plugin.
"""
import base64
import io
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
PRIVE = ROOT / "resourcepack-prive"
OUT = PRIVE / "assets"
NS = "crossfight"

# nom -> objet de Minecraft remplacé, genre, taille en main (troisième personne, première personne),
# dans l'inventaire, au sol ; pour un arc : le nom du pavé de la corde dans le projet.
ARMES = {
    "yumi_fujin": {"objet": "bow", "genre": "arc", "corde": "corde", "poignee": "poignee", "epaisseur": 2,
                   "tailles": {"tiers": 0.74, "premiere": 0.38, "inventaire": 0.62, "sol": 0.36, "cadre": 0.62}},
}

# Les modèles 3D faits par d'autres scripts (tools/build_falchion.py), que le pantin tient aussi : le modèle est
# droit, il penche d'un huitième de tour pour se poser comme l'image d'une épée, sa poignée est sur son axe.
AUTRES = {
    "falchion": {"prise": (0, -7.78, 0), "inclinaison": (0, 0, 45), "taille": 1.275},
    "falchion_sainte": {"prise": (0, -7.78, 0), "inclinaison": (0, 0, 45), "taille": 1.275},
}

# L'arc de Minecraft : sa définition (les trois images de l'arc bandé), ses réglages en main, et où sont
# la corde et la poignée dans son image de 16 pixels.
ARC_VANILLA = {
    "type": "minecraft:condition", "property": "minecraft:using_item",
    "on_false": {"type": "minecraft:model", "model": "minecraft:item/bow"},
    "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05,
                "entries": [{"threshold": 0.65, "model": {"type": "minecraft:model", "model": "minecraft:item/bow_pulling_1"}},
                            {"threshold": 0.9, "model": {"type": "minecraft:model", "model": "minecraft:item/bow_pulling_2"}}],
                "fallback": {"type": "minecraft:model", "model": "minecraft:item/bow_pulling_0"}},
}
ARC_AFFICHAGE = {
    "thirdperson_righthand": {"rotation": [-80, 260, -40], "translation": [-1, -2, 2.5], "scale": 0.9},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": 0.68},
}
ARC_POIGNEE_IMAGE = (5.5, 10.5, 8)      # le milieu du bois, dans l'image de l'arc (x vers la droite, y vers le haut)
# Du modèle droit (corde le long de +Y, ventre vers -Z, mince en X) à l'image de l'arc (corde le long de la
# diagonale, ventre vers le haut à gauche) : X -> (0, 0, -1), Y -> (1, 1, 0) / racine de 2, Z -> (1, -1, 0) / racine de 2.
K = math.sqrt(0.5)
EN_IMAGE = np.array([[0, K, K], [0, K, -K], [-1, 0, 0]])
CENTRE = np.array([8.0, 8.0, 8.0])

CYAN, BLANC = (0, 241, 255), (255, 255, 255)
TIRAGES = (2.5, 5.0, 7.5)               # la corde tirée, en pixels, pour les trois états de l'arc bandé
IMAGES = 24                             # images de l'animation de la corde


def r3(v):
    return round(float(v), 3)


# --------------------------------------------------------------------------- rotations

def rot_x(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return np.array([[1, 0, 0], [0, c, -s], [0, s, c]])


def rot_y(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return np.array([[c, 0, s], [0, 1, 0], [-s, 0, c]])


def rot_z(a):
    c, s = math.cos(math.radians(a)), math.sin(math.radians(a))
    return np.array([[c, -s, 0], [s, c, 0], [0, 0, 1]])


def minecraft(rotation):
    """La rotation d'un réglage d'affichage : Rx * Ry * Rz (z appliqué d'abord au modèle)."""
    return rot_x(rotation[0]) @ rot_y(rotation[1]) @ rot_z(rotation[2])


def angles(m):
    """Les trois angles (x, y, z) d'un réglage d'affichage qui donnent cette rotation."""
    y = math.degrees(math.asin(max(-1.0, min(1.0, m[0][2]))))
    x = math.degrees(math.atan2(-m[1][2], m[2][2]))
    z = math.degrees(math.atan2(-m[0][1], m[0][0]))
    return [round(x, 2), round(y, 2), round(z, 2)]


def quaternion(m):
    """(x, y, z, w) de la rotation m."""
    t = m[0][0] + m[1][1] + m[2][2]
    if t > 0:
        s = math.sqrt(t + 1) * 2
        q = ((m[2][1] - m[1][2]) / s, (m[0][2] - m[2][0]) / s, (m[1][0] - m[0][1]) / s, s / 4)
    elif m[0][0] > m[1][1] and m[0][0] > m[2][2]:
        s = math.sqrt(1 + m[0][0] - m[1][1] - m[2][2]) * 2
        q = (s / 4, (m[0][1] + m[1][0]) / s, (m[0][2] + m[2][0]) / s, (m[2][1] - m[1][2]) / s)
    elif m[1][1] > m[2][2]:
        s = math.sqrt(1 + m[1][1] - m[0][0] - m[2][2]) * 2
        q = ((m[0][1] + m[1][0]) / s, s / 4, (m[1][2] + m[2][1]) / s, (m[0][2] - m[2][0]) / s)
    else:
        s = math.sqrt(1 + m[2][2] - m[0][0] - m[1][1]) * 2
        q = ((m[0][2] + m[2][0]) / s, (m[1][2] + m[2][1]) / s, s / 4, (m[1][0] - m[0][1]) / s)
    return tuple(round(v, 5) for v in q)


# --------------------------------------------------------------------------- pavés

# Chaque face : sa normale, puis vers où vont la droite et le bas de sa texture (sans rotation de texture).
FACES = {
    "north": ((0, 0, -1), (-1, 0, 0), (0, -1, 0)),
    "south": ((0, 0, 1), (1, 0, 0), (0, -1, 0)),
    "east": ((1, 0, 0), (0, 0, -1), (0, -1, 0)),
    "west": ((-1, 0, 0), (0, 0, 1), (0, -1, 0)),
    "up": ((0, 1, 0), (1, 0, 0), (0, 0, 1)),
    "down": ((0, -1, 0), (1, 0, 0), (0, 0, -1)),
}
PAR_NORMALE = {axes[0]: name for name, axes in FACES.items()}


def quart(axis, k):
    """Un quart de tour (k = 1 ou -1) autour d'un axe, en entiers."""
    m = [rot_x, rot_y, rot_z][axis](90 * k)
    return np.rint(m).astype(int)


def tourne(el, axis, k, origin):
    """Le même pavé, tourné lui-même d'un quart de tour autour de `origin` : ses faces changent de place, et
    leur texture tourne avec elles (rotation de texture, dans le sens des aiguilles d'une montre)."""
    q = quart(axis, k)
    o = np.array(origin, dtype=float)
    p1, p2 = o + q @ (np.array(el["from"]) - o), o + q @ (np.array(el["to"]) - o)
    out = dict(el)
    out["from"], out["to"] = [r3(v) for v in np.minimum(p1, p2)], [r3(v) for v in np.maximum(p1, p2)]
    faces = {}
    for name, f in el["faces"].items():
        normal, right, _ = FACES[name]
        n2, r2 = tuple(int(v) for v in q @ np.array(normal)), q @ np.array(right)
        target = PAR_NORMALE[n2]
        _, t_right, t_down = FACES[target]
        turn = {(1, 0): 0, (0, 1): 90, (-1, 0): 180, (0, -1): 270}[(int(r2 @ np.array(t_right)), int(r2 @ np.array(t_down)))]
        g = dict(f)
        total = (f.get("rotation", 0) + turn) % 360
        g.pop("rotation", None)
        if total:
            g["rotation"] = total
        faces[target] = g
    out["faces"] = faces
    return out


def pave(el, scales):
    """Un pavé de Blockbench devenu pavé de modèle Minecraft."""
    grow = el.get("inflate") or 0
    e = {"name": el.get("name", "cube"), "from": [r3(v - grow) for v in el["from"]], "to": [r3(v + grow) for v in el["to"]], "faces": {}}
    for name, f in el.get("faces", {}).items():
        ti = f.get("texture")
        if ti is None or ti is False:
            continue
        ku, kv = scales[ti]
        face = {"uv": [r3(f["uv"][0] * ku), r3(f["uv"][1] * kv), r3(f["uv"][2] * ku), r3(f["uv"][3] * kv)], "texture": f"#{ti}"}
        if f.get("rotation"):
            face["rotation"] = f["rotation"]
        e["faces"][name] = face
    if el.get("shade") is False:
        e["shade"] = False
    if el.get("light_emission"):
        e["light_emission"] = int(el["light_emission"])
    rot = el.get("rotation", [0, 0, 0])
    axes = sorted((i for i in range(3) if abs(rot[i]) > 1e-6), key=lambda i: -abs(rot[i]))
    if axes:
        axis, angle = axes[0], float(rot[axes[0]])
        for other in axes[1:]:
            if abs(rot[other]) >= 2:
                print(f"  attention : '{e['name']}' tourne aussi de {rot[other]} degres autour de {'xyz'[other]} : ignore")
        origin = [r3(v) for v in el.get("origin", [8, 8, 8])]
        turns = round(angle / 90) if abs(angle) > 45 else 0
        for _ in range(abs(turns)):
            e = tourne(e, axis, 1 if turns > 0 else -1, origin)
        angle -= 90 * turns
        if abs(angle) > 1e-6:
            e["rotation"] = {"angle": r3(angle), "axis": "xyz"[axis], "origin": origin}
    return e


def coins(el):
    """Les huit coins d'un pavé, sa rotation appliquée."""
    a, b = el["from"], el["to"]
    pts = np.array([[x, y, z] for x in (a[0], b[0]) for y in (a[1], b[1]) for z in (a[2], b[2])], dtype=float)
    rot = el.get("rotation")
    if rot:
        m = {"x": rot_x, "y": rot_y, "z": rot_z}[rot["axis"]](rot["angle"])
        o = np.array(rot["origin"], dtype=float)
        pts = o + (pts - o) @ m.T
    return pts


def lire(path: Path):
    """Les pavés et les textures d'un projet Blockbench (format Java Block/Item)."""
    d = json.loads(path.read_text(encoding="utf-8"))
    textures, scales = [], []
    for t in d.get("textures", []):
        textures.append(Image.open(io.BytesIO(base64.b64decode(t["source"].split(",", 1)[1]))).convert("RGBA"))
        # Les coordonnées de texture d'un projet sont à l'échelle de sa résolution (pas de la taille de chaque image).
        res = d.get("resolution", {})
        scales.append((16 / res.get("width", t.get("uv_width", 16)), 16 / res.get("height", t.get("uv_height", 16))))
    els = [pave(el, scales) for el in d.get("elements", [])
           if el.get("type", "cube") == "cube" and el.get("export", True) and el.get("visibility", True)]
    return els, textures


# --------------------------------------------------------------------------- la corde qui brille

def corde_images():
    """La texture animée de la corde (4 x 64 par image, empilées) et celle de son halo.

    Le long de la corde (de bas en haut de l'image... donc de haut en bas de la corde) : une lueur cyan qui
    respire, et un éclat blanc qui la remonte ; un second, plus faible, le suit à mi-chemin.
    """
    w, h = 4, 64
    core = Image.new("RGBA", (w, h * IMAGES))
    halo = Image.new("RGBA", (w, h * IMAGES))
    pc, ph = core.load(), halo.load()
    for k in range(IMAGES):
        phase = k / IMAGES
        breath = 0.5 + 0.5 * math.sin(2 * math.pi * phase)
        for y in range(h):
            along = 1 - (y + 0.5) / h                     # 0 en bas de la corde, 1 en haut
            spark = 0.0
            for shift, strength in ((0.0, 1.0), (0.5, 0.55)):
                d = abs(((along - phase - shift + 0.5) % 1.0) - 0.5)
                spark = max(spark, strength * math.exp(-(d / 0.07) ** 2))
            light = min(1.0, 0.18 + 0.22 * breath + spark)
            color = tuple(round(CYAN[i] + (BLANC[i] - CYAN[i]) * light) for i in range(3))
            glow = tuple(round(CYAN[i] + (BLANC[i] - CYAN[i]) * 0.35 * spark) for i in range(3))
            alpha = round(46 + 34 * breath + 150 * spark)
            for x in range(w):
                pc[x, k * h + y] = color + (255,)
                ph[x, k * h + y] = glow + (min(230, alpha),)
    meta = {"animation": {"width": w, "height": h, "frametime": 2, "interpolate": True}}
    return core, halo, meta


def brin(y1, y2, z, pull, v1, v2, name, upper=False):
    """Un brin de corde (et son halo) entre les hauteurs y1 < y2, à la profondeur z ; `pull` : de combien le bout
    du côté de la poignée est tiré vers l'archer (+Z) ; `upper` : le brin du haut, qui tourne autour du bout haut
    de la corde (celui du bas tourne autour du bout bas). v1, v2 : la part de la texture (0 en haut, 16 en bas)."""
    out = []
    fixed, length = (y2, y2 - y1) if upper else (y1, y2 - y1)
    angle = 0.0
    if pull:
        reach = math.hypot(length, pull)
        angle = math.degrees(math.atan2(pull, length)) * (-1 if upper else 1)
        y1, y2 = (fixed - reach, fixed) if upper else (fixed, fixed + reach)
    for half, tex, suffix in ((0.1, "#corde", ""), (0.42, "#halo", "_halo")):
        side = {"uv": [0, v1, 16, v2], "texture": tex}
        cap = {"uv": [0, v1, 16, min(16, v1 + 0.25)], "texture": tex}
        el = {"name": name + suffix, "from": [r3(8 - half), r3(y1), r3(z - half)], "to": [r3(8 + half), r3(y2), r3(z + half)],
              "shade": False, "light_emission": 15,
              "faces": {"north": side, "south": side, "east": side, "west": side, "up": cap, "down": cap}}
        if angle:
            el["rotation"] = {"angle": r3(angle), "axis": "x", "origin": [8, r3(fixed), r3(z)]}
        out.append(el)
    return out


def corde(string, grip_y, pull=0.0):
    """La corde au repos (un brin), ou tirée (deux brins qui se rejoignent à hauteur de poignée), puis sa flèche."""
    y1, y2 = string["from"][1], string["to"][1]
    z = (string["from"][2] + string["to"][2]) / 2
    if not pull:
        return brin(y1, y2, z, 0, 0, 16, "corde")
    split = 16 * (y2 - grip_y) / (y2 - y1)                 # la part de la texture au-dessus de l'encoche
    els = brin(grip_y, y2, z, pull, 0, r3(split), "corde_haut", upper=True) + brin(y1, grip_y, z, pull, r3(split), 16, "corde_bas")
    # Halos après les brins : dessinés en dernier, ils se posent par-dessus sans les cacher.
    els.sort(key=lambda e: e["name"].endswith("_halo"))
    # La flèche de lumière : encochée sur la corde tirée, elle passe à droite du bois et pointe vers la cible (-Z).
    nock, tip = z + pull, z + pull - 13.5
    lit = lambda half_x, half_y, za, zb, name, tex="#corde": {
        "name": name, "from": [r3(8.62 - half_x), r3(grip_y + 1.75 - half_y), r3(za)], "to": [r3(8.62 + half_x), r3(grip_y + 1.75 + half_y), r3(zb)],
        "shade": False, "light_emission": 15,
        "faces": {f: {"uv": [0, 2, 16, 6], "texture": tex} for f in ("north", "south", "east", "west", "up", "down")}}
    els += [lit(0.14, 0.14, tip + 1.2, nock, "fleche"), lit(0.36, 0.36, tip, tip + 1.3, "fleche_pointe"),
            lit(0.3, 0.3, nock - 1.6, nock - 0.2, "fleche_empennage"),
            lit(0.5, 0.5, tip - 0.3, nock, "fleche_halo", "#halo")]
    return els


# --------------------------------------------------------------------------- affichage

def pose(vanilla, size, grip_model, grip_image):
    """Le réglage d'affichage qui pose le modèle droit comme l'image de l'arc l'est par `vanilla`, la poignée du
    modèle là où est celle de l'image."""
    rv = minecraft(vanilla["rotation"])
    rm = rv @ EN_IMAGE
    t = (np.array(vanilla["translation"]) + vanilla["scale"] * (rv @ (np.array(grip_image) - CENTRE))
         - size * (rm @ (np.array(grip_model) - CENTRE)))
    return {"rotation": angles(rm), "translation": [round(float(v), 2) for v in t], "scale": [size] * 3}


def affichage_arc(sizes, grip, middle):
    centre = tuple(CENTRE)
    d = {
        "thirdperson_righthand": pose(ARC_AFFICHAGE["thirdperson_righthand"], sizes["tiers"], grip, ARC_POIGNEE_IMAGE),
        "firstperson_righthand": pose(ARC_AFFICHAGE["firstperson_righthand"], sizes["premiere"], grip, ARC_POIGNEE_IMAGE),
        # Dans l'inventaire, au sol, dans un cadre : comme une image d'objet, le milieu de l'arc au milieu.
        "gui": pose({"rotation": [0, 0, 0], "translation": [0, 0, 0], "scale": 1}, sizes["inventaire"], middle, centre),
        "ground": pose({"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": 0.5}, sizes["sol"], middle, centre),
        "fixed": pose({"rotation": [0, 180, 0], "translation": [0, 0, 0], "scale": 1}, sizes["cadre"], middle, centre),
    }
    return d


# --------------------------------------------------------------------------- écriture

def construire(name, cfg):
    project = PRIVE / "modeles" / f"{name}.bbmodel"
    if not project.exists():
        print(f"{name} : pas de projet dans {project.relative_to(ROOT)}, ignore")
        return None
    els, images = lire(project)
    tex_dir = OUT / NS / "textures" / "item" / "arme" / name
    mod_dir = OUT / NS / "models" / "item" / "arme"
    tex_dir.mkdir(parents=True, exist_ok=True)
    mod_dir.mkdir(parents=True, exist_ok=True)
    for old in list(tex_dir.glob("*.png")) + list(tex_dir.glob("*.mcmeta")):
        old.unlink()
    textures = {}
    used = {int(f["texture"][1:]) for el in els for f in el["faces"].values()}
    model = {"credit": f"Crossfight UHC - {name} (retouche dans Blockbench)", "gui_light": "front"}
    states = {}
    if cfg["genre"] == "arc":
        string = next(el for el in els if el["name"] == cfg["corde"])
        grips = [el for el in els if el["name"] == cfg["poignee"]]
        grip = np.mean([coins(el).mean(axis=0) for el in grips], axis=0)
        used -= {int(f["texture"][1:]) for f in string["faces"].values()}
        body = [el for el in els if el is not string]
        # Le bois de l'arc, épaissi (la corde reste fine) : chaque pavé s'élargit autour du plan de l'arc.
        wide = cfg.get("epaisseur", 1)
        for el in body:
            for key in ("from", "to"):
                el[key][0] = r3(8 + (el[key][0] - 8) * wide)
            if "rotation" in el:
                el["rotation"]["origin"][0] = r3(8 + (el["rotation"]["origin"][0] - 8) * wide)
        core, halo, meta = corde_images()
        core.save(tex_dir / "corde.png")
        halo.save(tex_dir / "halo.png")
        for f in ("corde.png", "halo.png"):
            (tex_dir / f"{f}.mcmeta").write_text(json.dumps(meta, indent=1) + "\n", encoding="utf-8")
        textures.update({"corde": f"{NS}:item/arme/{name}/corde", "halo": f"{NS}:item/arme/{name}/halo"})
        everything = np.vstack([coins(el) for el in els])
        middle = (everything.min(axis=0) + everything.max(axis=0)) / 2
        model["display"] = affichage_arc(cfg["tailles"], grip, middle)
        elements = body + corde(string, float(grip[1]))
        for i, pull in enumerate(TIRAGES):
            states[f"{name}_tire_{i}"] = body + corde(string, float(grip[1]), pull)
        # Le pantin : le modèle vu par un affichage d'objet (qui le retourne d'un demi-tour : x et z changent de
        # signe) doit se poser comme l'image de l'arc, elle aussi retournée.
        flip = np.diag([-1.0, 1.0, -1.0])
        tilt = flip @ EN_IMAGE @ flip
        local = flip @ (grip - CENTRE)
        puppet = (name, [round(float(v), 2) for v in local], quaternion(tilt), round(cfg["tailles"]["tiers"] * 0.85 / 0.9, 2))
        size = everything.max(axis=0) - everything.min(axis=0)
        print(f"{name} : arc de {size[1]:.1f} px de haut, poignee en ({grip[0]:.2f}, {grip[1]:.2f}, {grip[2]:.2f}), "
              f"corde a z = {(string['from'][2] + string['to'][2]) / 2:.2f}")
    else:
        raise SystemExit(f"{name} : genre '{cfg['genre']}' pas encore pris en charge")
    for i in sorted(used):
        images[i].save(tex_dir / f"t{i}.png")
        textures[str(i)] = f"{NS}:item/arme/{name}/t{i}"
    textures["particle"] = textures[str(min(used))]
    model["textures"] = textures
    model["elements"] = elements
    (mod_dir / f"{name}.json").write_text(json.dumps(model, indent=1) + "\n", encoding="utf-8")
    for state, state_els in states.items():
        (mod_dir / f"{state}.json").write_text(json.dumps(
            {"parent": f"{NS}:item/arme/{name}", "elements": state_els}, indent=1) + "\n", encoding="utf-8")
    turned = sum(1 for el in els if any("rotation" in f for f in el["faces"].values()))
    print(f"  {len(elements)} paves ({turned} tournes d'un quart de tour pour rester sous 45 degres), {len(used)} textures, "
          f"{len(states)} etats")
    return puppet


def definition(objet, names):
    """L'objet de Minecraft : le modèle de chaque arme marquée, sinon l'objet habituel."""
    ref = lambda m: {"type": "minecraft:model", "model": f"{NS}:item/arme/{m}"}
    cases = []
    for name in names:
        if ARMES[name]["genre"] == "arc":
            look = {"type": "minecraft:condition", "property": "minecraft:using_item", "on_false": ref(name),
                    "on_true": {"type": "minecraft:range_dispatch", "property": "minecraft:use_duration", "scale": 0.05,
                                "entries": [{"threshold": 0.65, "model": ref(f"{name}_tire_1")}, {"threshold": 0.9, "model": ref(f"{name}_tire_2")}],
                                "fallback": ref(f"{name}_tire_0")}}
            cases.append({"when": name, "model": look})
            # L'arc bandé à la demande (le pantin d'une animation : il ne « vise » pas, c'est le plugin qui choisit).
            cases += [{"when": f"{name}_tire_{i}", "model": ref(f"{name}_tire_{i}")} for i in range(len(TIRAGES))]
        else:
            cases.append({"when": name, "model": ref(name)})
    fallback = ARC_VANILLA if objet == "bow" else {"type": "minecraft:model", "model": f"minecraft:item/{objet}"}
    return {"model": {"type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
                      "cases": cases, "fallback": fallback}}


def main() -> int:
    puppets, by_item = [], {}
    for name, cfg in ARMES.items():
        p = construire(name, cfg)
        if p:
            puppets.append(p)
            by_item.setdefault(cfg["objet"], []).append(name)
    items = OUT / "minecraft" / "items"
    items.mkdir(parents=True, exist_ok=True)
    for objet, names in by_item.items():
        (items / f"{objet}.json").write_text(json.dumps(definition(objet, names), indent=1) + "\n", encoding="utf-8")
        print(f"assets/minecraft/items/{objet}.json : {', '.join(names)}")
    # Ce que le plugin embarque : des nombres, pas de modèle. Les armes de la table y sont toutes, construites ou non
    # sur cette machine (le fichier est versionné) : on garde les lignes de celles dont le projet n'est pas là.
    table = ROOT / "src" / "main" / "resources" / "models.txt"
    kept = {}
    if table.exists():
        for line in table.read_text(encoding="utf-8").splitlines():
            if line.strip() and not line.startswith("#"):
                kept[line.split()[0]] = line
    for name, a in AUTRES.items():
        q = quaternion(minecraft(a["inclinaison"]))
        kept[name] = f"{name} {' '.join(str(v) for v in a['prise'])} {' '.join(str(v) for v in q)} {a['taille']}"
    for name, grip, q, size in puppets:
        kept[name] = f"{name} {' '.join(str(v) for v in grip)} {' '.join(str(v) for v in q)} {size}"
    head = ["# Genere par tools/build_armes.py : les modeles 3D d'armes que le pantin des animations sait tenir.",
            "# <nom> <prise x y z, en pixels depuis le milieu du modele> <inclinaison : quaternion x y z w> <taille>"]
    table.write_text("\n".join(head + [kept[k] for k in sorted(kept)]) + "\n", encoding="utf-8")
    print(f"{table.relative_to(ROOT)} : {', '.join(sorted(kept))}")
    if "--apercu" in sys.argv:
        apercu(Path(sys.argv[sys.argv.index("--apercu") + 1]))
    return 0


# --------------------------------------------------------------------------- aperçu

def apercu(out: Path):
    """Chaque arme vue de profil et de trois quarts, au repos et bandée, sans lancer le jeu."""
    sys.path.insert(0, str(ROOT / "tools"))
    import pose_preview as pp
    out.mkdir(parents=True, exist_ok=True)
    for name in ARMES:
        mod_dir = OUT / NS / "models" / "item" / "arme"
        if not (mod_dir / f"{name}.json").exists():
            continue
        base = json.loads((mod_dir / f"{name}.json").read_text())
        texs = {}
        for key, ref in base["textures"].items():
            im = Image.open(OUT / NS / "textures" / (ref.split(":", 1)[1] + ".png")).convert("RGBA")
            if im.height > im.width * 4:
                im = im.crop((0, 0, im.width, 64))          # la première image d'une texture animée
            texs[key] = np.array(im)
        panels = []
        for state in [name] + [f"{name}_tire_{i}" for i in range(len(TIRAGES))]:
            els = json.loads((mod_dir / f"{state}.json").read_text()).get("elements", base["elements"])
            quads = []
            for el in els:
                a, b = el["from"], el["to"]
                rot = el.get("rotation")
                for face, f in el["faces"].items():
                    tex = texs[f["texture"][1:]]
                    k = tex.shape[1] / 16
                    pts = []
                    for pt in pp.FACES[face](a, b):
                        q = np.array(pt, dtype=float)
                        if rot:
                            m = {"x": rot_x, "y": rot_y, "z": rot_z}[rot["axis"]](rot["angle"])
                            o = np.array(rot["origin"], dtype=float)
                            q = o + m @ (q - o)
                        # Vue de profil : la profondeur du modèle (z) devient la largeur de l'image.
                        pts.append(np.array([(q[2] - 8), q[1] - 8, -(q[0] - 8)]) / 16 * 1.25 + np.array([0, 1.0, 0]))
                    u1, v1, u2, v2 = (x * k for x in f["uv"])
                    v2 = min(v2, tex.shape[0] / tex.shape[1] * 16 * k)
                    quads.append((pts, [(u1, v1), (u2, v1), (u1, v2), (u2, v2)], tex))
            for yaw in (0, 40):
                panels.append(Image.fromarray(pp.render(quads, yaw, size=(360, 560), scale=170)))
        w, h = panels[0].size
        sheet = Image.new("RGB", (w * len(panels), h), (40, 40, 48))
        for i, im in enumerate(panels):
            sheet.paste(im.convert("RGB"), (i * w, 0))
        sheet.save(out / f"{name}.png")
        print("apercu :", out / f"{name}.png")


if __name__ == "__main__":
    raise SystemExit(main())
