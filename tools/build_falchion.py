#!/usr/bin/env python3
"""La Falchion de Marth en 3D, en pavés (style Blockbench), pour le pack privé.

Usage :  python3 tools/build_falchion.py [--apercu image.png] [--blockbench falchion.bbmodel]

--blockbench : écrit aussi un projet Blockbench (format Java Block/Item, les deux textures incluses) pour
               retoucher le modèle à la main. L'épée y est DROITE (pommeau en bas, pointe en haut, pavés
               alignés) pour être facile à modifier ; le modèle exporté de Blockbench (.json) se dépose dans
               resourcepack-prive/modeles/falchion.json : ce script le remet en diagonale pour le jeu (un
               huitième de tour autour de l'axe z) et il remplace alors son modèle à lui.

Modélisée à la main d'après des captures d'écran (lame argentée et sa gouttière dorée, garde dorée en
couronne avec un oeil au centre, poignée rouge, pommeau doré) ; c'est un dessin de Nintendo : elle va dans
resourcepack-prive/, jamais sur GitHub. Deux versions : la Falchion et la Sainte-Falchion (plus claire,
gouttière lumineuse).

Elle est posée comme l'image d'une épée de Minecraft (poignée en bas à gauche, pointe en haut à droite, à
plat, épaisseur centrée sur z = 8) : tenue en main, au sol, dans l'inventaire et par le modèle articulé des
animations (util/Puppet.java), elle se place exactement comme l'épée qu'elle remplace.

Le plugin marque l'arme de Marth (custom_model_data "falchion" ou "falchion_sainte") ; le pack privé
redéfinit l'épée en fer et l'épée en diamant pour montrer ce modèle à cette arme-là seulement. Sans le pack
privé, on voit l'épée normale.
"""
import json
import math
import sys
from pathlib import Path

from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent
OUT = ROOT / "resourcepack-prive" / "assets"

# Les teintes de chaque version : nom -> (couleur, ombre).
PALETTES = {
    "falchion": {
        "silver": ((205, 201, 223), (167, 163, 189)),
        "gold": ((226, 181, 58), (181, 136, 31)),
        "gold_dark": ((190, 142, 34), (150, 108, 22)),
        "red": ((176, 38, 29), (127, 25, 19)),
        "hole": ((42, 40, 51), (30, 28, 38)),
        "gem": ((210, 59, 90), (150, 30, 60)),
        "fuller": ((231, 186, 62), (190, 146, 40)),
    },
    "falchion_sainte": {
        "silver": ((232, 244, 255), (185, 212, 236)),
        "gold": ((243, 223, 138), (201, 169, 74)),
        "gold_dark": ((214, 188, 98), (170, 142, 60)),
        "red": ((40, 92, 190), (26, 62, 140)),
        "hole": ((120, 210, 255), (80, 170, 230)),
        "gem": ((140, 235, 255), (90, 200, 240)),
        "fuller": ((140, 235, 255), (100, 210, 250)),
    },
}
SWATCH = {"silver": (0, 0), "gold": (4, 0), "gold_dark": (8, 0), "red": (12, 0),
          "hole": (0, 4), "gem": (4, 4), "fuller": (8, 4)}

