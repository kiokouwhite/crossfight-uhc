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
import build_actifs as ba  # noqa: E402
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
    "duo_ruby_weiss": "Vous êtes sur la patinoire d'Ice Flower : Vitesse III tant que vous y restez.",
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
    "duo_ruby_weiss": "Ice Flower : Vitesse III",
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
    "corrin_ult": "La vague de Corrin vous a emporté : éjecté à 30 blocs de haut, et Lenteur III pendant 10 secondes.",
    "sage_lenteur": "Vous êtes sur les cristaux de Sage : Lenteur II tant que vous y restez.",
    "takumi_yumi": "Une flèche de Takumi vous a touché : Lenteur pendant 10 secondes.",
    "mikasa_assommage": "Mikasa vous a assommé : immobile et aveuglé pendant 3 secondes.",
    "remi_electricite": "L'éclair de Remi vous a électrisé : immobilisé 2 secondes.",
    "seraphina_arret": "Seraphina a arrêté le temps : immobilisé et aveuglé 3 secondes.",
    "seraphina_choc": "Le Choc Temporel de Seraphina vous immobilise 2 secondes.",
    "neon_relais": "L'Éclair Relais de Neon vous a pris : immobilisé 3 secondes.",
    "sova_electro": "L'Électroflèche de Sova a explosé sur vous : 2 cœurs et immobilisé 1 seconde.",
    "crystal_cristal": "Crystal vous a cristallisé : immobilisé 3 secondes.",
    "crystal_surcharge": "La Surcharge de Flux de Crystal : Lenteur, Faiblesse et Wither II pendant 5 secondes.",
    "crystal_ult": "Œil pour Œil de Crystal : Wither III, Lenteur II et Faiblesse IV pendant 10 secondes.",
    "yuki_ult": "Yuki est Imparfaite : Obscurité et Lenteur II pendant 20 secondes.",
    "aida_bulle": "Vous avez touché une bulle d'Aïda : immobilisé 3 secondes.",
    "aida_jet": "Le jet d'eau d'Aïda vous a touché : Lenteur 5 secondes.",
    "duo_weiss_yang": "Vous êtes sur le glyphe de Freezerburn : ralenti et enflammé tant que vous y restez.",
    "duo_weiss_blake": "Checkmate : le coup de Weiss ou de Blake vous gèle sur place 5 secondes.",
    "duo_blake_yang": "Un clone de Yang a explosé près de vous : 3 cœurs et vous brûlez.",
    "duo_ruby_yang": "Strawberry Sunrise : Ruby vous a percuté, 5 cœurs.",
    "yang_colere": "La Colère Flamboyante de Yang : ses coups vous enflamment.",
    "weiss_glyphs1": "Un Glyph de Weiss vous a touché : feu, cage de glace, recul ou Lenteur II selon sa Dust.",
    "weiss_glyphs2": "Un Glyph de Weiss vous a pris : feu, foudre, envol ou sol qui s'ouvre selon sa Dust.",
    "weiss_glyphs_passif": "La Dust de Weiss : feu, foudre, recul ou Lenteur selon sa couleur.",
    "blake_ombre": "Vous avez frappé une copie de Blake : aveuglé 4 secondes.",
    "sova_reco": "La flèche de reconnaissance de Sova vous révèle et vous ralentit.",
    "sova_drone": "Le drone de Sova vous tire dessus : Faiblesse.",
    "sova_ult": "Un tir d'énergie de Sova vous a traversé : 3 cœurs, et vous êtes révélé.",
    "eren_t_colossal": "La vapeur du Titan Colossal d'Eren : 1 cœur, et vous brûlez.",
    "mikasa_cible": "Mikasa vous a pris pour cible : vous êtes en surbrillance 1 minute.",
    "arlo_enfermement": "Arlo vous enferme dans une bulle dorée : vous n'en sortez pas tant qu'il la tient.",
    "arlo_etouffement": "Arlo vous étouffe : plaqué au sol, vous perdez 1 cœur par seconde tant qu'il tient la bulle.",
    "remi_dash": "Vous êtes dans la traînée électrique de Remi : Lenteur et 1 cœur par seconde.",
    "remi_electrocution": "Vous avez frappé Remi électrisée : 0,5 cœur perdu et Faiblesse 10 secondes.",
    "remi_ult": "Vous êtes dans le Dôme électrique de Remi : 1 cœur par seconde, Lenteur, et la foudre vous électrise.",
    "seraphina_ult": "L'Explosion temporelle de Seraphina vous a atteint : immobilisé 5 secondes.",
    "kishi_explosion": "La lumière de Kishi vous aveugle 1 seconde.",
    "kishi_lumiere": "La Lumière Étincelante de Kishi vous aveugle 3 secondes.",
    "kura_meurtre": "Kura vous a pris pour cible : surbrillance et Faiblesse tant qu'elle vous cible.",
    "hano_tome_aqua_affinity": "Hano vous enferme dans une cage d'eau pendant 6 secondes.",
    "hano_tome_fire_aspect": "Hano vous enferme dans une bulle de lave pendant 5 secondes.",
    "hano_tome_impaling": "La lance de Hano vous immobilise 10 secondes.",
    "hano_tome_loyalty": "Le trident de Hano vous a touché : Faiblesse III.",
    "hano_tome_lure": "Vous avez frappé le leurre de Hano : Wither 5 secondes.",
    "hano_tome_respiration": "La Concentration Ultime de Hano : Wither II.",
    "hano_tome_riptide": "Le Dash Poison de Hano vous a touché : Faiblesse IV pendant 10 secondes.",
    "hano_tome_sharpness": "Le javelot de Hano vous a touché : Faiblesse IV.",
    "hano_tome_sweeping_edge": "Le Triple Slash de Hano : Wither II pendant 5 secondes.",
    "hano_tome_thorns": "Vous avez touché Hano : empoisonné 2 secondes.",
    "hano_tome_unbreaking": "Brise Glace de Hano : vous prenez le double des dégâts pendant 5 secondes.",
    "hano_tome_channeling": "Vous êtes dans le Paratonnerre de Hano : Lenteur et 1 cœur par seconde.",
    "gv_cerbere": "Une balle de Gunvolt vous a touché : un stack d'Électricité Septimale pendant 3 secondes.",
    "acura_dash": "Acura vous a percuté : elle vous a pris pour cible.",
    "kirin_talisman": "Un talisman de Kirin vous marque pendant 10 secondes : elle peut se téléporter sur vous.",
    "pinkou_cocon": "Pinkou vous a enfermé avec elle dans son cocon pendant 5 secondes.",
    "pinkou_vigne": "Vous êtes dans les vignes de Pinkou : Poison 5 secondes.",
    "pinkou_ult": "Vous marchez sur les ronces de Pinkou : Poison 5 secondes.",
    "aida_clone": "Vous avez frappé Aïda dans son clone d'eau : Faiblesse et Lenteur 5 secondes.",
    "aida_eponge": "L'Éponge d'Aïda vous renvoie les mauvais effets que vous lui donnez.",
    "crystal_lance": "La lance de Crystal : Wither II pendant 5 secondes.",
    "crystal_patin": "Vous êtes sur la traînée de cristal de Crystal : Faiblesse III.",
    "netsu_jetee": "Crawnatus de Netsu vous a touché : Cécité 3 secondes.",
    "hannah_abnegation": "Hannah vous provoque : pendant 10 secondes, vous ne pouvez attaquer qu'elle.",
    "eirika_marque": "Eirika vous a posé une Marque de Sang : 0,5 cœur toutes les 2 secondes, 6 secondes.",
    "eirika_effroi": "L'Effroi d'Eirika : Nausée, Cécité, Obscurité, Lenteur ou Wither selon vos Marques de Sang.",
    "yuki_desesperee": "Yuki est Désespérée : Lenteur, Cécité, Obscurité ou Faiblesse selon la vie qui lui reste.",
    "byleth_ult": "Byleth vous a emmené dans une autre dimension : vous n'en sortez que si elle meurt.",
}
FROM_ENEMY_SHORT = {
    "marth_contre": "Contre de Marth : dégâts renvoyés, éjection, stun 1 s",
    "corrin_ult": "Vague de Corrin : éjection, Lenteur III 10 s",
    "sage_lenteur": "Cristaux de Sage : Lenteur II",
    "takumi_yumi": "Flèche de Takumi : Lenteur 10 s",
    "mikasa_assommage": "Assommé par Mikasa : stun + Cécité 3 s",
    "remi_electricite": "Électrisé par Remi : stun 2 s",
    "seraphina_arret": "Arrêt Temporel : stun + Cécité 3 s",
    "seraphina_choc": "Choc Temporel : stun 2 s",
    "neon_relais": "Éclair Relais : stun 3 s",
    "sova_electro": "Électroflèche : 2 PV, stun 1 s",
    "crystal_cristal": "Cristallisé : stun 3 s",
    "crystal_surcharge": "Surcharge : Lenteur + Faibl. + Wither II 5 s",
    "crystal_ult": "Œil pour Œil : Wither III, Lent. II, Faibl. IV 10 s",
    "yuki_ult": "Imparfaite : Obscurité + Lenteur II 20 s",
    "aida_bulle": "Bulle d'Aïda : stun 3 s",
    "aida_jet": "Jet d'Aïda : Lenteur 5 s",
    "duo_weiss_yang": "Freezerburn : Lenteur + feu",
    "duo_weiss_blake": "Checkmate : gelé 5 s",
    "duo_blake_yang": "Clone de Yang : 3 PV, feu",
    "duo_ruby_yang": "Strawberry Sunrise : 5 PV",
    "yang_colere": "Colère de Yang : feu",
    "weiss_glyphs1": "Glyph de Weiss : selon sa Dust",
    "weiss_glyphs2": "Glyph de Weiss : selon sa Dust",
    "weiss_glyphs_passif": "Dust de Weiss : selon sa couleur",
    "blake_ombre": "Copie de Blake : Cécité 4 s",
    "sova_reco": "Reconnaissance : révélé, ralenti",
    "sova_drone": "Drone de Sova : Faiblesse",
    "sova_ult": "Tir de Sova : 3 PV, révélé",
    "eren_t_colossal": "Vapeur du Colossal : 1 PV, feu",
    "mikasa_cible": "Cible de Mikasa : surbrillance 1 min",
    "arlo_enfermement": "Enfermé par Arlo",
    "arlo_etouffement": "Étouffé par Arlo : -1 PV / s",
    "remi_dash": "Traînée de Remi : Lenteur, -1 PV / s",
    "remi_electrocution": "Remi électrisée : -0,5 PV, Faibl. 10 s",
    "remi_ult": "Dôme de Remi : -1 PV / s, Lenteur, stun",
    "seraphina_ult": "Explosion temporelle : stun 5 s",
    "kishi_explosion": "Lumière de Kishi : Cécité 1 s",
    "kishi_lumiere": "Lumière de Kishi : Cécité 3 s",
    "kura_meurtre": "Cible de Kura : surbrillance, Faiblesse",
    "hano_tome_aqua_affinity": "Cage d'eau de Hano : 6 s",
    "hano_tome_fire_aspect": "Bulle de lave de Hano : 5 s",
    "hano_tome_impaling": "Lance de Hano : stun 10 s",
    "hano_tome_loyalty": "Trident de Hano : Faiblesse III",
    "hano_tome_lure": "Leurre de Hano : Wither 5 s",
    "hano_tome_respiration": "Concentration de Hano : Wither II",
    "hano_tome_riptide": "Dash Poison : Faiblesse IV 10 s",
    "hano_tome_sharpness": "Javelot de Hano : Faiblesse IV",
    "hano_tome_sweeping_edge": "Triple Slash : Wither II 5 s",
    "hano_tome_thorns": "Épines de Hano : Poison 2 s",
    "hano_tome_unbreaking": "Brise Glace : dégâts x2, 5 s",
    "hano_tome_channeling": "Paratonnerre : Lenteur, -1 PV / s",
    "gv_cerbere": "Balle de Gunvolt : 1 stack, 3 s",
    "acura_dash": "Ciblé par Acura",
    "kirin_talisman": "Marqué par Kirin : 10 s",
    "pinkou_cocon": "Cocon de Pinkou : 5 s",
    "pinkou_vigne": "Vignes de Pinkou : Poison 5 s",
    "pinkou_ult": "Ronces de Pinkou : Poison 5 s",
    "aida_clone": "Clone d'Aïda : Faibl. + Lenteur 5 s",
    "aida_eponge": "Éponge d'Aïda : effets renvoyés",
    "crystal_lance": "Lance de Crystal : Wither II 5 s",
    "crystal_patin": "Cristal de Crystal : Faiblesse III",
    "netsu_jetee": "Crawnatus : Cécité 3 s",
    "hannah_abnegation": "Provoqué par Hannah : 10 s",
    "eirika_marque": "Marque de Sang : -0,5 PV / 2 s",
    "eirika_effroi": "Effroi d'Eirika : selon vos Marques",
    "yuki_desesperee": "Désespérée : selon la vie de Yuki",
    "byleth_ult": "Dimension de Byleth",
}

