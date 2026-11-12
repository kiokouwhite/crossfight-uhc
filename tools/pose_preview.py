#!/usr/bin/env python3
"""Dessine une pose du modele articule sans lancer le jeu.

Usage :  python3 tools/pose_preview.py <personnage> <pose> [sortie.png] [--prive] [--sans-objet] [--objet image.png] [--arc] [--modele nom]

--objet : l'image 16x16 de l'objet tenu (sinon une epee de remplacement). --arc : tenu par le milieu, comme un arc.
--modele : un modele du pack public tenu en main (ex. book_open), centre sur la main.

Relit les modeles ecrits par add_skin.py et src/main/resources/poses.txt, et applique les memes
transformations que util/Puppet.java et que le client Minecraft (objet affiche centre sur son
articulation, retourne d'un demi-tour, puis rotation et translation de l'affichage).
Cinq vues : face, trois quarts gauche, trois quarts droit, profil droit, dos.
"""
import json
import math
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
K = 0.9375                      # echelle du modele de joueur
PIVOT = {"head": (0, 24, 0), "body": (0, 24, 0), "arm_r": (-5, 22, 0), "arm_l": (5, 22, 0),
         "leg_r": (-1.9, 12, 0), "leg_l": (1.9, 12, 0)}
HAND = (0, -9, 0)               # la main, dans le repere du bras
ITEM_SCALE = 0.85
GRIP = (5.5, -5.5, 0)           # la poignee de l'epee dans l'image de l'objet (pixels, apres le demi-tour)
GRIP_BOW = (2, 2, 0)            # le milieu du bois d'un arc


def rot(rx, ry, rz):
    """Ry * Rx * Rz, comme Quaternionf.rotationYXZ."""
    x, y, z = (math.radians(a) for a in (rx, ry, rz))
    mx = np.array([[1, 0, 0], [0, math.cos(x), -math.sin(x)], [0, math.sin(x), math.cos(x)]])
    my = np.array([[math.cos(y), 0, math.sin(y)], [0, 1, 0], [-math.sin(y), 0, math.cos(y)]])
    mz = np.array([[math.cos(z), -math.sin(z), 0], [math.sin(z), math.cos(z), 0], [0, 0, 1]])
    return my @ mx @ mz


ITEM_BASE = rot(90, 0, 0) @ rot(0, 90, 0) @ rot(0, 0, -45)


def load_poses():
    poses, cur = {}, None
    for line in (ROOT / "src/main/resources/poses.txt").read_text(encoding="utf-8").splitlines():
        t = line.split("#", 1)[0].split()
        if not t:
            continue
        if t[0] == "pose":
            cur = poses.setdefault(t[1], {})
        elif cur is not None:
            v = [float(x) for x in t[1:]]
            cur[t[0]] = (v[:3], (v[3:6] + [0, 0, 0])[:3])
    return poses


FACES = {  # coins haut-gauche, haut-droit, bas-gauche, bas-droit, vus de l'exterieur
    "north": lambda a, b: [(b[0], b[1], a[2]), (a[0], b[1], a[2]), (b[0], a[1], a[2]), (a[0], a[1], a[2])],
    "south": lambda a, b: [(a[0], b[1], b[2]), (b[0], b[1], b[2]), (a[0], a[1], b[2]), (b[0], a[1], b[2])],
    "east":  lambda a, b: [(b[0], b[1], b[2]), (b[0], b[1], a[2]), (b[0], a[1], b[2]), (b[0], a[1], a[2])],
    "west":  lambda a, b: [(a[0], b[1], a[2]), (a[0], b[1], b[2]), (a[0], a[1], a[2]), (a[0], a[1], b[2])],
    "up":    lambda a, b: [(a[0], b[1], a[2]), (b[0], b[1], a[2]), (a[0], b[1], b[2]), (b[0], b[1], b[2])],
    "down":  lambda a, b: [(a[0], a[1], b[2]), (b[0], a[1], b[2]), (a[0], a[1], a[2]), (b[0], a[1], a[2])],
}


