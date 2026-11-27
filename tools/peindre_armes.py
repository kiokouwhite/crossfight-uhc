#!/usr/bin/env python3
"""Peint les armes 3D d'après leurs images de référence, à la place des couleurs unies du projet Blockbench.

Usage : python3 tools/peindre_armes.py [nom...] [--essai dossier] [--origine]

Chaque face de chaque pavé reçoit sa case dans une texture, à D texels par pixel du modèle, peinte selon la partie de
l'arme (le nom du pavé), la couleur unie que le pavé avait dans Blockbench (sa matière) et la place du point sur
l'arme : lame grise à gauche et blanche à droite, gouttière dorée, cuir de la poignée, or des ornements, argent des
branches de l'arc, enroulement bleu... Les couleurs sont relevées sur les images de référence.

Le projet d'origine est gardé : resourcepack-prive/modeles/<nom>_origine.bbmodel (créé au premier passage, relu
aux suivants). Le projet peint le remplace sous resourcepack-prive/modeles/<nom>.bbmodel, que lisent
tools/build_armes.py et tools/build_falchion.py ; il s'ouvre dans Blockbench pour retoucher la peinture.
--essai dossier : écrit le projet peint dans ce dossier sans toucher aux modèles.
--origine : remet le projet d'origine à la place du projet peint.
Sans nom : les trois armes (epee_createur, falchion, yumi_fujin).
"""
import base64
import io
import json
import math
import random
import shutil
import sys
import uuid
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
MODELES = ROOT / "resourcepack-prive" / "modeles"
D = 8                          # texels par pixel du modèle


def melange(a, b, t):
    return tuple(int(round(a[k] + (b[k] - a[k]) * t)) for k in range(3))


# --------------------------------------------------------------------------- les faces

# Chaque face : (axe u, sens, axe v, sens) d'après les uv par défaut de Minecraft ; sens +1 : du min au max.
AXES = {
    "north": (0, -1, 1, -1), "south": (0, 1, 1, -1),
    "east": (2, -1, 1, -1), "west": (2, 1, 1, -1),
    "up": (0, 1, 2, 1), "down": (0, 1, 2, -1),
}
PLATS = ("north", "south")


def rotation(el):
    """La rotation du pavé (Blockbench : x, puis y, puis z, autour de son origine), en matrice."""
    rx, ry, rz = (math.radians(v) for v in el.get("rotation", [0, 0, 0]))
    mx = np.array([[1, 0, 0], [0, math.cos(rx), -math.sin(rx)], [0, math.sin(rx), math.cos(rx)]])
    my = np.array([[math.cos(ry), 0, math.sin(ry)], [0, 1, 0], [-math.sin(ry), 0, math.cos(ry)]])
    mz = np.array([[math.cos(rz), -math.sin(rz), 0], [math.sin(rz), math.cos(rz), 0], [0, 0, 1]])
    return mz @ my @ mx, np.array(el.get("origin", [8, 8, 8]), dtype=float)


class Point:
    """Un texel d'une face : où il est sur le pavé (x, y, z), dans le modèle (mx, my, mz), sur la face (i, j)."""

    def __init__(self, el, face, mat, w, h, i, j, m, o):
        a, b = el["from"], el["to"]
        au, su, av, sv = AXES[face]
        lu, lv = b[au] - a[au], b[av] - a[av]
        tu, tv = (i + 0.5) / w, (j + 0.5) / h
        q = [(a[k] + b[k]) / 2 for k in range(3)]
        q[au] = a[au] + (tu if su > 0 else 1 - tu) * lu
        q[av] = a[av] + (tv if sv > 0 else 1 - tv) * lv
        self.nom, self.face, self.mat, self.a, self.b = el["name"], face, mat, a, b
        self.x, self.y, self.z = q
        self.mx, self.my, self.mz = o + m @ (np.array(q) - o)
        self.i, self.j, self.w, self.h = i, j, w, h
        # Un contour d'un texel, seulement sur une face assez grande pour le porter.
        self.bord = (i == 0 or j == 0 or i == w - 1 or j == h - 1) and w > 4 and h > 4
        # Les bords gauche et droit d'une face qui court le long de l'arme (axe y).
        self.cote = (i == 0 or i == w - 1) and w > 9 and av == 1
        # En travers de la face (sa plus petite dimension) : de 0 au bord min à 1 au bord max, et les deux bords.
        if lu <= lv:
            axe, k, n = au, i, w
        else:
            axe, k, n = av, j, h
        self.travers = (q[axe] - a[axe]) / ((b[axe] - a[axe]) or 1)
        self.long_bord = k in (0, n - 1) and n >= 5


