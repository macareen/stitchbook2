# Stitchbook

Stitchbook is a planned private, local-first fibre-craft companion for Android. Knitting, crochet, Tunisian crochet, loom knitting, and other fibre crafts are intended to be first-class rather than variations of one knitting-centric model. The app is intended to help people manage projects, counters, patterns, yarn, tools, photos, work sessions, and statistics while retaining ownership and access to all of their data.

The project is in **active development**. The repository contains a working Jetpack Compose application with real project management, a manual guide-authoring editor with Publish and Focus Mode execution, a pattern library, a yarn/tools stash, JSON backup/restore, and a deterministic PDF-parsing prototype -- see "Currently implemented" below for specifics. Counters, yarn allocations, photos, a journal, crafting sessions with statistics, share cards, and a reviewed backup/restore are implemented too; Ravelry integration, AI assistance, and a user-chosen library folder are still plans.

## Core principles

- **Local first:** essential functions should work offline.
- **User-owned data:** imported patterns, photos, notes, inventories, and project records must remain accessible outside the app.
- **No mandatory account or subscription:** optional integrations must not become prerequisites.
- **First-class crafts:** knitting, crochet, Tunisian crochet, loom knitting, and other fibre crafts may use different terms, tools, calculations, and workflows.
- **Private by default:** purchased patterns and generated guides remain local unless the user explicitly exports or shares something.
- **Portable by design:** exports use JSON, CSV, Markdown, and PNG; PDF exports are planned.
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
- Navigation Compose with Home, Projects, Library, Stash, Tools, Counters, and Settings destinations, each with real content (not placeholders) and a header-less mobile shell matching the design reference
- Guides by size: each pattern has its own screen with one guide per size, and any project can use one of them.
- Pattern folder: choose one folder (on the phone, or a cloud folder your phone can open) and every PDF in it, including subfolders, appears in Library. Files stay where they are.
- Optional Ravelry pull (Settings → Ravelry): bring your stash yarn, needles, projects, and library patterns in with your own personal key, which is kept encrypted on the phone. Read-only from Ravelry, reviewed before saving, and never deletes anything.
- Import with an assistant: turn a pattern PDF into a draft guide by copying a request to Claude (a free account works) or another assistant and pasting the structured reply back. The app itself sends nothing.
- A calm Home: a Continue card for the guide in progress, the active-projects list, and quiet links to Counters and Statistics; five bottom-bar places (Home, Projects, Library, Stash with a Yarn | Tools switch, Settings)
- A project hub: each project opens on a small map with the project at the centre and its guides, patterns, yarn, tools, counters, journal, and time around it, plus one "next step" button. Tools and counters added there also land in the shared toolbox and counters
- Full project CRUD (create, list, view, edit, delete with confirmation) across a fixed craft/project-type/status taxonomy, plus a description, construction method, a custom type label for "Other", and start/target/completed dates
- A manual guide-authoring Draft editor supporting Section, Row range, Repeat, and Instruction nodes, with add/edit/delete/reorder, structural validation, and optimistic-concurrency conflict recovery
- Publish: a Draft becomes an immutable, versioned Definition Revision through a real Publish action; the Draft remains editable afterward and a later edit publishes as a new Revision
- Focus Mode: Start/Continue a published Guide's execution, with persisted Complete/Previous/Jump-to-incomplete transitions that survive app restarts -- reachable end to end through production UI with no debug tooling or database seeding required. While in progress, a compact strip shows the guide's project's counters with inline increment/decrement, so counters can be tracked without leaving the active-crafting screen (PRODUCT_SPEC.md 6.3's "active crafting screen")
- A pattern library (title, author, source link, tags, notes, bookmarks) with search and craft/bookmark filtering, plus CSV export, a downloadable template, and CSV import with a pre-commit validation report (the PDF attachment below is never a CSV column -- an update-in-place always preserves an item's existing attachment untouched)
- PDF pattern attachment: select a PDF via the Storage Access Framework with durable (persistable) read access, view it page-by-page in a built-in viewer (`android.graphics.pdf.PdfRenderer`, no bundled PDF library) or hand off to another app, and resume from the last-viewed page. The original file is never copied or modified -- only its `content://` URI and display name are stored
- A yarn/tools stash (category, brand, colorway, dye lot, weight, fiber, quantity, yardage, storage, care, purchase data, Ravelry yarn ID, weight per unit, and measured remaining weight) with search and category filtering, plus CSV export, a downloadable template, and CSV import with a pre-commit validation report (row-level errors never discard the file's other valid rows) and duplicate handling by matching a row's `id` column
- Project materials: reserve yarn from the stash for a project, record what was used (never below zero on hand), and link patterns to projects (one pattern can serve many projects). Remaining length from a weighed partial skein is always labelled as an estimate
- A project journal: photo references (never copied) with captions, dates, milestone links, and before/after roles; ordered milestones; and private dated entries. A photo that can't be opened any more can be relinked
- Crafting sessions with a start/pause/resume/stop timer that survives the app being closed, corrections, and a Statistics screen (time by project, craft, and week, rows, streaks, project durations, yarn use) where every figure is labelled recorded, derived, or estimated
- PNG share cards (progress, completed, milestone, before/after, yarn, weekly, annual) with field and photo selection, an exact preview, save to a chosen file, and the Android share sheet. Private notes are never on by default
- Theme (system, light, dark) and measurement display (imperial, metric) preferences in Settings
- A versioned JSON backup (format 2) of every record type above except guides, with a reviewed restore: a preview of new, identical, conflicting, and local-only records, then **Merge** (adds new records only, never overwrites) or **Replace** (confirmed twice, naming what it removes). Afterwards Settings lists what was written, conflicts kept, and files to relink. Version 1 backups still restore. Project detail also exports a single project as JSON or Markdown
- Full local reset via Settings
- A Tools inventory destination (search, category filtering, and a category-adaptive add/edit form covering needles, hooks, interchangeable tips/cables, looms, and notions) backed by Room and included in the JSON backup, plus a Bulk Create Tools screen that generates one item per size from a numeric range or custom list -- with a live, deduplicated preview -- and can group the generated items as a new set, plus CSV export/import (a `setId`/`setName` column pair reconstitutes grouped-set membership, creating or reusing a set by name when only `setName` is given), reusable user templates, a Tool Sets screen for browsing/renaming/deleting sets, and many-to-many project-tool assignment; manufacturer templates are not built -- see ROADMAP.md Phase 5
- A Counters destination: create/edit/delete a counter with a name, a free-text unit label (rows, rounds, motifs, or any user-defined term), an optional goal (shown with a progress bar and a "goal reached" accent color), and an optional owning Project (or standalone); increment/decrement controls and a reset action (with confirmation) persist immediately, and counters are included in the JSON backup. Each counter also supports value-specific notes (a note attached to whatever value it read at the time, e.g. "Row 42: switched to smaller needles"), also included in the JSON backup. A counter may optionally link to one other counter: every N increments of the source, the target is bumped by a fixed amount (e.g. every 4 rows, bump the round counter by 1) -- decrementing or resetting the source never triggers the link, and a link that would create a cycle between counters is rejected before it can be saved. A counter with a goal can also be set to automatically reset to 0 the moment it reaches that goal (the classic "row counter resets each repeat" pattern), independently of any link -- both can fire from the same increment. A counter can also be given a repeating reset schedule (every N days); since the app has no background-execution mechanism, a due schedule fires the next time the Counters screen loads, not at the exact scheduled moment. Focus Mode's in-progress screen now shows a compact strip of the current guide's project's counters with inline increment/decrement, so a project's counters can be tracked without leaving the active-crafting session. While Focus Mode is open, a persistent notification shows the project's counters and offers increment/decrement for the first one
- A deterministic PDF pattern-parsing prototype, end to end: digital-text extraction with page/line source references (PdfBox-Android-backed `data/parsing/PdfBoxTextExtractor.kt`), on-device OCR fallback for pages with no text layer (ML Kit's bundled Latin recognizer, no network dependency), deterministic parsing of the resulting text into sections/row-round ranges/repeats with an ambiguity-issue list (`domain/parsing/PatternTextParser.kt`), and a "Create from PDF" action on a Project's guide list that maps the result into a real, editable, unpublished Guide Draft with provenance kept visible in the existing Draft editor -- see ARCHITECTURE.md for what this prototype's small explicit grammar does and doesn't yet cover
- Room schema version 20 with an explicit, non-destructive migration for every step; schemas 1–20 are exported under `app/schemas`

See [ARCHITECTURE.md](ARCHITECTURE.md) and [docs/EXECUTION_ENGINE_SPEC.md](docs/EXECUTION_ENGINE_SPEC.md) for the execution engine's persistence and concurrency guarantees, the backup format, and the session day-boundary rule. [ROADMAP.md](ROADMAP.md) lists what each phase still leaves open.

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

The Storage Access Framework (documents and photos by reference, backups, exports) and the Android share sheet (cards) are in use. WorkManager is not a dependency: nothing runs in the background except a notification service scoped to an open Focus Mode session.

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

- Guides, drafts, revisions, and executions are not in the JSON backup, and fields written by a newer app version are dropped on re-export rather than preserved.
- The new Stash weight fields and Library pattern fields (gauge, sizes, yardage required, recommended tools, Ravelry pattern ID) are not in their CSV formats yet; a CSV update preserves them but cannot set them.
- PDFs and photos are referenced where the user keeps them; there is no user-chosen library folder or portable mirroring yet, and a missing PDF is re-attached from the edit form rather than through a dedicated relink flow.
- Statistics have no gauge history or tool-usage figures, and yarn consumption has no per-use date, so yarn statistics are all-time.
- Manufacturer tool-set templates are not built.
- Guide authoring can also start from a PDF's extracted (or, when needed, OCR'd) and parsed text; this prototype's parser only recognizes a small explicit set of phrasings, not general natural-language pattern text.
- Unsaved project-form input survives recomposition and ordinary configuration changes, but not full process death.
- Automatic Android app backup is disabled for this private local milestone; uninstalling the app or clearing its data removes records not covered by a user-initiated JSON backup.
- No Ravelry integration or AI assistance exists.
- Instrumented tests (including the Room migration tests) need an emulator or device; all of them pass on an API 36 emulator.
- Larger-screen layouts are not adapted yet.
- Data formats and UI designs are not yet stable.
- Release signing and production distribution are not configured.

## Documentation

- [Product specification](PRODUCT_SPEC.md)
- [Roadmap](ROADMAP.md)
- [Architecture](ARCHITECTURE.md)
- [Contributing](CONTRIBUTING.md)
- [Development and agent guidance](AGENTS.md)