def sword_sprite():
    """Une epee de remplacement : poignee en bas a gauche, pointe en haut a droite, comme celle du jeu."""
    im = np.zeros((16, 16, 4), dtype=np.uint8)
    for i in range(16):
        x, y = i, 15 - i
        col = (120, 80, 40, 255) if i < 4 else (220, 225, 235, 255)
        for dx, dy in ((0, 0), (1, 0), (0, -1)) if i >= 4 else ((0, 0),):
            if 0 <= x + dx < 16 and 0 <= y + dy < 16:
                im[y + dy, x + dx] = col
    for x, y in ((2, 10), (3, 11), (5, 13), (6, 12), (4, 12), (3, 12), (4, 13)):
        im[y, x] = (70, 110, 200, 255)
    return im


def held_sprite():
    if "--objet" in sys.argv:
        return np.array(Image.open(sys.argv[sys.argv.index("--objet") + 1]).convert("RGBA"))
    return sword_sprite()


def quads(name, pose_name, private):
    """Tous les quadrilateres (4 points en blocs, repere X gauche / Y haut / Z avant, 4 uv, texture)."""
    base = ROOT / ("resourcepack-prive" if private else "resourcepack") / "assets" / "crossfight"
    skin = np.array(Image.open(base / "textures/item/pose" / f"{name}.png").convert("RGBA"))
    pose = load_poses()[pose_name]
    get = lambda part: pose.get(part, ([0, 0, 0], [0, 0, 0]))
    root_r, root_t = rot(*get("root")[0]), np.array(get("root")[1]) / 16 * K
    flip = np.diag([-1, 1, -1])  # le demi-tour que le client applique aux objets affiches
    out = []
    for part, pivot in PIVOT.items():
        ang, off = get(part)
        r = root_r @ rot(*ang)
        t = root_t + root_r @ ((np.array(pivot) + np.array(off)) / 16 * K)
        model = json.loads((base / "models/item/pose" / f"{name}_{part}.json").read_text())
        for el in model["elements"]:
            a, b = el["from"], el["to"]
            for face, f in el["faces"].items():
                pts = [t + r @ (K * (flip @ ((np.array(p) - 8) / 16))) for p in FACES[face](a, b)]
                u1, v1, u2, v2 = (c * 4 for c in f["uv"])
                out.append((pts, [(u1, v1), (u2, v1), (u1, v2), (u2, v2)], skin))
        if part == ("arm_l" if "item_left" in pose else "arm_r") and "--modele" in sys.argv:
            # Un vrai modele du pack : memes regles que les parties du corps, plus la rotation propre a chaque element.
            mname = sys.argv[sys.argv.index("--modele") + 1]
            pub = ROOT / "resourcepack" / "assets" / "crossfight"
            mtex = np.array(Image.open(pub / "textures/item/book_open.png").convert("RGBA"))
            ri = rot(*pose["item_abs"][0]) if "item_abs" in pose else root_r @ rot(*pose["item_world"][0]) if "item_world" in pose else r @ rot(*get("item")[0])
            hand = t + r @ (np.array(HAND) / 16 * K)
            if "item_shift" in pose: hand = hand + np.array(pose["item_shift"][0]) / 16 * K
            for el in json.loads((pub / "models/item" / f"{mname}.json").read_text())["elements"]:
                a, b = el["from"], el["to"]
                def placed(pt):
                    pt = np.array(pt, dtype=float)
                    if "rotation" in el:
                        o = np.array(el["rotation"]["origin"], dtype=float); ang = el["rotation"]["angle"]
                        ax = {"x": (ang, 0, 0), "y": (0, ang, 0), "z": (0, 0, ang)}[el["rotation"]["axis"]]
                        pt = o + rot(*ax) @ (pt - o)
                    return hand + ri @ (K * (flip @ ((pt - 8) / 16)))
                for face, f in el["faces"].items():
                    u1, v1, u2, v2 = f["uv"]
                    out.append(([placed(pt) for pt in FACES[face](a, b)], [(u1, v1), (u2, v1), (u1, v2), (u2, v2)], mtex))
        elif part == ("arm_l" if "item_left" in pose else "arm_r") and "--sans-objet" not in sys.argv:
            ri = (rot(*pose["item_abs"][0]) if "item_abs" in pose else root_r @ rot(*pose["item_world"][0]) if "item_world" in pose
                  else r @ rot(*get("item")[0])) @ ITEM_BASE
            hand = t + r @ (np.array(HAND) / 16 * K)
            ti = hand - ri @ (np.array(GRIP_BOW if "--arc" in sys.argv else GRIP) / 16 * ITEM_SCALE)
            pts = [ti + ri @ (ITEM_SCALE * (flip @ (np.array(p) / 16))) for p in ((-8, 8, 0), (8, 8, 0), (-8, -8, 0), (8, -8, 0))]
            out.append((pts, [(0, 0), (16, 0), (0, 16), (16, 16)], held_sprite()))
    return out