# --------------------------------------------------------------------------- l'Épée du Créateur (Byleth)

class EpeeCreateur:
    """Os et cartilage : olive sombre cerné d'encre, lame kaki à gauche et crème à droite, fissures, crête."""
    ENCRE = (64, 59, 29)
    BRUN = (86, 74, 36)
    OLIVE_F = (108, 103, 66)
    OLIVE = (123, 114, 75)
    OMBRE = (156, 153, 114)
    OMBRE_C = (174, 171, 130)
    KAKI = (195, 193, 145)
    CLAIR = (215, 214, 168)
    CREME = (228, 227, 182)
    REFLET = (245, 243, 208)
    CENTRE_X = 8.0

    def __init__(self):
        # Des traits brisés, surtout sur la moitié claire de la lame : courts, en biais, parfois ramifiés.
        r = random.Random(7)
        self.segs = []
        y = 10.5
        while y < 29:
            px, py = self.CENTRE_X + r.uniform(0.05, 0.6), y
            for _ in range(r.randint(2, 4)):
                nx, ny = px + r.uniform(-0.35, 0.45), py + r.uniform(0.3, 0.9)
                self.segs.append(((px, py), (nx, ny)))
                if r.random() < 0.35:
                    self.segs.append(((nx, ny), (nx + r.uniform(0.15, 0.5), ny + r.uniform(-0.3, 0.2))))
                px, py = nx, ny
            y += r.uniform(1.4, 2.6)

    def fissure(self, x, y):
        best = 9.0
        for (ax, ay), (bx, by) in self.segs:
            dx, dy = bx - ax, by - ay
            t = max(0.0, min(1.0, ((x - ax) * dx + (y - ay) * dy) / (dx * dx + dy * dy + 1e-9)))
            best = min(best, math.hypot(x - (ax + t * dx), y - (ay + t * dy)))
        return best

    @staticmethod
    def partie(nom):
        if nom in ("lame1", "lame2", "lame3", "pointe"):
            return "lame"
        if nom.startswith("cran"):
            return "dent"
        if nom in ("arete", "poignee_haut", "poignee_bas"):
            return nom.split("_")[0]
        return "autre"

    def couleur(self, p):
        s, part, plat, x, y = self, self.partie(p.nom), p.face in PLATS, p.x, p.y
        if part == "arete":
            # La gouttière : une ligne plus sombre au milieu de la lame, brune et large à sa base.
            if y < 13.5:
                return s.BRUN if plat else s.ENCRE
            if not plat:
                return s.OLIVE
            return s.OMBRE if abs(x - s.CENTRE_X) < 0.07 else s.REFLET
        if p.mat == 0:                                     # les parties foncées : olive, cernées d'encre
            return s.ENCRE if p.bord else s.OLIVE
        if part == "lame":
            if not plat:
                return s.OLIVE_F if p.face in ("west", "east") else s.CLAIR
            gauche = x < s.CENTRE_X - 0.05
            c = s.KAKI if gauche else s.CREME
            if not gauche and x - s.CENTRE_X < 0.3:
                c = s.REFLET                               # le reflet le long de la gouttière
            if s.fissure(x if not gauche else x + 0.55, y) < 0.07:
                c = s.OMBRE_C if gauche else s.OLIVE       # les fissures, plus discrètes dans l'ombre
            elif not gauche and s.fissure(x - 0.12, y + 0.06) < 0.06:
                c = s.REFLET
            return s.OMBRE if p.cote else c
        if part == "dent":
            return s.OLIVE_F if p.bord else (s.CREME if plat else s.OMBRE_C)
        if part == "poignee":
            if plat:
                return s.KAKI if x < s.CENTRE_X - 0.15 else (s.REFLET if x < s.CENTRE_X + 0.1 else s.CREME)
            return s.OMBRE if p.face == "west" else s.KAKI
        if p.bord:
            return s.OMBRE_C
        return s.CREME if p.face in ("north", "south", "up") else s.CLAIR


# --------------------------------------------------------------------------- la Falchion (Marth)

