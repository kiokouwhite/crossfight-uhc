#!/usr/bin/env python3
"""Habille les tableaux d'énigmes des piliers (Central Town) avec le pack d'interface acheté à Franuka (pack privé seulement).

Usage : python3 tools/build_enigmes_franuka.py

Écrit, dans resourcepack-prive/assets/crossfight/ : font/enigme.json et textures/font/enigme_*.png :
  - une fenêtre lavande par taille de tableau (game/Panel.java : WINDOWS), à 32 points par bloc ;
  - une case (celle du pack, ramenée au blanc : le plugin la teinte à la couleur voulue) ;
  - un panneau clair (le fond pêche du pack), pour les zones où l'on pose les choses ;
  - un curseur (la main du pack), trois orbes (la source et les lampes du circuit), des bandeaux de titre.
Sans cette police dans le pack, le plugin dessine les tableaux en rectangles bleu nuit.
Sans les fichiers du pack de Franuka, ce script ne fait rien.
"""
import json
import sys
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_menu_franuka import ONE, ROOT, nine, sprite, wide  # noqa: E402

OUT = ROOT / "resourcepack-prive" / "assets" / "crossfight" / "textures" / "font"
# les tailles des tableaux, en blocs (les memes, dans le meme ordre, que Panel.WINDOWS)
SIZES = [(5.0, 3.2), (3.6, 3.6), (4.4, 3.5), (5.0, 3.7), (5.2, 3.6)]
DENSITY = 32
BANNERS = [64, 96, 128, 160, 192]


def white(im):
    """Ramene une image au blanc : sa couleur la plus claire devient blanche, les autres gardent leur ombre."""
    px = im.load()
    r, g, b, _ = px[im.width // 2, im.height // 2]            # le fond de la case : il devient blanc
    ref = 0.3 * r + 0.59 * g + 0.11 * b
    out = Image.new("RGBA", im.size, (0, 0, 0, 0))
    for y in range(im.height):
        for x in range(im.width):
            r, g, b, a = px[x, y]
            if a == 0:
                continue
            v = min(255, round((0.3 * r + 0.59 * g + 0.11 * b) * 255 / ref))
            out.putpixel((x, y), (v, v, v, a))
    return out


def main():
    if not ONE.is_dir():
        print("pack de Franuka absent (resourcepack-prive/ui-franuka/) : rien a faire")
        return 0
    OUT.mkdir(parents=True, exist_ok=True)
    providers = []
    for k, (w, h) in enumerate(SIZES):
        pw, ph = round(w * DENSITY), round(h * DENSITY)
        # la fenetre du pack a six pixels vides autour d'elle : on la compose plus grande, et on les rogne
        nine(sprite("Background boxes", "BGbox_01B"), pw + 12, ph + 12).crop((6, 6, pw + 6, ph + 6)).save(OUT / f"enigme_fenetre_{k}.png")
        providers.append({"type": "bitmap", "file": f"crossfight:font/enigme_fenetre_{k}.png", "height": ph, "ascent": ph, "chars": [chr(0xE600 + k)]})
    white(sprite("Item slots", "Slot_01_Empty")).save(OUT / "enigme_case.png")
    providers.append({"type": "bitmap", "file": "crossfight:font/enigme_case.png", "height": 16, "ascent": 16, "chars": [""]})
    # le panneau clair : le fond du cadre peche, sans ses marges vides
    nine(sprite("Background boxes", "BGbox_01A"), 64 + 12, 64 + 12).crop((6, 6, 70, 70)).save(OUT / "enigme_panneau.png")
    providers.append({"type": "bitmap", "file": "crossfight:font/enigme_panneau.png", "height": 64, "ascent": 64, "chars": [""]})
    # le curseur (une main qui pointe), les orbes du circuit (source, lampe eteinte, lampe allumee)
    sprite("Cursors", "Hand01_Up").save(OUT / "enigme_curseur.png")
    providers.append({"type": "bitmap", "file": "crossfight:font/enigme_curseur.png", "height": 16, "ascent": 16, "chars": ["\ue612"]})
    for k, name in enumerate(("Orb_Frame_MP", "Orb_Frame_Empty", "Orb_Frame_Energy")):
        sprite("Resource orbs", name).save(OUT / f"enigme_orbe_{k}.png")
        providers.append({"type": "bitmap", "file": f"crossfight:font/enigme_orbe_{k}.png", "height": 48, "ascent": 48, "chars": [chr(0xE613 + k)]})
    # les bandeaux des titres, a cinq largeurs (Panel.BANNERS)
    for k, w in enumerate(BANNERS):
        wide(sprite("Title banners", "BannerSmall_04A"), w).save(OUT / f"enigme_bandeau_{k}.png")
        providers.append({"type": "bitmap", "file": f"crossfight:font/enigme_bandeau_{k}.png", "height": 16, "ascent": 16, "chars": [chr(0xE620 + k)]})
    fonts = OUT.parent.parent / "font"
    fonts.mkdir(parents=True, exist_ok=True)
    (fonts / "enigme.json").write_text(json.dumps({"providers": providers}, ensure_ascii=True, indent=1), encoding="utf-8")
    print("enigme.json :", len(providers), "images")
    return 0


if __name__ == "__main__":
    sys.exit(main())
