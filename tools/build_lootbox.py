#!/usr/bin/env python3
"""La Lootbox en 3D : un coffre au tresor sous un parachute rond a la couleur de son tier.

Usage : python3 tools/build_lootbox.py [--apercu image.png]

Deux objets, montres par deux affichages superposes (game/Lootbox.java) :
  crossfight:lootbox             le coffre, pose au fond du bloc (y = 0 a 14). Pack public : le coffre de Minecraft
                                 (son modele special, rien n'est copie). Pack complet : le coffre violet et blanc du
                                 modele Blockbench fourni (resourcepack-prive/modeles/lootbox_coffre.gltf, jamais
                                 versionne, qui en contient trois ; seul celui-ci sert), le meme pour tous les tiers.
  crossfight:lootbox_parachute   le parachute (dessin original), un bloc plus haut : un dome rond de 24 pixels de large
                                 et 15 de haut, a bandes alternees (couleur du tier, blanc), aux fuseaux cousus, et huit
                                 suspentes qui descendent vers le dessus du coffre (y = -2,6 dans ce modele). La couleur :
                                 la premiere du composant custom_model_data de l'objet (le plugin y met celle du tier).

Ecrit :
  resourcepack/assets/crossfight/items/lootbox.json, lootbox_parachute.json
  resourcepack/assets/crossfight/models/item/lootbox_parachute.json
  resourcepack/assets/crossfight/textures/item/lootbox/parachute_{cote,dessus,dessous}.png, corde.png
  resourcepack-prive/assets/crossfight/items/lootbox.json             le coffre converti a la place de celui de Minecraft
  resourcepack-prive/assets/crossfight/models/item/lootbox_coffre.json
  resourcepack-prive/assets/crossfight/textures/item/lootbox/fer.png

Les deux modeles sont faits pour un affichage d'objet sans reglage (ItemDisplay, transformation NONE), qui centre le
point (8, 8, 8) du modele sur l'affichage.

Conversion du glTF de Blockbench : chaque noeud est un pave (des faces a la normale alignee sur un axe, reparties
parfois sur plusieurs primitives), son noeud porte son pivot (la translation) et sa rotation (45 degres au plus, sur
un axe : Minecraft l'accepte telle quelle). Les coordonnees de texture du glTF ont leur origine en haut a gauche de
l'image, comme Minecraft (verifie : avec l'axe v retourne, un tiers des triangles tomberaient sur du transparent). Une
face a la texture tournee d'un quart de tour garde sa rotation. Le coffre fait 14 pixels de large, sa serrure au sud
(+z), comme le coffre de Minecraft.
"""
import base64
import io
import json
import math
import struct
import sys
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
NS = "crossfight"
PUBLIC = ROOT / "resourcepack" / "assets" / NS
PRIVATE = ROOT / "resourcepack-prive" / "assets" / NS
GLTF = ROOT / "resourcepack-prive" / "modeles" / "lootbox_coffre.gltf"
NOMS = {"treasurechest_wood": "bois", "treasurechest_iron": "fer", "treasurechest_gold": "or"}
COFFRE = "fer"          # le coffre violet et blanc
LARGEUR = 14.0
# Le cote de la serrure de chaque coffre dans le glTF, et le nombre de quarts de tour (tourner_point) qui l'amene au sud.
SERRURE = {"bois": "west", "fer": "west", "or": "north"}
QUARTS = {"south": 0, "east": 1, "north": 2, "west": 3}

# Le parachute, dans son propre modele (un bloc au-dessus de celui du coffre).
HAUT_COFFRE = -2.6      # le dessus du coffre (13,4 pixels ; celui de Minecraft, 14, cache le pied des suspentes)
RAYON = 12.0
JUPE = (15, 19)          # la jupe : droite, du bord du parachute au debut du dome
SOMMET = 30
# Les bandes, du bas vers le haut : (bas, haut, a la couleur du tier ; sinon blanche).
BANDES = ((15, 19, True), (19, 23, False), (23, 27, True), (27, 30, False))
ECHELLE_UV = 16 / (2 * RAYON)   # les textures couvrent toute la largeur du parachute
PENTE = 22.5             # l'inclinaison des suspentes (Minecraft : -45, -22.5, 0, 22.5 ou 45 degres)
DEPART = 4.3             # les suspentes partent a 4,3 pixels du centre du dessus du coffre
CORDE = 0.2              # demi-epaisseur d'une suspente


