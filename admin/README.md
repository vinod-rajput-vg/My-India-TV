# My India TV Admin Dashboard

This is a static web dashboard backed by Firebase Authentication and Cloud Firestore. It is designed to be hosted free with GitHub Pages. The Android app must separately be updated to read the Firestore `channels` collection before dashboard changes appear on TV.

## Features
- Email/password sign-in
- Add, edit, delete and filter channels
- The existing eight app categories
- Logo preview, JSON import/export
- Firestore security rules: public channel reads; writes restricted to one admin UID

## 1. Create Firebase project (free plan)
1. Open https://console.firebase.google.com/ and create a project.
2. Keep the project on the **Spark (no-cost)** plan. Do not attach a billing account if you want to avoid pay-as-you-go charges.
3. In Project settings, add a **Web app** and copy its Firebase web configuration.
4. Open **Authentication → Sign-in method** and enable **Email/Password**.
5. Open **Authentication → Users → Add user** and create your administrator account. Do not create public sign-up in this dashboard.
6. Open **Firestore Database → Create database**. Start in production mode and choose a nearby supported region.

## 2. Configure dashboard
1. Edit `admin/index.html`.
2. Replace the four `REPLACE_WITH_...` values in `firebaseConfig` with your Firebase web app's `apiKey`, `authDomain`, `projectId` and `appId`.
3. In Firebase Authentication, open the administrator user and copy the **UID**.
4. Edit `admin/firestore.rules`, replace `REPLACE_WITH_ADMIN_AUTH_UID` with that exact UID, then publish the rules in **Firestore → Rules**.
5. The web Firebase config is client-side configuration, not an admin secret. **Never put a service-account JSON key, private key, or GitHub token in this HTML file.**

## 3. Deploy dashboard using GitHub Pages
1. Merge the dashboard files into `main`.
2. In GitHub, open **Settings → Pages** and set the source to **GitHub Actions**.
3. The workflow at `.github/workflows/deploy-admin.yml` deploys only the `admin/` directory.
4. After the workflow succeeds, open the Pages URL shown under **Settings → Pages**. For this repository it should resemble `https://vinod-rajput-vg.github.io/My-India-TV/`.
5. Sign in with the Firebase admin email and password.

## 4. Add initial channels
You can add channels in the dashboard or import a JSON array. Example:

```json
[
  {
    "name": "Example News",
    "category": "News",
    "logoUrl": "https://example.com/logo.png",
    "streamUrl": "https://example.com/channel.m3u8",
    "enabled": true
  }
]
```

Use stream URLs and logos that you are authorized to use. A syntactically valid URL does not guarantee that a stream works.

## 5. Android TV integration (required for live sync)
The Android app now has `RemoteChannelRepository.kt`, which reads the public Firestore `channels` collection over HTTPS. When a category opens, the app shows bundled assets immediately, then replaces the list with remote channels if the request succeeds. If Firebase is not configured or the network/database is unavailable, bundled channels remain available.

Before building the APK, edit `app/src/main/java/com/myindiatv/RemoteChannelRepository.kt` and replace `REPLACE_WITH_PROJECT_ID` and `REPLACE_WITH_FIREBASE_WEB_API_KEY` with the same Firebase project ID and web API key used by the dashboard. These are client configuration values, not admin secrets. Never embed an admin password, service-account JSON, or private key in the app.

After configuring, run the repository's existing Gradle build workflow and test on an Android TV device. The app only reads public channel data; all writes remain restricted by Firestore rules.

## Free-plan notes
Firestore's no-cost quota is limited (currently 1 GiB storage, 50,000 document reads/day, 20,000 writes/day, 20,000 deletes/day, and 10 GiB outbound transfer/month; confirm the current official pricing page). On the Spark plan, a product can stop serving requests after its free quota is exceeded until the quota period resets. Free service is not a lifetime guarantee.

Official docs:
- https://firebase.google.com/docs/projects/billing/firebase-pricing-plans
- https://firebase.google.com/docs/firestore/pricing
- https://firebase.google.com/docs/firestore/security/get-started
