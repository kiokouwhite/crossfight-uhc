#!/usr/bin/env python3
"""Dessine les deux apparences que prend le Titan d'Eren pendant qu'il se forme (pack public).

Usage :  python3 tools/build_titan.py [--apercu dossier]

  eren_squelette   le squelette : de vrais os en paves (crane et machoire, colonne, cotes en cerceaux,
                   sternum, bassin, os des bras et des jambes avec leurs articulations), a la place des
                   six boites habillees d'un skin
  eren_muscles     les muscles a vif : un skin dessine ici (fibres rouges, tendons clairs, dents a nu,
                   yeux verts), passe par tools/add_skin.py

Les deux sont des modeles articules comme ceux des personnages : memes six parties, memes articulations
(util/Puppet.java les anime avec les poses de poses.txt, et passe de l'un a l'autre partie par partie avec
Puppet.look). Le plugin les connait sous "skin.eren_squelette" et "skin.eren_muscles".

La troisieme apparence, le Titan Assaillant lui-meme, est le skin fourni a part (sous licence, pack prive) :
  python3 tools/add_skin.py <skin.png> eren_titan --prive
  python3 tools/sign_skins.py <dossier contenant eren_titan.png>     pour que le joueur le porte ensuite

Ensuite : python3 tools/build_pack.py
"""
import json
import random
import subprocess
import sys
import tempfile
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
NS = "crossfight"
BASE = ROOT / "resourcepack" / "assets" / NS
TINT = {"type": "minecraft:custom_model_data", "index": 0, "default": -1}

BONE, BONE_LIGHT, BONE_SHADE, HOLE = (226, 219, 196), (242, 237, 220), (176, 166, 138), (46, 38, 34)


# --------------------------------------------------------------------------- le squelette

def bone_texture() -> Image.Image:
    """32x32 : en haut a gauche l'os (un grain leger), a droite la face du crane et les dents, en bas les vertebres."""
    rnd = random.Random(7)
    im = Image.new("RGBA", (32, 32), (0, 0, 0, 0))
    px = im.load()
    for y in range(16):
        for x in range(16):
            px[x, y] = rnd.choice([BONE, BONE, BONE, BONE_LIGHT, BONE_SHADE]) + (255,)
    # La face du crane (8x6) : orbites, nez.
    face = ["BBBBBBBB",
            "BLLBBLLB",
            "BHHBBHHB",
            "BHHBBHHB",
            "SBBHHBBS",
            "SBBBBBBS"]
    # Les dents (6x2), puis le dessus de la machoire.
    teeth = ["LHLLHL",
             "SBSSBS"]
    key = {"B": BONE, "L": BONE_LIGHT, "S": BONE_SHADE, "H": HOLE}
    for y, row in enumerate(face):
        for x, c in enumerate(row):
            px[16 + x, y] = key[c] + (255,)
    for y, row in enumerate(teeth):
        for x, c in enumerate(row):
            px[16 + x, 8 + y] = key[c] + (255,)
    # Les vertebres : des bandes, une claire une sombre, separees d'un trait.
    for y in range(16, 32):
        for x in range(16):
            band = (y - 16) % 3
            px[x, y] = (HOLE if band == 2 and 0 < x < 15 else BONE if band == 0 else BONE_SHADE) + (255,)
    return im


def box(x1, y1, z1, x2, y2, z2, region="os", front=None):
    """Un pave du squelette, en pixels autour de l'articulation (droite = +X, haut = +Y, avant = -Z).

    region : "os" (grain) ou "vertebres". front : (u, v, largeur, hauteur) en pixels de texture pour la face avant.
    """
    w, h, d = x2 - x1, y2 - y1, z2 - z1
    u0, v0 = (0, 0) if region == "os" else (0, 16)
    q = lambda u, v, a, b: [(u0 + u) / 2, (v0 + v) / 2, (u0 + u + min(a, 16 - u)) / 2, (v0 + v + min(b, 16 - v)) / 2]
    # Chaque face prend un morceau different du grain, pour que deux os voisins ne se ressemblent pas.
    o = int(abs(x1 * 3 + y1 * 5 + z1 * 7)) % 6
    faces = {
        "north": q(o, 0, w, h), "south": q(o + 2, 0, w, h), "east": q(o, 2, d, h), "west": q(o + 1, 3, d, h),
        "up": q(o, 4, w, d), "down": q(o + 3, 1, w, d),
    }
    if front:
        u, v, fw, fh = front
        faces["north"] = [u / 2, v / 2, (u + fw) / 2, (v + fh) / 2]
    return {"from": [8 + x1, 8 + y1, 8 + z1], "to": [8 + x2, 8 + y2, 8 + z2],
            "faces": {k: {"uv": uv, "texture": "#skin", "tintindex": 0} for k, uv in faces.items()}}


