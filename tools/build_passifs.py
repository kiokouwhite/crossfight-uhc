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
    """Les passifs portes par l'arme : une seule carte, celle de l'arme. (Un actif d'arme, comme la Falchion,
    n'apparait que quand il sert : la carte d'actif, sous les passifs.)"""
    return [a for a in character["abilities"] if a["weapon"] and a["type"] == "Passif"]


# Les armes dont la carte change selon un choix du joueur : variante -> textes (le suffixe du code de l'image).
WEAPON_VARIANTS = {
    "corrin": {
        "l": {"vous": "Voie de la Lumière : la Yato devient Éclatante à 2 kills d'équipe (Vitesse, Résistance), puis Alpha à 5 (Vitesse II, Résistance, 15 cœurs).",
              "court": "Lumière : Éclatante à 2 kills équipe, Alpha à 5"},
        "t": {"vous": "Voie des Ténèbres : la Yato devient Yato de l'Ombre (9,5) à 1 kill de Corrin, puis Oméga (10,5) à 5.",
              "court": "Ténèbres : Ombre à 1 kill, Oméga à 5"},
    },
}


def variants(character):
    return [""] + list(WEAPON_VARIANTS.get(character["id"], {}))


def weapon_lines(character, text, font, variant=""):
    """Le texte de la carte de l'arme : chaque capacite de l'arme, son nom puis son texte (ou celui de la variante)."""
    lines = []
    v = WEAPON_VARIANTS.get(character["id"], {}).get(variant)
    if v:
        return wrap(v["court" if text == 2 else "vous"], font, TEXT_W)
    for a in weapon_abilities(character):
        # La capacite qui porte le nom de l'arme (Falchion, Yato...) : son texte seul, le titre suffit.
        same = accent(a["name"]) == accent(character.get("weapon") or "")
        lines += wrap(("" if same else accent(a["name"]) + " : ") + text_of(a, text), font, TEXT_W)
    return lines


def weapon_height(character, text, font, variant=""):
    if not weapon_abilities(character):
        return 0
    return (CELL if not text else max(CELL, 22 + len(weapon_lines(character, text, font, variant)) * 18 + 6)) + 4


def weapon_edge(character):
    """La bordure de l'arme : verte, comme les passifs (sa carte ne porte que des passifs)."""
    return EDGE


def weapon_icon(character):
    """L'image de l'arme : l'objet du jeu (l'export donne sa matiere), a defaut l'objet de sa premiere capacite."""
    return item_texture(character.get("weaponIcon") or weapon_abilities(character)[0].get("icon") or "iron_sword")


def draw_weapon(im, d, character, text, font, variant=""):
    """La carte de l'arme, en tete de la colonne de gauche : bordure verte, sans fleche ni croix."""
    y = MARGIN
    edge = weapon_edge(character)
    lines = weapon_lines(character, text, font, variant) if text else []
    h = weapon_height(character, text, font, variant) - 4
    if text:
        d.rectangle([MARGIN, y, MARGIN + CELL + 10 + TEXT_W + 6, y + h - 1], fill=PANEL, outline=edge)
    d.rectangle([MARGIN, y, MARGIN + CELL - 1, y + CELL - 1], fill=PANEL, outline=edge)
    d.rectangle([MARGIN + 1, y + 1, MARGIN + CELL - 2, y + CELL - 2], outline=edge[:3] + (120,))
    if variant:
        # L'icone un peu plus petite, en haut de la case, et sous elle une fenetre transparente centree : le badge
        # d'avancement, porte par l'image de l'orbe (tools/build_ultime_franuka.py), se dessine dessous et s'y voit.
        im.alpha_composite(weapon_icon(character).resize((24, 24), Image.NEAREST), (MARGIN + 8, y + 3))
        im.paste((0, 0, 0, 0), (MARGIN + 2, y + 26, MARGIN + CELL - 2, y + CELL - 1))
    else:
        im.alpha_composite(weapon_icon(character), (MARGIN + 4, y + 4))
    if text:
        tx = MARGIN + CELL + 6
        d.text((tx, y + 2), accent(character.get("weapon") or "Arme"), font=font, fill=(230, 232, 245, 255))
        for k, line in enumerate(lines):
            d.text((tx, y + 21 + k * 18), line, font=font, fill=WHITE)


def row_height(a, text, font):
    """La hauteur d'une case (avec son texte, selon la description)."""
    return CELL if not text else max(CELL, 22 + len(wrap(text_of(a, text), font, TEXT_W)) * 18 + 6)


def block_height(character, text, font, weapon=False, variant=""):
    """La hauteur du bloc de gauche (l'arme si montree, puis les passifs) : la ou commence la carte d'un actif."""
    return (weapon_height(character, text, font, variant) if weapon else 0) + sum(row_height(a, text, font) + 4 for a in passives(character))


def render(character, mask, text, rec, font, frame=0, cross=0, only=None, weapon=False, no_passives=False, allies=0, variant=""):
    im = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(im)
    d.fontmode = "1"
    y = MARGIN
    if weapon and weapon_abilities(character):
        draw_weapon(im, d, character, text, font, variant)
        y += weapon_height(character, text, font, variant)
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


