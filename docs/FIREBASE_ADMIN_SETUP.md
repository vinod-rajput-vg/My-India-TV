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

## Android app
The current app still reads bundled text files. It must be updated to read the public `channels` collection before the admin dashboard can update installed TVs. The recommended implementation should retain the bundled files as an offline fallback and only read public channel documents; admin authentication is never needed in the TV app.

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