# Les pièces, le long de la diagonale de l'image (s : depuis le bout du pommeau, p : de côté), en pixels :
# nom, s du centre, p du centre, longueur (le long de la lame), largeur, épaisseur, teinte.
PARTS = [
    ("pommeau_bout", 0.45, 0.0, 0.3, 1.0, 1.0, "gold_dark"),
    ("pommeau", 1.05, 0.0, 0.9, 1.4, 1.4, "gold"),
    ("poignee", 3.0, 0.0, 3.0, 0.9, 0.9, "red"),
    ("bague", 4.6, 0.0, 0.3, 1.15, 1.15, "gold"),
    # La garde en couronne : le coeur, deux ailes, leurs pointes relevées vers la lame, deux ergots, l'oeil.
    ("garde", 5.4, 0.0, 1.2, 2.6, 1.8, "gold"),
    ("aile_g", 5.5, 1.95, 0.9, 1.3, 1.5, "gold"),
    ("aile_d", 5.5, -1.95, 0.9, 1.3, 1.5, "gold"),
    ("pointe_g", 6.05, 2.85, 1.7, 0.6, 1.2, "gold_dark"),
    ("pointe_d", 6.05, -2.85, 1.7, 0.6, 1.2, "gold_dark"),
    ("ergot_g", 4.75, 1.35, 0.5, 0.5, 1.2, "gold_dark"),
    ("ergot_d", 4.75, -1.35, 0.5, 0.5, 1.2, "gold_dark"),
    ("oeil", 6.75, 0.0, 1.5, 1.7, 1.4, "gold"),
    ("oeil_creux", 6.8, 0.0, 0.8, 0.7, 1.5, "hole"),
    ("joyau", 5.4, 0.0, 0.5, 0.5, 2.0, "gem"),
    # La lame : le talon plus large, la lame, la gouttière dorée sur les deux faces, la pointe en gradins.
    ("talon", 7.95, 0.0, 0.9, 2.2, 1.3, "silver"),
    ("lame", 14.3, 0.0, 11.8, 1.9, 1.0, "silver"),
    ("gouttiere", 13.6, 0.0, 10.0, 0.6, 1.12, "fuller"),
    ("pointe_1", 20.75, 0.0, 1.1, 1.4, 0.9, "silver"),
    ("pointe_2", 21.6, 0.0, 0.6, 0.9, 0.8, "silver"),
    ("pointe_3", 22.1, 0.0, 0.4, 0.4, 0.7, "silver"),
]
ORIGIN = 0.55  # le bout du pommeau, en bas à gauche de l'image (x = y)

