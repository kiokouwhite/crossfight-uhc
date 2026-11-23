#!/usr/bin/env python3
"""L'orbe de l'ultime, en bas à droite de l'écran, avec le pack d'interface acheté à Franuka (pack privé seulement).

Usage : python3 tools/build_ultime_franuka.py [--apercu image.png]

Le jeu dessine en plein écran l'image « camera_overlay » d'un objet porté (hud/EffectHud.java) : ici, des images
de 960 x 540, vides sauf leur coin bas droit, où se trouve le cadre au dragon du pack (Resource orbs,
DecoratedFrame2_Right) et, dans son anneau, l'orbe doré rempli selon la charge de l'ultime : 21 images de 0 à 100 %
par pas de 5 (ult_00 à ult_20), et deux pour l'ultime prêt, qui alternent (ult_pret_0, ult_pret_1 : l'orbe plein,
plus clair, avec des éclats). Elles vont dans resourcepack-prive/assets/crossfight/textures/overlay_ult/.
Sans les fichiers du pack de Franuka, ce script ne fait rien et le jeu n'affiche pas d'orbe.
"""
import sys
from pathlib import Path
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent))
from build_menu_franuka import ONE, ROOT, SRC, sprite  # noqa: E402

OUT = ROOT / "resourcepack-prive" / "assets" / "crossfight" / "textures" / "overlay_ult"
W, H = 960, 540
MARGIN = 8                       # du bord de l'ecran, en points de l'image
ORB_AT = (0, 16)                 # ou poser l'orbe (48 x 48, un disque de 38) dans le cadre (80 x 80) : exactement sur le trou de son
                                 # anneau (un disque de 38 lui aussi, de x 5 a 42 et de y 21 a 58), a gauche du dragon
STEPS = 20


def orb(level, ready=0):
    """Le cadre et son orbe rempli a level / STEPS (par le bas) ; ready : 1 ou 2, l'orbe plein et eclatant."""
    frame = sprite("Resource orbs", "DecoratedFrame2_Right")
    full = sprite("Resource orbs", "Orb_Energy")
    box = full.getbbox()
    # le fond de l'orbe : sa forme, dans la couleur sombre du cadre vide du pack
    dark = Image.new("RGBA", full.size, (0, 0, 0, 0))
    dark.paste(Image.new("RGBA", full.size, (72, 44, 60, 255)), (0, 0), full.getchannel("A"))
    top = box[3] - round((box[3] - box[1]) * level / STEPS)
    inside = dark.copy()
    if top < box[3]:
        inside.alpha_composite(full.crop((0, top, full.width, full.height)), (0, top))
        if 0 < level < STEPS:                                             # la surface : un trait clair
            for x in range(full.width):
                if full.getpixel((x, top))[3]:
                    inside.putpixel((x, top), (255, 244, 200, 255))
    if ready:
        px = inside.load()
        for y in range(inside.height):
            for x in range(inside.width):
                r, g, b, a = px[x, y]
                if a:
                    k = 40 if ready == 1 else 70
                    px[x, y] = (min(255, r + k), min(255, g + k), min(255, b + k), a)
    # (l'orbe deborde d'un point a gauche du cadre : on compose plus large, puis on rogne)
    wide = Image.new("RGBA", (frame.width + 16, frame.height), (0, 0, 0, 0))
    wide.alpha_composite(inside, (ORB_AT[0] + 8, ORB_AT[1]))
    wide.alpha_composite(frame, (8, 0))
    out = wide.crop((8, 0, 8 + frame.width, frame.height))
    if ready:                                                              # des eclats autour, qui changent d'une image a l'autre
        for x, y in (((65, 44), (9, 6), (5, 62)) if ready == 1 else ((59, 60), (17, 2), (1, 40))):
            for dx, dy in ((0, 0), (1, 0), (-1, 0), (0, 1), (0, -1)):
                if 0 <= x + dx < 80 and 0 <= y + dy < 80:
                    out.putpixel((x + dx, y + dy), (255, 250, 210, 255))
    return out


def screen(piece):
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    im.alpha_composite(piece, (W - piece.width - MARGIN, H - piece.height - 1))            # tout en bas : sous la derniere ligne du tchat
    return im


