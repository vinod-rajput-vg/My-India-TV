# Firebase Admin + Android Sync — Setup Checklist

This checklist supplements [admin/README.md](../admin/README.md).

## Firebase project
- [ ] Create a Firebase project and keep it on Spark (no-cost).
- [ ] Register a Web app and copy the Firebase config.
- [ ] Enable Email/Password under Authentication.
- [ ] Create one admin user and record its UID.
- [ ] Create the Firestore database.
- [ ] Publish `admin/firestore.rules` after replacing the admin UID.
- [ ] Add the config values to `admin/index.html`.

## Dashboard
- [ ] Merge the dashboard files into `main`.
- [ ] Enable GitHub Pages using GitHub Actions.
- [ ] Wait for the Pages workflow to succeed.
- [ ] Sign in and add a test channel.
- [ ] Verify that signing out prevents writes.

## Android app remote sync
The branch adds `app/src/main/java/com/myindiatv/RemoteChannelRepository.kt` and updates `MainActivity.kt` to load Firestore channels after first displaying the bundled files as an offline fallback.

Before building the APK, replace these placeholders in `RemoteChannelRepository.kt`:
- `REPLACE_WITH_PROJECT_ID`
- `REPLACE_WITH_FIREBASE_WEB_API_KEY`

Use values from the same Firebase project as the dashboard. The app does not need admin login. Firestore rules should allow public read access only to `channels` and admin-UID-only writes. Then build with the existing Gradle workflow and test remote updates on a TV/device. If Firebase is unconfigured or unavailable, the app keeps using bundled assets.

## Firestore document schema
Each document in `channels` uses:
- `name`: string
- `category`: one of Entertainment, Infotainment, News, Music, Kids, Drama, Sports, Movies
- `logoUrl`: string (may be empty)
- `streamUrl`: string
- `enabled`: boolean
- `updatedAt`: server timestamp
- `updatedBy`: admin UID (dashboard writes only)

The dashboard stores each channel as a separate document with a stable ID. Import JSON updates a document when its `id` matches; otherwise it creates a new random ID.
