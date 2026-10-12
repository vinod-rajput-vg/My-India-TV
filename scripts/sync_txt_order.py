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


def read_existing_order(path):
    lines = path.read_text(encoding="utf-8-sig").splitlines()
    order = {}
    block = []
    for line in lines + [""]:
        if line.strip():
            block.append(line.strip())
        elif block:
            if len(block) >= 3 and norm(block[0]) not in order:
                order[norm(block[0])] = len(order)
            block = []
    return order


def main():
    raw = os.environ.get("FIREBASE_SERVICE_ACCOUNT_JSON", "")
    if not raw:
        raise SystemExit("Missing FIREBASE_SERVICE_ACCOUNT_JSON GitHub Actions secret.")

    info = json.loads(raw)
    app = firebase_admin.initialize_app(credentials.Certificate(info))
    db = firestore.client(app=app)
    docs = list(db.collection("channels").stream())
    print(f"Read {len(docs)} documents from Firestore collection 'channels'.")

    channels_by_category = {category: [] for category in FILES}
    seen = set()
    errors = []
    for doc in docs:
        data = doc.to_dict() or {}
        category_raw = str(data.get("category") or "").strip()
        category = next((c for c in FILES if norm(c) == norm(category_raw)), None)
        name = str(data.get("name") or "").strip()
        stream = str(data.get("streamUrl") or "").strip()
        logo = str(data.get("logoUrl") or "").strip()
        missing = []
        if not category:
            missing.append(f"unsupported category {category_raw!r}")
        if not name:
            missing.append("missing name")
        if not stream:
            missing.append("missing streamUrl")
        if missing:
            errors.append(f"Firestore document {doc.id}: " + ", ".join(missing))
            continue

        key = (category, norm(name))
        if key in seen:
            errors.append(f"Duplicate channel name in category {category}: {name!r}")
            continue
        seen.add(key)

        raw_order = data.get("sortOrder")
        try:
            order = float(raw_order) if raw_order is not None else None
        except (TypeError, ValueError):
            order = None
        channels_by_category[category].append({
            "name": name, "logo": logo, "stream": stream,
            "order": order, "doc_id": doc.id,
        })

    if errors:
        print("ERROR: Some Firestore channel records cannot be exported:")
        for error in errors:
            print(" - " + error)
        raise SystemExit("Sync stopped instead of reporting a false success. Fix the listed Firestore records and rerun.")

    changed = []
    for category, filename in FILES.items():
        path = CHANNEL_DIR / filename
        if not path.exists():
            raise SystemExit(f"Expected category TXT file is missing: {path}")

        old = path.read_text(encoding="utf-8-sig")
        old_order = read_existing_order(path)
        channels = channels_by_category[category]

        def sort_key(channel):
            if channel["order"] is not None:
                return (0, channel["order"], channel["name"].casefold(), channel["doc_id"])
            return (1, old_order.get(norm(channel["name"]), 1_000_000),
                    channel["name"].casefold(), channel["doc_id"])

        channels.sort(key=sort_key)
        blocks = [[c["name"], c["logo"], c["stream"]] for c in channels]
        output = "\n\n".join("\n".join(block) for block in blocks)
        if blocks:
            output += "\n"

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
