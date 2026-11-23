#!/usr/bin/env python3
"""La carte d'un actif dont l'effet dure, sous les passifs en haut a gauche (hud/InfluenceHud.selfImage).

Usage :  python3 tools/build_actifs.py                      ecrit les images dans le pack complet
         python3 tools/build_actifs.py --apercu image.png   planche de quelques cas

Bordure jaune, l'icone de la capacite, avec /cf passif show son nom et sa description ; en bas a droite de
l'icone, les secondes qui restent. Une image par personnage, par actif (dans l'ordre du plugin), avec ou sans
texte, et par etat : la carte posee (actif/<perso>_<j>_<texte>), qui arrive en glissant depuis le bord gauche
(_i<etape>), qui s'en va (_o<etape>), ou avec un compte a rebours (_s<secondes>, parmi SECONDS).
"""
import json
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_passifs as bp  # noqa: E402
from build_guide import accent  # noqa: E402

OUT = bp.ROOT / "resourcepack-prive" / "assets" / "crossfight" / "textures" / "actif"
W, H, MARGIN, CELL, TEXT_W = bp.W, bp.H, bp.MARGIN, bp.CELL, bp.TEXT_W
EDGE = (255, 210, 70, 255)
# Les comptes a rebours dessines : exacts jusqu'a 20 s, puis par paliers (le plugin montre le palier au-dessus).
SECONDS = list(range(1, 21)) + [25, 30, 40, 50, 60, 90, 120]
SRC = bp.ROOT / "src" / "main" / "java" / "com" / "kiokouwhite" / "crossfight" / "character"


def self_buffers():
    """Les capacites dont le code donne un effet au joueur lui-meme (potion, bonus de Boosts) : elles seules ont
    les images du compte a rebours. Lu dans les sources du plugin, classe par classe (super("<id>"...))."""
    import re
    pat = re.compile(r'(?:Effects\.(?:give|refresh)\((?:p|player|sp|self|caster|victim|owner)\b|\b(?:p|player|sp|self|caster)\.addPotionEffect\(|Boosts\.set\((?:p|player|sp|self|caster)\b|showFor\()')
    out = set()
    for f in SRC.rglob("*.java"):
        for part in re.split(r'\n(?=\s*(?:public |private |protected )?(?:static )?(?:final )?class \w+)', f.read_text(encoding="utf-8")):
            m = re.search(r'super\("([a-z0-9_]+)"', part)
            if m and pat.search(part):
                out.add(m.group(1))
    return out | TOMES


# Les tomes de Hano qui agissent sur elle un moment (character/earthdelta/Tomes.java : show) : une seule classe
# pour tous les tomes, donc declares ici.
TOMES = {"hano_tome_fire_protection", "hano_tome_respiration",
         # ...et les pouvoirs de titans d'Eren (une seule classe aussi : Eren.TitanPower).
         "eren_t_colossal", "eren_t_feminin", "eren_t_bestial", "eren_t_machoire", "eren_t_charrette", "eren_t_marteau"}


# Les chiffres du compte a rebours, dessines point par point (3 x 5, doubles : 6 x 10 pixels, traits de 2 pixels).
# La police du pack, tiree a une taille qui n'est pas un multiple de la sienne, perdait des traits (le 0 en deux points).
DIGITS = {"0": "111101101101111", "1": "010110010010111", "2": "111001111100111", "3": "111001111001111", "4": "101101111001001",
          "5": "111100111001111", "6": "111100111101111", "7": "111001001001001", "8": "111101111101111", "9": "111101111001111"}


