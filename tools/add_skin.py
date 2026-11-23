#!/usr/bin/env python3
"""Ajoute au pack le modele articule d'un personnage, habille de son skin Minecraft.

Usage :  python3 tools/add_skin.py <skin.png> <personnage> [--slim] [--prive]

Le skin est un skin Minecraft classique de 64x64 (bras larges, ou fins avec --slim ; sans l'option,
le format est devine d'apres les pixels vides du bras). Le script ecrit, sous assets/crossfight/ :
  textures/item/pose/<personnage>.png          le skin
  models/item/pose/<personnage>_<partie>.json  tete, torse, deux bras, deux jambes (avec la seconde couche)
  items/pose/<personnage>_<partie>.json        les objets que le plugin affiche
Chaque partie est centree sur son articulation : le plugin la fait tourner autour (util/Puppet.java,
poses dans src/main/resources/poses.txt). Apercu sans lancer le jeu : tools/pose_preview.py.

--prive range le tout dans resourcepack-prive/ (jamais envoye sur GitHub) : c'est la place des skins
de personnages sous licence. Ensuite : python3 tools/build_pack.py

Les parties acceptent une teinte (custom_model_data, blanc par defaut) : Puppet.tint en fait une
silhouette coloree. python3 tools/add_skin.py --teinte l'ajoute aux modeles fabriques avant.

python3 tools/add_skin.py --gris fabrique la version grisee de chaque skin (<personnage>_gris), a relancer
apres avoir ajoute ou change un skin : c'est l'apparence d'un joueur fige dans le temps (Seraphina).
"""
import json
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
NS = "crossfight"

# Chaque partie : boite (x1, y1, z1, x2, y2, z2) en pixels autour de l'articulation, dans le repere du
# modele (droite du personnage = +X, haut = +Y, avant = -Z), origine (u, v) dans le skin pour la
# premiere et la seconde couche, epaisseur de la seconde couche.
def parts(slim: bool):
    arm = 3 if slim else 4
    return {
        "head":  ((-4, 0, -4, 4, 8, 4), (0, 0), (32, 0), 0.5),
        "body":  ((-4, -12, -2, 4, 0, 2), (16, 16), (16, 32), 0.25),
        "arm_r": ((-1, -10, -2, -1 + arm, 2, 2), (40, 16), (40, 32), 0.25),
        "arm_l": ((1 - arm, -10, -2, 1, 2, 2), (32, 48), (48, 48), 0.25),
        "leg_r": ((-2, -12, -2, 2, 0, 2), (0, 16), (0, 32), 0.25),
        "leg_l": ((-2, -12, -2, 2, 0, 2), (16, 48), (0, 48), 0.25),
    }


# Le pantin peut etre teint (Puppet.tint : une silhouette d'ombre...) ; blanc par defaut = ses vraies couleurs.
TINT = {"type": "minecraft:custom_model_data", "index": 0, "default": -1}


def tintable(base: Path) -> int:
    """Ajoute la teinte aux modeles deja fabriques (avant qu'add_skin.py ne la mette lui-meme)."""
    n = 0
    for f in sorted((base / "models" / "item" / "pose").glob("*.json")):
        m = json.loads(f.read_text(encoding="utf-8"))
        for el in m.get("elements", []):
            for face in el["faces"].values():
                face["tintindex"] = 0
        f.write_text(json.dumps(m, indent=1) + "\n", encoding="utf-8")
        n += 1
    for f in sorted((base / "items" / "pose").glob("*.json")):
        it = json.loads(f.read_text(encoding="utf-8"))
        it["model"]["tints"] = [TINT]
        f.write_text(json.dumps(it, indent=1) + "\n", encoding="utf-8")
    return n


def grey(base: Path) -> int:
    """Une version grisee de chaque skin, sous le nom <personnage>_gris : le plugin la montre a la place du joueur
    fige dans le temps (Seraphina). Memes modeles, seule l'image change : son niveau de gris, un peu eclairci."""
    folder = base / "textures" / "item" / "pose"
    n = 0
    for tex in sorted(folder.glob("*.png")) if folder.is_dir() else []:
        name = tex.stem
        # Pas les etapes d'une transformation ni les silhouettes : seuls les skins que portent les joueurs.
        if name.endswith("_gris") or name in ("silhouette", "eren_muscles", "eren_squelette", "omori_negatif"):
            continue
        img = Image.open(tex).convert("RGBA")
        px = img.load()
        for y in range(img.height):
            for x in range(img.width):
                r, g, b, a = px[x, y]
                v = round(0.299 * r + 0.587 * g + 0.114 * b)
                v = round(40 + v * 0.72)          # ni noir ni blanc : la pierre grise d'un instant arrete
                px[x, y] = (v, v, min(255, v + 6), a)
        img.save(folder / f"{name}_gris.png")
        for sub in ("models/item/pose", "items/pose"):
            for f in sorted((base / sub).glob(f"{name}_*.json")):
                part = f.stem[len(name) + 1:]
                if part not in ("head", "body", "arm_r", "arm_l", "leg_r", "leg_l"):
                    continue
                text = f.read_text(encoding="utf-8").replace(f"item/pose/{name}_{part}", f"item/pose/{name}_gris_{part}").replace(f'item/pose/{name}"', f'item/pose/{name}_gris"')
                (f.parent / f"{name}_gris_{part}.json").write_text(text, encoding="utf-8")
        n += 1
    return n


