#!/usr/bin/env python3
"""Fait d'une video une suite d'images du pack, a jouer plein ecran en jeu (Minecraft ne lit pas de video).

Usage :  python3 tools/build_intro.py <video> <nom> [--prive] [--cadre x:y:l:h]
         puis  python3 tools/build_pack.py  et recompiler le plugin

Une police de Minecraft n'accepte pas d'image de plus de 256 pixels : chaque image de la video (594 x 440) est donc
decoupee en six tuiles (trois colonnes, deux rangees), que le plugin ecrit cote a cote sur une meme ligne. Des
caracteres de largeur negative ramenent le curseur en arriere (d'un pixel entre deux tuiles, de toute la largeur pour
passer a la rangee du dessous), et la hauteur de chaque rangee est donnee par sa police. Un caractere ne peut pas
monter plus haut que sa propre hauteur : les tuiles de la rangee du haut sont donc allongees d'une marge transparente.
Les images qui ne changent pas ne sont gardees qu'une fois : le plugin suit une table (dix cases par seconde) qui
dit quelle image montrer a chaque instant.

--cadre : la partie de la video a garder (ffmpeg crop, en pixels de la video) ; sans lui, toute l'image.

Ecrit (dans le pack prive avec --prive : une video sous droits n'est jamais versionnee) :
  assets/crossfight/textures/font/<nom>/i000_0.png ...  les tuiles des images gardees, en niveaux de gris
  assets/crossfight/font/<nom>.json                     la police : U+E000 un fond noir plein ecran, U+E100 et U+E101
                                                        les retours en arriere, puis six caracteres par image (U+E200...)
  assets/crossfight/sounds/ability/<nom>.ogg            le son de la video
  src/main/resources/intro.txt                          la table "<nom> <image a chaque dixieme de seconde...>"
"""
import json
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
FPS, COLS, ROWS, TW, TH = 10, 3, 2, 198, 220
ROW_H = 80                                   # hauteur d'une rangee a l'ecran, en pixels de police
TILE_W = TW * ROW_H // TH                    # largeur d'une tuile a l'ecran (72) ; son avance vaut un de plus


def ascent(row: int) -> int:
    """De combien une rangee monte au-dessus de la ligne du sous-titre, pour que l'image entiere soit centree a l'ecran."""
    return 12 + ROWS * ROW_H // 2 - row * ROW_H


def pad(row: int) -> int:
    """La marge (en pixels d'image) sous les tuiles d'une rangee qui doit monter plus haut que sa propre hauteur."""
    return max(0, ascent(row) - ROW_H) * TH // ROW_H


def main() -> int:
    private = "--prive" in sys.argv
    argv = [a for a in sys.argv if a != "--prive"]
    crop = None
    if "--cadre" in argv:
        i = argv.index("--cadre")
        crop = argv[i + 1]
        del argv[i:i + 2]
    if len(argv) != 3:
        print(__doc__)
        return 1
    src, name = Path(argv[1]), argv[2]
    base = ROOT / ("resourcepack-prive" if private else "resourcepack") / "assets" / "crossfight"
    out = base / "textures" / "font" / name
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("*.png"):
        old.unlink()
    W, H = COLS * TW, ROWS * TH
    with tempfile.TemporaryDirectory() as tmp:
        vf = (f"crop={crop}," if crop else "") + f"fps={FPS},scale={W}:{H}:flags=lanczos"
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(src), "-vf", vf, "-pix_fmt", "gray", f"{tmp}/f%04d.png"], check=True)
        files = sorted(Path(tmp).glob("f*.png"))
        table, kept, last = [], 0, None
        for f in files:
            im = Image.open(f)
            a = np.asarray(im, dtype=np.int16)
            # Une image est gardee si elle differe de la derniere gardee (le grain de la video ne compte pas).
            if last is None or float(np.abs(a - last).mean()) > 0.35:
                for r in range(ROWS):
                    for c in range(COLS):
                        tile = im.crop((c * TW, r * TH, (c + 1) * TW, (r + 1) * TH))
                        if pad(r):
                            # La marge transparente qui laisse ce caractere monter assez haut.
                            tall = Image.new("LA", (TW, TH + pad(r)), (0, 0))
                            tall.paste(tile.convert("LA"), (0, 0))
                            tile = tall
                        tile.save(out / f"i{kept:03d}_{r * COLS + c}.png", optimize=True)
                last = a
                kept += 1
            table.append(kept - 1)
    Image.new("RGBA", (32, 18), (0, 0, 0, 255)).save(out / "noir.png")
    line = COLS * (TILE_W + 1) - (COLS - 1)
    providers = [{"type": "space", "advances": {"": -1, "": -line}},
                 {"type": "bitmap", "file": f"crossfight:font/{name}/noir.png", "ascent": 125, "height": 256, "chars": [""]}]
    for i in range(kept):
        for t in range(COLS * ROWS):
            r = t // COLS
            providers.append({"type": "bitmap", "file": f"crossfight:font/{name}/i{i:03d}_{t}.png", "ascent": ascent(r), "height": max(ROW_H, ascent(r)),
                              "chars": [chr(0xE200 + i * COLS * ROWS + t)]})
    (base / "font").mkdir(parents=True, exist_ok=True)
    (base / "font" / f"{name}.json").write_text(json.dumps({"providers": providers}) + "\n", encoding="utf-8")
    sound = base / "sounds" / "ability" / f"{name}.ogg"
    sound.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", str(src), "-vn", "-map_metadata", "-1", "-ac", "2", "-ar", "44100",
                    "-c:a", "libvorbis", "-q:a", "4", str(sound)], check=True)
    index = ROOT / "src" / "main" / "resources" / "intro.txt"
    lines = [l for l in (index.read_text(encoding="utf-8").splitlines() if index.exists() else []) if l and not l.startswith("#") and l.split()[0] != name]
    lines.append(name + " " + " ".join(str(v) for v in table))
    index.write_text("# Genere par tools/build_intro.py : quelle image montrer a chaque dixieme de seconde\n" + "\n".join(lines) + "\n", encoding="utf-8")
    size = sum(f.stat().st_size for f in out.glob("*.png")) // 1024
    print(f"{name} : {kept} images gardees sur {len(table)} ({COLS * ROWS} tuiles chacune, {size} Ko), {len(table) / FPS:.1f} s")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