class Falchion:
    """Lame grise à gauche, blanche à droite, cernée d'un trait ; gouttière dorée en deux tons ; garde et pommeau
    en or pâle ciselé ; poignée de cuir rouge-brun, arrondie par ses ombres. Une texture par couleur unie du
    projet (gouttière, garde, lame, pommeau, poignée) : tools/build_falchion.py en recolore chacune à sa façon
    pour la Sainte-Falchion."""
    TRAIT = (122, 124, 116)
    GRIS_F = (170, 172, 164)
    GRIS = (192, 194, 186)
    BLANC_F = (222, 223, 218)
    BLANC = (239, 240, 236)
    REFLET = (251, 251, 249)
    LIGNE = (128, 122, 92)
    DORE_F = (212, 196, 146)
    DORE = (228, 216, 176)
    DORE_S = (170, 156, 108)
    ENCRE = (92, 74, 36)
    OR_S = (140, 120, 72)
    OR_O = (176, 156, 102)
    OR = (206, 186, 126)
    OR_C = (228, 212, 162)
    OR_R = (242, 232, 198)
    CUIR_E = (70, 34, 20)
    CUIR_S = (106, 56, 40)
    CUIR = (142, 72, 50)
    CUIR_M = (162, 94, 70)
    CUIR_C = (188, 130, 108)
    HAUT_GOUTTIERE = 18.6       # là où la gouttière s'arrête, la lame montre sa crête

    # La couleur unie de chaque pavé dans le projet : 0 la gouttière, 2 la lame, 4 la poignée, 1 et 3 l'or.
    MATIERES = {0: "gouttiere", 2: "lame", 4: "poignee"}

    def couleur(self, p):
        n, f = self.MATIERES.get(p.mat, "or"), p.face
        if n == "gouttiere":
            if f in PLATS:
                if p.i in (0, p.w - 1):
                    return self.DORE_S                   # le bord de la gouttière, à peine plus sombre
                return self.DORE_F if p.mx < 8.0 - 0.01 else self.DORE
            return self.DORE if f == "up" else self.DORE_S
        if n == "lame":
            gauche = p.mx < 8.0 - 0.01
            if f in PLATS:
                if p.cote or (p.bord and p.w <= 9):
                    return self.TRAIT
                large = p.b[0] - p.a[0] > 1.5           # la lame elle-même (pas ses épaules ni sa pointe)
                if large and p.my > self.HAUT_GOUTTIERE - 0.05 and abs(p.mx - 8.0) < 0.07:
                    return self.LIGNE                    # la crête, au-dessus de la gouttière
                if gauche:
                    return self.GRIS_F if p.x - p.a[0] < 0.32 else self.GRIS
                if p.mx < 8.55:
                    return self.REFLET                   # le reflet le long de la gouttière
                return self.BLANC_F if p.b[0] - p.x < 0.32 else self.BLANC
            if f == "west":
                return self.GRIS_F
            if f == "east":
                return self.BLANC_F
            return self.GRIS if gauche else self.BLANC_F
        if n == "poignee":
            t = p.travers
            if f in PLATS:
                return (self.CUIR_S if t < 0.14 else self.CUIR if t < 0.42 else self.CUIR_C if t < 0.64
                        else self.CUIR_M if t < 0.86 else self.CUIR_S)
            if f == "east":
                return self.CUIR_S if p.long_bord else self.CUIR_M
            if f == "west":
                return self.CUIR_S if p.long_bord else self.CUIR
            return self.CUIR_S
        # L'or : garde, ailes, pointes, ergots, œil, bagues, pommeau.
        if p.bord:
            return self.ENCRE
        if f == "up":
            return self.OR_C
        if f == "down":
            return self.OR_S
        if f in ("east", "west"):
            return self.OR_O
        if p.j == 1 and p.h > 5:
            return self.OR_R                             # un liseré clair sous le bord du haut
        if p.nom == "aile_g" and p.h >= 7 and p.j == p.h // 2 + 1:
            return self.OR_O                             # une rainure au milieu de l'aile
        return self.OR


# --------------------------------------------------------------------------- le Yumi Fujin (Takumi)

