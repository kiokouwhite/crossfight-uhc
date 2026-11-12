#!/usr/bin/env python3
"""Construit le pack de ressources de Crossfight UHC (sons, voix, musiques).

Usage :  python3 tools/build_pack.py [--depuis CrossfightPackComplet.zip]

--depuis : recree resourcepack-prive/ a partir d'un pack complet deja construit (sur une machine ou
           une session qui n'a plus les fichiers prives), avant d'y ajouter de nouveaux sons.

Deux sources, de meme structure (assets/crossfight/sounds/...) :
  resourcepack/          publique, versionnee : bruitages libres, voix et musiques dont on a les droits
  resourcepack-prive/    privee, jamais versionnee (.gitignore) : voix officielles, musiques sous droits

Lit      <source>/assets/crossfight/sounds/**.ogg     les sons
         resourcepack/abilities.txt                   "id_de_capacite = categorie" (bruitage d'une capacite)
         resourcepack/music.txt                       "licence_a + licence_b = piste" (musique de combat)
Ecrit    resourcepack/assets/crossfight/sounds.json   la declaration des sons publics
         dist/CrossfightPack.zip                      le pack public, envoye par defaut aux joueurs
         prive/CrossfightPackComplet.zip              public + prive, si resourcepack-prive/ existe (non versionne)
         resourcepack/prive.txt                       les noms des sons prives (versionne : des noms, pas de son)
         src/main/resources/sounds.txt, music.txt, durations.txt, pack.sha1    embarques dans le plugin

Conventions de nommage, sous sounds/ :
  sfx/<categorie>/<n>.ogg     bibliotheque de bruitages : tous les fichiers d'un dossier sont des variantes
  ability/<id>.ogg            bruitage propre a une capacite (prioritaire sur abilities.txt)
  voice/<cle>.ogg             une replique ; <cle>_1.ogg, <cle>_2.ogg... = variantes tirees au hasard
  music/<piste>.ogg           une musique de combat (stereo)
Le reste de assets/ (modeles, textures, objets) est embarque tel quel. Un personnage qui a un modele
articule (tools/add_skin.py) est declare au plugin sous le nom "skin.<personnage>".
Un son prive remplace le son public du meme nom, qui n'est alors plus declare dans le pack public.

Le plugin joue tout son connu de sa liste (publics + prives). Pour que les joueurs entendent les sons
prives, il faut leur envoyer le pack complet : l'heberger a une adresse privee, puis renseigner
resource-pack.url et resource-pack.sha1 dans config.yml.

Apres un changement : relancer ce script, recompiler le plugin, publier dist/.
"""
import hashlib
import json
import re
import subprocess
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
PACK = ROOT / "resourcepack"
PRIVATE = ROOT / "resourcepack-prive"
NS = "crossfight"
SUB = Path("assets") / NS / "sounds"


def variants(folder: Path):
    """Regroupe les fichiers d'un dossier : 'cle_1.ogg', 'cle_2.ogg' -> 'cle' ; 'cle.ogg' -> 'cle'."""
    groups = {}
    if not folder.is_dir():
        return groups
    for f in sorted(folder.glob("*.ogg")):
        m = re.fullmatch(r"(.+)_(\d+)", f.stem)
        key = m.group(1) if m else f.stem
        groups.setdefault(key, []).append(f)
    return groups


def millis(f: Path) -> int:
    r = subprocess.run(["ffprobe", "-v", "error", "-show_entries", "format=duration", "-of", "csv=p=0", str(f)],
                       capture_output=True, text=True)
    try:
        return int(round(float(r.stdout.strip()) * 1000))
    except ValueError:
        print(f"duree illisible (ffprobe) : {f.name}, 180 s par defaut")
        return 180000


def collect(base: Path, with_library: bool):
    """Les evenements d'une source : nom -> fichiers, et la duree des musiques."""
    sounds = base / SUB
    events, tracks = {}, {}
    library = {}
    if with_library and (sounds / "sfx").is_dir():
        for cat in sorted(p for p in (sounds / "sfx").iterdir() if p.is_dir()):
            files = sorted(cat.glob("*.ogg"))
            if files:
                library[cat.name] = files
                events["sfx." + cat.name] = files
    for key, files in variants(sounds / "ability").items():
        events["ability." + key] = files
    for key, files in variants(sounds / "voice").items():
        events["voice." + key] = files
    if (sounds / "music").is_dir():
        for f in sorted((sounds / "music").glob("*.ogg")):
            tracks[f.stem] = millis(f)
            events["music." + f.stem] = [f]
    return events, tracks, library


def skins(base: Path):
    """Les personnages qui ont un modele articule (tools/add_skin.py)."""
    folder = base / "assets" / NS / "textures" / "item" / "pose"
    return {"skin." + f.stem for f in folder.glob("*.png")} if folder.is_dir() else set()


def other_assets(base: Path):
    """Les fichiers du pack qui ne sont pas des sons : modeles, textures, objets."""
    out = {}
    assets = base / "assets"
    if assets.is_dir():
        for f in sorted(assets.rglob("*")):
            rel = f.relative_to(base)
            if f.is_file() and f.name != ".gitkeep" and SUB not in rel.parents and rel != Path("assets") / NS / "sounds.json":
                out[rel.as_posix()] = f.read_bytes()
    return out


