# Verset

A lightweight, offline-first KJV Bible app for Android with a built-in system for tagging verses by theme and attaching personal notes.

## Why

Most Bible apps are large, ad-heavy, or don't let you organize verses the way you actually think about them. Verset does one thing well: read the King James Bible offline, and build a personal library of verses classified however you want — "Promise," "Comfort," "Warning," anything — each with a note on why it matters to you.

## Features

- **Full offline KJV** — all 31,102 verses bundled with the app; no internet connection needed to read
- **Tag and classify verses** — create your own categories on the fly (a verse can carry multiple tags, each with its own note)
- **Verse of the Day** on the home screen, pulled from your own saved verses rather than a generic feed
- **Chapter navigation** — previous/next chapter buttons that flow naturally across book boundaries, plus full-text search across the whole KJV
- **Export** a tagged verse as a shareable image card (four style themes) or export a whole tag's verses and notes as a PDF
- **Optional cloud sync** — sign in with Google to back up tags/notes and use them on a second device via Firestore; everything works fully offline without signing in
- **Customizable reading experience** — adjustable font size, light/dark theme, custom tag colors

## Tech stack

- Kotlin + Jetpack Compose (Material 3)
- Room for local storage
- Firebase Authentication (Google Sign-In) + Cloud Firestore for sync
- Built and verified via GitHub Actions CI on every push

## Getting started

### Prerequisites

- Android Studio or the Gradle CLI, JDK 17
- A Firebase project — only required for sign-in/sync; the app is fully usable offline without one

### Build

```bash
git clone https://github.com/john1183-prog/verset.git
cd verset
gradle assembleDebug
```

### Enable cloud sync (optional)

1. Create a project at the [Firebase console](https://console.firebase.google.com)
2. Add an Android app with package name `com.johndev.verset`
3. Download `google-services.json` and place it in `app/`, replacing the placeholder
4. Enable **Authentication → Sign-in method → Google**, and enable **Firestore Database**
5. Copy the **Web client ID** (Authentication → Sign-in method → Google → Web SDK configuration) into `GoogleAuthManager.kt`, replacing `WEB_CLIENT_ID`
6. Add the Firestore security rules below in the Firebase console

```
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /users/{userId}/{document=**} {
      allow read, write: if request.auth != null && request.auth.uid == userId;
    }
  }
}
```

## Project structure

```
app/src/main/java/com/johndev/verset/
├── data/         Room entities, DAOs, database, local preferences
├── repository/   BibleRepository (local data), SyncRepository (Firestore sync)
├── auth/         Google Sign-In via Credential Manager
├── export/       Image card and PDF exporters
└── ui/
    ├── screens/      Home, Read, My Verses, Settings
    ├── navigation/   Bottom-tab nav graph
    └── theme/        Compose theming
```

## Data source

KJV text is bundled from the public-domain KJV distribution at [aruljohn/Bible-kjv](https://github.com/aruljohn/Bible-kjv), consolidated at build-prep time into JSON assets — not fetched at runtime.

## Known limitations

- Launcher icon is a placeholder vector; needs real branding art
- Sync is manual (tap "Sync now" in Settings); no automatic background sync yet
- Search is substring matching, not fuzzy or ranked
- PDF export has a single fixed layout (image export offers four style themes)
- Tag rename blocks exact duplicate names but doesn't support merging two existing tags
- Room schema JSON export is enabled; the `app/schemas/` folder is generated on build and should be committed once it appears, to preserve migration history for future schema changes

## License

Not yet specified.
