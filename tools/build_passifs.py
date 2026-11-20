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
from build_guide import accent  # noqa: E402
from build_menu_franuka import nine, sprite, SRC  # noqa: E402

ROOT = Path(__file__).resolve().parent.parent
DATA = ROOT / "tools" / "guide_capacites.json"
OUT = ROOT / "resourcepack-prive" / "assets" / "crossfight" / "textures" / "passif"
CLIENT = Path("/home/user/mcclient/client.jar")
W, H = 960, 540
MARGIN = 8
CELL = 40                         # la case de l'icone (32 x 32 au milieu)
TEXT_W = 250                      # largeur du texte a cote des icones
BADGE = 22                        # la fleche verte / la croix rouge d'activation
# L'animation de la fleche quand un passif s'allume (hud/PassiveHud : une image par etape, 2 ticks chacune) :
# (decalage vertical, opacite de la fleche, halo vert, eclat). L'etape 0 est la fleche au repos.
# Les passifs qui peuvent agir sur des allies en meme temps (PassiveHud.ALLY_PASSIVES) : une seconde fleche
# verte, a gauche de la premiere, quand c'est le cas (masque "a<bits>" dans le nom de l'image).
ALLY_PASSIVES = {"marth_charisme", "marth_leader", "byleth_embleme"}
FRAMES = [(9, 0.35, 0, False), (4, 0.8, 70, False), (-3, 1.0, 150, True), (-5, 1.0, 120, True), (-2, 1.0, 60, False), (0, 1.0, 0, False)]
# La croix quand un passif s'eteint (etapes "x1".."x6") : (taille, decalage horizontal, halo rouge).
CROSS = [(0.35, 0, 0), (0.75, 0, 70), (1.3, 0, 150), (1.15, -2, 110), (1.0, 2, 50), (1.0, 0, 0)]
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


# Bordure verte pour un passif (vive quand il agit), jaune pour un actif dont l'effet dure.
PANEL, EDGE, DIM_EDGE = (22, 18, 28, 190), (110, 225, 120, 255), (85, 145, 95, 230)
ACTIVE_EDGE = (255, 210, 70, 255)


def actives(character):
    """Les capacites non passives, dans l'ordre du plugin (hud/PassiveHud.actives)."""
    return [a for a in character["abilities"] if a["type"] != "Passif"]


TEXTS = json.loads((ROOT / "tools" / "textes_hud.json").read_text(encoding="utf-8"))


def text_of(a, text):
    """Le texte d'une capacite selon le mode : 1 = dit au joueur ("vous"), 2 = court (tools/textes_hud.json)."""
    t = TEXTS.get(a["id"], {})
    if text == 2:
        return t.get("court") or t.get("vous") or accent(a["description"])
    return t.get("vous") or accent(a["description"])


def weapon_abilities(character):
    """Les capacites portees par l'arme (passifs et actifs d'arme) : une seule carte, celle de l'arme."""
    return [a for a in character["abilities"] if a["weapon"]]


def weapon_lines(character, text, font):
    """Le texte de la carte de l'arme : chaque capacite de l'arme, son nom puis son texte."""
    lines = []
    for a in weapon_abilities(character):
        # La capacite qui porte le nom de l'arme (Falchion, Yato...) : son texte seul, le titre suffit.
        same = accent(a["name"]) == accent(character.get("weapon") or "")
        lines += wrap(("" if same else accent(a["name"]) + " : ") + text_of(a, text), font, TEXT_W)
    return lines


def weapon_height(character, text, font):
    if not weapon_abilities(character):
        return 0
    return (CELL if not text else max(CELL, 22 + len(weapon_lines(character, text, font)) * 18 + 6)) + 4


def weapon_edge(character):
    """La bordure de l'arme suit sa premiere capacite : verte pour un passif, jaune pour un actif."""
    return EDGE if weapon_abilities(character)[0]["type"] == "Passif" else ACTIVE_EDGE


def weapon_icon(character):
    """L'image de l'arme : l'objet du jeu (l'export donne sa matiere), a defaut l'objet de sa premiere capacite."""
    return item_texture(character.get("weaponIcon") or weapon_abilities(character)[0].get("icon") or "iron_sword")