def mirror(elements):
    """Le meme os de l'autre cote du corps."""
    out = []
    for el in elements:
        a, b = el["from"], el["to"]
        m = json.loads(json.dumps(el))
        m["from"], m["to"] = [16 - b[0], a[1], a[2]], [16 - a[0], b[1], b[2]]
        m["faces"]["east"], m["faces"]["west"] = el["faces"]["west"], el["faces"]["east"]
        out.append(m)
    return out


def skeleton_parts():
    head = [
        box(-4, 2, -4, 4, 8, 4, front=(16, 0, 8, 6)),                # la boite cranienne, sa face
        box(-3, 0, -4, 3, 2, 1, front=(16, 8, 6, 2)),                # la machoire et ses dents
        box(-4, 1, -2, -3, 3, 2), box(3, 1, -2, 4, 3, 2),            # les branches de la machoire
    ]
    body = [
        box(-1, -12, 0.6, 1, 0, 2, region="vertebres"),              # la colonne
        box(-4, -1.2, -1.2, 4, 0, 0.2),                              # les clavicules
        box(-0.8, -7, -2.2, 0.8, -1.2, -1.4),                        # le sternum
        box(-4, -12, -2, 4, -10.2, 2),                               # le bassin
        box(-2.6, -10.2, -1.4, 2.6, -9.4, 1.6),
    ]
    # Les cotes : quatre cerceaux, de plus en plus etroits vers le bas.
    for top, half in ((-2, 4), (-3.8, 4), (-5.6, 3.7), (-7.4, 3.2)):
        body += [box(-half, top - 1, -2, half, top, -1.2), box(-half, top - 1, 1.2, half, top, 2),
                 box(-half, top - 1, -1.2, -half + 0.8, top, 1.2), box(half - 0.8, top - 1, -1.2, half, top, 1.2)]
    arm_r = [
        box(-0.6, 0, -1.6, 2.6, 2, 1.6),                             # la tete de l'humerus
        box(0.2, -4, -0.8, 1.8, 0, 0.8),                             # l'humerus
        box(-0.3, -5.2, -1.3, 2.3, -4, 1.3),                         # le coude
        box(0, -9, -1.1, 0.9, -5.2, -0.2), box(1.1, -9, 0.2, 2, -5.2, 1.1),  # le radius et le cubitus
        box(-0.5, -10, -1.5, 2.5, -9, 1.5),                          # la main
    ]
    leg = [
        box(-1.5, -1.5, -1.5, 1.5, 0, 1.5),                          # la tete du femur
        box(-0.8, -5.5, -0.8, 0.8, -1.5, 0.8),                       # le femur
        box(-1.3, -6.8, -1.3, 1.3, -5.5, 1.3),                       # le genou
        box(-0.7, -11, -0.8, 0.5, -6.8, 0.4), box(0.8, -11, 0.2, 1.4, -6.8, 0.8),  # le tibia et le perone
        box(-1.5, -12, -3, 1.5, -11, 1.5),                           # le pied
    ]
    return {"head": head, "body": body, "arm_r": arm_r, "arm_l": mirror(arm_r), "leg_r": leg, "leg_l": mirror(leg)}


def write_skeleton(name="eren_squelette"):
    tex = BASE / "textures" / "item" / "pose" / f"{name}.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    bone_texture().save(tex)
    for part, elements in skeleton_parts().items():
        model = {"textures": {"skin": f"{NS}:item/pose/{name}", "particle": f"{NS}:item/pose/{name}"}, "elements": elements}
        item = {"model": {"type": "minecraft:model", "model": f"{NS}:item/pose/{name}_{part}", "tints": [TINT]}}
        for folder, content in (("models/item/pose", model), ("items/pose", item)):
            f = BASE / folder / f"{name}_{part}.json"
            f.parent.mkdir(parents=True, exist_ok=True)
            f.write_text(json.dumps(content, indent=1) + "\n", encoding="utf-8")
    print(f"{name} : squelette en paves ({sum(len(e) for e in skeleton_parts().values())} os)")


# --------------------------------------------------------------------------- les muscles

RED, RED_DARK, RED_LIGHT, TENDON = (150, 40, 36), (104, 24, 26), (196, 78, 64), (226, 196, 176)
EYE = (96, 232, 128)