# ------------------------------------------------------------------ le glTF de Blockbench

def lire_gltf(path: Path):
    """Les noeuds (translation, rotation, primitives (positions, normales, uv, materiau)), la texture (PIL) et le nom
    de chaque materiau."""
    g = json.loads(path.read_text(encoding="utf-8"))
    buf = base64.b64decode(g["buffers"][0]["uri"].split(",", 1)[1])

    def acc(i):
        a = g["accessors"][i]
        bv = g["bufferViews"][a["bufferView"]]
        comp = {5126: "f", 5123: "H", 5125: "I"}[a["componentType"]]
        n = {"SCALAR": 1, "VEC2": 2, "VEC3": 3}[a["type"]]
        size = struct.calcsize(comp)
        off, stride = bv.get("byteOffset", 0) + a.get("byteOffset", 0), bv.get("byteStride", size * n)
        return np.array([struct.unpack_from("<" + comp * n, buf, off + k * stride) for k in range(a["count"])], dtype=float)

    images = [Image.open(io.BytesIO(base64.b64decode(im["uri"].split(",", 1)[1]))).convert("RGBA") for im in g["images"]]
    tex_mat, noms_mat = [], []
    for m in g["materials"]:
        src = g["textures"][m["pbrMetallicRoughness"]["baseColorTexture"]["index"]]["source"]
        tex_mat.append(images[src])
        nom = m.get("name", "").removesuffix(".png")
        noms_mat.append(NOMS.get(nom, nom))
    noeuds = []
    for node in g["nodes"]:
        if "mesh" not in node:
            continue
        prims = []
        for prim in g["meshes"][node["mesh"]]["primitives"]:
            P, N, T = (acc(prim["attributes"][k]) for k in ("POSITION", "NORMAL", "TEXCOORD_0"))
            if "indices" in prim:
                idx = acc(prim["indices"])[:, 0].astype(int)
                P, N, T = P[idx], N[idx], T[idx]
            prims.append((P, N, T, prim.get("material", 0)))
        noeuds.append({"t": np.array(node.get("translation", [0, 0, 0]), dtype=float),
                       "q": node.get("rotation", [0, 0, 0, 1]), "prims": prims})
    return noeuds, tex_mat, noms_mat


def matrice(q):
    x, y, z, w = q
    return np.array([[1 - 2 * (y * y + z * z), 2 * (x * y - z * w), 2 * (x * z + y * w)],
                     [2 * (x * y + z * w), 1 - 2 * (x * x + z * z), 2 * (y * z - x * w)],
                     [2 * (x * z - y * w), 2 * (y * z + x * w), 1 - 2 * (x * x + y * y)]])


def rotation(q):
    """Le quaternion d'un noeud en rotation Minecraft (un axe, un angle en degres), ou None."""
    x, y, z, w = q
    angle = math.degrees(2 * math.acos(max(-1.0, min(1.0, w))))
    if abs(angle) < 1e-3:
        return None
    s = math.sqrt(max(1e-12, 1 - w * w))
    axe = np.array([x, y, z]) / s
    k = int(np.argmax(np.abs(axe)))
    if abs(abs(axe[k]) - 1) > 1e-3:
        raise SystemExit("Rotation sur plusieurs axes : impossible dans Minecraft")
    angle *= math.copysign(1, axe[k])
    if angle > 180:
        angle -= 360
    if abs(angle) > 45.01:
        raise SystemExit(f"Rotation de {angle:.1f} degres : Minecraft n'accepte que -45 a 45")
    return "xyz"[k], round(angle, 3)


# Pour chaque face : (axe u, coin u1 : 0 min, 1 max), (axe v, coin v1), d'apres les uv par defaut de Minecraft.
UV_COINS = {
    "north": ((0, 1), (1, 1)),
    "south": ((0, 0), (1, 1)),
    "east": ((2, 1), (1, 1)),
    "west": ((2, 0), (1, 1)),
    "up": ((0, 0), (2, 0)),
    "down": ((0, 0), (2, 1)),
}
NORMALES = {(0, 0, -1): "north", (0, 0, 1): "south", (1, 0, 0): "east", (-1, 0, 0): "west", (0, 1, 0): "up", (0, -1, 0): "down"}


