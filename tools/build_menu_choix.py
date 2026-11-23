#!/usr/bin/env python3
"""Habille les menus de choix (ChoiceMenu : Abnegation, Strategie, Sus a la Cible, la voie de la Yato...) avec le
pack d'interface achete a Franuka (pack prive seulement).

Usage : python3 tools/build_menu_choix.py [--apercu image.png]

Comme les menus d'Anna (tools/build_menu_franuka.py, dont ce script reprend les outils) : une image de police qui
recouvre le coffre, posee par le titre du menu. La fenetre lavande deborde de dix pixels autour du coffre ; en haut,
la place du titre (le plugin l'ecrit par-dessus, dans la couleur du pack) ; dessous, les cases des choix ; en bas,
l'inventaire du joueur.

Une image par forme de menu, dans la police font/choix.json :
  - un menu d'une rangee de un a cinq choix (U+E010 a U+E014) : seules leurs cases sont dessinees, centrees,
    une case sur deux, la ou ChoiceMenu pose ses objets ;
  - un menu de une a six rangees pleines (U+E020 a U+E025).
U+E100 recule de 18 pixels (l'image part du bord de la fenetre) ; U+E101 revient au debut du titre apres l'image.
Sans les fichiers du pack d'interface, ce script ne fait rien : les menus gardent le coffre de Minecraft.
"""
import json
import sys
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_menu_franuka as mf

ROOT = Path(__file__).resolve().parent.parent
ASSETS = ROOT / "resourcepack-prive" / "assets" / "crossfight"
W, M = 176, mf.MARGIN


def height(rows):
    """La hauteur d'un coffre de tant de rangees, comme Minecraft le dessine."""
    return 114 + 18 * rows


def menu(rows, choices=None):
    """Un menu : choices cases centrees sur une rangee (une case sur deux), ou toutes les cases de rows rangees."""
    slot, mine = mf.sprite("Item slots", "Slot_01_Empty"), mf.sprite("Item slots", "Slot_02_Empty")
    h = height(rows)
    im = Image.new("RGBA", (W, h), (0, 0, 0, 0))
    # Les cases des choix, sous la place du titre.
    if choices:
        start = (9 - (choices * 2 - 1)) // 2
        cols = [(start + 2 * k, 0) for k in range(choices)]
    else:
        cols = [(c, r) for r in range(rows) for c in range(9)]
    for c, r in cols:
        im.alpha_composite(slot, (8 + 18 * c, 18 + 18 * r))
    # L'inventaire du joueur : trois rangees, puis sa barre.
    top = 32 + 18 * rows
    for c in range(9):
        for y in (top, top + 18, top + 36, top + 58):
            im.alpha_composite(mine, (8 + 18 * c, y))
    out = mf.nine(mf.sprite("Background boxes", "BGbox_01B"), W + 2 * M, h + 2 * M)
    out.alpha_composite(im, (M, M))
    return out


# La roue des menus a trois choix (ChoiceMenu.openWheel) : son centre est celui de la case du milieu d'un coffre
# de cinq rangees, ses trois parts sont celles de ChoiceMenu.wheelSector (en haut, en bas a gauche, en bas a droite).
WHEEL_ROWS, RADIUS = 5, 44
NAVY, CREAM = (62, 66, 110, 255), (255, 240, 220, 255)


