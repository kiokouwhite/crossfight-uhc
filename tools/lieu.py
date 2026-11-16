#!/usr/bin/env python3
"""Prepare un lieu pour le plugin a partir d'un monde telecharge : trouve la construction, ecrit sa fiche, allege le monde.

Usage :  python3 tools/lieu.py <dossier du monde> <nom> [--titre "..."] [--auteur "..."] [--source <adresse>]
                               [--seuil 3] [--boite x1,z1,x2,z2] [--sol y] [--coeur x,z] [--profondeur 12] [--hauteur 240]
                               [--creuse] [--sortie <dossier>] [--serveur 4556]

Lit les chunks du monde (toutes versions : avant la 1.13, des numeros de blocs ; depuis, des palettes) et note, pour
chacun, combien de sortes de blocs "batis" il contient (ce qui n'est ni terrain ni vegetation). La construction est le
plus grand ensemble de chunks batis qui se touchent. De la :
  boite   le rectangle de ces chunks, et les hauteurs ou l'on trouve des blocs batis
  sol     la hauteur du premier niveau au-dessus du sol : la hauteur de surface la plus frequente dans la boite
  coeur   la ou la construction est la plus dense : c'est autour de lui qu'elle est recadree sur une petite map
--boite, --sol et --coeur remplacent ce que l'outil a trouve, quand il se trompe. La boite ne descend pas a plus de
--profondeur blocs sous le sol (le terrain de la map fera le reste) et ne monte pas a plus de --hauteur blocs au-dessus
(au-dela, le lieu ne tiendrait pas sous le ciel de Minecraft une fois pose sur le terrain).

Ecrit, dans <sortie>/<nom>/ (par defaut prive/structures/, jamais versionne : ce sont les oeuvres de leurs auteurs) :
  structure.yml        la fiche lue par le plugin (game/Structures.java)
  monde/               le monde, reduit aux fichiers de region que touche la boite (level.dat, region/, entities/)
  apercu.png           la carte des chunks batis, la boite et le coeur, pour verifier d'un coup d'oeil
A deposer ensuite dans plugins/CrossfightUHC/structures/ sur le serveur. Demande nbtlib, numpy et Pillow.

Un monde enregistre dans une version de Minecraft plus recente que celle du serveur ne peut pas y etre charge tel
quel : Paper arrete le serveur plutot que de lire un chunk venu du futur. Quand le monde est plus recent que --serveur
(le numero de version des donnees du serveur ; 4556 pour la 1.21.10), les chunks gardes sont recopies avec ce numero,
et level.dat aussi. Ce n'est pas une vraie conversion : cela ne vaut que si le monde n'utilise rien que le serveur ne
connaisse pas (entre deux versions voisines, c'est le cas des blocs ; l'outil le rappelle quand il le fait).
"""
import gzip
import io
import os
import re
import shutil
import struct
import sys
import zlib
from collections import Counter
from pathlib import Path

import nbtlib
import numpy as np
from PIL import Image, ImageDraw

ROOT = Path(__file__).resolve().parent.parent

# Ce qui n'est pas "bati" : le terrain, l'eau, la vegetation, les minerais.
NATURAL = re.compile(r"^minecraft:(air|cave_air|void_air|stone|granite|diorite|andesite|deepslate|tuff|dirt|coarse_dirt|grass_block|grass|tall_grass|short_grass|fern|"
                     r"large_fern|sand|red_sand|gravel|sandstone|red_sandstone|water|lava|bedrock|.*_ore|clay|snow|snow_block|ice|packed_ice|blue_ice|.*_leaves|.*_log|"
                     r"dandelion|poppy|.*tulip|azure_bluet|oxeye_daisy|cornflower|allium|blue_orchid|lily_of_the_valley|sunflower|lilac|rose_bush|peony|seagrass|"
                     r"tall_seagrass|kelp|kelp_plant|sugar_cane|vine|podzol|mycelium|terracotta|.*_terracotta|cactus|dead_bush|pumpkin|melon|bamboo|mossy_cobblestone|"
                     r"cobblestone|obsidian|magma_block|bubble_column|sweet_berry_bush|brown_mushroom|red_mushroom|mushroom_stem|.*_mushroom_block|infested_.*|spawner|"
                     r"cobweb|lily_pad|calcite|amethyst.*|budding_amethyst|.*_amethyst_bud|smooth_basalt|dripstone_block|pointed_dripstone|moss_block|moss_carpet|"
                     r"glow_lichen|rooted_dirt|hanging_roots|azalea.*|flowering_azalea.*|cave_vines.*|big_dripleaf.*|small_dripleaf|spore_blossom|powder_snow|mud|"
                     r".*coral.*|sea_pickle|prismarine|sponge|wet_sponge|netherrack|basalt|blackstone|soul_sand|soul_soil|glowstone|end_stone)$")
