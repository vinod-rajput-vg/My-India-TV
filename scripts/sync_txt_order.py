#!/usr/bin/env python3
"""Make the eight bundled channel TXT files match Firestore channels and sortOrder."""
import json
import os
import subprocess
from pathlib import Path

import firebase_admin
from firebase_admin import credentials, firestore

ROOT = Path(__file__).resolve().parents[1]
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


def main():
    raw = os.environ.get("FIREBASE_SERVICE_ACCOUNT_JSON", "")
    if not raw:
        raise SystemExit("Missing FIREBASE_SERVICE_ACCOUNT_JSON GitHub Actions secret.")

    info = json.loads(raw)
    app = firebase_admin.initialize_app(credentials.Certificate(info))
    db = firestore.client(app=app)
    docs = list(db.collection("channels").stream())

    # Firestore is the source of truth. Match by normalized category + channel
    # name because the current TXT format has no Firestore document ID.
    channels_by_category = {category: [] for category in FILES}
    seen = set()
    for doc in docs:
        data = doc.to_dict() or {}
        category_raw = str(data.get("category") or "").strip()
        category = next((c for c in FILES if norm(c) == norm(category_raw)), None)
        name = str(data.get("name") or "").strip()
        stream = str(data.get("streamUrl") or "").strip()
        logo = str(data.get("logoUrl") or "").strip()
        if not category or not name or not stream:
            print(f"Skipping incomplete/unknown Firestore channel: {doc.id}")
            continue

        key = (category, norm(name))
        if key in seen:
            raise SystemExit(f"Duplicate channel name in category {category}: {name}")
        seen.add(key)

        raw_order = data.get("sortOrder")
        try:
            order = float(raw_order) if raw_order is not None else None
        except (TypeError, ValueError):
            order = None
        channels_by_category[category].append({
            "name": name,
            "logo": logo,
            "stream": stream,
            "order": order,
            "doc_id": doc.id,
        })

    changed = []
    for category, filename in FILES.items():
        path = CHANNEL_DIR / filename
        if not path.exists():
            raise SystemExit(f"Expected category TXT file is missing: {path}")

        channels = channels_by_category[category]
        # When every channel has an explicit sortOrder, use it. Otherwise retain
        # stable Firestore query order for unranked channels and sort ranked ones
        # first; admin reorder writes a rank for every channel in that category.
        channels.sort(key=lambda c: (0, c["order"], c["name"].casefold())
                      if c["order"] is not None
                      else (1, 0, c["name"].casefold()))

        blocks = [[c["name"], c["logo"], c["stream"]] for c in channels]
        output = "\n\n".join("\n".join(block) for block in blocks)
        if blocks:
            output += "\n"

        old = path.read_text(encoding="utf-8-sig")
        if output != old:
            path.write_text(output, encoding="utf-8", newline="\n")
            changed.append(str(path.relative_to(ROOT)))
            print(f"Updated {category}: {len(blocks)} channels")
        else:
            print(f"Already up to date: {category} ({len(blocks)} channels)")

    if not changed:
        print("All category TXT files already match Firestore.")
        return

    subprocess.run(["git", "config", "user.name", "github-actions[bot]"], cwd=ROOT, check=True)
    subprocess.run(["git", "config", "user.email", "41898282+github-actions[bot]@users.noreply.github.com"], cwd=ROOT, check=True)
    subprocess.run(["git", "add", *changed], cwd=ROOT, check=True)
    result = subprocess.run(["git", "diff", "--cached", "--quiet"], cwd=ROOT)
    if result.returncode == 0:
        print("No TXT changes to commit.")
        return
    subprocess.run(["git", "commit", "-m", "Sync channel data and order from Firestore"], cwd=ROOT, check=True)
    subprocess.run(["git", "push"], cwd=ROOT, check=True)
    print("Channel TXT files synced and pushed.")


if __name__ == "__main__":
    main()
