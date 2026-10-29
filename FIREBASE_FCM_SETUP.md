# Firebase FCM catalog refresh setup

This app uses Firebase Cloud Messaging (FCM) as a refresh signal. GitHub remains the canonical source for the category and channel catalog; the push payload must not contain the catalog itself.

## Firebase Android app

- Firebase project ID: `my-india-tv-admin`
- Android application ID: `com.myindiatv`
- Add the Android app in Firebase Console using the exact application ID.
- Download `google-services.json` and place it at `app/google-services.json`.
- Sync Gradle and build the app.

The Google Services Gradle plugin is conditionally applied while `app/google-services.json` is present, so source-only builds can still be configured before the project-specific file is added. FCM itself is not activated until that Firebase config is supplied.

## FCM topic and message contract

The app subscribes to this topic:

```
my-india-tv-catalog
```

After a successful catalog write/commit, the trusted Admin Panel backend should send a **data-only** FCM message to the topic with this contract:

```json
{
  "message": {
    "topic": "my-india-tv-catalog",
    "data": {
      "event": "catalog_updated",
      "catalogVersion": "42",
      "changedType": "channel",
      "action": "update"
    }
  }
}
```

- `event` must equal `catalog_updated`.
- `catalogVersion`, `changedType`, and `action` are optional metadata; the app currently uses the event field as the refresh signal.
- Do not include a `notification` payload for this silent refresh event.
- Send from a trusted server using Firebase Admin SDK / FCM HTTP v1. Never embed service-account credentials or server keys in the Android app.

## App behavior

1. `CatalogMessagingService` receives the data message.
2. It persists a pending refresh marker and notifies the foreground Activity.
3. The app fetches the latest category list from GitHub.
4. If categories changed, the home view is updated.
5. If a category is open, the app fetches its channel file and replaces the visible list only when the parsed channel data differs.
6. A pending marker is consumed on resume as a fallback if the app was stopped when the message arrived.

FCM is best-effort delivery, not a hard real-time guarantee. Keep the app's resume/periodic sync fallback.

## Still required outside this Android repository

The Admin Panel/backend must send the message above after its GitHub catalog commit succeeds. This repository contains the Android receiver and topic subscription; it does not contain the Admin Panel's server-side sender. The Firebase project-specific `google-services.json` is also not checked in here.
