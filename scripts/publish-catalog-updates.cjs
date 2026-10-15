const { execFileSync } = require("node:child_process");
const admin = require("firebase-admin");

const serviceAccountJson = process.env.FIREBASE_SERVICE_ACCOUNT_JSON;
if (!serviceAccountJson) throw new Error("Missing FIREBASE_SERVICE_ACCOUNT_JSON GitHub Actions secret.");
const serviceAccount = JSON.parse(serviceAccountJson);
admin.initializeApp({ credential: admin.credential.cert(serviceAccount) });
const db = admin.firestore();
const messaging = admin.messaging();

const base = process.env.BEFORE_SHA;
const head = process.env.AFTER_SHA || process.env.GITHUB_SHA;
let files;
try {
  files = execFileSync("git", ["diff", "--name-only", base, head], { encoding: "utf8" })
    .split("\n").map(s => s.trim()).filter(Boolean);
} catch {
  files = execFileSync("git", ["diff-tree", "--no-commit-id", "--name-only", "-r", head], { encoding: "utf8" })
    .split("\n").map(s => s.trim()).filter(Boolean);
}

const allowed = files.filter(path =>
  path === "Categories/categories.txt" ||
  path.startsWith("Category Channels/") ||
  path.startsWith("Category Icons/") ||
  path.startsWith("Channel Icons/")
);
if (allowed.length === 0) {
  console.log("No catalog files changed; nothing to publish.");
  process.exit(0);
}

function docIdFor(path) {
  if (path === "Categories/categories.txt") return "categories_index";
  const match = path.match(/^Category Channels\/([^/]+)\.txt$/i);
  return match ? match[1].toLowerCase() : null;
}

(async () => {
  const batch = db.batch();
  for (const path of allowed) {
    const id = docIdFor(path);
    if (!id) continue;
    const ref = db.collection("category_updates").doc(id);
    batch.set(ref, {
      sourcePath: path,
      revision: head,
      updatedAt: admin.firestore.FieldValue.serverTimestamp()
    }, { merge: true });
  }
  await batch.commit();
  await messaging.send({
    topic: "catalog_updates",
    data: {
      paths: allowed.join("|"),
      revision: head,
      source: "github-actions"
    },
    android: { priority: "high" }
  });
  console.log("Published catalog revision " + head + " for: " + allowed.join(", "));
})().catch(error => {
  console.error(error);
  process.exit(1);
});
