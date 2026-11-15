#!/usr/bin/env python3
"""Fabrique la documentation GitBook (docs/) à partir du README.

Usage :  python3 tools/build_docs.py

Le README reste la source : chaque partie « ## » devient une page de docs/, chaque « ### » une sous-page, et
docs/SUMMARY.md en donne le sommaire. .gitbook.yaml, à la racine, dit à GitBook de lire docs/. Une fois
l'espace GitBook branché sur le dépôt (Git Sync, branche main), chaque envoi sur GitHub met le GitBook à jour.
À relancer après chaque changement du README.
"""
import re
import unicodedata
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
DOCS = ROOT / "docs"


def slug(title: str) -> str:
    t = unicodedata.normalize("NFKD", title).encode("ascii", "ignore").decode().lower()
    return re.sub(r"[^a-z0-9]+", "-", t).strip("-") or "page"


def main() -> int:
    lines = (ROOT / "README.md").read_text(encoding="utf-8").splitlines()
    title, intro, pages = "Crossfight UHC", [], []      # pages : [niveau, titre, lignes]
    fence = False
    for line in lines:
        if line.startswith("```"):
            fence = not fence
        m = None if fence else re.match(r"^(#{1,3}) (.+)$", line)
        if m and len(m.group(1)) == 1:
            title = m.group(2).strip()
        elif m:
            pages.append([len(m.group(1)), m.group(2).strip(), []])
        elif pages:
            pages[-1][2].append(line)
        else:
            intro.append(line)
    DOCS.mkdir(exist_ok=True)
    for old in DOCS.rglob("*.md"):
        old.unlink()
    (DOCS / "README.md").write_text(f"# {title}\n\n" + "\n".join(intro).strip() + "\n", encoding="utf-8")
    summary = ["# Sommaire", "", f"* [{title}](README.md)"]
    parent = None
    for level, name, body in pages:
        if level == 2:
            parent = slug(name)
            path = f"{parent}.md"
        else:
            path = f"{parent or 'divers'}/{slug(name)}.md"
        f = DOCS / path
        f.parent.mkdir(parents=True, exist_ok=True)
        f.write_text(f"# {name}\n\n" + "\n".join(body).strip() + "\n", encoding="utf-8")
        summary.append(("* " if level == 2 else "  * ") + f"[{name}]({path})")
    (DOCS / "SUMMARY.md").write_text("\n".join(summary) + "\n", encoding="utf-8")
    (ROOT / ".gitbook.yaml").write_text("root: ./docs/\n\nstructure:\n  readme: README.md\n  summary: SUMMARY.md\n", encoding="utf-8")
    print(f"docs/ : {len(pages) + 1} pages, sommaire dans docs/SUMMARY.md")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
