#!/usr/bin/env python3
"""Fait signer des skins par MineSkin et les inscrit dans src/main/resources/skins.txt.

Usage :  python3 tools/sign_skins.py <dossier>      le dossier contient <personnage>.png (64x64)

Minecraft n'affiche sur un joueur qu'un skin signe par Mojang. MineSkin (api.mineskin.org) envoie
l'image a Mojang et rend la reference signee (valeur + signature), que le plugin place dans le profil
du joueur (util/Skins.java). Les skins sont envoyes en "non liste" : ils n'apparaissent pas dans la
galerie publique de MineSkin. Sans cle d'API, le service impose environ 6 s entre deux envois.
Un personnage deja present dans skins.txt est remplace. Ensuite : recompiler le plugin.
"""
import json
import subprocess
import sys
import time
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
TABLE = ROOT / "src" / "main" / "resources" / "skins.txt"


def main() -> int:
    if len(sys.argv) != 2 or not Path(sys.argv[1]).is_dir():
        print(__doc__)
        return 1
    head, rows = [], {}
    if TABLE.exists():
        for line in TABLE.read_text(encoding="utf-8").splitlines():
            if line.startswith("#"):
                head.append(line)
            elif line.strip():
                rows[line.split()[0]] = line
    for f in sorted(Path(sys.argv[1]).glob("*.png")):
        img = Image.open(f).convert("RGBA")
        if img.size != (64, 64):
            print(f"{f.name} : ignore, le skin doit faire 64x64")
            continue
        slim = all(img.getpixel((x, y))[3] == 0 for x in (54, 55) for y in range(20, 32))
        for attempt in range(5):
            r = subprocess.run(["curl", "-sS", "-m", "90", "-A", "skin-import/1.0", "-X", "POST", "https://api.mineskin.org/v2/generate",
                                "-F", f"file=@{f};type=image/png", "-F", "variant=" + ("slim" if slim else "classic"),
                                "-F", "visibility=unlisted"], capture_output=True, text=True)
            try:
                d = json.loads(r.stdout)
            except ValueError:
                d = {"success": False, "error": (r.stdout or r.stderr)[:200]}
            wait = (d.get("rateLimit", {}).get("next", {}).get("relative") or 6000) / 1000 + 0.7
            if d.get("success"):
                t = d["skin"]["texture"]["data"]
                rows[f.stem.lower()] = f"{f.stem.lower()} {t['value']} {t['signature']}"
                print(f"{f.stem.lower()} : signe ({d['skin']['variant']})", flush=True)
                time.sleep(wait)
                break
            print(f"{f.stem} : echec ({json.dumps(d)[:200]}), nouvel essai", flush=True)
            time.sleep(max(wait, 8))
    TABLE.write_text("\n".join(head + [rows[k] for k in sorted(rows)]) + "\n", encoding="utf-8")
    print(f"{len(rows)} skins dans {TABLE.relative_to(ROOT)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
