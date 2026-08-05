# Stitchbook

Stitchbook is a planned private, local-first fibre-craft companion for Android. Knitting, crochet, Tunisian crochet, loom knitting, and other fibre crafts are intended to be first-class rather than variations of one knitting-centric model. The app is intended to help people manage projects, counters, patterns, yarn, tools, photos, work sessions, and statistics while retaining ownership and access to all of their data.

The project is in **active development**. The repository contains a working Jetpack Compose application with real project management, a manual guide-authoring editor with Publish and Focus Mode execution, a pattern library, a yarn/tools stash, and JSON backup/restore -- see "Currently implemented" below for specifics. Counters, photos, sessions, PDF import, CSV import/export, and deterministic pattern parsing remain plans, not implemented features.

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

- A warm, editorial Material 3 light/dark theme (ivory/rose/serif-headline palette) ported from the approved webapp design reference -- see [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md)
- Navigation Compose with Home, Projects, Library, Stash, Tools, and Settings destinations, each with real content (not placeholders) and a header-less mobile shell matching the design reference
- Home dashboard aggregating real project/execution state: a resume-in-progress hero, project/craft-type stats, quick navigation, and an active-projects list
- Full project CRUD (create, list, view, edit, delete with confirmation) across a fixed craft/project-type/status taxonomy
- A manual guide-authoring Draft editor supporting Section, Row range, Repeat, and Instruction nodes, with add/edit/delete/reorder, structural validation, and optimistic-concurrency conflict recovery
- Publish: a Draft becomes an immutable, versioned Definition Revision through a real Publish action; the Draft remains editable afterward and a later edit publishes as a new Revision
- Focus Mode: Start/Continue a published Guide's execution, with persisted Complete/Previous/Jump-to-incomplete transitions that survive app restarts -- reachable end to end through production UI with no debug tooling or database seeding required
- A pattern library (title, author, source link, tags, notes, bookmarks) with search and craft/bookmark filtering
- PDF pattern attachment: select a PDF via the Storage Access Framework with durable (persistable) read access, view it page-by-page in a built-in viewer (`android.graphics.pdf.PdfRenderer`, no bundled PDF library) or hand off to another app, and resume from the last-viewed page. The original file is never copied or modified -- only its `content://` URI and display name are stored
- A yarn/tools stash (category, brand, colorway, dye lot, weight, fiber, quantity, yardage, notes) with search and category filtering, plus CSV export, a downloadable template, and CSV import with a pre-commit validation report (row-level errors never discard the file's other valid rows) and duplicate handling by matching a row's `id` column
- Portable JSON backup, restore, and full local reset via Settings, through the Storage Access Framework
- A Tools inventory destination (search, category filtering, and a category-adaptive add/edit form covering needles, hooks, interchangeable tips/cables, looms, and notions) backed by Room and included in the JSON backup, plus a Bulk Create Tools screen that generates one item per size from a numeric range or custom list -- with a live, deduplicated preview -- and can group the generated items as a new set, plus CSV export/import (a `setId`/`setName` column pair reconstitutes grouped-set membership, creating or reusing a set by name when only `setName` is given); manufacturer/user templates and browsing/renaming existing sets outside CSV are not yet built -- see ROADMAP.md Phase 5
- Room schema versioned through v6 with real migrations backing Guides, Definition Revisions, Executions, Projects, Library items, Stash items, and Tool sets/items
- A deterministic PDF pattern-parsing prototype, end to end: digital-text extraction with page/line source references (PdfBox-Android-backed `data/parsing/PdfBoxTextExtractor.kt`), on-device OCR fallback for pages with no text layer (ML Kit's bundled Latin recognizer, no network dependency), deterministic parsing of the resulting text into sections/row-round ranges/repeats with an ambiguity-issue list (`domain/parsing/PatternTextParser.kt`), and a "Create from PDF" action on a Project's guide list that maps the result into a real, editable, unpublished Guide Draft with provenance kept visible in the existing Draft editor -- see ARCHITECTURE.md for what this prototype's small explicit grammar does and doesn't yet cover

See [ARCHITECTURE.md](ARCHITECTURE.md) and [docs/EXECUTION_ENGINE_SPEC.md](docs/EXECUTION_ENGINE_SPEC.md) for the execution engine's persistence and concurrency guarantees. Photo attachments, counters, sessions, Tools bulk-creation/grouped-set UI/CSV import-export, and deterministic pattern parsing are not implemented yet -- see [ROADMAP.md](ROADMAP.md).

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

- No photo, counter, or session tracking yet. PDF pattern attachment/viewing exists (see above), but many-to-many pattern-to-project linking and PDF text parsing do not.
- Stash has CSV import/export (see above); Library entries do not. Stash's field set doesn't yet cover full/partial skein tracking, storage location, or purchase data (see ROADMAP.md Phase 4).
- Needle/hook/cable/interchangeable-set tool inventory (with bulk creation) is not implemented.
- Guide authoring can now also start from a PDF's extracted (or, when needed, OCR'd) and parsed text (see above); this prototype's parser only recognizes a small explicit set of phrasings, not general natural-language pattern text.
- Project records contain only the basic fields listed in Currently implemented; custom "Other" project-type labels are deferred.
- Unsaved project-form input survives recomposition and ordinary configuration changes, but not full process death.
- Automatic Android app backup is disabled for this private local milestone; uninstalling the app or clearing its data removes records not covered by a user-initiated JSON export.
- WorkManager is not a dependency.
- No Ravelry integration or AI assistance exists.
- Compose instrumented tests are written and compile but do not execute successfully in this environment (a pre-existing Espresso/emulator `InputManager.getInstance` incompatibility, not specific to any one feature) -- see [ARCHITECTURE.md](ARCHITECTURE.md).
- Data formats and UI designs are not yet stable.
- Release signing and production distribution are not configured.

## Documentation

- [Product specification](PRODUCT_SPEC.md)
- [Roadmap](ROADMAP.md)
- [Architecture](ARCHITECTURE.md)
- [Contributing](CONTRIBUTING.md)
- [Development and agent guidance](AGENTS.md)