class YumiFujin:
    """Branches d'argent blanc, cernées, un reflet sur le dos ; têtes d'oiseau, ailes et anneaux en or ;
    poignée enroulée de bleu ; la corde reste cyan (tools/build_armes.py la remplace par sa corde qui brille).

    Les pièces de la poignée (ornements, embouts, poignée, aile) se chevauchent dans les mêmes plans : elles sont
    peintes d'après leur place dans le modèle, pas d'après leur face, pour qu'au même endroit elles aient la même
    couleur (sinon le jeu les ferait scintiller l'une à travers l'autre)."""
    ARGENT = {"encre": (60, 62, 72), "sombre": (128, 130, 140), "ombre": (186, 188, 196),
              "base": (226, 228, 232), "clair": (242, 243, 245), "reflet": (253, 253, 253)}
    OR = {"encre": (88, 62, 22), "sombre": (156, 124, 78), "ombre": (190, 156, 106),
          "base": (218, 188, 138), "clair": (234, 214, 168), "reflet": (248, 236, 198)}
    BLEU = {"encre": (22, 28, 58), "sombre": (40, 50, 96), "ombre": (58, 74, 126),
            "base": (74, 94, 152), "clair": (106, 126, 180), "reflet": (142, 160, 208)}
    CORDE = (0, 241, 255)
    GRAPPE = ("ornement", "embout_haut", "embout_bas", "poignee", "aile1")

    def __init__(self, projet):
        # L'étendue en z de chaque pièce de la poignée (toutes ses copies ensemble), dans le modèle.
        self.etendue = {}
        for el in projet["elements"]:
            if el["name"] in self.GRAPPE:
                m, o = rotation(el)
                zs = [(o + m @ (np.array([x, y, z], dtype=float) - o))[2] for x in (el["from"][0], el["to"][0])
                      for y in (el["from"][1], el["to"][1]) for z in (el["from"][2], el["to"][2])]
                lo, hi = self.etendue.get(el["name"], (9e9, -9e9))
                self.etendue[el["name"]] = (min(lo, min(zs)), max(hi, max(zs)))

    def couleur(self, p):
        if p.mat == 7 or p.nom == "corde":
            return self.CORDE
        P = self.BLEU if p.mat == 3 else self.ARGENT if p.mat in (4, 5) else self.OR
        f = p.face
        grappe = p.nom in self.GRAPPE
        if f in ("up", "down"):
            return P["ombre"] if p.bord and not grappe else P["base"]
        if grappe:
            if p.mat == 3:
                # L'enroulement : des bandes en biais, plus sombres, autour de la poignée.
                autour = p.mz if f in ("east", "west") else p.mx
                if ((p.my + 0.8 * autour) / 0.5) % 1 < 0.3:
                    return P["sombre"]
            if f in ("east", "west"):
                lo, hi = self.etendue[p.nom]
                t = (p.mz - lo) / ((hi - lo) or 1)
            else:
                return P["clair"] if f == "north" else P["ombre"]
        else:
            t = p.travers
            if p.long_bord:                              # le contour : plus sombre côté corde
                return P["ombre"] if t < 0.5 else P["sombre"]
            if f not in ("east", "west"):
                return P["clair"] if f == "north" else P["ombre"]
        # Les flancs : le reflet sur le dos de l'arc, l'ombre côté corde.
        return P["reflet"] if t < 0.22 else P["clair"] if t < 0.45 else P["base"] if t < 0.78 else P["ombre"]


PEINTRES = {
    "epee_createur": {"peintre": EpeeCreateur, "par_matiere": False},
    "falchion": {"peintre": Falchion, "par_matiere": True},
    "yumi_fujin": {"peintre": YumiFujin, "par_matiere": False, "projet": True},
}


# --------------------------------------------------------------------------- la peinture et les cases