# Les comptes a rebours dessines sur les cartes de droite (le plugin montre le palier au-dessus du temps restant),
# pour les capacites qui posent un effet limite sur un autre joueur (lu dans les sources, comme build_actifs).
SECONDS = list(range(1, 11)) + [15, 20, 30]


def timed_on_others():
    import re
    selfv = r'(?:p|player|sp|self|caster|attacker|killer|owner)'
    pat = re.compile(r'Effects\.(?:give|refresh)\((?!' + selfv + r'\b)|(?<![A-Za-z_])(?!' + selfv + r'\b)[A-Za-z_]+\.addPotionEffect\(|Effects\.root\((?!' + selfv + r'\b)|Effects\.freeze\(')
    out = set()
    for f in ba.SRC.rglob("*.java"):
        for part in re.split(r'\n(?=\s*(?:public |private |protected )?(?:static )?(?:final )?class \w+)', f.read_text(encoding="utf-8")):
            m = re.search(r'super\("([a-z0-9_]+)"', part)
            if m and pat.search(part):
                out.add(m.group(1))
    return out


def text_of(a, text=1, ally=True):
    if not ally and a["id"] in FROM_ENEMY:
        return FROM_ENEMY_SHORT.get(a["id"], FROM_ENEMY[a["id"]]) if text == 2 else FROM_ENEMY[a["id"]]
    if text == 2:
        return FOR_YOU_SHORT.get(a["id"]) or bp.text_of(a, 2)
    return FOR_YOU.get(a["id"]) or accent(a["description"])