# Les memes, en numeros de blocs d'avant la 1.13.
NATURAL_IDS = {0, 1, 2, 3, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17, 18, 21, 24, 31, 32, 37, 38, 39, 40, 56, 73, 74, 78, 79, 80, 81, 82, 83, 86, 106, 111, 129, 161, 162, 175}


def chunks(region_dir: Path):
    """Chaque chunk du monde : (x, z, donnees NBT)."""
    for f in sorted(os.listdir(region_dir)):
        if not f.endswith(".mca"):
            continue
        _, rx, rz, _ = f.split(".")
        data = (region_dir / f).read_bytes()
        if len(data) < 8192:
            continue
        for i in range(1024):
            loc = struct.unpack(">I", data[i * 4:i * 4 + 4])[0]
            if not loc:
                continue
            off = (loc >> 8) * 4096
            length = struct.unpack(">I", data[off:off + 4])[0]
            raw = data[off + 5:off + 4 + length]
            try:
                tag = nbtlib.File.parse(io.BytesIO(zlib.decompress(raw) if data[off + 4] == 2 else raw))
            except Exception:
                continue
            yield int(rx) * 32 + i % 32, int(rz) * 32 + i // 32, tag.get("Level", tag)


def surface(level, modern: bool):
    """Les 256 hauteurs de surface d'un chunk (le premier bloc libre au-dessus du sol), ou None."""
    old = level.get("HeightMap")
    if old is not None and len(old) == 256:
        return np.array([int(v) for v in old])
    maps = level.get("Heightmaps") or {}
    packed = maps.get("MOTION_BLOCKING") or maps.get("WORLD_SURFACE")
    if packed is None:
        return None
    longs = [int(v) & 0xFFFFFFFFFFFFFFFF for v in packed]
    out = []
    if len(longs) == 37:                      # depuis la 1.16 : sept hauteurs de 9 bits par mot
        for word in longs:
            for k in range(7):
                out.append((word >> (9 * k)) & 511)
    else:                                     # avant : les hauteurs s'enchainent d'un mot a l'autre
        bits = 0
        for k, word in enumerate(longs):
            bits |= word << (64 * k)
        out = [(bits >> (9 * k)) & 511 for k in range(256)]
    return np.array(out[:256]) + (-64 if modern else 0)


def stamp(nbt: bytes, version: int):
    """Les donnees d'un chunk, ses numeros de version plus recents que celui du serveur ramenes a celui-ci."""
    mark, out, pos, n = b"\x03\x00\x0bDataVersion", bytearray(nbt), 0, 0
    while True:
        pos = nbt.find(mark, pos)
        if pos < 0:
            return bytes(out), n
        at = pos + len(mark)
        if struct.unpack(">i", nbt[at:at + 4])[0] > version:
            out[at:at + 4] = struct.pack(">i", version)
            n += 1
        pos = at