def draw_weapon(im, d, character, text, font):
    """La carte de l'arme, en tete de la colonne de gauche : bordure verte (passif) ou jaune (actif), sans fleche ni croix."""
    y = MARGIN
    edge = weapon_edge(character)
    lines = weapon_lines(character, text, font) if text else []
    h = weapon_height(character, text, font) - 4
    if text:
        d.rectangle([MARGIN, y, MARGIN + CELL + 10 + TEXT_W + 6, y + h - 1], fill=PANEL, outline=edge)
    d.rectangle([MARGIN, y, MARGIN + CELL - 1, y + CELL - 1], fill=PANEL, outline=edge)
    d.rectangle([MARGIN + 1, y + 1, MARGIN + CELL - 2, y + CELL - 2], outline=edge[:3] + (120,))
    im.alpha_composite(weapon_icon(character), (MARGIN + 4, y + 4))
    if text:
        tx = MARGIN + CELL + 6
        d.text((tx, y + 2), accent(character.get("weapon") or "Arme"), font=font, fill=(230, 232, 245, 255))
        for k, line in enumerate(lines):
            d.text((tx, y + 21 + k * 18), line, font=font, fill=WHITE)


def row_height(a, text, font):
    """La hauteur d'une case (avec son texte, selon la description)."""
    return CELL if not text else max(CELL, 22 + len(wrap(text_of(a, text), font, TEXT_W)) * 18 + 6)


def block_height(character, text, font, weapon=False):
    """La hauteur du bloc de gauche (l'arme si montree, puis les passifs) : la ou commence la carte d'un actif."""
    return (weapon_height(character, text, font) if weapon else 0) + sum(row_height(a, text, font) + 4 for a in passives(character))


