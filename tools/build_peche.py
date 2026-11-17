#!/usr/bin/env python3
"""Dessine le jeu des cercles de la pêche (quête de Garreg Mach), au milieu de l'écran.

Usage : python3 tools/build_peche.py

Vingt et une images de 96 x 96. Au centre, la cible : trois zones emboîtées (Bien, Très bien, Parfait, de l'extérieur
vers le centre). Autour, un cercle qui se resserre d'image en image, de 46 à 6 pixels de rayon : il faut l'arrêter dans
la zone la plus au centre possible (game/Quests.java dit quelle image vaut quoi). Le plugin les affiche en titre, donc
centrées : une image haute de 24 unités de police fait 96 pixels d'interface, un pixel d'image par pixel d'écran.
Dessiné ici, rien n'est repris d'un jeu. Écrit resourcepack/assets/crossfight/font/peche.json et textures/font/peche_*.png.
"""
import json
from pathlib import Path
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent / "resourcepack" / "assets" / "crossfight"
SIZE, FRAMES = 96, 21
GOOD, GREAT, PERFECT = 30, 20, 12          # rayon exterieur de chaque zone (game/Quests.java a les memes)


def main():
    (ROOT / "textures" / "font").mkdir(parents=True, exist_ok=True)
    for old in (ROOT / "textures" / "font").glob("peche_*.png"):
        old.unlink()
    chars = []
    k = 4                                    # dessine quatre fois plus grand, puis reduit : des bords doux
    for i in range(FRAMES):
        radius = 46 - 2 * i
        big = Image.new("RGBA", (SIZE * k, SIZE * k), (0, 0, 0, 0))
        d = ImageDraw.Draw(big)
        c = SIZE * k / 2
        disc = lambda r, fill: d.ellipse([c - r * k, c - r * k, c + r * k, c + r * k], fill=fill)
        ring = lambda r, color, w: d.ellipse([c - r * k, c - r * k, c + r * k, c + r * k], outline=color, width=w)
        # la cible : trois zones, de plus en plus chaudes vers le centre ; celle ou se trouve le cercle s'allume
        zone = 3 if radius <= PERFECT else 2 if radius <= GREAT else 1 if radius <= GOOD else 0
        disc(GOOD, (70, 200, 120, 150 if zone == 1 else 70))
        disc(GREAT, (0, 0, 0, 0)); disc(GREAT, (250, 210, 70, 170 if zone == 2 else 80))
        disc(PERFECT, (0, 0, 0, 0)); disc(PERFECT, (255, 140, 40, 220 if zone == 3 else 110))
        for r in (GOOD, GREAT, PERFECT):
            ring(r, (255, 255, 255, 190), 3)
        disc(2, (255, 255, 255, 255))
        # le cercle qui se resserre
        ring(radius, (20, 20, 40, 160), 14)
        ring(radius, (255, 255, 255, 255), 8)
        big.resize((SIZE, SIZE), Image.LANCZOS).save(ROOT / "textures" / "font" / f"peche_{i:02d}.png")
        chars.append(chr(0xE200 + i))
    providers = [{"type": "bitmap", "file": f"crossfight:font/peche_{i:02d}.png", "height": 24, "ascent": 9, "chars": [ch]} for i, ch in enumerate(chars)]
    (ROOT / "font" / "peche.json").write_text(json.dumps({"providers": providers}, ensure_ascii=True, indent=1), encoding="utf-8")
    print(len(chars), "images")


if __name__ == "__main__":
    main()
