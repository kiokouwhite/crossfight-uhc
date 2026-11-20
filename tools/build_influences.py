#!/usr/bin/env python3
"""En haut a droite de l'ecran : ce que les capacites des autres font sur toi (hud/InfluenceHud.java).

Usage :  python3 tools/build_influences.py                      ecrit les images dans le pack complet
         python3 tools/build_influences.py --apercu image.png   planche de quelques cas

Le pendant des passifs de gauche (tools/build_passifs.py) : l'icone de la capacite d'un allie qui te donne un
bonus (bordure bleue) ou d'un adversaire qui te donne un malus (bordure rouge), et avec /cf passif show son nom,
le personnage qui la lance, et ce qu'elle te fait. Deux lignes au plus, une image par capacite, par ligne, avec
ou sans texte et par camp : influence/<capacite>_<ligne>_<texte>_<a|e>.png dans resourcepack-prive/.
"""
import json
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

sys.path.insert(0, str(Path(__file__).resolve().parent))
import build_passifs as bp  # noqa: E402
from build_guide import accent  # noqa: E402

OUT = bp.ROOT / "resourcepack-prive" / "assets" / "crossfight" / "textures" / "influence"
W, H, MARGIN, CELL = bp.W, bp.H, bp.MARGIN, bp.CELL
TEXT_W = 300                      # plus large qu'a gauche : trois lignes au plus, pour rester au-dessus du tableau
MAX_LINES = 3
ROW_TEXT = 22 + MAX_LINES * 18 + 6    # hauteur reservee a une ligne avec texte
ALLY, ENEMY = (80, 150, 255, 255), (235, 85, 70, 255)            # allie : bleu ; adversaire : rouge
TITLE_ALLY, TITLE_ENEMY = (160, 200, 255, 255), (255, 150, 130, 255)

# Ce que la capacite d'un allie te fait, dit pour toi. A defaut, la description de la capacite.
FOR_YOU = {
    "marth_charisme": "Tant que vous êtes à moins de 10 blocs de Marth, vous avez Vitesse.",
    "marth_leader": "Tant que Marth a plus de 5 cœurs, vous avez Force.",
    "marth_ult": "Marth a touché des ennemis avec Coup Critique : vous avez Force (un niveau par ennemi touché) pendant 1 minute.",
    "corrin_gardien": "Corrin est à moins de 10 blocs de vous : elle a Résistance.",
    "byleth_tutorat": "Byleth vous fait progresser : arme, physique ou capacités.",
    "sage_soin": "Sage vous soigne de 3 cœurs en 3 secondes.",
    "armin_strategie": "Armin vous donne Force, Résistance ou Vitesse, tant qu'il vous garde choisi.",
    "arlo_ult": "Tant que vous êtes dans la cage de verre d'Arlo, vous avez Force.",
    "kishi_aura": "Kishi vous a choisi : vous avez Force, un niveau par cône de lumière (Force IV au plus).",
    "hano_tome_depth_strider": "Dans la zone d'eau d'Hano, vous avez Vitesse.",
    "pinkou_ronces": "Une armure de ronces vous entoure pendant 10 secondes : qui vous frappe perd 2 cœurs.",
    "pinkou_ult": "Sur les ronces de Pinkou, vous régénérez.",
}


# La version courte (reglage Textes courts) de ce qu'un allie te fait ; a defaut, le texte court de la capacite.
FOR_YOU_SHORT = {
    "marth_charisme": "Vitesse près de Marth (10 blocs)",
    "marth_leader": "Force tant que Marth a > 5 PV",
    "marth_ult": "Force 1 min (Coup Critique)",
    "corrin_gardien": "Corrin près de vous : elle a Résistance",
    "byleth_tutorat": "Byleth vous fait progresser",
    "sage_soin": "Soin 3 PV en 3 s",
    "armin_strategie": "Bonus d'Armin (Force, Résist. ou Vitesse)",
    "arlo_ult": "Force dans la cage d'Arlo",
    "kishi_aura": "Force = cônes de Kishi (IV max)",
    "hano_tome_depth_strider": "Vitesse dans l'eau d'Hano",
    "pinkou_ronces": "Thorns IV 10 s",
    "pinkou_ult": "Régénération sur les ronces",
}


# Ce que la capacite d'un adversaire vient de te faire, dit pour toi (le Contre de Marth) ; a defaut, sa description.
FROM_ENEMY = {
    "marth_contre": "Vous avez frappé Marth pendant son Contre : vos dégâts vous sont renvoyés, vous êtes éjecté et immobilisé 1 seconde.",
}
FROM_ENEMY_SHORT = {
    "marth_contre": "Contre de Marth : dégâts renvoyés, éjection, stun 1 s",
}