def render(a, who, row, text, ally, rec, font, slide=0.0, seconds=None, small=None):
    """Une carte, alignee a droite : l'icone au bord, le texte a sa gauche."""
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    edge = ALLY if ally else ENEMY
    y = MARGIN + row * (ROW_TEXT + 4 if text else CELL + 4)
    x = W - MARGIN - CELL
    if text:
        lines = bp.wrap(text_of(a, text, ally), font, TEXT_W)
        if len(lines) > MAX_LINES:        # trop long pour la carte : le texte court, qui tient toujours
            lines = bp.wrap(text_of(a, 2, ally), font, TEXT_W)
        if len(lines) > MAX_LINES:        # encore trop long : coupe, avec des points de suspension
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
    if seconds:
        ba.badge(im, x, y, seconds, small, edge)
    if slide:      # en glissement : decalee vers la droite, hors de l'ecran
        width = (CELL + 10 + TEXT_W + 6 if text else CELL) + MARGIN
        out = Image.new("RGBA", (W, H), (0, 0, 0, 0))
        bp.slide_in(out, im, round(width * slide))
        im = out
    return im


def sides(a):
    """Les camps possibles : tout peut venir d'un adversaire ; d'un allie, ce qui est ecrit pour (FOR_YOU) ou parle d'allies."""
    if a["id"] in DUO_SIDES:
        return DUO_SIDES[a["id"]]
    return ("a", "e") if a["id"] in FOR_YOU or "allie" in a["description"].lower() else ("e",)