def element(noeud, noms_mat):
    """Un noeud du glTF en element de modele Minecraft (dans les coordonnees du glTF)."""
    t = noeud["t"]
    tout = np.concatenate([p[0] for p in noeud["prims"]])
    lo, hi = tout.min(axis=0), tout.max(axis=0)
    el = {"from": list(lo + t), "to": list(hi + t), "faces": {}}
    rot = rotation(noeud["q"])
    if rot:
        el["rotation"] = {"origin": list(t), "axis": rot[0], "angle": rot[1]}
    for P, N, T, mat in noeud["prims"]:
        par_face = {}
        for i, n in enumerate(N):
            nom = NORMALES.get(tuple(int(round(c)) for c in n))
            if nom:
                par_face.setdefault(nom, []).append(i)
        for nom, idx in par_face.items():
            pts, uvs = P[idx], T[idx]
            (au, cu), (av, cv) = UV_COINS[nom]

            def coin(su, sv):
                d = np.abs(pts[:, au] - (hi[au] if su else lo[au])) + np.abs(pts[:, av] - (hi[av] if sv else lo[av]))
                uv = uvs[int(np.argmin(d))]
                return np.array([uv[0] * 16, uv[1] * 16])
            # Les sommets de la face dans l'ordre de Minecraft : 0 prend (u1, v1), 1 (u1, v2), 2 (u2, v2), 3 (u2, v1) ;
            # une face tournee de r quarts de tour donne au sommet i les uv du coin (i + r) % 4.
            sommets = [coin(cu, cv), coin(cu, 1 - cv), coin(1 - cu, 1 - cv), coin(1 - cu, cv)]
            for r in range(4):
                (u1, v1), (u2, v2) = sommets[(0 - r) % 4], sommets[(2 - r) % 4]
                coins = [(u1, v1), (u1, v2), (u2, v2), (u2, v1)]
                if all(np.allclose(sommets[i], coins[(i + r) % 4], atol=1e-3) for i in range(4)):
                    break
            else:
                raise SystemExit(f"Face {nom} : texture deformee, impossible dans Minecraft")
            face = {"uv": [round(float(c), 4) for c in (u1, v1, u2, v2)], "texture": "#" + noms_mat[mat]}
            if r:
                face["rotation"] = 90 * r
            el["faces"][nom] = face
    return el


def tourner_point(v, k=1):
    """Un point tourne k fois de -90 degres autour de l'axe vertical du centre du bloc (8, 8) : l'ouest passe au nord,
    le nord a l'est, l'est au sud."""
    x, y, z = v
    for _ in range(k):
        x, z = 16 - z, x
    return [round(float(x), 4), float(y), round(float(z), 4)]


RENOMME = {"west": "north", "north": "east", "east": "south", "south": "west", "up": "up", "down": "down"}


def tourner(el):
    """Un element tourne d'un quart de tour (voir tourner_point). Les faces des cotes changent de nom et gardent leur
    texture (Minecraft les plaque toutes de gauche a droite, vues de dehors) ; celles du dessus et du dessous tournent
    avec le coffre (dessus : +90 degres, dessous : +270)."""
    a, b = tourner_point(el["from"]), tourner_point(el["to"])
    el["from"], el["to"] = [min(a[i], b[i]) for i in range(3)], [max(a[i], b[i]) for i in range(3)]
    if "rotation" in el:
        r = el["rotation"]
        r["origin"] = tourner_point(r["origin"])
        # Une rotation autour de +x devient une rotation autour de +z ; autour de +z, autour de -x.
        if r["axis"] == "x":
            r["axis"] = "z"
        elif r["axis"] == "z":
            r["axis"], r["angle"] = "x", -r["angle"]
    faces = {}
    for nom, f in el["faces"].items():
        f = dict(f)
        if nom in ("up", "down"):
            f["rotation"] = (f.get("rotation", 0) + (90 if nom == "up" else 270)) % 360
            if not f["rotation"]:
                del f["rotation"]
        faces[RENOMME[nom]] = f
    el["faces"] = faces