def wheel(icons, tints):
    """Un rond coupe en trois parts, chacune a sa teinte et avec son icone ; dessous, l'inventaire du joueur."""
    import math
    mine = mf.sprite("Item slots", "Slot_02_Empty")
    h = height(WHEEL_ROWS)
    im = Image.new("RGBA", (W, h), (0, 0, 0, 0))
    cx, cy = 8 + 4 * 18 + 9, 18 + 2 * 18 + 9
    px = im.load()
    for y in range(cy - RADIUS - 2, cy + RADIUS + 3):
        for x in range(cx - RADIUS - 2, cx + RADIUS + 3):
            d = math.hypot(x - cx + 0.5, y - cy + 0.5)
            if d > RADIUS + 2:
                continue
            a = math.degrees(math.atan2(-(y - cy + 0.5), x - cx + 0.5)) % 360
            k = 0 if 30 < a < 150 else 1 if 150 <= a < 270 else 2
            # Le bord du rond et les trois rayons qui separent les parts, dans le bleu nuit du pack.
            edge = d > RADIUS or min(abs((a - b + 180) % 360 - 180) for b in (30, 150, 270)) * math.pi / 180 * d < 1.1
            shade = tints[k] if d < RADIUS - 3 else tuple(max(0, c - 28) for c in tints[k][:3]) + (255,)
            px[x, y] = NAVY if edge else shade
    # Le moyeu, au centre.
    for y in range(cy - 6, cy + 6):
        for x in range(cx - 6, cx + 6):
            d = math.hypot(x - cx + 0.5, y - cy + 0.5)
            if d <= 5.5:
                px[x, y] = NAVY if d > 3.6 else CREAM
    # L'icone de chaque part, a mi-rayon sur sa bissectrice.
    for k, a in enumerate((90, 210, 330)):
        ic = icons[k]
        x = cx + 25 * math.cos(math.radians(a)) - ic.width / 2
        y = cy - 25 * math.sin(math.radians(a)) - ic.height / 2
        im.alpha_composite(ic, (round(x), round(y)))
    top = 32 + 18 * WHEEL_ROWS
    for c in range(9):
        for y in (top, top + 18, top + 36, top + 58):
            im.alpha_composite(mine, (8 + 18 * c, y))
    out = mf.nine(mf.sprite("Background boxes", "BGbox_01B"), W + 2 * M, h + 2 * M)
    out.alpha_composite(im, (M, M))
    return out


def main():
    if not mf.ONE.is_dir():
        print("pack de Franuka absent (resourcepack-prive/ui-franuka/) : rien a faire, les menus gardent le coffre de Minecraft")
        return 0
    tex = ASSETS / "textures" / "font"
    tex.mkdir(parents=True, exist_ok=True)
    shapes = [(f"menu_choix_{n}", 0xE010 + n - 1, menu(1, n)) for n in range(1, 6)]
    shapes += [(f"menu_choix_r{r}", 0xE020 + r - 1, menu(r)) for r in range(1, 7)]
    # La roue des trois bonus d'Armin (Abnegation) : la botte en haut, le bouclier en bas a gauche, le poing en bas a droite.
    icones = ASSETS / "textures" / "item" / "icone"
    bonus = [icones / f"bonus_{n}.png" for n in ("speed", "resistance", "force")]
    if all(f.is_file() for f in bonus):
        shapes.append(("menu_choix_roue", 0xE030, wheel([Image.open(f).convert("RGBA") for f in bonus],
                                                         [(160, 196, 236, 255), (196, 198, 214, 255), (238, 176, 170, 255)])))
    else:
        print("  icones bonus_* absentes (python3 tools/build_icones.py d'abord) : pas de roue")
    providers = [{"type": "space", "advances": {"": -18, "": -(W + 2 * M + 1) + 18}}]
    for name, code, im in shapes:
        im.save(tex / f"{name}.png")
        providers.append({"type": "bitmap", "file": f"crossfight:font/{name}.png", "height": im.height, "ascent": 13 + M, "chars": [chr(code)]})
    font = ASSETS / "font"
    font.mkdir(parents=True, exist_ok=True)
    (font / "choix.json").write_text(json.dumps({"providers": providers}, ensure_ascii=True, indent=1), encoding="utf-8")
    print(f"{len(shapes)} habillages de menus de choix dans resourcepack-prive/ (police choix)")
    if "--apercu" in sys.argv:
        shapes[-1][2].save(sys.argv[sys.argv.index("--apercu") + 1])
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
