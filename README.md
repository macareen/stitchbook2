# Stitchbook

Stitchbook is a planned private, local-first fibre-craft companion for Android. Knitting, crochet, Tunisian crochet, loom knitting, and other fibre crafts are intended to be first-class rather than variations of one knitting-centric model. The app is intended to help people manage projects, counters, patterns, yarn, tools, photos, work sessions, and statistics while retaining ownership and access to all of their data.

The project is in **early development**. The repository currently contains a working Jetpack Compose application shell and basic local project CRUD. Most product capabilities described below are plans, not implemented features.

## Core principles

- **Local first:** essential functions should work offline.
- **User-owned data:** imported patterns, photos, notes, inventories, and project records must remain accessible outside the app.
- **No mandatory account or subscription:** optional integrations must not become prerequisites.
- **First-class crafts:** knitting, crochet, Tunisian crochet, loom knitting, and other fibre crafts may use different terms, tools, calculations, and workflows.
- **Private by default:** purchased patterns and generated guides remain local unless the user explicitly exports or shares something.
- **Portable by design:** planned exports use JSON, CSV, Markdown, PDF, and PNG where appropriate; complete backup and restore is later roadmap work.
- **Original implementation:** learn from useful craft workflows without copying proprietary code, branding, text, prompts, or UI.

## Planned capabilities

- Projects with status, construction, gauge, notes, milestones, photos, counters, sessions, yarn use, and exportable summaries
- Configurable project and standalone counters, including goals, schedules, links, and notification actions
- A pattern library for PDFs, web links, publications, personal designs, and manual instructions
- Yarn stash and tool inventories, including partial skeins and grouped interchangeable sets
- Session tracking and clearly labelled recorded versus estimated statistics
- Private journals and selectable, exportable PNG project cards
- User-controlled library storage, portable metadata, backup, and restore
- Later, carefully controlled Ravelry import/synchronization and deterministic pattern parsing
- Experimental, optional local AI assistance only after deterministic parsing and user-review workflows exist

See [PRODUCT_SPEC.md](PRODUCT_SPEC.md) for structured requirements and [ROADMAP.md](ROADMAP.md) for sequencing.

## Currently implemented

- Warm Material 3 light and dark themes
- Navigation Compose with Home, Projects, Library, Stash, and Settings destinations
- A Room version-1 database for basic project records
- Create, list, view, edit, and explicitly confirm deletion of local projects
- Fixed craft, project-type, and project-status choices
- Local project persistence across application restarts

Project persistence currently covers only name, craft, project type, status, optional notes, UUID identity, and created/updated timestamps. Portable export and relationships to patterns, yarn, tools, photos, counters, and sessions are not implemented.

## Technology

The current project uses:

- Kotlin
- Jetpack Compose
- Material 3
- Navigation Compose
- Room
- KSP
- Lifecycle ViewModels and Flow
- Gradle Kotlin DSL
- A single Android `app` module
- Minimum SDK 26

Planned standard Android components still include the Storage Access Framework, WorkManager, and the Android share sheet. These are architectural directions, not current features.

## Open and run

Prerequisites:

- A current Android Studio installation compatible with the repository's Android Gradle Plugin
- JDK 21 (Android Studio's bundled runtime is appropriate)
- Android SDK platform matching the configured compile SDK
- An emulator or physical device running Android 8.0 (API 26) or newer

To run from Android Studio:

1. Clone or download the repository.
2. Open the repository root in Android Studio.
3. Allow Gradle sync to complete.
4. Select the `app` run configuration and an emulator or connected device.
5. Run the app.

Do not commit `local.properties`; Android Studio generates it for the local SDK path.

## Command-line checks

Run these from the repository root:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:test
.\gradlew.bat :app:lint
```

On macOS or Linux, replace `.\gradlew.bat` with `./gradlew`.

The debug APK is normally produced under `app/build/outputs/apk/debug/`.

## Current limitations

- Home, Library, Stash, and Settings remain placeholders.
- Project records contain only the basic fields listed above; custom “Other” project-type labels are deferred.
- Unsaved project-form input survives recomposition and ordinary configuration changes, but not full process death.
- Counters, inventories, pattern storage, settings behavior, portable export, backup, and integrations are not implemented.
- Room is currently local operational storage; complete portability and restore remain roadmap work.
- Automatic Android app backup is disabled for this private local milestone. Uninstalling the app or clearing its data removes project records until user-controlled export/backup is implemented.
- WorkManager is not a dependency.
- No parser or Ravelry integration exists.
- Data formats and UI designs are not yet stable.
- Release signing and production distribution are not configured.

## Documentation

- [Product specification](PRODUCT_SPEC.md)
- [Roadmap](ROADMAP.md)
- [Architecture](ARCHITECTURE.md)
- [Contributing](CONTRIBUTING.md)
- [Development and agent guidance](AGENTS.md)