def coffre():
    """Le coffre violet et blanc : ses elements (a l'echelle, centre, pose a y = 0, serrure au sud), sa texture et ce
    qu'il faut pour rendre le glTF tel quel dans l'apercu (placement, noeuds, quarts de tour)."""
    noeuds, tex_mat, noms_mat = lire_gltf(GLTF)
    nds = [nd for nd in noeuds if noms_mat[min(p[3] for p in nd["prims"])] == COFFRE]
    pts = np.concatenate([p[0] + nd["t"] for nd in nds for p in nd["prims"]])
    lo, hi = pts.min(axis=0), pts.max(axis=0)
    s = LARGEUR / max(hi[0] - lo[0], hi[2] - lo[2])
    base = np.array([(lo[0] + hi[0]) / 2, lo[1], (lo[2] + hi[2]) / 2])

    def place(p):
        p = (np.array(p, dtype=float) - base) * s + np.array([8, 0, 8])
        return [round(float(c), 4) for c in p]
    k = QUARTS[SERRURE.get(COFFRE, "south")]
    els = [element(nd, noms_mat) for nd in nds]
    for el in els:
        el["from"], el["to"] = place(el["from"]), place(el["to"])
        if "rotation" in el:
            el["rotation"]["origin"] = place(el["rotation"]["origin"])
        for _ in range(k):
            tourner(el)
    texture = tex_mat[noms_mat.index(COFFRE)]
    return els, texture, (hi[1] - lo[1]) * s, place, nds, noms_mat, k


# ------------------------------------------------------------------ le parachute (dessin original)

def textures_parachute():
    """En gris que la teinte colore : les cotes (des coutures verticales toutes les 3 pixels du parachute), le dessus
    (seize coutures qui partent du centre), le dessous (plus sombre, memes coutures) ; et la corde (non teintee)."""
    cote = Image.new("RGBA", (32, 32))
    dessus = Image.new("RGBA", (32, 32))
    dessous = Image.new("RGBA", (32, 32))
    pc, pd, pb = cote.load(), dessus.load(), dessous.load()
    pas = 3 * 32 / (2 * RAYON)          # 3 pixels du parachute, en pixels de texture
    for x in range(32):
        couture = abs(((x + 0.5 - 16) / pas + 0.5) % 1 - 0.5) * pas < 0.5
        for y in range(32):
            pc[x, y] = (222, 222, 222, 255) if couture else (252, 252, 252, 255)
            a = math.degrees(math.atan2(y + 0.5 - 16, x + 0.5 - 16)) % 22.5
            r = math.hypot(y + 0.5 - 16, x + 0.5 - 16)
            radiale = min(a, 22.5 - a) * math.pi / 180 * r < 0.5 and r > 1
            pd[x, y] = (222, 222, 222, 255) if radiale else (252, 252, 252, 255)
            pb[x, y] = (128, 128, 128, 255) if radiale else (160, 160, 160, 255)
    corde = Image.new("RGBA", (16, 16), (214, 208, 196, 255))
    return {"parachute_cote": cote, "parachute_dessus": dessus, "parachute_dessous": dessous, "corde": corde}


def rayon(y):
    """Le rayon du parachute a cette hauteur : droit sur la jupe, puis un quart d'ellipse jusqu'au sommet."""
    if y <= JUPE[1]:
        return RAYON
    t = (y - JUPE[1]) / (SOMMET - JUPE[1])
    return RAYON * math.sqrt(max(0.0, 1 - t * t))


def etages():
    """Les etages du parachute : (bas, haut, rayon, teinte), la jupe d'un bloc, le dome pixel par pixel ; deux etages
    voisins de meme rayon et de meme bande n'en font qu'un."""
    out = [(JUPE[0], JUPE[1], RAYON)]
    for y in range(JUPE[1], SOMMET):
        out.append((y, y + 1, round(rayon(y + 0.5) * 2) / 2))
    fusion = []
    for y0, y1, r in out:
        teinte = next(b[2] for b in BANDES if b[0] <= y0 < b[1])
        if fusion and fusion[-1][2] == r and fusion[-1][3] == teinte:
            fusion[-1] = (fusion[-1][0], y1, r, teinte)
        else:
            fusion.append((y0, y1, r, teinte))
    return fusion


def u(c):
    return round(8 + (c - 8) * ECHELLE_UV, 4)


def disque(r):
    """Un disque de rayon r en cinq paves (vu de dessus, une rangee centrale et deux de chaque cote) : (x0, z0, x1, z1).
    Les rangees se touchent sans se chevaucher (pas de faces superposees qui scintillent)."""
    a, b = round(0.42 * r * 4) / 4, round(0.74 * r * 4) / 4
    r = round(r * 4) / 4
    return [(8 - r, 8 - a, 8 + r, 8 + a),
            (8 - b, 8 + a, 8 + b, 8 + b), (8 - b, 8 - b, 8 + b, 8 - a),
            (8 - a, 8 + b, 8 + a, 8 + r), (8 - a, 8 - r, 8 + a, 8 - b)]