def restamp(src: Path, dst: Path, version: int):
    """Recopie un fichier de region, ses chunks marques a la version du serveur. Rend (chunks remarques, chunks laisses)."""
    data = src.read_bytes()
    head, body, done, left = bytearray(8192), bytearray(), 0, 0
    for i in range(1024):
        loc = struct.unpack(">I", data[i * 4:i * 4 + 4])[0]
        if not loc:
            continue
        off = (loc >> 8) * 4096
        length = struct.unpack(">I", data[off:off + 4])[0]
        kind, raw = data[off + 4], data[off + 5:off + 4 + length]
        try:
            nbt = zlib.decompress(raw) if kind == 2 else gzip.decompress(raw) if kind == 1 else raw if kind == 3 else None
        except Exception:
            nbt = None
        if nbt is None:                           # une compression que l'outil ne lit pas : le chunk reste comme il est
            chunk, left = data[off:off + 4 + length], left + 1
        else:
            patched, n = stamp(nbt, version)
            payload = zlib.compress(patched, 6)
            chunk, done = struct.pack(">IB", len(payload) + 1, 2) + payload, done + (1 if n else 0)
        chunk += bytes(-len(chunk) % 4096)
        if len(chunk) // 4096 > 255:              # trop gros pour un fichier de region : on ne le garde pas
            left += 1
            continue
        head[i * 4:i * 4 + 4] = struct.pack(">I", ((2 + len(body) // 4096) << 8) | (len(chunk) // 4096))
        head[4096 + i * 4:4100 + i * 4] = data[4096 + i * 4:4100 + i * 4]
        body += chunk
    dst.write_bytes(bytes(head) + bytes(body))
    return done, left


def option(argv, name, default=None):
    if name in argv:
        i = argv.index(name)
        value = argv[i + 1]
        del argv[i:i + 2]
        return value
    return default


def main() -> int:
    argv = list(sys.argv)
    carve = "--creuse" in argv
    argv = [a for a in argv if a != "--creuse"]
    title, author, source = option(argv, "--titre", ""), option(argv, "--auteur", ""), option(argv, "--source", "")
    threshold = int(option(argv, "--seuil", 3))
    box_arg, ground_arg, heart_arg = option(argv, "--boite"), option(argv, "--sol"), option(argv, "--coeur")
    depth, height = int(option(argv, "--profondeur", 12)), int(option(argv, "--hauteur", 240))
    out_root = Path(option(argv, "--sortie", str(ROOT / "prive" / "structures")))
    server = int(option(argv, "--serveur", 4556))
    if len(argv) != 3:
        print(__doc__)
        return 1
    world, name = Path(argv[1]), argv[2]
    if not (world / "level.dat").is_file() or not re.fullmatch(r"[a-z0-9_]+", name):
        print("Il faut le dossier d'un monde (avec level.dat) et un nom en minuscules, chiffres et _.")
        return 1

    built, low, high, tops = {}, {}, {}, {}
    for cx, cz, level in chunks(world / "region"):
        modern = "sections" in level            # depuis la 1.18 : le monde descend a -64
        score, lo, hi = 0, None, None
        for sec in level.get("Sections", level.get("sections", [])):
            palette = sec.get("Palette") or (sec.get("block_states") or {}).get("palette")
            if palette:
                n = sum(1 for p in palette if not NATURAL.match(str(p["Name"])))
            elif "Blocks" in sec:
                n = len({b & 255 for b in set(bytes(sec["Blocks"]))} - NATURAL_IDS)
            else:
                n = 0
            if n:
                y = int(sec["Y"])
                lo, hi = (y if lo is None else min(lo, y)), (y if hi is None else max(hi, y))
            score += n
        built[(cx, cz)] = score
        if lo is not None:
            low[(cx, cz)], high[(cx, cz)] = lo * 16, hi * 16 + 15
        s = surface(level, modern)
        if s is not None:
            tops[(cx, cz)] = s
    if not built:
        print("Aucun chunk lisible dans ce monde.")
        return 1

    # La construction : le plus grand ensemble de chunks batis qui se touchent (un chunk d'ecart est tolere).
    hot = {k for k, v in built.items() if v >= threshold}
    seen, best = set(), set()
    for start in hot:
        if start in seen:
            continue
        group, todo = set(), [start]
        seen.add(start)
        while todo:
            x, z = todo.pop()
            group.add((x, z))
            for dx in range(-2, 3):
                for dz in range(-2, 3):
                    n = (x + dx, z + dz)
                    if n in hot and n not in seen:
                        seen.add(n)
                        todo.append(n)
        if len(group) > len(best):
            best = group
    if not best:
        print("Aucune construction trouvee (essayer --seuil 2).")
        return 1
    if box_arg:
        x1, z1, x2, z2 = (int(v) for v in box_arg.split(","))
    else:
        x1, x2 = min(k[0] for k in best) * 16, max(k[0] for k in best) * 16 + 15
        z1, z2 = min(k[1] for k in best) * 16, max(k[1] for k in best) * 16 + 15
    inside = [k for k in built if x1 <= k[0] * 16 + 8 <= x2 and z1 <= k[1] * 16 + 8 <= z2]
    y1 = min((low[k] for k in inside if k in low), default=0)
    y2 = max((high[k] for k in inside if k in high), default=255)
    # Le sol : la hauteur de surface la plus frequente dans la boite.
    # (Les colonnes vides d'un monde sans terrain ne comptent pas.)
    count, empty, total = Counter(), 0, 0
    floor = min((low[k] for k in inside if k in low), default=0)
    for k in inside:
        if k in tops:
            total += 256
            values = [int(v) for v in tops[k] if v > floor]
            empty += 256 - len(values)
            count.update(values)
    ground = int(ground_arg) if ground_arg else (count.most_common(1)[0][0] if count else y1)
    ground = max(y1, min(y2, ground))
    y1 = max(min(y1, ground - 1), ground - depth)
    y2 = min(y2, ground + height)
    # Le coeur : le milieu de la fenetre de 7 x 7 chunks la plus batie.
    if heart_arg:
        hx, hz = (int(v) for v in heart_arg.split(","))
    else:
        dense = max(inside, key=lambda k: sum(built.get((k[0] + a, k[1] + b), 0) for a in range(-3, 4) for b in range(-3, 4)))
        hx, hz = dense[0] * 16 + 8, dense[1] * 16 + 8

    out = out_root / name
    if out.exists():
        shutil.rmtree(out)
    (out / "monde").mkdir(parents=True)
    # Un monde plus recent que le serveur : level.dat et les chunks gardes sont marques a la version du serveur.
    info = nbtlib.load(world / "level.dat")
    made = int(info.get("Data", {}).get("DataVersion", 0))
    newer = made > server
    if newer:
        info["Data"]["DataVersion"] = nbtlib.Int(server)
        if "Version" in info["Data"]:
            info["Data"]["Version"]["Id"] = nbtlib.Int(server)
        info["Data"].pop("Player", None)
        info.save(out / "monde" / "level.dat", gzipped=True)
    else:
        shutil.copy2(world / "level.dat", out / "monde" / "level.dat")
    kept, restamped, untouched = 0, 0, 0
    for sub in ("region", "entities"):
        if not (world / sub).is_dir():
            continue
        (out / "monde" / sub).mkdir()
        for rx in range(x1 >> 9, (x2 >> 9) + 1):
            for rz in range(z1 >> 9, (z2 >> 9) + 1):
                f = world / sub / f"r.{rx}.{rz}.mca"
                if f.is_file() and f.stat().st_size >= 8192:
                    if newer:
                        a, b = restamp(f, out / "monde" / sub / f.name, server)
                        restamped, untouched = restamped + a, untouched + b
                    else:
                        shutil.copy2(f, out / "monde" / sub / f.name)
                    kept += 1
    lines = [f"# Fiche du lieu {name} (tools/lieu.py). Coordonnees dans son monde d'origine.",
             f"boite: [{x1}, {y1}, {z1}, {x2}, {y2}, {z2}]", f"sol: {ground}", f"coeur: [{hx}, {hz}]", f"sous-sol: {'creuse' if carve else 'garde'}"]
    for key, value in (("titre", title), ("auteur", author), ("source", source)):
        if value:
            lines.append(f"{key}: \"{value}\"")
    (out / "structure.yml").write_text("\n".join(lines) + "\n", encoding="utf-8")

    # L'apercu : un pixel par chunk (gris : terrain, blanc : bati), la boite en rouge, le coeur en bleu.
    xs, zs = [k[0] for k in built], [k[1] for k in built]
    ox, oz = min(xs), min(zs)
    im = Image.new("RGB", (max(xs) - ox + 1, max(zs) - oz + 1), (0, 0, 0))
    for (cx, cz), v in built.items():
        im.putpixel((cx - ox, cz - oz), (255, 255, 255) if v >= threshold else (70, 70, 70))
    scale = max(1, 900 // max(im.size))
    im = im.resize((im.width * scale, im.height * scale), Image.NEAREST)
    draw = ImageDraw.Draw(im)
    draw.rectangle([(x1 / 16 - ox) * scale, (z1 / 16 - oz) * scale, ((x2 + 1) / 16 - ox) * scale, ((z2 + 1) / 16 - oz) * scale], outline=(255, 40, 40), width=2)
    px, pz = (hx / 16 - ox) * scale, (hz / 16 - oz) * scale
    draw.ellipse([px - 5, pz - 5, px + 5, pz + 5], fill=(60, 120, 255))
    im.save(out / "apercu.png")
    size = sum(f.stat().st_size for f in (out / "monde").rglob("*") if f.is_file()) // (1024 * 1024)
    print("surfaces les plus frequentes (hauteur : colonnes) :", ", ".join(f"{h} : {n}" for h, n in count.most_common(6)),
          f"| colonnes vides : {100 * empty // max(1, total)} %")
    print(f"{name} : boite {x2 - x1 + 1} x {y2 - y1 + 1} x {z2 - z1 + 1} (x {x1}..{x2}, y {y1}..{y2}, z {z1}..{z2}), sol {ground}, coeur {hx} {hz} ; "
          f"{len(best)} chunks batis, {kept} fichiers de region gardes, {size} Mo -> {out}")
    if newer:
        print(f"monde plus recent que le serveur (version des donnees {made} > {server}) : {restamped} chunks marques a la version du serveur"
              + (f", {untouched} laisses tels quels (illisibles ou trop gros)" if untouched else "")
              + ". A verifier en jeu : ce que le serveur ne connait pas serait perdu.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