def voice_ms(events):
    """La duree (ms) de chaque replique : la plus longue de ses variantes."""
    return {name: max(millis(f) for f in files) for name, files in events.items() if name.startswith("voice.")}


def sounds_json(events, root_of):
    out = {}
    for name in sorted(events):
        refs = [NS + ":" + f.relative_to(root_of(f) / SUB).with_suffix("").as_posix() for f in events[name]]
        out[name] = {"sounds": [{"name": r, "stream": True} for r in refs]} if name.startswith("music.") else {"sounds": refs}
    return json.dumps(out, indent=1, sort_keys=True) + "\n"


def write_zip(zip_path: Path, members):
    """Zip deterministe (ordre et dates fixes) : la meme entree donne la meme empreinte."""
    zip_path.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(zip_path, "w", zipfile.ZIP_DEFLATED) as z:
        for arcname, data in sorted(members.items()):
            info = zipfile.ZipInfo(arcname, date_time=(2025, 1, 1, 0, 0, 0))
            info.compress_type = zipfile.ZIP_DEFLATED
            info.external_attr = 0o644 << 16
            z.writestr(info, data)
    return hashlib.sha1(zip_path.read_bytes()).hexdigest()


def restore_private(zip_path: Path):
    """Reprend d'un pack complet les sons qui ne sont pas dans la source publique."""
    n = 0
    with zipfile.ZipFile(zip_path) as z:
        for arc in z.namelist():
            rel = Path(arc)
            if rel.suffix != ".ogg" or ".." in rel.parts or rel.is_absolute() or SUB not in rel.parents:
                continue
            if (PACK / rel).exists() or (PRIVATE / rel).exists():
                continue
            (PRIVATE / rel).parent.mkdir(parents=True, exist_ok=True)
            (PRIVATE / rel).write_bytes(z.read(arc))
            n += 1
    print(f"{n} son(s) prive(s) repris de {zip_path.name}")