def parachute():
    els = []
    for n, (y0, y1, r, teinte) in enumerate(etages()):
        for x0, z0, x1, z1 in disque(r):
            def f(tex, uv):
                face = {"uv": uv, "texture": "#" + tex}
                if teinte:
                    face["tintindex"] = 0
                return face
            faces = {
                "north": f("cote", [u(x1), 0, u(x0), 16]), "south": f("cote", [u(x0), 0, u(x1), 16]),
                "east": f("cote", [u(z1), 0, u(z0), 16]), "west": f("cote", [u(z0), 0, u(z1), 16]),
                "up": f("dessus", [u(x0), u(z0), u(x1), u(z1)]),
            }
            if n == 0:   # le dessous de la jupe : celui des etages superieurs est cache
                faces["down"] = f("dessous", [u(x0), u(z1), u(x1), u(z0)])
            els.append({"from": [x0, y0, z0], "to": [x1, y1, z1], "faces": faces})
    # Huit suspentes : deux de chaque cote, du dessus du coffre jusque dans la jupe, penchees vers l'exterieur.
    # Une rotation positive autour de z penche le haut vers -x, autour de x vers +z.
    longueur = (JUPE[0] - HAUT_COFFRE + 1.0) / math.cos(math.radians(PENTE))
    corde = {"uv": [0, 0, 16, 16], "texture": "#corde"}
    for d in (-3, 3):
        for x, z, axe, angle in ((8 - DEPART, 8 + d, "z", PENTE), (8 + DEPART, 8 + d, "z", -PENTE),
                                 (8 + d, 8 - DEPART, "x", -PENTE), (8 + d, 8 + DEPART, "x", PENTE)):
            faces = {f: dict(corde) for f in ("north", "south", "east", "west")}
            els.append({"from": [round(x - CORDE, 4), HAUT_COFFRE, round(z - CORDE, 4)],
                        "to": [round(x + CORDE, 4), round(HAUT_COFFRE + longueur, 4), round(z + CORDE, 4)],
                        "rotation": {"origin": [x, HAUT_COFFRE, z], "axis": axe, "angle": angle},
                        "faces": faces})
    tex = {n: f"{NS}:item/lootbox/{t}" for n, t in (("cote", "parachute_cote"), ("dessus", "parachute_dessus"),
                                                    ("dessous", "parachute_dessous"), ("corde", "corde"))}
    tex["particle"] = tex["cote"]
    return {"textures": tex, "elements": els}


# ------------------------------------------------------------------ ecriture

def ecrire_json(path: Path, data):
    path.parent.mkdir(parents=True, exist_ok=True)
    path.write_text(json.dumps(data, indent=1) + "\n", encoding="utf-8")


def main():
    ecrire_json(PUBLIC / "models" / "item" / "lootbox_parachute.json", parachute())
    tex = PUBLIC / "textures" / "item" / "lootbox"
    tex.mkdir(parents=True, exist_ok=True)
    for nom, im in textures_parachute().items():
        im.save(tex / f"{nom}.png")
    teinte = {"type": "minecraft:custom_model_data", "index": 0, "default": 16777215}
    ecrire_json(PUBLIC / "items" / "lootbox_parachute.json",
                {"model": {"type": "minecraft:model", "model": f"{NS}:item/lootbox_parachute", "tints": [teinte]}})
    ecrire_json(PUBLIC / "items" / "lootbox.json", {"model": {"type": "minecraft:special", "base": "minecraft:item/chest",
                                                              "model": {"type": "minecraft:chest", "texture": "minecraft:normal"}}})
    print(f"pack public : parachute ({len(parachute()['elements'])} paves), coffre de Minecraft")
    if GLTF.exists():
        els, texture, haut, _, _, _, _ = coffre()
        ptex = PRIVATE / "textures" / "item" / "lootbox"
        ptex.mkdir(parents=True, exist_ok=True)
        texture.save(ptex / f"{COFFRE}.png")
        ecrire_json(PRIVATE / "models" / "item" / "lootbox_coffre.json",
                    {"textures": {COFFRE: f"{NS}:item/lootbox/{COFFRE}", "particle": f"{NS}:item/lootbox/{COFFRE}"}, "elements": els})
        ecrire_json(PRIVATE / "items" / "lootbox.json", {"model": {"type": "minecraft:model", "model": f"{NS}:item/lootbox_coffre"}})
        print(f"pack prive : coffre violet et blanc ({len(els)} paves, {haut:.1f} pixels de haut)")
    if "--apercu" in sys.argv:
        apercu(Path(sys.argv[sys.argv.index("--apercu") + 1]))
    return 0


