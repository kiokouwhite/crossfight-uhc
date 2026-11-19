#!/usr/bin/env python3
"""Icones des capacites, prises dans les packs d'icones achetes (prive/icones/, jamais versionnes).

Usage :  python3 tools/build_icones.py                              ecrit les icones dans le pack complet
         python3 tools/build_icones.py --apercu image.png [licence]   planche d'apercu d'une licence

Une recette designe un dessin d'un pack de competences et sa couleur : "Wizard 23 vert", ou une fusion "fond + motif". Chaque pack
contient 32 dessins, declines dans plusieurs couleurs (fichiers Original/32/<n>.png, un bloc de 32 par couleur).
Les icones vont dans resourcepack-prive/ (images achetees, jamais versionnees) : une image, un modele et une
definition d'objet "icone/<identifiant>" par capacite. tools/build_pack.py les declare ("icone.<identifiant>") et le
plugin donne ce modele a l'objet de la capacite quand le pack complet le contient.
"""
import sys
from pathlib import Path
from PIL import Image, ImageDraw, ImageFont

ROOT = Path(__file__).resolve().parent.parent
PACKS = ROOT / "prive" / "icones" / "Collection" / "New_updates" / "New updates"

# L'ordre des blocs de couleur dans chaque pack.
COLOURS = {
    "Warrior": ["gris", "acier", "feu", "bleu", "vert", "brun", "cramoisi", "or", "violet"],
    "Wizard": ["violet", "feu", "vert", "brun", "cramoisi", "or", "bleu", "gris"],
    "Rogue": ["violet", "feu", "bleu", "vert", "brun", "cramoisi", "or", "gris"],
    "Acolyte": ["or", "feu", "bleu", "vert", "brun", "cramoisi", "violet", "gris"],
}

# Les licences dont la planche est validee : seules leurs icones vont dans le pack.
VALIDATED = ["Fire Emblem"]

# licence -> personnage -> [(capacite, identifiant dans le plugin, recette)]
RECIPES = {
    "Fire Emblem": {
        "Byleth": [
            ("Emblème de Feu", "byleth_embleme", "Warrior 27 or + Acolyte 3 feu"),
            ("Tutorat", "byleth_tutorat", "Wizard 23 or"),
            ("Déchaînement d'Épine", "byleth_epine", "Warrior 1 or"),
            ("Les Ténèbres se consumeront elles-mêmes", "byleth_ult", "Wizard 21 violet"),
        ],
        "Corrin": [
            ("Dragon Gardien", "corrin_gardien", "Acolyte 14 bleu"),
            ("Sang Draconique", "corrin_sang", "Wizard 3 cramoisi"),
            ("Dragon de l'eau", "corrin_eau", "Wizard 8 bleu"),
            ("Les Vagues Bleues de l'Océan Gris", "corrin_ult", "Wizard 29 bleu"),
        ],
        "Marth": [
            ("Contre", "marth_contre", "Warrior 27 bleu-fonce"),
            ("Charisme", "marth_charisme", "Acolyte 26 bleu-fonce"),
            ("Esprit du Leader", "marth_leader", "Acolyte 9 bleu-fonce"),
            ("Coup Critique", "marth_ult", "Warrior 2 bleu-fonce"),
        ],
        "Takumi": [
            ("Contre Rapproché", "takumi_contre", "Warrior 10 cramoisi"),
            ("Vengeance", "takumi_vengeance", "Warrior 23 cramoisi"),
            ("Furie", "takumi_furie", "Wizard 11 cramoisi"),
            ("Rayon Démoniaque", "takumi_ult", "Wizard 19 cramoisi"),
        ],
    },
    "RWBY": {
        "Ruby Rose": [
            ("Explosion de Pétales (rapide)", "ruby_petales_rapide", "Rogue 32 rouge"),
            ("Explosion de Pétales (lent)", "ruby_petales_lent", "Wizard 30 rouge"),
            ("Yeux d'Argent", "ruby_yeux", "Acolyte 25 gris"),
            ("Rush du Chaperon Rouge", "ruby_ult", "Warrior 3 rouge"),
        ],
        "Weiss Schnee": [
            ("Glyphs (Type 1)", "weiss_glyphs1", "Wizard 17 bleu"),
            ("Glyphs (Type 2)", "weiss_glyphs2", "Wizard 26 bleu"),
            ("Glyphs (passif)", "weiss_glyphs_passif", "Wizard 18 bleu"),
            ("Invocation", "weiss_ult", "Wizard 9 bleu"),
        ],
        "Blake Belladonna": [
            ("Ombre", "blake_ombre", "Rogue 20 violet"),
            ("Discrétion", "blake_discretion", "Rogue 16 violet"),
            ("Effacement", "blake_effacement", "Rogue 9 violet"),
            ("Instinct Animal", "blake_ult", "Rogue 13 violet"),
        ],
        "Yang Xiao Long": [
            ("Brûlure", "yang_brulure", "Wizard 7 or"),
            ("Incendie Interne", "yang_incendie", "Wizard 21 or"),
            ("Colère Flamboyante", "yang_colere", "Warrior 25 or"),
            ("Courroux Impérissable", "yang_ult", "Wizard 12 or"),
        ],
        "Duo": [
            ("Ultime en Duo", "rwby_duo", "Acolyte 4 or"),
        ],
    },
}