# Comme une épée de Minecraft (item/handheld et item/generated).
DISPLAY = {
    "thirdperson_righthand": {"rotation": [0, -90, 55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "thirdperson_lefthand": {"rotation": [0, 90, -55], "translation": [0, 4.0, 0.5], "scale": [0.85, 0.85, 0.85]},
    "firstperson_righthand": {"rotation": [0, -90, 25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "firstperson_lefthand": {"rotation": [0, 90, -25], "translation": [1.13, 3.2, 1.13], "scale": [0.68, 0.68, 0.68]},
    "ground": {"rotation": [0, 0, 0], "translation": [0, 2, 0], "scale": [0.5, 0.5, 0.5]},
    "head": {"rotation": [0, 180, 0], "translation": [0, 13, 7], "scale": [1, 1, 1]},
    "fixed": {"rotation": [0, 180, 0], "scale": [1, 1, 1]},
}


def r2(v):
    return round(v, 3)


def elements():
    out = []
    k = math.sqrt(0.5)
    for name, s, p, length, width, thick, tint in PARTS:
        cx = ORIGIN + s * k - p * k
        cy = ORIGIN + s * k + p * k
        u, v = SWATCH[tint]
        face = lambda shade: {"uv": [u + (2 if shade else 0.5), v + 0.5, u + (3.5 if shade else 2), v + 3.5], "texture": "#0"}
        out.append({
            "name": name,
            "from": [r2(cx - length / 2), r2(cy - width / 2), r2(8 - thick / 2)],
            "to": [r2(cx + length / 2), r2(cy + width / 2), r2(8 + thick / 2)],
            # Chaque pavé est couché le long de la diagonale : un quart de tour et demi autour de l'axe z.
            "rotation": {"angle": 45, "axis": "z", "origin": [r2(cx), r2(cy), 8]},
            "faces": {"north": face(False), "south": face(False), "east": face(True), "west": face(True),
                      "up": face(False), "down": face(True)},
        })
    return out


def texture(palette):
    img = Image.new("RGBA", (16, 16), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    for key, (x, y) in SWATCH.items():
        base, shade = palette[key]
        d.rectangle((x, y, x + 1, y + 3), fill=base + (255,))
        d.rectangle((x + 2, y, x + 3, y + 3), fill=shade + (255,))
        d.point((x, y), fill=tuple(min(255, c + 30) for c in base) + (255,))
    return img


def write():
    made = []
    tex_dir = OUT / "crossfight" / "textures" / "item"
    mod_dir = OUT / "crossfight" / "models" / "item"
    tex_dir.mkdir(parents=True, exist_ok=True)
    mod_dir.mkdir(parents=True, exist_ok=True)
    # Un modèle retouché dans Blockbench (exporté en .json, avec ses textures s'il les a changées) remplace
    # celui de ce script : resourcepack-prive/modeles/falchion.json, falchion.png, falchion_sainte.png.
    custom = ROOT / "resourcepack-prive" / "modeles"
    for name, palette in PALETTES.items():
        own = custom / f"{name}.png"
        if own.exists():
            Image.open(own).save(tex_dir / f"{name}.png")
        else:
            texture(palette).save(tex_dir / f"{name}.png")
        made.append(tex_dir / f"{name}.png")
    if (custom / "falchion.json").exists():
        model = to_diagonal(json.loads((custom / "falchion.json").read_text(encoding="utf-8")))
        model["display"] = DISPLAY
        model["gui_light"] = "front"
        # Les textures du modèle exporté : la Falchion sur la clé "0", comme celle du script.
        model.setdefault("textures", {})
        model["textures"]["0"] = "crossfight:item/falchion"
        model["textures"]["particle"] = "crossfight:item/falchion"
        print("modèle retouché :", (custom / "falchion.json").relative_to(ROOT))
    else:
        model = {"credit": "Crossfight UHC - Falchion low-poly", "gui_light": "front",
                 "textures": {"0": "crossfight:item/falchion", "particle": "crossfight:item/falchion"},
                 "elements": elements(), "display": DISPLAY}
    (mod_dir / "falchion.json").write_text(json.dumps(model, indent=1) + "\n", encoding="utf-8")
    (mod_dir / "falchion_sainte.json").write_text(json.dumps(
        {"parent": "crossfight:item/falchion",
         "textures": {"0": "crossfight:item/falchion_sainte", "particle": "crossfight:item/falchion_sainte"}}, indent=1) + "\n", encoding="utf-8")
    made += [mod_dir / "falchion.json", mod_dir / "falchion_sainte.json"]
    # L'épée en fer (Falchion) et en diamant (Sainte-Falchion) : le modèle seulement pour l'arme de Marth.
    items = OUT / "minecraft" / "items"
    items.mkdir(parents=True, exist_ok=True)
    for vanilla, mark in (("iron_sword", "falchion"), ("diamond_sword", "falchion_sainte")):
        definition = {"model": {
            "type": "minecraft:select", "property": "minecraft:custom_model_data", "index": 0,
            "cases": [{"when": mark, "model": {"type": "minecraft:model", "model": f"crossfight:item/{mark}"}}],
            "fallback": {"type": "minecraft:model", "model": f"minecraft:item/{vanilla}"}}}
        (items / f"{vanilla}.json").write_text(json.dumps(definition, indent=1) + "\n", encoding="utf-8")
        made.append(items / f"{vanilla}.json")
    return made


# ---------------------------------------------------------------- version droite (Blockbench)

K = math.sqrt(0.5)
# La version droite se déduit de la diagonale par un huitième de tour autour du centre de l'image (8, 8) ;
# décalée vers le haut pour que le bout du pommeau soit à y = 0.
STRAIGHT_SHIFT = -((ORIGIN - 8) / K + 8) - 0.3


def straight_elements():
    """Les mêmes pavés, l'épée droite : la lame le long de y, les pavés alignés sur les axes."""
    out = []
    for name, s, p, length, width, thick, tint in PARTS:
        vx, vy = 8 - p, 8 + (ORIGIN - 8) / K + s + STRAIGHT_SHIFT
        u, v = SWATCH[tint]
        face = lambda shade: {"uv": [u + (2 if shade else 0.5), v + 0.5, u + (3.5 if shade else 2), v + 3.5], "texture": "#0"}
        out.append({
            "name": name,
            "from": [r2(vx - width / 2), r2(vy - length / 2), r2(8 - thick / 2)],
            "to": [r2(vx + width / 2), r2(vy + length / 2), r2(8 + thick / 2)],
            "faces": {"north": face(False), "south": face(False), "east": face(True), "west": face(True),
                      "up": face(False), "down": face(True)},
        })
    return out


ALLOWED = (-45, -22.5, 0, 22.5, 45)


def to_diagonal(model):
    """Remet en diagonale un modèle droit (exporté de Blockbench) : chaque pavé tourne d'un huitième de tour
    autour du centre de l'image. Un pavé déjà tourné autour de z garde sa rotation en plus (0, 22.5 ou 45
    degrés seulement) ; un pavé tourné autour de x ou de y ne peut pas l'être (Minecraft : un seul axe)."""
    for el in model.get("elements", []):
        rot = el.get("rotation") or {"angle": 0, "axis": "z", "origin": [(a + b) / 2 for a, b in zip(el["from"], el["to"])]}
        angle = float(rot.get("angle", 0))
        if angle != 0 and rot.get("axis") != "z":
            print(f"attention : '{el.get('name', '?')}' est tourne autour de {rot.get('axis')} ; il garde cette rotation, sans le huitieme de tour")
            continue
        new = angle - 45
        if new not in ALLOWED:
            print(f"attention : '{el.get('name', '?')}' tournerait de {new} degres (impossible) : rotation ramenee a -45")
            new = -45
        ox, oy = rot["origin"][0], rot["origin"][1] - STRAIGHT_SHIFT
        ax, ay = ox - 8, oy - 8
        dx, dy = 8 + K * (ax + ay), 8 + K * (ay - ax)
        mx, my = dx - rot["origin"][0], dy - rot["origin"][1]
        el["from"] = [r2(el["from"][0] + mx), r2(el["from"][1] + my), el["from"][2]]
        el["to"] = [r2(el["to"][0] + mx), r2(el["to"][1] + my), el["to"][2]]
        el["rotation"] = {"angle": new, "axis": "z", "origin": [r2(dx), r2(dy), rot["origin"][2]]}
    return model


# ---------------------------------------------------------------- Blockbench

GROUPS = {"poignee": ("pommeau_bout", "pommeau", "poignee", "bague"),
          "garde": ("garde", "aile_g", "aile_d", "pointe_g", "pointe_d", "ergot_g", "ergot_d", "oeil", "oeil_creux", "joyau"),
          "lame": ("talon", "lame", "gouttiere", "pointe_1", "pointe_2", "pointe_3")}


def blockbench(out: Path):
    """Un projet Blockbench (format Java Block/Item) : les pavés rangés en poignée / garde / lame, les deux
    textures incluses (0 : Falchion, 1 : Sainte-Falchion), les réglages d'affichage en main."""
    import base64
    import io
    import uuid
    els, ids = [], {}
    for el in straight_elements():
        u = str(uuid.uuid4())
        ids[el["name"]] = u
        center = [r2((a + b) / 2) for a, b in zip(el["from"], el["to"])]
        els.append({
            "name": el["name"], "box_uv": False, "rescale": False, "locked": False, "light_emission": 0,
            "render_order": "default", "allow_mirror_modeling": True,
            "from": el["from"], "to": el["to"], "autouv": 0, "color": 0,
            "rotation": [0, 0, 0], "origin": center,
            "faces": {k: {"uv": f["uv"], "texture": 0} for k, f in el["faces"].items()},
            "type": "cube", "uuid": u,
        })
    outliner = [{"name": g, "origin": [8, 11, 8], "color": 0, "uuid": str(uuid.uuid4()), "export": True,
                 "mirror_uv": False, "isOpen": True, "locked": False, "visibility": True, "autouv": 0,
                 "children": [ids[n] for n in names]} for g, names in GROUPS.items()]
    textures = []
    for i, (name, palette) in enumerate(PALETTES.items()):
        buf = io.BytesIO()
        texture(palette).save(buf, "PNG")
        textures.append({
            "path": "", "name": f"{name}.png", "folder": "item", "namespace": "crossfight", "id": str(i),
            "width": 16, "height": 16, "uv_width": 16, "uv_height": 16, "particle": i == 0,
            "render_mode": "default", "render_sides": "auto", "visible": True, "internal": True, "saved": False,
            "uuid": str(uuid.uuid4()), "source": "data:image/png;base64," + base64.b64encode(buf.getvalue()).decode(),
        })
    project = {
        "meta": {"format_version": "4.10", "model_format": "java_block", "box_uv": False},
        "name": "falchion", "parent": "", "ambientocclusion": True, "front_gui_light": True,
        "visible_box": [1, 1, 0], "resolution": {"width": 16, "height": 16},
        # Pas de reglages d'affichage : ceux de la version droite ne seraient pas ceux du jeu (le script remet
        # ses propres reglages, ceux d'une epee de Minecraft, en remettant le modele en diagonale).
        "elements": els, "outliner": outliner, "textures": textures,
    }
    out.write_text(json.dumps(project, indent=1) + "\n", encoding="utf-8")


# ---------------------------------------------------------------- apercu

def preview(out: Path):
    """Trois vues du modèle (face, trois quarts, profil), sans lancer le jeu."""
    import numpy as np
    sys.path.insert(0, str(ROOT / "tools"))
    import pose_preview as pp
    model = json.loads((OUT / "crossfight" / "models" / "item" / "falchion.json").read_text())
    panels = []
    for name in ("falchion", "falchion_sainte"):
        tex = np.array(Image.open(OUT / "crossfight" / "textures" / "item" / f"{name}.png").convert("RGBA").resize((64, 64), Image.NEAREST))
        quads = []
        for el in model["elements"]:
            a, b = el["from"], el["to"]
            rot = el["rotation"]
            o = np.array(rot["origin"], dtype=float)
            R = pp.rot(0, 0, rot["angle"])
            for face, f in el["faces"].items():
                pts = []
                for pt in pp.FACES[face](a, b):
                    q = o + R @ (np.array(pt, dtype=float) - o)
                    # repere de l'apercu (X gauche, Y haut, Z devant), en blocs, centre sur l'image
                    pts.append(np.array([-(q[0] - 8), q[1] - 8, q[2] - 8]) / 16 * 2.2 + np.array([0, 1.15, 0]))
                u1, v1, u2, v2 = (c * 4 for c in f["uv"])
                quads.append((pts, [(u1, v1), (u2, v1), (u1, v2), (u2, v2)], tex))
        for yaw in (0, 35, 90):
            panels.append(Image.fromarray(pp.render(quads, yaw, size=(360, 360), scale=120)))
    w, h = panels[0].size
    sheet = Image.new("RGB", (w * 3, h * 2), (40, 40, 48))
    for i, im in enumerate(panels):
        sheet.paste(im.convert("RGB"), ((i % 3) * w, (i // 3) * h))
    sheet.save(out)


def main() -> int:
    for f in write():
        print(f.relative_to(ROOT))
    if "--apercu" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--apercu") + 1])
        preview(out)
        print("apercu :", out)
    if "--blockbench" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--blockbench") + 1])
        blockbench(out)
        print("projet Blockbench :", out)
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
