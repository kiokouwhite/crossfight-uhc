#!/usr/bin/env python3
"""Compare la decision du plugin (dossier -Dcrossfight.socle) a celle de la reference, sur le volume que le plugin a lu.
Usage : python3 -I compare2.py <dossier du plugin> [<lieu> ...]"""
import gzip, json, os, sys, tempfile
import numpy as np
HERE = os.path.dirname(os.path.abspath(__file__))
sys.path.insert(0, os.path.join(os.path.dirname(HERE), "outils"))
sys.path.insert(0, HERE)
import socle_lib as sl, socle

def one(folder, place):
    w, h, d, sx, sz, y1, jsol, sheet = (int(t) for t in open(os.path.join(folder, place + ".txt")).readline().split())
    names, bits = {}, {}
    for line in open(os.path.join(folder, "ordinaux.txt")):
        o, n, b = line.split()
        names[int(o)] = n; bits[int(o)] = b
    raw = np.frombuffer(gzip.open(os.path.join(folder, place + ".ids.gz")).read(), dtype=">i2").astype(np.int32).reshape(h, d, w)
    uniq, inv = np.unique(raw, return_inverse=True)
    air = [i for i, o in enumerate(uniq) if names[o & 0x3FFF] == "air"]
    order = list(range(len(uniq)))
    if air and air[0] != 0:                                   # l'air doit etre l'identifiant 0
        order[0], order[air[0]] = order[air[0]], order[0]
    remap = np.empty(len(uniq), dtype=np.int64); remap[order] = np.arange(len(uniq))
    ids = remap[inv].reshape(h, d, w).astype(np.uint16)
    local = [None] * len(uniq)
    for i, o in enumerate(uniq):
        local[remap[i]] = names[o & 0x3FFF] + ("#double" if o & 0x4000 else "")
    tmp = tempfile.mkdtemp()
    np.savez_compressed(os.path.join(tmp, place + ".npz"), ids=ids, names=np.array(local), meta=json.dumps({"sol": sheet + y1, "y1": y1}))
    v = sl.load(place, tmp)
    n = w * h * d
    jfill = np.unpackbits(np.frombuffer(gzip.open(os.path.join(folder, place + ".fill.gz")).read(), dtype=np.uint8), bitorder="little")[:n].reshape(h, d, w).astype(bool)
    cols = gzip.open(os.path.join(folder, place + ".colonnes.gz")).read()
    a = np.frombuffer(cols[:w * d * 8], dtype=">i2").reshape(4, d, w)
    inside, jskip, jdig, jcapy = a[0].astype(bool), a[1].astype(bool), a[2].astype(np.int64), a[3].astype(np.int64)
    jcapid = np.frombuffer(cols[w * d * 8:], dtype=">i2").reshape(d, w).astype(np.int64)
    # les proprietes des blocs : celles du plugin contre celles de la reference
    c = socle.Columns(v)
    luts = [c.lut_air, c.lut_solid, c.lut_liquid, c.lut_thing, c.lut_light, c.lut_soil, c.lut_opening, c.lut_plant, c.lut_glass]
    bad = []
    for i, o in enumerate(uniq):
        mine = "".join("1" if l[remap[i]] else "0" for l in luts)
        if mine != bits[o & 0x3FFF]:
            bad.append((local[remap[i]], bits[o & 0x3FFF], mine))
    sol, fill, skip, dig, (capy, capid), note = socle.decide(v, inside)
    jn = np.array([names[o & 0x3FFF] + ("#double" if o & 0x4000 else "") for o in range(0x8000) if (o & 0x3FFF) in names] , dtype=object) if False else None
    capsame = (jcapy == capy)
    has = capy >= 0
    capname_j = np.array([names[int(o) & 0x3FFF] + ("#double" if int(o) & 0x4000 else "") for o in jcapid[has]])
    capname_p = np.array([local[int(i)] for i in capid[has]])
    print(f"{place:15s} {w} x {h} x {d} | proprietes differentes {len(bad)} {bad[:4]} | niveau plugin {jsol} reference {sol} | comblement {int(jfill.sum())} / {int(fill.sum())} diff {int((jfill != fill).sum())} | "
          f"non posees {int(jskip.sum())} / {int(skip.sum())} diff {int((jskip != skip).sum())} | creux diff {int((jdig != dig).sum())} ({int((dig < sol).sum())} creusees) | "
          f"bouchons {int(has.sum())} diff {int((~capsame).sum()) + int((capname_j != capname_p).sum())}")
    print("   ", note)

folder = sys.argv[1]
for place in sys.argv[2:] or sorted(f[:-4] for f in os.listdir(folder) if f.endswith(".txt") and f != "ordinaux.txt"):
    one(folder, place)