def badge(im, x, y, seconds, small, edge=None):
    """Les secondes restantes, dans une pastille sombre en bas a droite de l'icone (liseret de la couleur de la carte)."""
    edge = edge or EDGE
    d = ImageDraw.Draw(im)
    label = str(seconds)
    w = len(label) * 8 - 2 + 6
    x0, y0 = x + CELL - w + 2, y + CELL - 15
    d.rectangle([x0, y0, x0 + w - 1, y0 + 14], fill=(18, 14, 22, 235), outline=edge)
    for k, ch in enumerate(label):
        bits = DIGITS[ch]
        for n, bit in enumerate(bits):
            if bit == "1":
                px, py = x0 + 3 + k * 8 + (n % 3) * 2, y0 + 2 + (n // 3) * 2
                d.rectangle([px, py, px + 1, py + 1], fill=(255, 240, 180, 255))


def render(character, j, text, font, small, rec, slide=0.0, seconds=None, weapon=False, variant="", tv=""):
    a = bp.actives(character)[j]
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    y = MARGIN + bp.block_height(character, text, font, weapon, variant)
    lines = bp.wrap(bp.text_of(a, text, tv), font, TEXT_W) if text else []
    h = CELL if not text else max(CELL, 22 + len(lines) * 18 + 6)
    if text:
        d.rectangle([MARGIN, y, MARGIN + CELL + 10 + TEXT_W + 6, y + h - 1], fill=bp.PANEL, outline=EDGE)
    d.rectangle([MARGIN, y, MARGIN + CELL - 1, y + CELL - 1], fill=bp.PANEL, outline=EDGE)
    d.rectangle([MARGIN + 1, y + 1, MARGIN + CELL - 2, y + CELL - 2], outline=(255, 240, 170, 120))
    im.alpha_composite(bp.icon_for(a, rec), (MARGIN + 4, y + 4))
    if text:
        tx = MARGIN + CELL + 6
        d.text((tx, y + 2), accent(a["name"]), font=font, fill=bp.GOLD)
        for k, line in enumerate(lines):
            d.text((tx, y + 21 + k * 18), line, font=font, fill=bp.WHITE)
    if seconds:
        badge(im, MARGIN, y, seconds, small)
    if slide:
        width = (CELL + 10 + TEXT_W + 6 if text else CELL) + MARGIN
        out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        bp.slide_in(out, im, -round(width * slide))
        im = out
    return im


OUT2 = OUT.parent / "actif2"
HUD_SPRITES = bp.ROOT / "resourcepack-prive" / "assets" / "minecraft" / "textures" / "gui" / "sprites" / "hud"


def render_second(character, j, text, font, small, rec, seconds=None, weapon=False, variant=""):
    """Le second actif en cours : une petite case (icone et secondes, sans texte) a droite de la carte du premier.
    Elle est portee par la main gauche du joueur, cinquieme image du HUD (hud/EffectHud : OFFHAND)."""
    a = bp.actives(character)[j]
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    y = MARGIN + bp.block_height(character, text, font, weapon, variant)
    x = MARGIN + (CELL + 10 + TEXT_W + 6 if text else CELL) + 4
    d.rectangle([x, y, x + CELL - 1, y + CELL - 1], fill=bp.PANEL, outline=EDGE)
    d.rectangle([x + 1, y + 1, x + CELL - 2, y + CELL - 2], outline=(255, 240, 170, 120))
    im.alpha_composite(bp.icon_for(a, rec), (x + 4, y + 4))
    if seconds:
        badge(im, x, y, seconds, small)
    return im


def hide_offhand_frame():
    """La case de la main gauche a cote de la barre d'action devient invisible : le second actif y est porte par un
    objet sans apparence, et son cadre vide clignoterait. Un vrai objet en main gauche se voit toujours, sans cadre."""
    HUD_SPRITES.mkdir(parents=True, exist_ok=True)
    for name in ("hotbar_offhand_left", "hotbar_offhand_right"):
        Image.new("RGBA", (29, 24), (0, 0, 0, 0)).save(HUD_SPRITES / f"{name}.png")


def main():
    data = json.loads(bp.DATA.read_text(encoding="utf-8"))
    rec = bp.recipes()
    font = ImageFont.truetype(str(bp.FONT), 24)
    small = ImageFont.truetype(str(bp.FONT), 16)
    chars = [c for l in data for c in l["characters"] if bp.actives(c)]
    timed = self_buffers()
    if len(sys.argv) >= 3 and sys.argv[1] == "--apercu":
        c = next(c for c in chars if c["id"] == "marth")
        sheet = Image.new("RGBA", (W * 2, H), (70, 110, 60, 255))
        sheet.alpha_composite(bp.render(c, 1, 1, rec, font, weapon=True))
        sheet.alpha_composite(render(c, 0, 1, font, small, rec, seconds=12, weapon=True))
        sheet.alpha_composite(bp.render(c, 1, 0, rec, font, weapon=True), (W, 0))
        sheet.alpha_composite(render(c, 0, 0, font, small, rec, seconds=120, weapon=True), (W, 0))
        sheet.save(sys.argv[2])
        print(sys.argv[2])
        return
    # --perso <id> [<id>...] : seulement ces personnages, sans toucher aux images des autres.
    only = set(sys.argv[sys.argv.index("--perso") + 1:]) if "--perso" in sys.argv else None
    if only:
        chars = [c for c in chars if c["id"] in only]
    OUT.mkdir(parents=True, exist_ok=True)
    OUT2.mkdir(parents=True, exist_ok=True)
    hide_offhand_frame()
    jobs, jobs2 = [], []
    for c in chars:
        ing = bp.ingredients(c, rec) + [sorted(timed & {a["id"] for a in c["abilities"]})]
        for j in range(len(bp.actives(c))):
            # Le troisieme champ du nom : texte (0 aucun, 1 long, 2 court) + 3 x carte de l'arme au-dessus (si le personnage en a une).
            # Avec le texte et sans carte d'arme, la variante est celle du texte (Weiss : la Dust du lancer, d0 a d5).
            for code, v in bp.codes(c):
                text, weapon = code % 3, code >= 3
                tv = v if v in bp.text_variants(c) else ""
                v = "" if tv else v
                kw = dict(weapon=weapon, variant=v, tv=tv)
                base = OUT / f"{c['id']}_{j}_{code}{v}{tv}"
                jobs.append((render, (c, j, text, font, small, rec), kw, f"{base}.png", bp.job_key(ing, j, text, weapon, v, tv)))
                for kind, steps in (("i", bp.SLIDE_IN), ("o", bp.SLIDE_OUT)):
                    for k, f in enumerate(steps, 1):
                        jobs.append((render, (c, j, text, font, small, rec), dict(kw, slide=f), f"{base}_{kind}{k}.png", bp.job_key(ing, j, text, weapon, v, tv, kind, k)))
                for sec in SECONDS if bp.actives(c)[j]["id"] in timed else ():
                    jobs.append((render, (c, j, text, font, small, rec), dict(kw, seconds=sec), f"{base}_s{sec}.png", bp.job_key(ing, j, text, weapon, v, tv, "s", sec)))
                # La petite case du second actif (actif2/), pour ceux qui ont un compte a rebours : sans texte, donc
                # sans variante de texte (une seule image par code).
                if bp.actives(c)[j]["id"] in timed and (not tv or tv == bp.text_variants(c)[0]):
                    base2 = OUT2 / f"{c['id']}_{j}_{code}{v}"
                    jobs2.append((render_second, (c, j, text, font, small, rec), dict(weapon=weapon, variant=v), f"{base2}.png", bp.job_key(ing, j, text, weapon, v, "second")))
                    for sec in SECONDS:
                        jobs2.append((render_second, (c, j, text, font, small, rec), dict(seconds=sec, weapon=weapon, variant=v), f"{base2}_s{sec}.png", bp.job_key(ing, j, text, weapon, v, "second", sec)))
    count = bp.run_jobs(jobs, OUT, keep_others=bool(only))
    count2 = bp.run_jobs(jobs2, OUT2, keep_others=bool(only))
    print(f"{count2} images de second actif dans resourcepack-prive/")
    print(f"{count} images d'actifs ({len(chars)} personnages, {len(timed)} capacites avec compte a rebours) dans resourcepack-prive/")


if __name__ == "__main__":
    main()