def apercu(out: Path):
    """Quatre vues (de face, de trois quarts, de profil, de dos) : le coffre du glTF rendu tel quel (ses triangles, ses
    quaternions) et le coffre converti, qui doivent etre identiques ; puis la Lootbox entiere, au violet du tier 3 et
    au blanc du tier 1."""
    sys.path.insert(0, str(ROOT / "tools"))
    import pose_preview as pp
    from build_armes import rot_x, rot_y, rot_z
    textures = {n: np.array(im) for n, im in textures_parachute().items()}
    textures = {"cote": textures["parachute_cote"], "dessus": textures["parachute_dessus"],
                "dessous": textures["parachute_dessous"], "corde": textures["corde"]}

    def quads_modele(elements, teinte, dy=0.0):
        quads = []
        for el in elements:
            a, b = el["from"], el["to"]
            rot = el.get("rotation")
            for face, f in el["faces"].items():
                tex = textures[f["texture"][1:]]
                if "tintindex" in f:
                    tex = (tex * teinte).astype(np.uint8)
                k = tex.shape[1] / 16
                pts = []
                for pt in pp.FACES[face](a, b):
                    q = np.array(pt, dtype=float)
                    if rot:
                        m = {"x": rot_x, "y": rot_y, "z": rot_z}[rot["axis"]](rot["angle"])
                        o = np.array(rot["origin"], dtype=float)
                        q = o + m @ (q - o)
                    pts.append((q + np.array([-8, dy, -8])) / 16)
                u1, v1, u2, v2 = (x * k for x in f["uv"])
                # FACES donne les coins haut-gauche, haut-droit, bas-gauche, bas-droit : les sommets 0, 3, 1, 2 de
                # Minecraft, qui prennent les uv du coin (i + r) % 4.
                coins = [(u1, v1), (u1, v2), (u2, v2), (u2, v1)]
                r = f.get("rotation", 0) // 90
                quads.append((pts, [coins[(i + r) % 4] for i in (0, 3, 1, 2)], tex))
        return quads

    def rangee(quads, scale, size):
        return np.concatenate([pp.render(quads, yaw, size=size, scale=scale) for yaw in (0, 35, 90, 200)], axis=1)

    rangees = []
    para = parachute()["elements"]
    violet, blanc = np.array([220, 110, 255, 255]) / 255.0, np.array([235, 235, 235, 255]) / 255.0
    coffre_els = []
    if GLTF.exists():
        els, texture, haut, place, nds, noms_mat, k = coffre()
        textures[COFFRE] = np.array(texture)
        coffre_els = els
        brut = []
        for nd in nds:
            R = matrice(nd["q"])
            for P, N, T, mat in nd["prims"]:
                tex = textures[COFFRE]
                th, tw = tex.shape[:2]
                for i in range(0, len(P), 3):
                    pts = [(np.array(tourner_point(place(nd["t"] + R @ P[j]), k)) - np.array([8, 0, 8])) / 16 for j in range(i, i + 3)]
                    uvs = [(T[j][0] * tw, T[j][1] * th) for j in range(i, i + 3)]
                    brut.append((pts + [pts[2]], uvs + [uvs[2]], tex))
        rangees.append(rangee(brut, 200, (420, 260)))
        rangees.append(rangee(quads_modele(els, violet), 200, (420, 260)))
    for teinte in (violet, blanc):
        rangees.append(rangee(quads_modele(coffre_els, teinte) + quads_modele(para, teinte, dy=16), 95, (420, 420)))
    out.parent.mkdir(parents=True, exist_ok=True)
    Image.fromarray(np.concatenate(rangees, axis=0)).save(out)
    print("apercu :", out)


if __name__ == "__main__":
    sys.exit(main())
