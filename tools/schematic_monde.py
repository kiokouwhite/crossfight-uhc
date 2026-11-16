#!/usr/bin/env python3
"""Fait d'un ancien .schematic (MCEdit, WorldEdit d'avant la 1.13) un monde que tools/lieu.py sait preparer.

Usage :  python3 tools/schematic_monde.py <fichier.schematic> <dossier du monde a creer>

Un .schematic n'est pas un monde : c'est un bloc de construction (largeur, hauteur, longueur, des numeros de blocs).
Le plugin, lui, copie ses lieux depuis un monde. Ce script ecrit donc un monde vide ou la construction est posee, son
coin en (0, 0, 0), dans le format des mondes d'avant la 1.13 (des numeros de blocs, comme le fichier) : le serveur le
met a jour en le lisant, comme n'importe quelle vieille map. Ce que portent les blocs (panneaux, coffres...) est garde ;
les creatures ne le sont pas. Ensuite : python3 tools/lieu.py <dossier> <nom> ...   Demande nbtlib et numpy.
"""
import struct
import sys
import time
import zlib
from pathlib import Path

import nbtlib
import numpy as np
from nbtlib import Byte, ByteArray, Compound, Int, IntArray, List, Long, String


def nibbles(values: np.ndarray) -> np.ndarray:
    """4096 valeurs de 0 a 15, en 2048 octets (la premiere dans les bits bas)."""
    v = values.astype(np.uint8)
    return (v[0::2] | (v[1::2] << 4)).astype(np.int8)


def main() -> int:
    if len(sys.argv) != 3:
        print(__doc__)
        return 1
    src, out = Path(sys.argv[1]), Path(sys.argv[2])
    s = nbtlib.load(src, gzipped=True)
    s = s.get("Schematic", s)
    W, H, L = int(s["Width"]), int(s["Height"]), int(s["Length"])
    blocks = np.asarray(s["Blocks"], dtype=np.uint8).astype(np.uint16).reshape(H, L, W)
    data = np.asarray(s["Data"], dtype=np.uint8).reshape(H, L, W) & 15
    if "AddBlocks" in s:                       # les numeros au-dela de 255 : un demi-octet de plus par bloc
        add = np.asarray(s["AddBlocks"], dtype=np.uint8)
        high = np.zeros(W * H * L, dtype=np.uint16)
        high[0::2] = (add >> 4)[:len(high[0::2])]
        high[1::2] = (add & 15)[:len(high[1::2])]
        blocks |= high.reshape(H, L, W) << 8
    tiles = {}
    for t in s.get("TileEntities", []):
        tiles.setdefault((int(t["x"]) >> 4, int(t["z"]) >> 4), []).append(t)

    (out / "region").mkdir(parents=True, exist_ok=True)
    regions, count = {}, 0
    for cz in range((L + 15) // 16):
        for cx in range((W + 15) // 16):
            b = np.zeros((H, 16, 16), dtype=np.uint16)
            d = np.zeros((H, 16, 16), dtype=np.uint8)
            part = blocks[:, cz * 16:cz * 16 + 16, cx * 16:cx * 16 + 16]
            b[:, :part.shape[1], :part.shape[2]] = part
            d[:, :part.shape[1], :part.shape[2]] = data[:, cz * 16:cz * 16 + 16, cx * 16:cx * 16 + 16]
            sections = []
            for sy in range((H + 15) // 16):
                sb = np.zeros((16, 16, 16), dtype=np.uint16)
                sd = np.zeros((16, 16, 16), dtype=np.uint8)
                sb[:b[sy * 16:sy * 16 + 16].shape[0]] = b[sy * 16:sy * 16 + 16]
                sd[:d[sy * 16:sy * 16 + 16].shape[0]] = d[sy * 16:sy * 16 + 16]
                if not sb.any():
                    continue
                flat = sb.reshape(-1)
                sec = Compound({"Y": Byte(sy), "Blocks": ByteArray((flat & 255).astype(np.int8)), "Data": ByteArray(nibbles(sd.reshape(-1))),
                                "BlockLight": ByteArray(np.zeros(2048, dtype=np.int8)), "SkyLight": ByteArray(np.full(2048, -1, dtype=np.int8))})
                if (flat > 255).any():
                    sec["Add"] = ByteArray(nibbles(flat >> 8))
                sections.append(sec)
            if not sections:
                continue
            level = Compound({"xPos": Int(cx), "zPos": Int(cz), "LastUpdate": Long(0), "InhabitedTime": Long(0), "TerrainPopulated": Byte(1),
                              "LightPopulated": Byte(0), "V": Byte(1), "Biomes": ByteArray(np.full(256, 1, dtype=np.int8)),
                              "HeightMap": IntArray(np.zeros(256, dtype=np.int32)), "Sections": List[Compound](sections),
                              "Entities": List[Compound]([]), "TileEntities": List[Compound](tiles.get((cx, cz), []))})
            raw = nbtlib.File({"Level": level})
            import io
            buf = io.BytesIO()
            raw.write(buf)
            payload = zlib.compress(buf.getvalue(), 6)
            chunk = struct.pack(">IB", len(payload) + 1, 2) + payload
            chunk += bytes(-len(chunk) % 4096)
            regions.setdefault((cx >> 5, cz >> 5), {})[(cx & 31) + (cz & 31) * 32] = chunk
            count += 1
    for (rx, rz), chunks in regions.items():
        head, body = bytearray(8192), bytearray()
        for i, chunk in sorted(chunks.items()):
            head[i * 4:i * 4 + 4] = struct.pack(">I", ((2 + len(body) // 4096) << 8) | (len(chunk) // 4096))
            body += chunk
        (out / "region" / f"r.{rx}.{rz}.mca").write_bytes(bytes(head) + bytes(body))
    info = Compound({"Data": Compound({
        "version": Int(19133), "initialized": Byte(1), "LevelName": String(src.stem), "generatorName": String("flat"), "generatorVersion": Int(0),
        "generatorOptions": String(""), "RandomSeed": Long(0), "MapFeatures": Byte(0), "LastPlayed": Long(int(time.time() * 1000)), "SizeOnDisk": Long(0),
        "allowCommands": Byte(1), "hardcore": Byte(0), "GameType": Int(1), "Difficulty": Byte(2), "Time": Long(0), "DayTime": Long(6000),
        "SpawnX": Int(W // 2), "SpawnY": Int(H), "SpawnZ": Int(L // 2), "raining": Byte(0), "rainTime": Int(0), "thundering": Byte(0),
        "thunderTime": Int(0), "clearWeatherTime": Int(0), "GameRules": Compound({})})})
    nbtlib.File(info).save(out / "level.dat", gzipped=True)
    used = np.unique(blocks)
    print(f"{src.name} : {W} x {H} x {L} blocs, {len(used)} sortes de blocs, {sum(len(v) for v in tiles.values())} blocs a contenu -> {count} chunks dans {out}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