# Les Ultimes en Duo de RWBY ne sont pas des capacites du plugin (un seul objet pour les six) : ceux qui touchent
# un autre joueur ont pourtant leur carte, a leur nom et avec leur icone ("duo_<a>_<b>", RWBY.duos). Le camp ou
# chacun se montre : aux adversaires qu'il gene, aux allies qu'il aide (la patinoire d'Ice Flower).
DUO_SIDES = {"duo_weiss_yang": ("e",), "duo_weiss_blake": ("e",), "duo_blake_yang": ("e",), "duo_ruby_yang": ("e",), "duo_ruby_weiss": ("a",)}
# Ceux dont l'effet a une duree : la carte porte son compte a rebours (Checkmate : gele 5 secondes).
DUO_TIMED = {"duo_weiss_blake"}


def duo_items(data):
    """Les cartes des Ultimes en Duo : une fausse capacite par paire, au nom de l'attaque, signee des deux prenoms."""
    out = []
    for l in data:
        first = {c["id"]: c["name"].split()[0] for c in l["characters"]}
        for d in l.get("duos", []):
            if d["id"] in DUO_SIDES:
                out.append(({"id": d["id"], "name": d["name"], "description": d["description"], "type": "Actif", "weapon": False, "icon": "amethyst_cluster"},
                            f"{first[d['a']]} et {first[d['b']]}"))
    return out


def main():
    data = json.loads(bp.DATA.read_text(encoding="utf-8"))
    rec = bp.recipes()
    font = ImageFont.truetype(str(bp.FONT), 24)
    small = ImageFont.truetype(str(bp.FONT), 16)
    timed = timed_on_others() | DUO_TIMED
    pairs = [(a, c["name"]) for l in data for c in l["characters"] for a in c["abilities"]] + duo_items(data)
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
        ing = [a, who, bp.TEXTS.get(a["id"]), rec.get(a["id"]), FOR_YOU.get(a["id"]), FOR_YOU_SHORT.get(a["id"]), FROM_ENEMY.get(a["id"]), FROM_ENEMY_SHORT.get(a["id"]), a["id"] in timed]
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
                    # Le compte a rebours : pour un adversaire toujours, pour un allie seulement si ce n'est pas un passif
                    # (un passif d'allie se renouvelle sans cesse : le compte ne voudrait rien dire).
                    if a["id"] in timed and (not ally or a["type"] != "Passif"):
                        for sec in SECONDS:
                            jobs.append((render, (a, who, row, text, ally, rec, font), dict(seconds=sec, small=small), f"{base}_s{sec}.png", bp.job_key(ing, row, text, ally, "s", sec)))
    count = bp.run_jobs(jobs, OUT)
    print(f"{count} images d'influences ({len(items)} capacites) dans resourcepack-prive/")


if __name__ == "__main__":
    main()
