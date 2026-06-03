# Cell Recorder — Agent Guidelines

## Project Context

- **Platform:** Android (min API 30 / Android 11)
- **Language:** Kotlin, Jetpack Compose + Material 3, Hilt, Room
- **Build:** Gradle wrapper (`./gradlew`)
- **Version:** `1.0.2` (versionCode `3`) — defined in `app/build.gradle.kts`
- **Tags:** `v1.0.0`, `v1.0.1`, `v1.0.2`

---

## Build Policy — Clean Before Rebuild

Always run a clean build before assembling the APK to avoid stale artifacts.

**Debug:**
```bash
./gradlew clean
./gradlew assembleDebug
```

**Release:** (requires signing env vars)
```bash
export RELEASE_STORE_PASSWORD="your_keystore_password"
export RELEASE_KEY_ALIAS="your_key_alias"
export RELEASE_KEY_PASSWORD="your_key_password"
./gradlew clean
./gradlew assembleRelease
```

Never commit the keystore (`*.jks`) or hardcode signing passwords.

---

## Git Policy — Commit After Changes

After completing any task that modifies files:
1. Review `git status` to see what changed.
2. Stage only files relevant to the task with `git add` (respect `.gitignore` — never stage `app/build/`, `.gradle/`, or `*.jks`).
3. Commit locally with a concise, descriptive message following the existing style (e.g., `Fix invalid null looper crash and bump to v1.0.2`).
4. Do **not** push. The user controls timing of pushes.

---

## Release Policy — Update CHANGELOG.md for Tagged Releases

When the user asks for a version bump or release:
1. Update `CHANGELOG.md` following [Keep a Changelog](https://keepachangelog.com/en/1.1.0/).
2. Add a new `## [X.Y.Z] - YYYY-MM-DD` section with categories: `Added`, `Changed`, `Fixed`, etc.
3. Update `versionName` and `versionCode` in `app/build.gradle.kts`.
4. Commit the changes locally.
5. Create the git tag **only if the user explicitly requests it**.

---

## Spec Policy — Keep `openspec.md` in Sync

After any **major functional change** (new feature, removed feature, altered behavior, new screen, data model change, architecture change, permission change), update `openspec.md` to match the latest code.

Key sections to keep current:
- **Core Stack** — libraries, versions, dependencies
- **Data Model** — Room entities, fields, relationships
- **Screens & Navigation** — Composables, routes, UI flows
- **Architecture & Layers** — repositories, use cases, ViewModels
- **Permissions** — any changes to Android permissions
- **Services** — foreground service behavior, notification channels

Skip trivial changes (typos, pure refactors, UI-only styling). If unsure whether a change qualifies, update the spec.