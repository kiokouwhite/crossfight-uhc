#!/usr/bin/env python3
"""Le menu d'une Lootbox : une fenetre par tier, a sa couleur, pour sa roulette (game/Lootbox.java).

Usage : python3 tools/build_menu_lootbox.py [--apercu image.png]

Un coffre de trois rangees (176 x 166) dans une fenetre qui deborde de dix pixels de chaque cote (196 x 186). En haut,
une banniere « Lootbox tier N » ; dessous, trois roulettes verticales comme une machine a sous, chacune une bande
sombre sur les trois rangees : les diamants (colonne 3 du coffre), l'objet (colonne 5), l'or (colonne 7). Leurs cases
du milieu (11, 13 et 15) sont en or : la ligne gagnante, montree par une fleche a gauche et une a droite. En bas,
l'inventaire du joueur. Quatre fenetres, une par tier, a sa couleur (blanc, cyan, violet, or) : pendant la roulette,
le plugin les fait defiler et finit sur celle du tier de la Lootbox.

Pack public : des fenetres dessinees ici, pixel par pixel. Pack complet : les memes, composees avec le pack
d'interface de Franuka (resourcepack-prive/ui-franuka/, payant, jamais versionne), au meme nom et a la meme taille :
elles remplacent les premieres. Sans ses fichiers, le pack complet garde les fenetres dessinees.

Les images sont des caracteres de la police par defaut de Minecraft : le plugin change le titre du coffre ouvert par
InventoryView.setTitle, qui ne prend que du texte, sans police. U+E2A0 a U+E2A3 (tiers 1 a 4) : hautes de 186 et
montees de 23 (leur haut a dix pixels au-dessus du coffre) ; U+E2AF, un espace qui recule de 18 pixels (le titre
s'ecrit a 8 pixels du bord du coffre, la fenetre commence dix pixels avant). La police par defaut du pack s'ajoute a
celle du jeu, elle ne la remplace pas.

Ecrit resourcepack/assets/minecraft/font/default.json, resourcepack/assets/crossfight/textures/font/lootbox_<1-4>.png
et, avec le pack de Franuka, resourcepack-prive/assets/crossfight/textures/font/lootbox_<1-4>.png.
"""
import json
import sys
from pathlib import Path

from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_menu_anna as anna          # noqa: E402  (ses lettres de 5 x 7)
import build_menu_franuka as franuka    # noqa: E402  (ses cadres etires, ses bannieres)

ROOT = Path(__file__).resolve().parent.parent
PUBLIC = ROOT / "resourcepack" / "assets"
PRIVATE = ROOT / "resourcepack-prive" / "assets"
W, H, MARGE = 176, 166, 10
PREMIER, ESPACE = 0xE2A0, 0xE2AF
# La couleur de chaque tier (celle de sa colonne de lumiere, game/Lootbox.java : TIER_BEAM), et sa teinte pour la
# fenetre de Franuka : (teinte HSV sur 255, saturation multipliee, luminosite multipliee).
COULEURS = {1: (226, 230, 238), 2: (90, 220, 255), 3: (206, 120, 255), 4: (255, 190, 40)}
TEINTES = {1: (160, 0.15, 1.12), 2: (135, 3.2, 1.05), 3: (198, 3.0, 1.0), 4: (30, 4.2, 1.12)}
ROULEAUX = (2, 4, 6)                     # les colonnes des trois roulettes : diamants, objet, or
RANGEES = (18, 36, 54)                   # les trois rangees du coffre ; celle du milieu est la ligne gagnante
INVENTAIRE = (84, 102, 120, 142)         # les rangees de l'inventaire du joueur, sous un coffre de trois rangees

anna.LETTERS.setdefault("4", ["...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."])


def titre(tier):
    return f"LOOTBOX TIER {tier}"


# ------------------------------------------------------------------ pack public : dessinees ici

def fonce(c, k):
    return tuple(int(v * k) for v in c[:3]) + (255,)