def element(box, uv, grow):
    x1, y1, z1, x2, y2, z2 = box
    w, h, d = x2 - x1, y2 - y1, z2 - z1
    u, v = uv
    q = lambda *px: [p / 4 for p in px]  # pixels d'un skin 64x64 -> unites de texture (0..16)
    return {
        "from": [8 + x1 - grow, 8 + y1 - grow, 8 + z1 - grow],
        "to": [8 + x2 + grow, 8 + y2 + grow, 8 + z2 + grow],
        "faces": {
            # Bandeau du skin : cote droit, avant, cote gauche, arriere ; au-dessus : haut puis bas.
            "east":  {"uv": q(u, v + d, u + d, v + d + h), "texture": "#skin", "tintindex": 0},
            "north": {"uv": q(u + d, v + d, u + d + w, v + d + h), "texture": "#skin", "tintindex": 0},
            "west":  {"uv": q(u + d + w, v + d, u + 2 * d + w, v + d + h), "texture": "#skin", "tintindex": 0},
            "south": {"uv": q(u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h), "texture": "#skin", "tintindex": 0},
            "up":    {"uv": q(u + d + w, v + d, u + d, v), "texture": "#skin", "tintindex": 0},
            "down":  {"uv": q(u + d + 2 * w, v, u + d + w, v + d), "texture": "#skin", "tintindex": 0},
        },
    }


def main() -> int:
    if "--teinte" in sys.argv:
        for base in (ROOT / "resourcepack" / "assets" / NS, ROOT / "resourcepack-prive" / "assets" / NS):
            if base.is_dir():
                print(f"{base.parent.parent.relative_to(ROOT)} : {tintable(base)} modeles rendus teintables")
        return 0
    if "--gris" in sys.argv:
        for base in (ROOT / "resourcepack" / "assets" / NS, ROOT / "resourcepack-prive" / "assets" / NS):
            if base.is_dir():
                print(f"{base.parent.parent.relative_to(ROOT)} : {grey(base)} skins grises")
        return 0
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if len(args) != 2:
        print(__doc__)
        return 1
    src, name = Path(args[0]), args[1].lower()
    img = Image.open(src).convert("RGBA")
    if img.size != (64, 64):
        print(f"Le skin doit faire 64x64 pixels (celui-ci : {img.size[0]}x{img.size[1]}).")
        return 1
    # Bras fins : la colonne 54-55 du bras droit est vide.
    slim = "--slim" in sys.argv or all(img.getpixel((x, y))[3] == 0 for x in (54, 55) for y in range(20, 32))
    base = ROOT / ("resourcepack-prive" if "--prive" in sys.argv else "resourcepack") / "assets" / NS
    tex = base / "textures" / "item" / "pose" / f"{name}.png"
    tex.parent.mkdir(parents=True, exist_ok=True)
    img.save(tex)
    for part, (box, uv, uv2, grow) in parts(slim).items():
        model = {"textures": {"skin": f"{NS}:item/pose/{name}", "particle": f"{NS}:item/pose/{name}"},
                 "elements": [element(box, uv, 0), element(box, uv2, grow)]}
        for folder, content in (("models/item/pose", model),
                                ("items/pose", {"model": {"type": "minecraft:model", "model": f"{NS}:item/pose/{name}_{part}", "tints": [TINT]}})):
            f = base / folder / f"{name}_{part}.json"
            f.parent.mkdir(parents=True, exist_ok=True)
            f.write_text(json.dumps(content, indent=1) + "\n", encoding="utf-8")
    print(f"{name} : modele articule ({'bras fins' if slim else 'bras larges'}) dans {base.parent.parent.relative_to(ROOT)}/")
    return 0


if __name__ == "__main__":
    sys.exit(main())
