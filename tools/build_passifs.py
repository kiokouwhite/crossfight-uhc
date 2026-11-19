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


def stretch(img, w, h, b):
    """Etire une piece du pack a w x h en gardant ses bords (b pixels) intacts : les coins tels quels, le reste etire."""
    out = Image.new("RGBA", (w, h), (0, 0, 0, 0))
    sw, sh = img.size
    cols = ((0, b, 0, b), (b, sw - b, b, w - b), (sw - b, sw, w - b, w))
    rows = ((0, b, 0, b), (b, sh - b, b, h - b), (sh - b, sh, h - b, h))
    for sx1, sx2, dx1, dx2 in cols:
        for sy1, sy2, dy1, dy2 in rows:
            if dx2 > dx1 and dy2 > dy1:
                out.alpha_composite(img.crop((sx1, sy1, sx2, sy2)).resize((dx2 - dx1, dy2 - dy1), Image.NEAREST), (dx1, dy1))
    return out


def render(character, mask, text, rec, font):
    """Tout vient du pack d'interface : cases (Item slots), panneau (Background boxes), eclat (Mini icons), filet (Dividers)."""
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    slot_off, slot_on = sprite("Item slots", "Slot_02_Empty"), sprite("Item slots", "Slot_03_Empty")
    panel = sprite("Background boxes", "BGbox_01B")
    star = sprite("Mini icons", "Icon_01_Selected")
    rule_on, rule_off = sprite("Dividers", "Divider_07"), sprite("Dividers", "Divider_03")
    y = MARGIN
    for i, a in enumerate(passives(character)):
        lit = bool(mask & (1 << i))
        lines = wrap(a["description"], font, TEXT_W) if text else []
        h = CELL if not text else max(CELL, 38 + len(lines) * 18 + 10)
        if text:
            im.alpha_composite(nine(panel, CELL + 10 + TEXT_W + 14, h), (MARGIN, y))
        cell = stretch(slot_on if lit else slot_off, CELL, CELL, 4)
        im.alpha_composite(cell, (MARGIN, y))
        icon = icon_for(a, rec)
        if not lit:
            icon = ImageEnhance.Color(icon).enhance(0.25)
            icon = ImageEnhance.Brightness(icon).enhance(0.6)
        im.alpha_composite(icon, (MARGIN + 4, y + 4))
        if lit:
            im.alpha_composite(star, (MARGIN + CELL - star.width + 2, y - 2))
        if text:
            tx = MARGIN + CELL + 8
            # Texte sombre sur le panneau clair du pack, comme dans les menus d'Anna.
            d.text((tx, y + 7), a["name"], font=font, fill=(176, 84, 22, 255) if lit else (62, 66, 110, 255))
            rule = rule_on if lit else rule_off
            im.alpha_composite(rule.resize((TEXT_W, rule.height), Image.NEAREST), (tx, y + 31))
            for k, line in enumerate(lines):
                d.text((tx, y + 36 + k * 18), line, font=font, fill=(40, 34, 56, 255))
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
