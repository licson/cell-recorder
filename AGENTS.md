# Cell Recorder — Agent Guidelines

## Project Context

- **Platform:** Android (min API 30 / Android 11)
- **Language:** Kotlin, Jetpack Compose + Material 3, Hilt, Room
- **Build:** Gradle wrapper (`./gradlew`)
- **Version:** `1.0.3` (versionCode `4`) — defined in `app/build.gradle.kts`
- **Tags:** `v1.0.0`, `v1.0.1`, `v1.0.2`, `v1.0.3`

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
3. Write each entry from the user's perspective — describe **what** changed functionally and **why it matters**, not how it was coded. Use plain language, avoiding class names, field names, or other implementation jargon.
4. Update `versionName` and `versionCode` in `app/build.gradle.kts`.
5. Commit the changes locally.
6. Create the git tag **only if the user explicitly requests it**.

---

## Spec Policy — Update OpenSpec Specs

After any **major functional change** (new feature, removed feature, altered behavior, new screen, data model change, architecture change, permission change), update the relevant specs in `openspec/specs/<domain>/spec.md`.

Behavioral requirements live in spec files under:
- `recording/` — recording lifecycle, triggers, GPS, multi-SIM
- `cell-info/` — cell info collection, CA bands, cell ID split
- `connectivity/` — ping engine, latency measurement
- `analytics/` — session analytics, anomaly detection, handoff
- `sessions/` — session management, detail, replay
- `data/` — import/export formats (CSV, GeoJSON)
- `ui/` — screens, navigation, interactions
- `service/` — background service, notifications, permissions

Implementation details and architecture decisions go in `openspec/design.md`.

Skip trivial changes (typos, pure refactors, UI-only styling). If unsure whether a change qualifies, update the spec.

---

## Code Working Flow — Plan, Confirm, Review, Iterate

When writing or modifying code, always follow this cycle:

1. **Plan** — Analyze the task, search the codebase, and present a concise plan of what will be changed and why (files, functions, approach). Do **not** write any code yet.

2. **Confirm & Execute** — Wait for the user to confirm the plan before writing any code. Only after explicit approval, implement the changes as described.

3. **Review** — After implementation, run the `code-review` subagent (via the Task tool) against the written code. The review prompt must include:
   - The original plan from step 1.
   - All modified files and their diffs.
   - A request to check: (a) code correctness and quality, (b) adherence to the plan, (c) spec compliance if applicable.

4. **Iterate** — If the code-review agent raises major comments or suggestions, address them and repeat steps 2–3 (re-confirm with the user if the fixes deviate from the original plan, then re-review). The code is only considered complete when the code-review agent has **no major comments**.

**Exception:** Trivial one-line fixes (typos, import sorting, etc.) may skip this flow if the user agrees.