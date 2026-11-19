#!/usr/bin/env python3
"""Les passifs en haut a gauche de l'ecran (hud/PassiveHud.java), avec le pack d'interface et les icones achetes.

Usage :  python3 tools/build_passifs.py                      ecrit les images dans le pack complet
         python3 tools/build_passifs.py --apercu image.png   planche de quelques personnages

Comme l'orbe de l'ultime, ce sont des images de 960 x 540 que le jeu etire en plein ecran (camera_overlay) :
vides sauf le coin haut gauche, ou chaque passif du personnage a sa case. Une image par personnage, par
combinaison de passifs allumes (bit i : le i-e passif agit en ce moment) et avec ou sans texte (/cf passif show) :
passif/<perso>_<allumes>_<0|1>.png dans resourcepack-prive/assets/crossfight/textures/. Les passifs d'un personnage
et leur ordre viennent de tools/guide_capacites.json (l'export du plugin) ; leurs icones, des recettes de
tools/build_icones.py, ou a defaut de l'objet du jeu (client.jar, jamais versionne).
"""
import io
import json
import sys
import zipfile
from pathlib import Path
from PIL import Image, ImageDraw, ImageEnhance, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_icones  # noqa: E402
from build_menu_franuka import nine, sprite, SRC  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "tools" / "guide_capacites.json"
OUT = ROOT / "resourcepack-prive" / "assets" / "crossfight" / "textures" / "passif"
CLIENT = Path("/home/user/mcclient/client.jar")
W, H = 960, 540
MARGIN = 8
CELL = 40                         # la case de l'icone (32 x 32 au milieu)
TEXT_W = 250                      # largeur du texte a cote des icones
FONT = SRC / "Fonts" / "FantasyRPGtext (size 12).ttf"
GOLD, WHITE, SHADOW = (255, 214, 120, 255), (245, 240, 230, 255), (30, 20, 25, 255)


def passives(character):
    return [a for a in character["abilities"] if a["type"] == "Passif" and not a["weapon"]]


def recipes():
    out = {}
    for people in build_icones.RECIPES.values():
        for abilities in people.values():
            for _, ident, recipe in abilities:
                out[ident] = recipe
    return out


def item_texture(name):
    """L'objet du jeu (16 x 16, agrandi deux fois) quand le passif n'a pas encore d'icone a lui."""
    try:
        with zipfile.ZipFile(CLIENT) as z:
            raw = z.read(f"assets/minecraft/textures/item/{name}.png")
        return Image.open(io.BytesIO(raw)).convert("RGBA").crop((0, 0, 16, 16)).resize((32, 32), Image.NEAREST)
    except Exception:
        return Image.new("RGBA", (32, 32), (0, 0, 0, 0))


def icon_for(a, rec):
    if a["id"] in rec:
        return build_icones.icon(rec[a["id"]])
    return item_texture(a.get("icon") or "paper")


def wrap(text, font, width):
    words, lines, line = text.split(), [], ""
    d = ImageDraw.Draw(Image.new("RGBA", (1, 1)))
    for w in words:
        test = (line + " " + w).strip()
        if d.textlength(test, font=font) > width and line:
            lines.append(line)
            line = w
        else:
            line = test
    lines.append(line)
    return lines


def spark(im, x, y):
    """Le petit eclat d'activation : une etoile doree a quatre branches."""
    d = ImageDraw.Draw(im)
    for dx, dy in ((0, -3), (0, -2), (0, -1), (0, 1), (0, 2), (0, 3), (-3, 0), (-2, 0), (-1, 0), (1, 0), (2, 0), (3, 0)):
        d.point((x + dx, y + dy), fill=(255, 236, 150, 255))
    d.point((x, y), fill=(255, 255, 255, 255))
    for dx, dy in ((-1, -1), (1, -1), (-1, 1), (1, 1)):
        d.point((x + dx, y + dy), fill=(255, 200, 80, 200))


PANEL, EDGE, DIM_EDGE = (22, 18, 28, 190), (255, 214, 120, 255), (120, 100, 90, 220)


def render(character, mask, text, rec, font):
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    y = MARGIN
    for i, a in enumerate(passives(character)):
        lit = bool(mask & (1 << i))
        lines = wrap(a["description"], font, TEXT_W) if text else []
        h = CELL if not text else max(CELL, 30 + len(lines) * 18 + 4)
        if text:
            # Le panneau du texte, sombre et translucide, borde d'un filet.
            d.rectangle([MARGIN, y, MARGIN + CELL + 10 + TEXT_W + 6, y + h - 1], fill=PANEL, outline=EDGE if lit else DIM_EDGE)
        d.rectangle([MARGIN, y, MARGIN + CELL - 1, y + CELL - 1], fill=PANEL, outline=EDGE if lit else DIM_EDGE)
        icon = icon_for(a, rec)
        if not lit:
            icon = ImageEnhance.Color(icon).enhance(0.25)
            icon = ImageEnhance.Brightness(icon).enhance(0.55)
        im.alpha_composite(icon, (MARGIN + 4, y + 4))
        if lit:
            d.rectangle([MARGIN + 1, y + 1, MARGIN + CELL - 2, y + CELL - 2], outline=(255, 236, 160, 120))
        # La fleche du pack d'icones, en bas a droite de la case : verte vers le haut quand le passif agit,
        # rouge vers le bas quand il est au repos.
        arrow = build_icones.icon_of("Objets 3217" if lit else "Objets 3268", 16)
        im.alpha_composite(arrow, (MARGIN + CELL - 17, y + CELL - 17))
        if text:
            tx = MARGIN + CELL + 6
            d.text((tx, y + 2), a["name"], font=font, fill=GOLD if lit else (215, 195, 150, 255))
            for k, line in enumerate(lines):
                d.text((tx, y + 28 + k * 18), line, font=font, fill=WHITE)
        y += h + 4
    return im


def main():
    data = json.loads(DATA.read_text(encoding="utf-8"))
    rec = recipes()
    font = ImageFont.truetype(str(FONT), 24)      # la police du pack, doublee : lisible a l'ecran
    chars = [c for l in data for c in l["characters"] if passives(c)]
    if len(sys.argv) >= 3 and sys.argv[1] == "--apercu":
        picks = [c for c in chars if c["id"] in ("corrin", "marth", "kaze", "eirika")]
        sheet = Image.new("RGBA", (W * 2, H * 2), (70, 110, 60, 255))
        for k, c in enumerate(picks[:4]):
            n = len(passives(c))
            sheet.alpha_composite(render(c, (1 << n) - 2 if n > 1 else 1, k % 2 == 1, rec, font), ((k % 2) * W, (k // 2) * H))
        sheet.save(sys.argv[2])
        print(sys.argv[2])
        return
    if OUT.exists():
        for f in OUT.glob("*.png"):
            f.unlink()
    OUT.mkdir(parents=True, exist_ok=True)
    count = 0
    for c in chars:
        for mask in range(1 << len(passives(c))):
            for text in (0, 1):
                render(c, mask, text, rec, font).save(OUT / f"{c['id']}_{mask}_{text}.png", optimize=True)
                count += 1
    print(f"{count} images de passifs ({len(chars)} personnages) dans resourcepack-prive/")


if __name__ == "__main__":
    main()