# Les images se rendent en parallele (un processus par coeur) : chaque tache est (fonction, arguments, chemin).
# Generation incrementale : chaque image garde dans .manifest.json l'empreinte de ses ingredients (le code des
# outils, le personnage, ses textes et ses icones, les parametres du rendu) ; une image dont l'empreinte n'a pas
# change n'est pas redessinee, et les images qui ne correspondent plus a aucune tache sont supprimees.
def tools_hash():
    import hashlib
    h = hashlib.sha1()
    for name in ("build_passifs.py", "build_actifs.py", "build_influences.py", "build_icones.py", "build_guide.py"):
        h.update((ROOT / "tools" / name).read_bytes())
    return h.hexdigest()


TOOLS_HASH = None


def job_key(*parts):
    """L'empreinte d'une image : les outils, et ce que la tache utilise (serialisable en JSON)."""
    import hashlib
    global TOOLS_HASH
    if TOOLS_HASH is None:
        TOOLS_HASH = tools_hash()
    return hashlib.sha1(json.dumps([TOOLS_HASH, parts], sort_keys=True, default=str, ensure_ascii=False).encode()).hexdigest()


def ingredients(character, rec):
    """Ce qui entre dans les images d'un personnage : sa fiche, ses textes, ses recettes d'icones."""
    ids = [a["id"] for a in character["abilities"]]
    return [character, {i: TEXTS.get(i) for i in ids}, {i: rec.get(i) for i in ids}, WEAPON_VARIANTS.get(character["id"])]


def run_jobs(jobs, out, keep_others=False):
    """jobs : (fonction, args, kwargs, chemin, empreinte). Rend ce qui a change, supprime ce qui n'a plus lieu d'etre."""
    import multiprocessing, os
    manifest_path = out / ".manifest.json"
    try:
        manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
    except Exception:
        manifest = {}
    wanted = {Path(j[3]).name: j[4] for j in jobs}
    todo = [j for j in jobs if manifest.get(Path(j[3]).name) != j[4] or not Path(j[3]).exists()]
    if not keep_others:
        for f in out.glob("*.png"):
            if f.name not in wanted:
                f.unlink()
                manifest.pop(f.name, None)
    if todo:
        with multiprocessing.Pool(max(1, os.cpu_count() or 1)) as pool:
            pool.map(_job, [j[:4] for j in todo], chunksize=8)
    for j in todo:
        manifest[Path(j[3]).name] = j[4]
    if not keep_others:
        manifest = {k: v for k, v in manifest.items() if k in wanted}
    manifest_path.write_text(json.dumps(manifest, sort_keys=True), encoding="utf-8")
    print(f"  {len(todo)} image(s) redessinee(s), {len(jobs) - len(todo)} inchangee(s)")
    return len(jobs)


def _job(job):
    fn, args, kwargs, path = job
    save(fn(*args, **kwargs), path)


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
    OUT.mkdir(parents=True, exist_ok=True)
    jobs = []
    for c in chars:
        ing = ingredients(c, rec)
        # Les passifs masques (/cf hud), la carte de l'arme seule : <perso>_arme_<texte>.
        for text in (0, 1, 2) if weapon_abilities(c) else ():
            for v in variants(c):
                jobs.append((render, (c, 0, text, rec, font), dict(weapon=True, no_passives=True, variant=v), OUT / f"{c['id']}_arme_{text}{v}.png", job_key(ing, 0, text, "arme", v)))
        ally_bits = sum(1 << i for i, a in enumerate(passives(c)) if a["id"] in ALLY_PASSIVES)
        # Les sous-ensembles des passifs allumes qui peuvent agir sur des allies : "a<bits>" dans le nom.
        n_pass = len(passives(c))
        for mask, allies in [(m, am) for m in range(1 << n_pass) for am in range(1 << n_pass) if am & ~(m & ally_bits) == 0]:
            tag = f"{mask}a{allies}" if allies else str(mask)
            # Le troisieme champ du nom : texte (0 aucun, 1 long, 2 court) + 3 x carte de l'arme (si le personnage en a une).
            # Avec la carte de l'arme, ses variantes (Yato : l, t, en minuscules : une cle de ressource) s'ajoutent au code.
            for code, v in [(k, v) for k in range(6 if weapon_abilities(c) else 3) for v in (variants(c) if k >= 3 else [""])]:
                text, weapon = code % 3, code >= 3
                jobs.append((render, (c, mask, text, rec, font), dict(weapon=weapon, allies=allies, variant=v), OUT / f"{c['id']}_{tag}_{code}{v}.png", job_key(ing, mask, text, weapon, allies, v)))
                # Les etapes de l'animation, pour le seul passif i qui vient de changer : sa fleche s'il est
                # allume (<perso>_<allumes>_<texte>_<i>_<etape>), sa croix s'il est eteint (..._x<i>_<etape>).
                for i in range(len(passives(c))):
                    lit = mask & (1 << i)
                    for frame in range(1, len(FRAMES if lit else CROSS) + 1):
                        kw = dict(frame=frame, only=i, weapon=weapon, allies=allies, variant=v) if lit else dict(cross=frame, only=i, weapon=weapon, allies=allies, variant=v)
                        jobs.append((render, (c, mask, text, rec, font), kw, OUT / f"{c['id']}_{tag}_{code}{v}_{'' if lit else 'x'}{i}_{frame}.png", job_key(ing, mask, text, weapon, allies, v, i, frame, bool(lit))))
    count = run_jobs(jobs, OUT, keep_others=bool(only))
    print(f"{count} images de passifs ({len(chars)} personnages) dans resourcepack-prive/")


if __name__ == "__main__":
    main()
