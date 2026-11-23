#!/usr/bin/env python3
"""Les tetes des personnages, a cote des noms : dans le tableau de droite (les allies, les cibles) et devant le
pseudo des joueurs (au-dessus de leur tete, dans la liste des joueurs).

Usage : python3 tools/build_tetes.py [--apercu image.png]

Les portraits sont fournis (resourcepack-prive/tetes/<personnage>.png ou .webp, carres : des dessins de personnages
sous licence, jamais versionnes). Chacun devient un petit carre borde de sombre, et un glyphe de la police
"crossfight:tetes" (U+E200...), haut comme une ligne de texte. tools/build_pack.py les declare
("fonttete.<personnage>.<point de code>") et le plugin les ecrit devant les noms (util/Heads.java) pour les joueurs
qui ont charge le pack complet. Puis : python3 tools/build_pack.py
"""
import json
import sys
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "resourcepack-prive" / "tetes"
ASSETS = ROOT / "resourcepack-prive" / "assets" / "crossfight"
SIZE = 36            # l'image du glyphe : quatre fois la hauteur d'une ligne, pour rester nette sur un grand ecran
BORDER = (28, 22, 34, 255)


def head(src: Path) -> Image.Image:
    im = Image.open(src).convert("RGBA")
    side = min(im.size)
    im = im.crop(((im.width - side) // 2, (im.height - side) // 2, (im.width + side) // 2, (im.height + side) // 2))
    out = Image.new("RGBA", (SIZE, SIZE), BORDER)
    out.paste(im.resize((SIZE - 4, SIZE - 4), Image.LANCZOS).convert("RGB"), (2, 2))
    return out


def main() -> int:
    sources = sorted(f for f in SRC.glob("*") if f.suffix.lower() in (".png", ".webp", ".jpg", ".jpeg")) if SRC.is_dir() else []
    if not sources:
        print("aucun portrait dans resourcepack-prive/tetes/ : rien a faire")
        return 0
    tex = ASSETS / "textures" / "font" / "tete"
    tex.mkdir(parents=True, exist_ok=True)
    for old in tex.glob("*.png"):
        old.unlink()
    providers, heads = [], []
    for n, src in enumerate(sources):
        im = head(src)
        im.save(tex / f"{src.stem.lower()}.png")
        heads.append(im)
        providers.append({"type": "bitmap", "file": f"crossfight:font/tete/{src.stem.lower()}.png", "height": 9, "ascent": 8, "chars": [chr(0xE200 + n)]})
    (ASSETS / "font").mkdir(parents=True, exist_ok=True)
    (ASSETS / "font" / "tetes.json").write_text(json.dumps({"providers": providers}, ensure_ascii=False), encoding="utf-8")
    print(f"{len(providers)} tetes dans resourcepack-prive/ (police crossfight:tetes) : " + ", ".join(s.stem.lower() for s in sources))
    if "--apercu" in sys.argv:
        sheet = Image.new("RGBA", (len(heads) * (SIZE + 4) + 4, SIZE + 8), (60, 70, 90, 255))
        for i, im in enumerate(heads):
            sheet.alpha_composite(im, (4 + i * (SIZE + 4), 4))
        sheet.resize((sheet.width * 4, sheet.height * 4), Image.NEAREST).save(sys.argv[sys.argv.index("--apercu") + 1])
    return 0


if __name__ == "__main__":
    sys.exit(main())