def main() -> int:
    if "--depuis" in sys.argv:
        restore_private(Path(sys.argv[sys.argv.index("--depuis") + 1]))
    events, tracks, library = collect(PACK, True)

    # Bruitages des capacites d'apres la correspondance (les fichiers propres la remplacent).
    unknown = []
    mapping = PACK / "abilities.txt"
    if mapping.exists():
        for n, line in enumerate(mapping.read_text(encoding="utf-8").splitlines(), 1):
            line = line.split("#", 1)[0].strip()
            if not line:
                continue
            if "=" not in line:
                print(f"abilities.txt ligne {n} ignoree : {line}")
                continue
            ability, cat = (x.strip() for x in line.split("=", 1))
            if cat in ("", "-", "aucun"):
                continue
            if cat not in library:
                unknown.append(f"{ability} -> {cat}")
                continue
            events.setdefault("ability." + ability, library[cat])
    if unknown:
        print("Categories inconnues dans abilities.txt :", ", ".join(unknown))

    # Sons prives : presents sur cette machine, ou seulement connus par leur nom (prive.txt).
    private_list = PACK / "prive.txt"
    private_names, private_tracks, p_events, private_voice_ms = set(), {}, {}, {}
    if PRIVATE.is_dir():
        p_events, private_tracks, _ = collect(PRIVATE, False)
        private_names = set(p_events) | skins(PRIVATE)
        private_voice_ms = voice_ms(p_events)
        if private_list.exists():
            before = {l.split()[0] for l in private_list.read_text(encoding="utf-8").splitlines() if l.strip() and not l.startswith("#")}
            if before - private_names:
                print("ATTENTION, sons prives disparus depuis la derniere construction (--depuis pour les reprendre) :",
                      ", ".join(sorted(before - private_names)))
        lines = ["# Sons du pack prive (resourcepack-prive/, non versionne). Genere par tools/build_pack.py.",
                 "# Seuls les noms sont ici : le plugin doit les connaitre pour les jouer."]
        lines += [name + (f" {private_tracks[name[6:]]}" if name.startswith("music.") else f" {private_voice_ms[name]}" if name in private_voice_ms else "")
                  for name in sorted(private_names)]
        private_list.write_text("\n".join(lines) + "\n", encoding="utf-8")
    elif private_list.exists():
        for line in private_list.read_text(encoding="utf-8").splitlines():
            parts = line.split("#", 1)[0].split()
            if not parts:
                continue
            private_names.add(parts[0])
            if parts[0].startswith("music.") and len(parts) > 1:
                private_tracks[parts[0][6:]] = int(parts[1])
            if parts[0].startswith("voice.") and len(parts) > 1:
                private_voice_ms[parts[0]] = int(parts[1])

    # Pack public. Un son qui a une version privee n'y est pas declare : Minecraft additionne les
    # declarations d'un meme son venant de deux packs (le pack public envoye par le serveur et le pack
    # complet installe par le joueur), et tirerait alors au hasard entre le bruitage libre et le vrai.
    public = {name: files for name, files in events.items() if name not in private_names}
    json_public = sounds_json(public, lambda f: PACK)
    (PACK / "assets" / NS).mkdir(parents=True, exist_ok=True)
    (PACK / "assets" / NS / "sounds.json").write_text(json_public, encoding="utf-8")
    members = {"pack.mcmeta": (PACK / "pack.mcmeta").read_bytes(), f"assets/{NS}/sounds.json": json_public.encode("utf-8")}
    if (PACK / "CREDITS.md").exists():
        members["CREDITS.md"] = (PACK / "CREDITS.md").read_bytes()
    for f in {f for files in public.values() for f in files}:
        members[f.relative_to(PACK).as_posix()] = f.read_bytes()
    members.update(other_assets(PACK))
    public_zip = ROOT / "dist" / "CrossfightPack.zip"
    sha1 = write_zip(public_zip, members)

    # Pack complet : les sons publics, plus les sons prives qui les completent ou les remplacent.
    complete_zip = None
    if private_names and PRIVATE.is_dir():
        merged = dict(events)
        merged.update(p_events)
        json_complete = sounds_json(merged, lambda f: PRIVATE if PRIVATE in f.parents else PACK)
        c_members = {k: v for k, v in members.items() if not k.endswith(".ogg")}
        c_members[f"assets/{NS}/sounds.json"] = json_complete.encode("utf-8")
        for f in {f for files in merged.values() for f in files}:
            c_members[f.relative_to(PRIVATE if PRIVATE in f.parents else PACK).as_posix()] = f.read_bytes()
        c_members.update(other_assets(PRIVATE))
        complete_zip = ROOT / "prive" / "CrossfightPackComplet.zip"
        complete_sha1 = write_zip(complete_zip, c_members)

    # Musiques : les licences qui declenchent chaque piste.
    all_tracks = dict(tracks)
    all_tracks.update(private_tracks)
    pairs, missing = [], []
    music_map = PACK / "music.txt"
    if music_map.exists():
        for n, line in enumerate(music_map.read_text(encoding="utf-8").splitlines(), 1):
            line = line.split("#", 1)[0].strip()
            if not line:
                continue
            m = re.fullmatch(r"(\w+)\s*\+\s*(\w+)\s*=\s*([\w.-]+)", line)
            if not m:
                print(f"music.txt ligne {n} ignoree : {line}")
                continue
            a, b, track = m.groups()
            (pairs if track in all_tracks else missing).append((a, b, track))

    # Ce que le plugin embarque : tous les sons connus (publics et prives), l'empreinte du pack public.
    res = ROOT / "src" / "main" / "resources"
    index = sorted(set(events) | skins(PACK) | private_names)
    (res / "sounds.txt").write_text("# Genere par tools/build_pack.py\n" + "\n".join(index) + "\n", encoding="utf-8")
    (res / "pack.sha1").write_text(sha1 + "\n", encoding="utf-8")
    # La duree des repliques : le plugin enchaine un dialogue sans que la seconde replique coupe la premiere.
    durations = voice_ms(public)
    durations.update(private_voice_ms)
    (res / "durations.txt").write_text("# Genere par tools/build_pack.py : duree (ms) de chaque replique\n"
                                       + "".join(f"{k} {v}\n" for k, v in sorted(durations.items())), encoding="utf-8")
    lines = ["# Genere par tools/build_pack.py"]
    lines += [f"track {t} {s}" for t, s in sorted(all_tracks.items())]
    lines += [f"pair {a} {b} {t}" for a, b, t in pairs]
    (res / "music.txt").write_text("\n".join(lines) + "\n", encoding="utf-8")

    kinds = {}
    for name in public:
        kinds[name.split(".", 1)[0]] = kinds.get(name.split(".", 1)[0], 0) + 1
    print(f"pack public : {len(public)} sons ({', '.join(f'{v} {k}' for k, v in sorted(kinds.items()))})")
    print(f"  {public_zip.relative_to(ROOT)} : {public_zip.stat().st_size // 1024} Ko, sha1 {sha1}")
    if private_names:
        print(f"sons prives : {len(private_names)}" + ("" if PRIVATE.is_dir() else " (connus par leur nom seulement, fichiers absents de cette machine)"))
    if complete_zip:
        print(f"  {complete_zip.relative_to(ROOT)} : {complete_zip.stat().st_size // 1024} Ko, sha1 {complete_sha1}")
        print("  -> a heberger a une adresse privee ; config.yml : resource-pack.url et resource-pack.sha1")
    if music_map.exists():
        absent = sorted({t for _, _, t in missing})
        print(f"musiques : {len(all_tracks)} piste(s), {len(pairs)} affrontement(s) couverts, {len(absent)} piste(s) attendue(s) mais absente(s)")
    for z in (public_zip, complete_zip):
        if z and z.stat().st_size > 100 * 1024 * 1024:
            print(f"ATTENTION : {z.name} depasse 100 Mo (limite d'un fichier sur GitHub, et lourd a telecharger).")
    return 0


if __name__ == "__main__":
    sys.exit(main())
