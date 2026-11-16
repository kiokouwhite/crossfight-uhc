#!/usr/bin/env python3
"""La porte noire de l'Espace Blanc : une vraie porte de Minecraft, noire, sans toucher aux portes du jeu.

Usage :  python3 tools/build_porte.py        puis  python3 tools/build_pack.py

Minecraft n'a pas de porte noire. Une porte a une propriete "powered" (alimentee par de la redstone) que le jeu ne
montre pas : alimentee ou non, elle a le meme modele. Le pack lui en donne un autre : une porte en chene noir
*alimentee* prend la texture noire dessinee ici. Le plugin pose sa porte de sortie dans cet etat, sans redstone
(Omori.java) ; les portes en chene noir du jeu, elles, ne changent pas (sauf le temps qu'un bouton ou une plaque les
alimente). Sans le pack, la porte de sortie est une porte en chene noir ordinaire : la plus sombre du jeu.

Ecrit, dans resourcepack/assets/ :
  crossfight/textures/block/porte_noire_haut.png, porte_noire_bas.png    les deux moities, dessinees ici
  crossfight/models/block/porte_noire_<moitie>_<charniere>[_open].json   les huit modeles (ceux d'une porte, autre texture)
  minecraft/blockstates/dark_oak_door.json                               quel modele pour quel etat de la porte
"""
import json
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "resourcepack" / "assets"

BLACK = (9, 9, 12, 255)          # le battant
DEEP = (4, 4, 6, 255)            # le creux des panneaux
EDGE = (24, 24, 30, 255)         # les aretes qui prennent un peu de lumiere
LINE = (16, 16, 21, 255)         # le tour des panneaux
KNOB = (150, 150, 160, 255)      # la poignee
KNOB_SHADE = (78, 78, 86, 255)


def half(top: bool) -> Image.Image:
    """Une moitie de la porte, 16 x 16 : un battant noir, un panneau en creux, la poignee au milieu de la porte."""
    im = Image.new("RGBA", (16, 16), BLACK)
    px = im.load()
    for i in range(16):
        px[0, i] = px[15, i] = EDGE                      # les deux montants
        px[i, 0 if top else 15] = EDGE                   # la traverse du haut (ou du bas)
    # Le panneau : un rectangle en creux, son tour un peu plus clair, un filet de lumiere en haut a gauche.
    x0, x1 = 3, 12
    y0, y1 = (3, 13) if top else (4, 12)
    for x in range(x0, x1 + 1):
        for y in range(y0, y1 + 1):
            px[x, y] = LINE if x in (x0, x1) or y in (y0, y1) else DEEP
    for x in range(x0, x1):
        px[x, y0] = EDGE
    for y in range(y0, y1):
        px[x0, y] = EDGE
    if not top:
        # La poignee, juste sous la jointure des deux moities.
        px[12, 1] = px[13, 1] = KNOB
        px[12, 2] = KNOB
        px[13, 2] = KNOB_SHADE
    return im


def main() -> int:
    textures = ASSETS / "crossfight" / "textures" / "block"
    models = ASSETS / "crossfight" / "models" / "block"
    states = ASSETS / "minecraft" / "blockstates"
    for d in (textures, models, states):
        d.mkdir(parents=True, exist_ok=True)
    half(True).save(textures / "porte_noire_haut.png")
    half(False).save(textures / "porte_noire_bas.png")

    # Les huit modeles d'une porte : la moitie (bas, haut), le cote de la charniere, fermee ou ouverte.
    shapes = [f"{h}_{side}{o}" for h in ("bottom", "top") for side in ("left", "right") for o in ("", "_open")]
    for shape in shapes:
        model = {"parent": f"minecraft:block/door_{shape}",
                 "textures": {"bottom": "crossfight:block/porte_noire_bas", "top": "crossfight:block/porte_noire_haut"}}
        (models / f"porte_noire_{shape}.json").write_text(json.dumps(model, indent=2) + "\n", encoding="utf-8")

    # Les etats d'une porte : elle tourne d'un quart de tour par orientation ; ouverte, le battant pivote d'un quart
    # de tour de plus du cote de sa charniere. Non alimentee : les modeles du jeu. Alimentee : la porte noire.
    turn = {"east": 0, "south": 90, "west": 180, "north": 270}
    variants = {}
    for facing, base in turn.items():
        for h, name in (("lower", "bottom"), ("upper", "top")):
            for hinge in ("left", "right"):
                for is_open in (False, True):
                    y = (base + (0 if not is_open else 90 if hinge == "left" else 270)) % 360
                    shape = f"{name}_{hinge}" + ("_open" if is_open else "")
                    for powered in (False, True):
                        key = f"facing={facing},half={h},hinge={hinge},open={str(is_open).lower()},powered={str(powered).lower()}"
                        v = {"model": f"crossfight:block/porte_noire_{shape}" if powered else f"minecraft:block/dark_oak_door_{shape}"}
                        if y:
                            v["y"] = y
                        variants[key] = v
    (states / "dark_oak_door.json").write_text(json.dumps({"variants": variants}, indent=2, sort_keys=True) + "\n", encoding="utf-8")
    print(f"porte noire : 2 textures, {len(shapes)} modeles, {len(variants)} etats (resourcepack/assets/)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
