#!/usr/bin/env python3
"""La police du puzzle des piliers (Central Town) : les tableaux du jeu, découpés en neuf pièces.

Usage : python3 tools/build_puzzle_font.py

Écrit resourcepack/assets/crossfight/font/puzzle.json. Aucune image n'est copiée : la police désigne les textures
de tableaux du jeu lui-même (minecraft:painting/...), que le client a déjà. Chaque tableau de 48 x 48 points donne
neuf pièces de 16 x 16 (une lettre chacune) et son image entière (pour le modèle). L'ordre des tableaux est celui de
game/Jigsaw.java (PAINTINGS) : le tableau k occupe les lettres E800 + 16 k (ses neuf pièces, puis l'image entière).
"""
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent / "resourcepack" / "assets" / "crossfight"
PAINTINGS = ["bouquet", "cavebird", "cotan", "endboss", "fern", "owlemons", "sunflowers", "tides", "dennis"]


def main():
    providers = []
    for k, name in enumerate(PAINTINGS):
        base = 0xE800 + 16 * k
        rows = ["".join(chr(base + y * 3 + x) for x in range(3)) for y in range(3)]
        providers.append({"type": "bitmap", "file": f"minecraft:painting/{name}.png", "height": 16, "ascent": 16, "chars": rows})
        providers.append({"type": "bitmap", "file": f"minecraft:painting/{name}.png", "height": 48, "ascent": 48, "chars": [chr(base + 9)]})
    (ROOT / "font").mkdir(parents=True, exist_ok=True)
    (ROOT / "font" / "puzzle.json").write_text(json.dumps({"providers": providers}, ensure_ascii=True, indent=1), encoding="utf-8")
    print("puzzle.json :", len(PAINTINGS), "tableaux")


if __name__ == "__main__":
    main()
