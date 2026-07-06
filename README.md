# Verset — a lighter, tag-first KJV Bible app

Kotlin + Jetpack Compose. Full offline KJV (31,102 verses, bundled as a ~6MB JSON
asset, loaded into Room on first launch — no network needed to read). Same
CI pattern as your other repos: `gradle assembleDebug` directly, no `gradlew`.

## What's built (v0.1 scaffold)

- **Read tab**: book/chapter picker, verse list, tap any verse to classify it.
- **Tag a verse**: pick an existing tag or type a new one (created on the fly),
  add a note about what the verse is about. A verse can carry multiple tags
  (e.g. "Promise" + "Comfort"), each with its own note — matches your spec.
- **My Verses tab**: browse by tag, see every verse + note under it, export.
- **Export**: per-verse shareable image card (PNG, saved to Pictures/Verset),
  and per-tag PDF document (all verses + notes, saved to Downloads/Verset).
- **Settings tab**: reader font-size slider, dark mode / follow-system toggle,
  Google Sign-In, manual "Sync now" button.
- **Sync**: Firestore, last-write-wins, one collection per user
  (`users/{uid}/entries`, `users/{uid}/tags`). Push-then-pull, triggered
  manually from Settings for now (v0.1 — auto-sync on app open/close is an
  easy next step).

## Before this builds and runs for real

Two things are placeholders right now and need your actual Firebase project:

1. **`app/google-services.json`** — currently a fake placeholder (structurally
   valid so CI compiles, but Firebase calls will fail at runtime). Steps:
   - Go to the [Firebase console](https://console.firebase.google.com) → Add project
   - Add an Android app with package name `com.johndev.verset`
   - Download the real `google-services.json` and replace the placeholder
   - Enable **Authentication → Sign-in method → Google**
   - Enable **Firestore Database** (start in production mode, add rules below)

2. **`WEB_CLIENT_ID` in `GoogleAuthManager.kt`** — after enabling Google
   sign-in in Firebase, copy the **Web client ID** (not the Android one) from
   Firebase console → Authentication → Sign-in method → Google → Web SDK
   configuration, and paste it in place of `REPLACE_WITH_YOUR_FIREBASE_WEB_CLIENT_ID`.

### Recommended Firestore security rules

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

## Package name / app name

Currently `com.johndev.verset`, app name "Verset" — both easy to rename
(find/replace `com.johndev.verset`, and `<string name="app_name">` in
`res/values/strings.xml`) before you publish anything.

## Known gaps for next session (kept small on purpose)

- Launcher icon is a placeholder vector — swap for real branding art.
- Sync is manual-trigger only, no background WorkManager job yet.
- Tag renaming and tag colors aren't editable from the UI yet (schema supports colorHex, nothing sets it).
- "Publish my listing in any format/style" — image card + PDF cover the two
  formats you asked for. If you want more export *styles* (different card
  themes/colors), that's a good next addition — the exporter is isolated in
  `export/ImageCardExporter.kt` so adding theme variants is additive, not a rewrite.
- No onboarding/first-run screen — app drops straight into Read tab.
- Search is substring match only (SQL LIKE), no fuzzy/ranked search.

## Implemented in this pass (v0.2)

- Verse search (tap search icon in Read, 3+ characters, jumps to result + opens tagging)
- Tag deletion with a confirmation dialog (cascades to delete all entries under that tag)
- Note editing on existing tagged entries (previously add/delete only)

## KJV data source

Bundled from the public-domain KJV text distributed at
`github.com/aruljohn/Bible-kjv`, consolidated into `assets/kjv_verses.json`
and `assets/kjv_books.json` at build-prep time (not fetched at runtime).
