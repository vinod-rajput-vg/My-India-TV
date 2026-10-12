#!/usr/bin/env python3
"""Sync Firestore channel sortOrder into the bundled three-line channel TXT files."""
import json
import os
import subprocess
import tempfile
from pathlib import Path

import firebase_admin
from firebase_admin import credentials, firestore

ROOT = Path(__file__).resolve().parents[2]
CHANNEL_DIR = ROOT / "app" / "src" / "main" / "assets" / "channels"
FILES = {
    "Entertainment": "entertainment.txt",
    "Infotainment": "infotainment.txt",
    "News": "news.txt",
    "Music": "music.txt",
    "Kids": "kids.txt",
    "Drama": "drama.txt",
    "Sports": "sports.txt",
    "Movies": "movies.txt",
}


def norm(value):
    return " ".join(str(value or "").strip().lower().split())


def read_blocks(path):
    text = path.read_text(encoding="utf-8-sig")
    lines = text.splitlines()
    blocks, current = [], []
    for line in lines:
        if line.strip():
            current.append(line.rstrip())
        elif current:
            if len(current) >= 3:
                blocks.append(current[:3])
            current = []
    if current and len(current) >= 3:
        blocks.append(current[:3])
    return blocks


def main():
    raw = os.environ.get("FIREBASE_SERVICE_ACCOUNT_JSON", "")
    if not raw:
        raise SystemExit("Missing FIREBASE_SERVICE_ACCOUNT_JSON GitHub Actions secret.")
    info = json.loads(raw)
    app = firebase_admin.initialize_app(credentials.Certificate(info))
    db = firestore.client(app=app)
    docs = list(db.collection("channels").stream())
    by_category_name = {}
    for doc in docs:
        data = doc.to_dict() or {}
        category = str(data.get("category", "")).strip()
        name = norm(data.get("name", ""))
        if category and name:
            by_category_name[(category, name)] = data

    changed = []
    for category, filename in FILES.items():
        path = CHANNEL_DIR / filename
        if not path.exists():
            print(f"Skipping missing file: {path}")
            continue
        blocks = read_blocks(path)
        indexed = list(enumerate(blocks))

        def order_key(item):
            index, block = item
            data = by_category_name.get((category, norm(block[0])))
            sort_order = data.get("sortOrder") if data else None
            try:
                return (0, float(sort_order), index) if sort_order is not None else (1, index, index)
            except (TypeError, ValueError):
                return (1, index, index)

        ordered = [block for _, block in sorted(indexed, key=order_key)]
        output = "\n\n".join("\n".join(block) for block in ordered) + ("\n" if ordered else "")
        old = path.read_text(encoding="utf-8-sig")
        if output != old:
            path.write_text(output, encoding="utf-8", newline="\n")
            changed.append(str(path.relative_to(ROOT)))
            print(f"Updated order: {category} ({len(ordered)} channels)")

    if changed:
        subprocess.run(["git", "config", "user.name", "github-actions[bot]"], cwd=ROOT, check=True)
        subprocess.run(["git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"], cwd=ROOT, check=True)
        subprocess.run(["git", "add", *changed], cwd=ROOT, check=True)
        result = subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=ROOT)
        if result.returncode != 0:
            subprocess.run(["git", "commit", "-m", "Sync channel order from Firestore"], cwd=ROOT, check=True)
            subprocess.run(["git", "push"], cwd=ROOT, check=True)
            print("TXT order synced and pushed.")
        else:
            print("No TXT changes to commit.")
    else:
        print("TXT files already match Firestore sort order.")


if __name__ == "__main__":
    main()
