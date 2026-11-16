#!/usr/bin/env python3
"""Le panneau "OMORI did not succumb." des changements de phase d'Omori, tire d'une video du jeu.

Usage :  python3 tools/build_panneau.py <video>        puis  python3 tools/build_pack.py

La video montre le panneau du jeu (un cadre noir borde de blanc) ou la phrase s'ecrit lettre a lettre. Chaque etat du
texte devient une image, coupee en trois tuiles (une police n'accepte pas d'image de plus de 256 pixels) rangees dans
une police du pack complet (images du jeu : jamais versionnees). Le plugin les ecrit dans la barre d'action, au-dessus
de la barre d'objets (game/Omori.java), au rythme de la table ecrite dans src/main/resources/intro.txt (ligne
"panneau_omori" : l'image a montrer a chaque tick). Les images d'avant l'apparition du panneau sont ecartees.
"""
import json
import subprocess
import sys
import tempfile
from pathlib import Path

import numpy as np
from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
NAME, TILES, SHOWN = "panneau_omori", 3, 48      # trois tuiles ; 48 pixels d'interface de haut a l'ecran


def main() -> int:
    if len(sys.argv) != 2:
        print(__doc__)
        return 1
    base = ROOT / "resourcepack-prive" / "assets" / "crossfight"
    out = base / "textures" / "font" / NAME
    out.mkdir(parents=True, exist_ok=True)
    for old in out.glob("*.png"):
        old.unlink()
    with tempfile.TemporaryDirectory() as tmp:
        subprocess.run(["ffmpeg", "-hide_banner", "-loglevel", "error", "-y", "-i", sys.argv[1], "-vf", "fps=20", f"{tmp}/f%04d.png"], check=True)
        table, kept, last, width = [], 0, None, 0
        for f in sorted(Path(tmp).glob("f*.png")):
            im = Image.open(f).convert("RGB")
            a = np.asarray(im, dtype=np.int16)
            # Le panneau est la quand son bord blanc l'est. On le cherche (la premiere et la derniere ligne claires, la
            # premiere et la derniere colonne claires) et l'on ne garde que lui, sans la marge de la video.
            rows, cols = np.where(a.mean(axis=(1, 2)) > 200)[0], np.where(a.mean(axis=(0, 2)) > 200)[0]
            if len(rows) < 4 or len(cols) < 4 or rows[-1] - rows[0] < im.height // 2 or cols[-1] - cols[0] < im.width // 2:
                continue
            im = im.crop((int(cols[0]), int(rows[0]), int(cols[-1]) + 1, int(rows[-1]) + 1))
            a = np.asarray(im, dtype=np.int16)
            if last is not None and a.shape != last.shape:
                continue
            if last is None or float(np.abs(a - last).mean()) > 0.12:
                width = -(-im.width // TILES)
                wide = Image.new("RGB", (width * TILES, im.height), (0, 0, 0))
                wide.paste(im, (0, 0))
                for t in range(TILES):
                    wide.crop((t * width, 0, (t + 1) * width, im.height)).save(out / f"i{kept:03d}_{t}.png", optimize=True)
                last = a
                kept += 1
            table.append(kept - 1)
    # La fin de la video ne change plus : la table s'arrete a la derniere image nouvelle.
    while len(table) > 1 and table[-1] == table[-2]:
        table.pop()
    providers = [{"type": "space", "advances": {"": -1}}]
    for i in range(kept):
        for t in range(TILES):
            providers.append({"type": "bitmap", "file": f"crossfight:font/{NAME}/i{i:03d}_{t}.png", "ascent": SHOWN, "height": SHOWN, "chars": [chr(0xE200 + i * TILES + t)]})
    (base / "font").mkdir(parents=True, exist_ok=True)
    (base / "font" / f"{NAME}.json").write_text(json.dumps({"providers": providers}) + "\n", encoding="utf-8")
    index = ROOT / "src" / "main" / "resources" / "intro.txt"
    lines = [l for l in index.read_text(encoding="utf-8").splitlines() if l and l.split()[0] != NAME]
    lines.append(NAME + " " + " ".join(str(v) for v in table))
    index.write_text("\n".join(lines) + "\n", encoding="utf-8")
    print(f"{NAME} : {kept} images ({TILES} tuiles de {width} px chacune), ecriture en {len(table)} ticks")
    return 0


if __name__ == "__main__":
    sys.exit(main())