def unwrap(u, v, w, h, d):
    """Les six faces d'une boite dans un skin : nom -> (x, y, largeur, hauteur)."""
    return {"droite": (u, v + d, d, h), "avant": (u + d, v + d, w, h), "gauche": (u + d + w, v + d, d, h),
            "arriere": (u + 2 * d + w, v + d, w, h), "dessus": (u + d, v, w, d), "dessous": (u + d + w, v, w, d)}


def muscle_skin() -> Image.Image:
    rnd = random.Random(11)
    im = Image.new("RGBA", (64, 64), (0, 0, 0, 0))
    px = im.load()

    def fibers(rect, joints=(), across=False):
        """Des fibres le long du membre (ou en travers) ; aux articulations, un bourrelet de tendon clair."""
        x0, y0, w, h = rect
        for y in range(h):
            for x in range(w):
                stripe = (y if across else x) + (1 if rnd.random() < 0.12 else 0)
                c = RED_DARK if stripe % 2 else RED
                if rnd.random() < 0.14:
                    c = RED_LIGHT
                if y in joints:
                    c = TENDON if (x + y) % 2 == 0 else RED_LIGHT
                px[x0 + x, y0 + y] = c + (255,)

    # La tete : muscles des joues, orbites sombres aux yeux verts, nez creux, dents a nu.
    head = unwrap(0, 0, 8, 8, 8)
    for name, rect in head.items():
        fibers(rect, across=name in ("dessus", "dessous"))
    fx, fy = head["avant"][:2]
    face = ["........",
            "........",
            ".HH..HH.",
            ".HG..GH.",
            "...HH...",
            "T......T",
            "LHLLLLHL",
            "LLHLLHLL"]
    key = {"H": HOLE, "G": EYE, "L": BONE_LIGHT, "T": TENDON}
    for y, row in enumerate(face):
        for x, c in enumerate(row):
            if c != ".":
                px[fx + x, fy + y] = key[c] + (255,)
    # Les dents se prolongent un peu sur les cotes.
    for side in ("droite", "gauche"):
        sx, sy, sw, _ = head[side]
        cols = range(sw - 3, sw) if side == "droite" else range(0, 3)
        for x in cols:
            px[sx + x, sy + 6] = (BONE_LIGHT if x % 2 else HOLE) + (255,)
            px[sx + x, sy + 7] = BONE_LIGHT + (255,)

    # Le torse : fibres en long, pectoraux soulignes, sternum et abdominaux marques de tendon.
    body = unwrap(16, 16, 8, 12, 4)
    for name, rect in body.items():
        fibers(rect, across=name in ("dessus", "dessous"))
    bx, by = body["avant"][:2]
    for y in range(1, 11):
        px[bx + 3, by + y] = px[bx + 4, by + y] = (TENDON if y < 5 else RED_DARK) + (255,)
    for x in range(8):
        px[bx + x, by + 4] = RED_DARK + (255,)                       # sous les pectoraux
    for y in (6, 8, 10):
        for x in (1, 2, 5, 6):
            px[bx + x, by + y] = TENDON + (255,)                     # les abdominaux
    kx, ky = body["arriere"][:2]
    for y in range(12):
        px[kx + 3, ky + y] = px[kx + 4, ky + y] = (TENDON if y % 2 == 0 else RED_DARK) + (255,)  # la colonne affleure

    # Bras et jambes : fibres en long, tendons a l'epaule, au coude ou au genou, au poignet ou a la cheville.
    for u, v in ((40, 16), (32, 48)):
        for name, rect in unwrap(u, v, 4, 12, 4).items():
            fibers(rect, joints=() if name in ("dessus", "dessous") else (0, 5, 10), across=name in ("dessus", "dessous"))
    for u, v in ((0, 16), (16, 48)):
        for name, rect in unwrap(u, v, 4, 12, 4).items():
            fibers(rect, joints=() if name in ("dessus", "dessous") else (0, 6, 11), across=name in ("dessus", "dessous"))
    return im


def write_muscles(name="eren_muscles"):
    with tempfile.TemporaryDirectory() as tmp:
        skin = Path(tmp) / f"{name}.png"
        muscle_skin().save(skin)
        subprocess.run([sys.executable, str(ROOT / "tools" / "add_skin.py"), str(skin), name], check=True)


def main() -> int:
    write_skeleton()
    write_muscles()
    if "--apercu" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--apercu") + 1])
        out.mkdir(parents=True, exist_ok=True)
        bone_texture().resize((256, 256), Image.NEAREST).save(out / "squelette_texture.png")
        muscle_skin().resize((512, 512), Image.NEAREST).save(out / "muscles_skin.png")
        print(f"apercus dans {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