def peindre(projet: dict, peintre, par_matiere: bool):
    """Peint chaque face, range les cases (un texel de marge, recopié du bord) dans une texture par matière ou une
    seule, toutes de même taille ; refait les uv des faces. Renvoie les images."""
    cases = []
    for el in projet["elements"]:
        m, o = rotation(el)
        a, b = el["from"], el["to"]
        for face, f in el["faces"].items():
            if f.get("texture") is None or f.get("texture") is False:
                continue
            mat = int(f["texture"])
            au, _, av, _ = AXES[face]
            w, h = max(1, round((b[au] - a[au]) * D)), max(1, round((b[av] - a[av]) * D))
            px = np.zeros((h, w, 3), dtype=np.uint8)
            for j in range(h):
                for i in range(w):
                    px[j, i] = peintre.couleur(Point(el, face, mat, w, h, i, j, m, o))
            cases.append((mat if par_matiere else 0, f, np.pad(px, ((1, 1), (1, 1), (0, 0)), mode="edge")))
    groupes = sorted({g for g, _, _ in cases})
    cases.sort(key=lambda c: -c[2].shape[0])
    for taille in (64, 128, 256, 512, 1024, 2048):
        places, ok = {}, True
        for g in groupes:
            x0 = y0 = rang = 0
            for k, (gg, _, px) in enumerate(cases):
                if gg != g:
                    continue
                h, w = px.shape[:2]
                if x0 + w > taille:
                    x0, y0, rang = 0, y0 + rang, 0
                if y0 + h > taille or w > taille:
                    ok = False
                    break
                places[k] = (x0, y0)
                x0 += w
                rang = max(rang, h)
            if not ok:
                break
        if ok:
            break
    else:
        raise SystemExit("trop de faces pour une texture de 2048")
    atlas = {g: np.zeros((taille, taille, 4), dtype=np.uint8) for g in groupes}
    for k, (g, f, px) in enumerate(cases):
        x0, y0 = places[k]
        h, w = px.shape[:2]
        atlas[g][y0:y0 + h, x0:x0 + w, :3] = px
        atlas[g][y0:y0 + h, x0:x0 + w, 3] = 255
        f["uv"] = [x0 + 1, y0 + 1, x0 + w - 1, y0 + h - 1]
        f["texture"] = groupes.index(g)
        f.pop("rotation", None)
    return [Image.fromarray(atlas[g]) for g in groupes], groupes, taille


def peindre_projet(nom: str, src: Path, dst: Path):
    cfg = PEINTRES[nom]
    projet = json.loads(src.read_text(encoding="utf-8"))
    anciennes = projet.get("textures", [])
    peintre = cfg["peintre"](projet) if cfg.get("projet") else cfg["peintre"]()
    images, groupes, taille = peindre(projet, peintre, cfg["par_matiere"])
    projet["resolution"] = {"width": taille, "height": taille}
    textures = []
    for k, (im, g) in enumerate(zip(images, groupes)):
        buf = io.BytesIO()
        im.save(buf, "PNG")
        base = dict(anciennes[g] if g < len(anciennes) else anciennes[0])
        base.update(name=f"{nom}_peinte_{k}.png", id=str(k), path="", width=taille, height=taille,
                    uv_width=taille, uv_height=taille, particle=k == 0, uuid=str(uuid.uuid4()),
                    source="data:image/png;base64," + base64.b64encode(buf.getvalue()).decode())
        textures.append(base)
    projet["textures"] = textures
    dst.write_text(json.dumps(projet), encoding="utf-8")
    print(f"{nom} : {len(images)} texture(s) de {taille} x {taille} -> {dst}")
    return images


def main() -> int:
    args = sys.argv[1:]
    essai = None
    if "--essai" in args:
        k = args.index("--essai")
        essai = Path(args[k + 1])
        del args[k:k + 2]
    retour = "--origine" in args
    noms = [a for a in args if not a.startswith("--")] or list(PEINTRES)
    for nom in noms:
        if nom not in PEINTRES:
            print(f"arme inconnue : {nom} (connues : {', '.join(PEINTRES)})")
            return 1
        actuel, origine = MODELES / f"{nom}.bbmodel", MODELES / f"{nom}_origine.bbmodel"
        if retour:
            if origine.exists():
                shutil.copyfile(origine, actuel)
                print(f"{nom} : projet d'origine remis")
            continue
        if not origine.exists():
            if not actuel.exists():
                print(f"{nom} : pas de projet dans {MODELES.relative_to(ROOT)}")
                continue
            if essai is None:
                shutil.copyfile(actuel, origine)
                print(f"{nom} : projet d'origine gardé sous {origine.relative_to(ROOT)}")
        src = origine if origine.exists() else actuel
        if essai is not None:
            essai.mkdir(parents=True, exist_ok=True)
            images = peindre_projet(nom, src, essai / f"{nom}.bbmodel")
            for k, im in enumerate(images):
                im.save(essai / f"{nom}_texture_{k}.png")
        else:
            peindre_projet(nom, src, actuel)
    return 0


if __name__ == "__main__":
    sys.exit(main())
