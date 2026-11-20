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
    return out


def badge(im, x, y, seconds, small, edge=None):
    """Les secondes restantes, dans une pastille sombre en bas a droite de l'icone (liseret de la couleur de la carte)."""
    edge = edge or EDGE
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    label = str(seconds)
    w = int(d.textlength(label, font=small)) + 6
    x0, y0 = x + CELL - w + 2, y + CELL - 15
    d.rectangle([x0, y0, x0 + w - 1, y0 + 14], fill=(18, 14, 22, 235), outline=edge)
    d.text((x0 + 3, y0 + 1), label, font=small, fill=(255, 240, 180, 255))


def render(character, j, text, font, small, rec, slide=0.0, seconds=None, weapon=False, variant=""):
    a = bp.actives(character)[j]
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    y = MARGIN + bp.block_height(character, text, font, weapon, variant)
    lines = bp.wrap(bp.text_of(a, text), font, TEXT_W) if text else []
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
    jobs = []
    for c in chars:
        ing = bp.ingredients(c, rec) + [sorted(timed & {a["id"] for a in c["abilities"]})]
        for j in range(len(bp.actives(c))):
            # Le troisieme champ du nom : texte (0 aucun, 1 long, 2 court) + 3 x carte de l'arme au-dessus (si le personnage en a une).
            for code, v in [(k, v) for k in range(6 if bp.weapon_abilities(c) else 3) for v in (bp.variants(c) if k >= 3 else [""])]:
                text, weapon = code % 3, code >= 3
                base = OUT / f"{c['id']}_{j}_{code}{v}"
                jobs.append((render, (c, j, text, font, small, rec), dict(weapon=weapon, variant=v), f"{base}.png", bp.job_key(ing, j, text, weapon, v)))
                for kind, steps in (("i", bp.SLIDE_IN), ("o", bp.SLIDE_OUT)):
                    for k, f in enumerate(steps, 1):
                        jobs.append((render, (c, j, text, font, small, rec), dict(slide=f, weapon=weapon, variant=v), f"{base}_{kind}{k}.png", bp.job_key(ing, j, text, weapon, v, kind, k)))
                for sec in SECONDS if bp.actives(c)[j]["id"] in timed else ():
                    jobs.append((render, (c, j, text, font, small, rec), dict(seconds=sec, weapon=weapon, variant=v), f"{base}_s{sec}.png", bp.job_key(ing, j, text, weapon, v, "s", sec)))
    count = bp.run_jobs(jobs, OUT, keep_others=bool(only))
    print(f"{count} images d'actifs ({len(chars)} personnages, {len(timed)} capacites avec compte a rebours) dans resourcepack-prive/")


if __name__ == "__main__":
    main()