# L'avancement de la Yato de Corrin (hud : Character.hudOrbVariant) : un badge "kills / prochain palier" en bas
# a droite de l'icone de l'arme, en haut a gauche. Porte par l'image de l'orbe, pour ne pas multiplier celles des passifs.
YATO = {"l": (2, 5), "t": (1, 5)}       # voie -> paliers de kills


def yato_badge(im, kills, path):
    from PIL import ImageDraw, ImageFont
    font = ImageFont.truetype(str(SRC / "Fonts" / "FantasyRPGtext (size 12).ttf"), 16)
    first, last = YATO[path]
    label = f"{min(kills, last)}/{first if kills < first else last}"
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    cell = 40
    # Une pastille centree sous l'icone de l'arme, dans la fenetre laissee par la carte (build_passifs.draw_weapon).
    w = int(d.textlength(label, font=font)) + 6
    x0, y0 = MARGIN + (cell - w) // 2, MARGIN + 26
    d.rectangle([x0, y0, x0 + w - 1, y0 + 12], fill=(18, 14, 22, 255), outline=(110, 225, 120, 255))
    d.text((x0 + 3, y0 - 1), label, font=font, fill=(220, 255, 220, 255))
    return im


# La Force gagnee par Eren avec Elimination Morale contre le dernier adversaire frappe (0,2 par kill de cet
# adversaire) : un badge en bas a droite de la case du passif, le premier de sa colonne, dans la fenetre laissee
# par tools/build_passifs.py (COUNTERS). Un dossier par nombre de kills : "eren_m<kills>" (Eren.hudOrbVariant).
MORALE_MAX = 12


def morale_badge(im, kills):
    from PIL import ImageDraw, ImageFont
    import build_passifs as bp
    font = ImageFont.truetype(str(SRC / "Fonts" / "FantasyRPGtext (size 12).ttf"), 16)
    label = "+" + f"{0.2 * kills:.1f}".replace(".", ",")
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    x0, y0, x1, y1 = bp.counter_box(bp.MARGIN)
    # La pastille remplit toute la fenetre (sinon le decor se verrait a travers), le texte au milieu.
    w = int(d.textlength(label, font=font))
    d.rectangle([x0, y0, x1 - 1, y1 - 1], fill=(18, 14, 22, 255), outline=(110, 225, 120, 255))
    d.text((x0 + (x1 - x0 - w) // 2, y0 + 1), label, font=font, fill=(220, 255, 220, 255))
    return im


def main():
    if not ONE.is_dir():
        print("pack de Franuka absent (resourcepack-prive/ui-franuka/) : rien a faire")
        return 0
    OUT.mkdir(parents=True, exist_ok=True)
    for k in range(STEPS + 1):
        screen(orb(k)).save(OUT / f"ult_{k:02d}.png")
    for k in (1, 2):
        screen(orb(STEPS, k)).save(OUT / f"ult_pret_{k - 1}.png")
    for kills in range(MORALE_MAX + 1):
        sub = OUT / f"eren_m{kills}"
        sub.mkdir(exist_ok=True)
        for k in range(STEPS + 1):
            morale_badge(screen(orb(k)), kills).save(sub / f"ult_{k:02d}.png", optimize=True)
        for k in (1, 2):
            morale_badge(screen(orb(STEPS, k)), kills).save(sub / f"ult_pret_{k - 1}.png", optimize=True)
    for path, (first, last) in YATO.items():
        for kills in range(last + 1):
            sub = OUT / f"yato_{path}{kills}"
            sub.mkdir(exist_ok=True)
            for k in range(STEPS + 1):
                yato_badge(screen(orb(k)), kills, path).save(sub / f"ult_{k:02d}.png", optimize=True)
            for k in (1, 2):
                yato_badge(screen(orb(STEPS, k)), kills, path).save(sub / f"ult_pret_{k - 1}.png", optimize=True)
    if "--apercu" in sys.argv:
        sheet = Image.new("RGBA", (6 * 84, 84), (60, 70, 90, 255))
        for i, piece in enumerate((orb(0), orb(5), orb(10), orb(15), orb(20), orb(20, 2))):
            sheet.alpha_composite(piece, (i * 84 + 2, 2))
        sheet.resize((sheet.width * 3, sheet.height * 3), Image.NEAREST).save(sys.argv[sys.argv.index("--apercu") + 1])
    print("overlay_ult :", STEPS + 3, "images", (W, H))
    return 0


if __name__ == "__main__":
    sys.exit(main())