def fenetre_publique(tier):
    c = COULEURS[tier]
    im = Image.new("RGBA", (W + 2 * MARGE, H + 2 * MARGE), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    # La fenetre : cernee d'encre, un liseré clair dedans, les coins arrondis d'un pixel.
    d.rounded_rectangle([0, 0, W + 2 * MARGE - 1, H + 2 * MARGE - 1], radius=4, fill=fonce(c, 0.78), outline=anna.INK)
    d.rounded_rectangle([2, 2, W + 2 * MARGE - 3, H + 2 * MARGE - 3], radius=3, outline=fonce(c, 0.95))
    o = MARGE
    # La banniere du titre.
    t = titre(tier)
    lw = anna.width(t)
    bx1, bx2 = o + 88 - lw // 2 - 10, o + 88 + lw // 2 + 10
    anna.panel(d, bx1, o - 6, bx2, o + 11, 4)
    anna.text(im, o + 88 - lw // 2, o - 2, t, color=anna.WHITE)
    # Les trois roulettes : une bande sombre sur les trois rangees, et ses cases ; celle de la ligne gagnante en or.
    for col in ROULEAUX:
        x = o + 8 + 18 * col
        d.rounded_rectangle([x - 4, o + RANGEES[0] - 4, x + 19, o + RANGEES[-1] + 19], radius=3, fill=fonce(c, 0.42), outline=anna.INK)
        for k, y in enumerate(RANGEES):
            y += o
            if k == 1:
                d.rectangle([x - 2, y - 2, x + 17, y + 17], fill=anna.GOLD, outline=anna.INK)
                d.rectangle([x - 1, y - 1, x + 16, y + 16], fill=anna.GOLD_DEEP)
                d.line([(x, y + 16), (x + 16, y + 16), (x + 16, y)], fill=anna.GOLD_LIGHT)
            else:
                anna.slot(d, x, y)
    # Les fleches de la ligne gagnante, a gauche de la premiere roulette et a droite de la derniere.
    cy = o + RANGEES[1] + 8
    gauche, droite = o + 8 + 18 * ROULEAUX[0] - 6, o + 8 + 18 * ROULEAUX[-1] + 21
    d.polygon([(gauche - 6, cy - 5), (gauche - 6, cy + 5), (gauche, cy)], fill=anna.GOLD, outline=anna.INK)
    d.polygon([(droite + 6, cy - 5), (droite + 6, cy + 5), (droite, cy)], fill=anna.GOLD, outline=anna.INK)
    # L'inventaire du joueur.
    for y in INVENTAIRE:
        for col in range(9):
            anna.slot(d, o + 8 + 18 * col, o + y)
    return im


# ------------------------------------------------------------------ pack complet : avec le pack de Franuka

def teinter(im, tier):
    """La fenetre lavande de Franuka a la couleur du tier : sa teinte remplacee, sa saturation et sa luminosite
    ajustees, ses ombres et ses reflets gardes."""
    h, ks, kv = TEINTES[tier]
    alpha = im.getchannel("A")
    hh, s, v = im.convert("RGB").convert("HSV").split()
    hh = hh.point(lambda _: h)
    s = s.point(lambda x: min(255, int(x * ks)))
    v = v.point(lambda x: min(255, int(x * kv)))
    out = Image.merge("HSV", (hh, s, v)).convert("RGBA")
    out.putalpha(alpha)
    return out


def fenetre_franuka(tier, police, cases):
    case, case_or, case_inv = cases
    im = franuka.nine(franuka.sprite("Background boxes", "BGbox_01B"), W + 2 * MARGE, H + 2 * MARGE)
    o = MARGE
    banniere = franuka.wide(franuka.sprite("Title banners", "BannerSmall_02B"), 154)
    im.alpha_composite(banniere, (o + 88 - 77, o - 7))
    im = teinter(im, tier)
    # La police de titre du pack : ses lettres font 7 pixels de haut, 4 pixels sous le point ou on l'ecrit.
    franuka.write(im, o + 88, o - 6, f"Lootbox tier {tier}", police, (255, 244, 226, 255), (60, 40, 50, 255))
    # Les trois roulettes, des bandes plus sombres que la fenetre ; la ligne gagnante en cases dorees.
    fond = im.getpixel((o + 20, o + 75))
    d = ImageDraw.Draw(im)
    for col in ROULEAUX:
        x = o + 8 + 18 * col
        d.rounded_rectangle([x - 4, o + RANGEES[0] - 4, x + 19, o + RANGEES[-1] + 19], radius=3, fill=fonce(fond, 0.5), outline=fonce(fond, 0.3))
        for k, y in enumerate(RANGEES):
            im.alpha_composite(case_or if k == 1 else case, (x, o + y))
    # Les fleches : le triangle du pack (sa version doree), de part et d'autre de la ligne gagnante (le triangle occupe
    # les pixels 3 a 13 de son image).
    fleche = franuka.sprite("Mini icons", "Icon_21_Selected")
    y = o + RANGEES[1]
    im.alpha_composite(fleche, (o + 8 + 18 * ROULEAUX[0] - 19, y))
    im.alpha_composite(fleche.transpose(Image.Transpose.FLIP_LEFT_RIGHT), (o + 8 + 18 * ROULEAUX[-1] + 19, y))
    for y in INVENTAIRE:
        for col in range(9):
            im.alpha_composite(case_inv, (o + 8 + 18 * col, o + y))
    return im


# ------------------------------------------------------------------ ecriture

def police_defaut():
    providers = [{"type": "space", "advances": {chr(ESPACE): -(MARGE + 8)}}]
    for tier in COULEURS:
        providers.append({"type": "bitmap", "file": f"crossfight:font/lootbox_{tier}.png", "height": H + 2 * MARGE,
                          "ascent": 23, "chars": [chr(PREMIER + tier - 1)]})
    return {"providers": providers}


def main():
    tex = PUBLIC / "crossfight" / "textures" / "font"
    tex.mkdir(parents=True, exist_ok=True)
    images = {}
    for tier in COULEURS:
        images[tier] = fenetre_publique(tier)
        images[tier].save(tex / f"lootbox_{tier}.png")
    font = PUBLIC / "minecraft" / "font" / "default.json"
    font.parent.mkdir(parents=True, exist_ok=True)
    font.write_text(json.dumps(police_defaut(), indent=1, ensure_ascii=True) + "\n", encoding="utf-8")
    print("pack public : quatre fenetres dessinees, police par defaut")
    if franuka.ONE.is_dir():
        police = ImageFont.truetype(str(franuka.SRC / "Fonts" / "FantasyRPGtitle (size 17).ttf"), 17)
        cases = (franuka.sprite("Item slots", "Slot_01_Empty"), franuka.sprite("Item slots", "Slot_03_Empty"),
                 franuka.sprite("Item slots", "Slot_02_Empty"))
        ptex = PRIVATE / "crossfight" / "textures" / "font"
        ptex.mkdir(parents=True, exist_ok=True)
        for tier in COULEURS:
            images[tier] = fenetre_franuka(tier, police, cases)
            images[tier].save(ptex / f"lootbox_{tier}.png")
        print("pack complet : quatre fenetres du pack de Franuka")
    else:
        print("pack de Franuka absent : le pack complet garde les fenetres dessinees")
    if "--apercu" in sys.argv:
        out = Path(sys.argv[sys.argv.index("--apercu") + 1])
        w, h = W + 2 * MARGE, H + 2 * MARGE
        planche = Image.new("RGBA", (4 * w + 30, h), (34, 34, 40, 255))
        for k, tier in enumerate(COULEURS):
            planche.alpha_composite(images[tier], (k * (w + 10), 0))
        planche.resize((planche.width * 2, planche.height * 2), Image.NEAREST).save(out)
        print("apercu :", out)
    return 0


if __name__ == "__main__":
    sys.exit(main())