# Couleurs absentes des packs, derivees d'une couleur existante : (base, decalage de teinte, saturation, luminosite).
DERIVED = {"bleu-fonce": ("bleu", 0.03, 1.2, 0.68), "rouge": ("feu", -0.05, 1.1, 0.95)}


def icon(recipe: str) -> Image.Image:
    """Une recette, ou une fusion "fond + motif" : le motif, pris en 16 pixels, est pose au centre du fond."""
    if " + " in recipe:
        base, mark = recipe.split(" + ")
        im = icon(base)
        im.alpha_composite(icon_of(mark, 16), (8, 8))
        return im
    return icon_of(recipe, 32)


def icon_of(recipe: str, size: int) -> Image.Image:
    pack, number, colour = recipe.split()
    shift = DERIVED.get(colour)
    block = COLOURS[pack].index(shift[0] if shift else colour)
    im = Image.open(PACKS / f"{pack} Skills" / "Original" / str(size) / f"{block * 32 + int(number)}.png").convert("RGBA")
    if shift:
        import colorsys
        px = im.load()
        for y in range(im.height):
            for x in range(im.width):
                r, g, b, a = px[x, y]
                h, sat, v = colorsys.rgb_to_hsv(r / 255, g / 255, b / 255)
                # Les gris (lames, contours clairs) gardent leur teinte : seule la couleur fonce.
                if sat > 0.15: h, sat, v = (h + shift[1]) % 1, min(1, sat * shift[2]), v * shift[3]
                r, g, b = colorsys.hsv_to_rgb(h, sat, v)
                px[x, y] = (round(r * 255), round(g * 255), round(b * 255), a)
    return im


def preview(out: Path, licence: str):
    font = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans.ttf", 15)
    bold = ImageFont.truetype("/usr/share/fonts/truetype/dejavu/DejaVuSans-Bold.ttf", 22)
    people = RECIPES[licence]
    cell_w, cell_h, zoom = 230, 230, 4
    cols = max(len(v) for v in people.values())
    sheet = Image.new("RGBA", (cols * cell_w + 20, len(people) * (cell_h + 40) + 10), (30, 32, 44, 255))
    draw = ImageDraw.Draw(sheet)
    for r, (name, abilities) in enumerate(people.items()):
        y = r * (cell_h + 40) + 10
        draw.text((14, y), name, font=bold, fill=(255, 255, 255, 255))
        for c, (ability, _, recipe) in enumerate(abilities):
            x = c * cell_w + 10
            im = icon(recipe).resize((32 * zoom, 32 * zoom), Image.NEAREST)
            sheet.alpha_composite(im, (x + (cell_w - 32 * zoom) // 2, y + 38))
            # A la taille reelle de la barre d'action (x2), a cote.
            sheet.alpha_composite(icon(recipe).resize((64, 64), Image.NEAREST).resize((32, 32), Image.NEAREST), (x + cell_w - 44, y + 38))
            words, lines, line = ability.split(), [], ""
            for w in words:
                if draw.textlength((line + " " + w).strip(), font=font) > cell_w - 16: lines.append(line); line = w
                else: line = (line + " " + w).strip()
            lines.append(line)
            for k, text in enumerate(lines[:3]):
                draw.text((x + (cell_w - draw.textlength(text, font=font)) / 2, y + 38 + 32 * zoom + 6 + k * 18), text, font=font, fill=(225, 228, 240, 255))
            for k, text in enumerate(recipe.replace(" + ", " +\n").split("\n")):
                draw.text((x + (cell_w - draw.textlength(text, font=font)) / 2, y + 38 + 32 * zoom + 6 + (2 + k) * 18), text, font=font, fill=(130, 136, 160, 255))
    sheet.save(out)
    print(out, sheet.size)


def build():
    import json, shutil
    assets = ROOT / "resourcepack-prive" / "assets" / "crossfight"
    folders = [assets / "textures" / "item" / "icone", assets / "models" / "item" / "icone", assets / "items" / "icone"]
    for f in folders:
        if f.is_dir(): shutil.rmtree(f)
        f.mkdir(parents=True)
    count = 0
    for licence in VALIDATED:
        for abilities in RECIPES[licence].values():
            for _, ident, recipe in abilities:
                icon(recipe).save(folders[0] / f"{ident}.png")
                (folders[1] / f"{ident}.json").write_text(json.dumps({"parent": "minecraft:item/generated", "textures": {"layer0": f"crossfight:item/icone/{ident}"}}))
                (folders[2] / f"{ident}.json").write_text(json.dumps({"model": {"type": "minecraft:model", "model": f"crossfight:item/icone/{ident}"}}))
                count += 1
    print(f"{count} icones de capacites dans resourcepack-prive/")


if __name__ == "__main__":
    if len(sys.argv) >= 3 and sys.argv[1] == "--apercu":
        preview(Path(sys.argv[2]), sys.argv[3] if len(sys.argv) > 3 else "Fire Emblem")
    elif len(sys.argv) == 1:
        build()
    else:
        print(__doc__)