def render(character, mask, text, rec, font, frame=0, cross=0, only=None, weapon=False, no_passives=False, allies=0):
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    y = MARGIN
    if weapon and weapon_abilities(character):
        draw_weapon(im, d, character, text, font)
        y += weapon_height(character, text, font)
    for i, a in enumerate([] if no_passives else passives(character)):
        lit = bool(mask & (1 << i))
        lines = wrap(text_of(a, text), font, TEXT_W) if text else []
        h = row_height(a, text, font)
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
            d.rectangle([MARGIN + 1, y + 1, MARGIN + CELL - 2, y + CELL - 2], outline=(170, 255, 170, 120))
        # La fleche du pack d'icones, en bas a droite de la case : verte vers le haut quand le passif agit,
        # une croix rouge quand il est au repos.
        # Version 32 x 32 (plus detaillee), rognee puis ramenee a 22 px : elle deborde un peu de la case.
        arrow = build_icones.icon_of("Objets 3217" if lit else "Objets 136", 32)
        arrow = arrow.crop(arrow.getbbox()).resize((BADGE, BADGE), Image.NEAREST)
        ax, ay = MARGIN + CELL - BADGE + 3, y + CELL - BADGE
        if lit and allies & (1 << i):
            # La seconde fleche : des allies en profitent aussi.
            second = build_icones.icon_of("Objets 3217", 32)
            second = second.crop(second.getbbox()).resize((BADGE, BADGE), Image.NEAREST)
            im.alpha_composite(second, (ax - BADGE + 4, ay))
        if lit and frame and i == only:
            dy, alpha, glow, sparkle = FRAMES[frame - 1]
            if glow:      # un halo vert qui pulse derriere la fleche
                halo = Image.new("RGBA", im.size, (0, 0, 0, 0))
                hd = ImageDraw.Draw(halo)
                for r, op in ((15, glow // 3), (12, glow // 2), (9, glow)):
                    hd.ellipse([ax + BADGE // 2 - r, ay + dy + BADGE // 2 - r, ax + BADGE // 2 + r, ay + dy + BADGE // 2 + r], fill=(90, 255, 120, op))
                im.alpha_composite(halo)
            if alpha < 1:
                arrow.putalpha(arrow.getchannel("A").point(lambda v: int(v * alpha)))
            im.alpha_composite(arrow, (ax, ay + dy))
            if sparkle:
                spark(im, ax + BADGE - 1, ay + dy + 2)
                spark(im, ax + 2, ay + dy + 6)
        elif not lit and cross and i == only:
            size, dx, glow = CROSS[cross - 1]
            cx, cy = ax + BADGE // 2 + dx, ay + BADGE // 2
            if glow:      # un halo rouge qui claque derriere la croix
                halo = Image.new("RGBA", im.size, (0, 0, 0, 0))
                hd = ImageDraw.Draw(halo)
                for r, op in ((15, glow // 3), (12, glow // 2), (9, glow)):
                    hd.ellipse([cx - r, cy - r, cx + r, cy + r], fill=(255, 70, 60, op))
                im.alpha_composite(halo)
            n = max(4, round(BADGE * size))
            big = build_icones.icon_of("Objets 136", 32)
            big = big.crop(big.getbbox()).resize((n, n), Image.NEAREST)
            im.alpha_composite(big, (cx - n // 2, cy - n // 2))
        else:
            im.alpha_composite(arrow, (ax, ay))
        if text:
            tx = MARGIN + CELL + 6
            d.text((tx, y + 2), a["name"], font=font, fill=GOLD if lit else (215, 195, 150, 255))
            for k, line in enumerate(lines):
                d.text((tx, y + 21 + k * 18), line, font=font, fill=WHITE)
        y += h + 4
    return im


# Le glissement d'une carte qui arrive (de dehors de l'ecran) ou qui s'en va : part de sa largeur, etape par etape.
SLIDE_IN = [0.75, 0.45, 0.15]
SLIDE_OUT = [0.3, 0.7]


def slide_in(base, layer, dx):
    """Pose le calque sur l'image, decale de dx pixels (negatif : vers la gauche)."""
    if dx < 0:
        base.alpha_composite(layer, (0, 0), (-dx, 0))
    else:
        base.alpha_composite(layer, (dx, 0))


def save(im, path):
    """En palette (256 couleurs, transparence comprise) : des images bien plus legeres, identiques a l'oeil."""
    im.quantize(colors=256, method=Image.Quantize.FASTOCTREE).save(path, optimize=True)


def main():
    data = json.loads(DATA.read_text(encoding="utf-8"))
    rec = recipes()
    font = ImageFont.truetype(str(FONT), 24)      # la police du pack, doublee : lisible a l'ecran
    chars = [c for l in data for c in l["characters"] if passives(c) or weapon_abilities(c)]
    if len(sys.argv) >= 3 and sys.argv[1] == "--apercu":
        picks = [c for c in chars if c["id"] in ("corrin", "marth", "kaze", "eirika")]
        sheet = Image.new("RGBA", (W * 2, H * 2), (70, 110, 60, 255))
        for k, c in enumerate(picks[:4]):
            n = len(passives(c))
            sheet.alpha_composite(render(c, (1 << n) - 2 if n > 1 else 1, k % 2 == 1, rec, font), ((k % 2) * W, (k // 2) * H))
        sheet.save(sys.argv[2])
        print(sys.argv[2])
        return
    # --perso <id> [<id>...] : seulement ces personnages, sans toucher aux images des autres.
    only = set(sys.argv[sys.argv.index("--perso") + 1:]) if "--perso" in sys.argv else None
    if only:
        chars = [c for c in chars if c["id"] in only]
        for c in chars:
            for f in OUT.glob(f"{c['id']}_*.png"):
                f.unlink()
    elif OUT.exists():
        for f in OUT.glob("*.png"):
            f.unlink()
    OUT.mkdir(parents=True, exist_ok=True)
    count = 0
    for c in chars:
        # Les passifs masques (/cf hud), la carte de l'arme seule : <perso>_arme_<texte>.
        for text in (0, 1, 2) if weapon_abilities(c) else ():
            save(render(c, 0, text, rec, font, weapon=True, no_passives=True), OUT / f"{c['id']}_arme_{text}.png")
            count += 1
        ally_bits = sum(1 << i for i, a in enumerate(passives(c)) if a["id"] in ALLY_PASSIVES)
        # Les sous-ensembles des passifs allumes qui peuvent agir sur des allies : "a<bits>" dans le nom.
        n_pass = len(passives(c))
        for mask, allies in [(m, am) for m in range(1 << n_pass) for am in range(1 << n_pass) if am & ~(m & ally_bits) == 0]:
            tag = f"{mask}a{allies}" if allies else str(mask)
            # Le troisieme champ du nom : texte (0 aucun, 1 long, 2 court) + 3 x carte de l'arme (si le personnage en a une).
            for code in range(6 if weapon_abilities(c) else 3):
                text, weapon = code % 3, code >= 3
                save(render(c, mask, text, rec, font, weapon=weapon, allies=allies), OUT / f"{c['id']}_{tag}_{code}.png")
                count += 1
                # Les etapes de l'animation, pour le seul passif i qui vient de changer : sa fleche s'il est
                # allume (<perso>_<allumes>_<texte>_<i>_<etape>), sa croix s'il est eteint (..._x<i>_<etape>).
                for i in range(len(passives(c))):
                    lit = mask & (1 << i)
                    for frame in range(1, len(FRAMES if lit else CROSS) + 1):
                        im = render(c, mask, text, rec, font, frame=frame, only=i, weapon=weapon, allies=allies) if lit else render(c, mask, text, rec, font, cross=frame, only=i, weapon=weapon, allies=allies)
                        save(im, OUT / f"{c['id']}_{tag}_{code}_{'' if lit else 'x'}{i}_{frame}.png")
                        count += 1
    print(f"{count} images de passifs ({len(chars)} personnages) dans resourcepack-prive/")


if __name__ == "__main__":
    main()