def render(qs, view_yaw, size=(420, 460), scale=115):
    """Vue orthographique : la camera tourne autour du personnage (0 = de face)."""
    w, h = size
    img = np.full((h, w, 3), 235, dtype=np.float32)
    zbuf = np.full((h, w), -1e9)
    a = math.radians(view_yaw)
    # A 0 degre la camera est devant le personnage : a l'ecran, sa gauche (X) est a droite.
    right = np.array([math.cos(a), 0, -math.sin(a)]); fwd = np.array([-math.sin(a), 0, -math.cos(a)])
    light = np.array([0.3, 0.8, 0.5]); light /= np.linalg.norm(light)
    for pts, uvs, tex in qs:
        p = np.array(pts)
        n = np.cross(p[1] - p[0], p[2] - p[0]); n = n / (np.linalg.norm(n) + 1e-9)
        shade = 0.62 + 0.38 * abs(float(n @ (right * light[0] + np.array([0, 1, 0]) * light[1] + fwd * light[2])))
        sx = w / 2 + (p @ right) * scale; sy = h - 70 - p[:, 1] * scale; sz = -(p @ fwd)
        for tri in ((0, 1, 2), (1, 3, 2)):
            x, y = sx[list(tri)], sy[list(tri)]
            x0, x1 = int(max(0, math.floor(x.min()))), int(min(w - 1, math.ceil(x.max())))
            y0, y1 = int(max(0, math.floor(y.min()))), int(min(h - 1, math.ceil(y.max())))
            if x0 > x1 or y0 > y1:
                continue
            gx, gy = np.meshgrid(np.arange(x0, x1 + 1) + 0.5, np.arange(y0, y1 + 1) + 0.5)
            den = (y[1] - y[2]) * (x[0] - x[2]) + (x[2] - x[1]) * (y[0] - y[2])
            if abs(den) < 1e-9:
                continue
            l0 = ((y[1] - y[2]) * (gx - x[2]) + (x[2] - x[1]) * (gy - y[2])) / den
            l1 = ((y[2] - y[0]) * (gx - x[2]) + (x[0] - x[2]) * (gy - y[2])) / den
            l2 = 1 - l0 - l1
            inside = (l0 >= 0) & (l1 >= 0) & (l2 >= 0)
            z = l0 * sz[tri[0]] + l1 * sz[tri[1]] + l2 * sz[tri[2]]
            uv = np.array(uvs)[list(tri)]
            u = l0 * uv[0][0] + l1 * uv[1][0] + l2 * uv[2][0]; v = l0 * uv[0][1] + l1 * uv[1][1] + l2 * uv[2][1]
            # Les uv inverses (u1 > u2) echantillonnent le pixel du bon cote.
            th, tw = tex.shape[:2]
            ui = np.clip(np.where(uvs[1][0] < uvs[0][0], np.ceil(u) - 1, np.floor(u)), 0, tw - 1).astype(int)
            vi = np.clip(np.where(uvs[2][1] < uvs[0][1], np.ceil(v) - 1, np.floor(v)), 0, th - 1).astype(int)
            col = tex[vi, ui]
            ok = inside & (col[..., 3] > 25) & (z > zbuf[y0:y1 + 1, x0:x1 + 1])
            sub = img[y0:y1 + 1, x0:x1 + 1]; sub[ok] = col[..., :3][ok] * shade
            zb = zbuf[y0:y1 + 1, x0:x1 + 1]; zb[ok] = z[ok]
    return img.astype(np.uint8)


def main() -> int:
    skip = {sys.argv.index(o) + 1 for o in ("--objet", "--modele") if o in sys.argv}
    args = [a for i, a in enumerate(sys.argv) if i > 0 and i not in skip and not a.startswith("--")]
    if len(args) < 2:
        print(__doc__)
        return 1
    qs = quads(args[0], args[1], "--prive" in sys.argv)
    views = [render(qs, yaw) for yaw in (0, 45, -45, -90, 180)]
    out = Path(args[2] if len(args) > 2 else f"{args[0]}_{args[1]}.png")
    Image.fromarray(np.concatenate(views, axis=1)).save(out)
    print(out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
