#!/usr/bin/env python3
"""Extrait d'un lieu (son monde d'origine) le volume que le plugin poserait, bloc par bloc, pour l'analyser hors jeu.

Usage : python3 -I extrait.py <dossier structures> <lieu> <dossier de sortie> [--max 336] [--decoupe x,z] [--tout]

Lit la fiche du lieu (structure.yml : boite, sol, coeur), prend la meme decoupe que le plugin (un rectangle de cote
--max au plus, centre sur le coeur, borne a la boite ; --decoupe impose son coin ; --tout prend la boite entiere),
lit les chunks du monde (formats 1.16 a 1.21) et ecrit <sortie>/<lieu>.npz :
  ids    uint16 [y, z, x]   pour chaque bloc du volume, l'indice de son nom dans `names` (y, z, x relatifs au coin)
  names  les noms de blocs (sans "minecraft:" ; un slab double est note "<nom>#double")
  meta   un JSON : lieu, x0, z0 (coin de la decoupe dans le monde d'origine), y1, y2, sol, coeur, boite, dimensions
Les chunks absents ou d'un format non lu (avant la 1.16) sont de l'air ; leur nombre est affiche.
"""
import io, json, os, struct, sys, zlib
import numpy as np
import nbtlib
import yaml


def option(argv, name, default=None):
    if name in argv:
        i = argv.index(name); v = argv[i + 1]; del argv[i:i + 2]; return v
    return default


def main():
    argv = list(sys.argv)
    whole = "--tout" in argv
    argv = [a for a in argv if a != "--tout"]
    side = int(option(argv, "--max", 336))
    corner = option(argv, "--decoupe")
    root, place, out = argv[1], argv[2], argv[3]
    sheet = yaml.safe_load(open(os.path.join(root, place, "structure.yml")))
    b = sheet["boite"]
    x1, y1, z1, x2, y2, z2 = min(b[0], b[3]), min(b[1], b[4]), min(b[2], b[5]), max(b[0], b[3]), max(b[1], b[4]), max(b[2], b[5])
    sol = int(sheet.get("sol", y1))
    heart = sheet.get("coeur", [(x1 + x2) // 2, (z1 + z2) // 2])
    sx, sz = x2 - x1 + 1, z2 - z1 + 1
    if whole:
        side = max(sx, sz)
    wide, deep = min(sx, side), min(sz, side)
    cx = max(x1, min(x2 - wide + 1, heart[0] - wide // 2))
    cz = max(z1, min(z2 - deep + 1, heart[1] - deep // 2))
    if corner:
        cx, cz = (int(v) for v in corner.split(","))
    H = y2 - y1 + 1
    ids = np.zeros((H, deep, wide), dtype=np.uint16)
    names, index = ["air"], {"air": 0}
    region_dir = os.path.join(root, place, "monde", "region")
    read = skipped = missing = 0
    for rx in range(cx >> 9, ((cx + wide - 1) >> 9) + 1):
        for rz in range(cz >> 9, ((cz + deep - 1) >> 9) + 1):
            path = os.path.join(region_dir, f"r.{rx}.{rz}.mca")
            if not os.path.isfile(path):
                continue
            data = open(path, "rb").read()
            if len(data) < 8192:
                continue
            for i in range(1024):
                chx, chz = rx * 32 + i % 32, rz * 32 + i // 32
                if chx * 16 + 15 < cx or chx * 16 > cx + wide - 1 or chz * 16 + 15 < cz or chz * 16 > cz + deep - 1:
                    continue
                loc = struct.unpack(">I", data[i * 4:i * 4 + 4])[0]
                if not loc:
                    missing += 1
                    continue
                off = (loc >> 8) * 4096
                length = struct.unpack(">I", data[off:off + 4])[0]
                raw = data[off + 5:off + 4 + length]
                try:
                    tag = nbtlib.File.parse(io.BytesIO(zlib.decompress(raw) if data[off + 4] == 2 else raw))
                except Exception:
                    skipped += 1
                    continue
                level = tag.get("Level", tag)
                sections = level.get("sections", level.get("Sections", []))
                ok = False
                for sec in sections:
                    states = sec.get("block_states")
                    palette = states.get("palette") if states is not None else sec.get("Palette")
                    packed = states.get("data") if states is not None else sec.get("BlockStates")
                    if palette is None:
                        continue
                    ok = True
                    sy = int(sec["Y"]) * 16
                    if sy + 15 < y1 or sy > y2:
                        continue
                    local = []
                    for p in palette:
                        n = str(p["Name"]).replace("minecraft:", "")
                        props = p.get("Properties")
                        if props is not None and str(props.get("type", "")) == "double":
                            n += "#double"
                        if n not in index:
                            index[n] = len(names); names.append(n)
                        local.append(index[n])
                    local = np.array(local, dtype=np.uint16)
                    if packed is not None and len(local) > 1:
                        bits = max(4, (len(local) - 1).bit_length())
                        per = 64 // bits
                        longs = np.array([int(v) & 0xFFFFFFFFFFFFFFFF for v in packed], dtype=np.uint64)
                        v = ((longs[:, None] >> (np.arange(per, dtype=np.uint64) * np.uint64(bits))) & np.uint64((1 << bits) - 1)).reshape(-1)[:4096].astype(np.int64)
                        cube = local[np.minimum(v, len(local) - 1)].reshape(16, 16, 16)
                    else:
                        cube = np.full((16, 16, 16), local[0], dtype=np.uint16)
                    # La part de cette section qui tombe dans le volume.
                    ya, yb = max(sy, y1), min(sy + 15, y2)
                    xa, xb = max(chx * 16, cx), min(chx * 16 + 15, cx + wide - 1)
                    za, zb = max(chz * 16, cz), min(chz * 16 + 15, cz + deep - 1)
                    ids[ya - y1:yb - y1 + 1, za - cz:zb - cz + 1, xa - cx:xb - cx + 1] = cube[ya - sy:yb - sy + 1, za - chz * 16:zb - chz * 16 + 1, xa - chx * 16:xb - chx * 16 + 1]
                if ok:
                    read += 1
                else:
                    skipped += 1
    meta = {"lieu": place, "x0": cx, "z0": cz, "y1": y1, "y2": y2, "sol": sol, "coeur": heart, "boite": [x1, y1, z1, x2, y2, z2],
            "largeur": wide, "profondeur": deep, "hauteur": H, "chunks_lus": read, "chunks_ignores": skipped, "chunks_absents": missing}
    os.makedirs(out, exist_ok=True)
    np.savez_compressed(os.path.join(out, place + ".npz"), ids=ids, names=np.array(names), meta=json.dumps(meta))
    print(f"{place} : volume {wide} x {H} x {deep} (x {cx}..{cx + wide - 1}, y {y1}..{y2}, z {cz}..{cz + deep - 1}), sol {sol}, {len(names)} sortes de blocs, "
          f"{read} chunks lus, {skipped} ignores, {missing} absents, {int((ids != 0).sum())} blocs non vides")


if __name__ == "__main__":
    main()