def text_of(a, text=1, ally=True):
    if not ally and a["id"] in FROM_ENEMY:
        return FROM_ENEMY_SHORT.get(a["id"], FROM_ENEMY[a["id"]]) if text == 2 else FROM_ENEMY[a["id"]]
    if text == 2:
        return FOR_YOU_SHORT.get(a["id"]) or bp.text_of(a, 2)
    return FOR_YOU.get(a["id"]) or accent(a["description"])


def render(a, who, row, text, ally, rec, font, slide=0.0):
    """Une carte, alignee a droite : l'icone au bord, le texte a sa gauche."""
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    edge = ALLY if ally else ENEMY
    y = MARGIN + row * (ROW_TEXT + 4 if text else CELL + 4)
    x = W - MARGIN - CELL
    if text:
        lines = bp.wrap(text_of(a, text, ally), font, TEXT_W)
        if len(lines) > MAX_LINES:        # trop long : coupe, avec des points de suspension
            lines = lines[:MAX_LINES]
            while lines[-1] and d.textlength(lines[-1] + "…", font=font) > TEXT_W:
                lines[-1] = lines[-1].rsplit(" ", 1)[0] if " " in lines[-1] else lines[-1][:-1]
            lines[-1] += "…"
        h = max(CELL, 22 + len(lines) * 18 + 6)
        left = x - 10 - TEXT_W - 6
        d.rectangle([left, y, W - MARGIN - 1, y + h - 1], fill=bp.PANEL, outline=edge)
        d.text((left + 6, y + 2), f"{accent(a['name'])} · {who}", font=font, fill=TITLE_ALLY if ally else TITLE_ENEMY)
        for k, line in enumerate(lines):
            d.text((left + 6, y + 21 + k * 18), line, font=font, fill=bp.WHITE)
    d.rectangle([x, y, x + CELL - 1, y + CELL - 1], fill=bp.PANEL, outline=edge)
    d.rectangle([x + 1, y + 1, x + CELL - 2, y + CELL - 2], outline=edge[:3] + (110,))
    im.alpha_composite(bp.icon_for(a, rec), (x + 4, y + 4))
    if slide:      # en glissement : decalee vers la droite, hors de l'ecran
        width = (CELL + 10 + TEXT_W + 6 if text else CELL) + MARGIN
        out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        bp.slide_in(out, im, round(width * slide))
        im = out
    return im


def sides(a):
    """Les camps possibles : tout peut venir d'un adversaire ; d'un allie, ce qui est ecrit pour (FOR_YOU) ou parle d'allies."""
    return ("a", "e") if a["id"] in FOR_YOU or "allie" in a["description"].lower() else ("e",)


def main():
    data = json.loads(bp.DATA.read_text(encoding="utf-8"))
    rec = bp.recipes()
    font = ImageFont.truetype(str(bp.FONT), 24)
    pairs = [(a, c["name"]) for l in data for c in l["characters"] for a in c["abilities"]]
    seen, items = set(), []
    for a, who in pairs:                # une capacite commune (Synergie, Burst...) une seule fois
        if a["id"] not in seen:
            seen.add(a["id"])
            items.append((a, who))
    if len(sys.argv) >= 3 and sys.argv[1] == "--apercu":
        by = {a["id"]: (a, who) for a, who in items}
        sheet = Image.new("RGBA", (W * 2, H), (70, 110, 60, 255))
        for k, text in enumerate((0, 1)):
            for row, (ident, ally) in enumerate((("marth_charisme", True), ("sage_lenteur", False))):
                a, who = by[ident]
                sheet.alpha_composite(render(a, who, row, text, ally, rec, font), (k * W, 0))
        sheet.save(sys.argv[2])
        print(sys.argv[2])
        return
    OUT.mkdir(parents=True, exist_ok=True)
    jobs = []
    for a, who in items:
        ing = [a, who, bp.TEXTS.get(a["id"]), rec.get(a["id"]), FOR_YOU.get(a["id"]), FOR_YOU_SHORT.get(a["id"]), FROM_ENEMY.get(a["id"]), FROM_ENEMY_SHORT.get(a["id"])]
        for side in sides(a):
            ally = side == "a"
            for text in (0, 1, 2):
                for row in (0, 1):
                    base = OUT / f"{a['id']}_{row}_{text}_{side}"
                    jobs.append((render, (a, who, row, text, ally, rec, font), {}, f"{base}.png", bp.job_key(ing, row, text, ally)))
                    # La carte arrive toujours en haut (la plus recente) et peut partir des deux lignes.
                    steps = [("i", bp.SLIDE_IN)] if row == 0 else []
                    for kind, fracs in steps + [("o", bp.SLIDE_OUT)]:
                        for k, f in enumerate(fracs, 1):
                            jobs.append((render, (a, who, row, text, ally, rec, font), dict(slide=f), f"{base}_{kind}{k}.png", bp.job_key(ing, row, text, ally, kind, k)))
    count = bp.run_jobs(jobs, OUT)
    print(f"{count} images d'influences ({len(items)} capacites) dans resourcepack-prive/")


if __name__ == "__main__":
    main()
