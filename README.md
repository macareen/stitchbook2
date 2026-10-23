# Stitchbook

Stitchbook is a private, local-first fibre-craft companion for Android. Knitting, crochet, Tunisian crochet, loom knitting, and other fibre crafts are first-class rather than variations of one knitting-centric model.

The heart of the app is one path from a pattern to finished knitting:

1. **Patterns** live in a folder you choose (on the phone or in Drive), and every PDF there appears in the Library. The files are never copied or changed.
2. **Guides by size:** for each pattern you make one guide per size. *Fill from the pattern* reads the PDF and keeps only your size's numbers ("60 (66, 72) sts" becomes "66 sts"), and you review every step in the Draft editor before publishing.
3. **Projects** use a pattern's size guide and gather its yarn, needles, counters, journal, and time around it on a small hub.
4. **The knitting view** shows the step, "Row 4 of 10", how far through the whole guide you are (weighted by stitches), total time and time on this step, an overview of the guide, and a button that opens the original PDF at the step's page.

Everything works offline with no account or subscription, and a full JSON backup carries every record, including guides and progress. The project is in **active development**; see "Currently implemented" below and [ROADMAP.md](ROADMAP.md). Functional reference capability targets and unresolved parity gaps are tracked in the roadmap and [LooseLoop functional findings](docs/LOOSELOOP_FUNCTIONAL_FINDINGS.md).

## Core principles

For repository code searches, use the local Graphify index and refresh it after
substantial work; see [Graphify setup and workflow](docs/GRAPHIFY.md) and `AGENTS.md`.

- **Local first:** essential functions should work offline.
- **User-owned data:** imported patterns, photos, notes, inventories, and project records must remain accessible outside the app.
- **No mandatory account or subscription:** optional integrations must not become prerequisites.
- **First-class crafts:** knitting, crochet, Tunisian crochet, loom knitting, and other fibre crafts may use different terms, tools, calculations, and workflows.
- **Private by default:** purchased patterns and generated guides remain local unless the user explicitly exports or shares something.
- **Portable by design:** exports use JSON, CSV, Markdown, and PNG; PDF exports are planned.
- **Original implementation:** learn from useful craft workflows without copying proprietary code, branding, text, prompts, or UI.

## What's next

- Size-aware parsing beyond "a (b, c)" groups: charts, "for size L only" lines, and per-size stitch tables
- Optional on-device AI help for patterns the deterministic parser can't read, always reviewed before use
- A PDF export
- Manufacturer tool templates and richer skein modelling

See [PRODUCT_SPEC.md](PRODUCT_SPEC.md) for structured requirements and [ROADMAP.md](ROADMAP.md) for sequencing.

## Currently implemented

- Manage pieces can create a quantity of 1–100 independently tracked pieces in one reviewed batch, or make a fresh named copy of a piece with the same guide and no copied progress. Numbered names are previewed; all pieces use existing portable exports.

- Edit pattern details directly on the pattern page; failed saves keep the form open and concurrent changes require an explicit reload. The scrollable form supports all crafts and validates optional yardage.
- Pattern pages offer PDF actions to review replacing or unlinking the document. Original files, guide versions and project progress are preserved; a concurrent pattern change requires a fresh confirmation. New guide drafts must be created and reviewed explicitly.
- Library removal reviews named affected projects and retained guides, rechecks references atomically before removing the index entry, and retains failed selections for retry. Original files and guide/progress history are kept.
- Offline calculators for shaping, yarn requirements, gauge conversion and measured swatch gauge. Count stitches and height units over independently measured spans in centimetres or inches; results show both per 10 cm and per 4 inches. Calculator inputs are temporary, not saved swatch records.
- Focus reading uses smaller regular-weight text throughout grouped instructions, an explicit More below scrolling control, and a fresh reading position for each occurrence.
- A cozy pastel Material 3 light/dark theme (blush cream, rose, sky and sage; Fraunces and Nunito fonts bundled offline) with a floating pill bottom bar and an adaptive heart yarn-ball launcher icon (themed-icon ready) -- see [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md)
- Navigation Compose with Home, Projects, Library, Stash, Tools, Counters, and Settings destinations, each with real content (not placeholders) and a header-less mobile shell matching the design reference
- Guides by size: each pattern has its own screen (tap its Library card) with one guide per size, and any project can use one of them; using it also links the pattern to the project. *Fill from the pattern* builds the draft from the PDF with only that size's numbers, and says so in the draft when the size isn't one the pattern lists.
- The knitting view (Focus Mode) shows a stitch-weighted progress bar, labelled as an estimate when some rows don't state a count; total time and time on this step from the project's crafting sessions, with a Pause/Resume timer that starts on your first Complete; an *Overview* of the whole guide where tapping a line jumps there; and *Pattern, p.N*, which opens the PDF at the current step's page. Knitting in a *Planned* project makes it *Active*, and progress is kept separately per project when two projects use one guide.
- Pattern folder: choose one folder (on the phone, or a cloud folder your phone can open) and every PDF in it, including subfolders, appears in Library. Files stay where they are.
- Pattern guides resolve explicit size-only lines and size branches closed by All sizes. Unclear selectors remain intact for review, including their original multi-size values.
- Optional Ravelry pull (Settings → Ravelry): bring your stash yarn, needles, projects, and library patterns in with your own personal key, which is kept encrypted on the phone. Reviewed before saving, and never deletes anything. Stash cards can show your own Ravelry entry photo: source links are included in full-library JSON backups; bounded thumbnails download only during an explicit pull and remain in cacheDir for offline browsing. Local attached photos take cover priority. Clearing cache or restoring on another device requires another pull to refill thumbnails. On a Ravelry pattern's page, *Get the PDF from Ravelry* saves your purchased PDF into your pattern folder (never replacing a file already there) and links it, so it opens in the app and the card shows its cover.
- Import with an assistant: turn a pattern PDF into a draft guide by copying a request to Claude (a free account works) or another assistant and pasting the structured reply back. The app itself sends nothing.
- A calm Home: a Continue card for the guide in progress, the active-projects list, and quiet links to Counters and Statistics; five bottom-bar places (Home, Projects, Library, Stash with a Yarn | Tools switch, Settings)
- A project hub: each project opens on a small map with the project at the centre and its guides, patterns, yarn, tools, counters, journal, and time around it, plus one "next step" button. Tools and counters added there also land in the shared toolbox and counters
- Full project CRUD (create, list, view, edit, delete with confirmation) across a fixed craft/project-type/status taxonomy, plus a description, construction method, a custom type label for "Other", and start/target/completed dates
- Correct a guide directly from Focus using **Edit guide**, without visiting the pattern page. **Repeat this instruction** wraps just one instruction (for example, seven plain rows before a separate decrease row). Publish, return to the same project/piece, then review matching work and choose your next step. Explicitly confirming earlier steps as done can reconcile changed structures; it is never inferred. The previous publication and saved progress remain available. Editing a reusable guide changes its shared draft; other projects keep their pinned publication until reviewed.
- PDF imports put unreadable-page warnings first in the editable draft, before the working instructions. Every newly imported instruction retains its own PDF page, including repeated cycles and backwards/jump navigation. Assistant request text also identifies pages without readable text. A page with partial text or a chart still needs manual review; existing publications are never silently rebuilt.
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
- A versioned JSON backup (format 4) of every record type above, including guides with their drafts, published revisions, and progress, with a reviewed restore: a preview of new, identical, conflicting, and local-only records, then **Merge** (adds new records only, never overwrites) or **Replace** (confirmed twice, naming what it removes). Afterwards Settings lists what was written, conflicts kept, links Merge had to clear or skip, and files to relink. Version 1 and 2 backups still restore, leaving guides untouched; version 3 guide backups also remain readable. Project detail also exports a single project as JSON (restorable, carrying the guides it knits from and only its own progress) or Markdown (with each guide written out as plain steps)
- Full local reset via Settings, including pattern guides that belong to no project
- A Tools inventory destination (search, category filtering, and a category-adaptive add/edit form covering needles, hooks, interchangeable tips/cables, looms, and notions) backed by Room and included in the JSON backup, plus a Bulk Create Tools screen that generates one item per size from a numeric range or custom list -- with a live, deduplicated preview -- and can group the generated items as a new set, plus CSV export/import (a `setId`/`setName` column pair reconstitutes grouped-set membership, creating or reusing a set by name when only `setName` is given), reusable user templates, a Tool Sets screen for browsing/renaming/deleting sets, and many-to-many project-tool assignment; manufacturer templates are not built -- see ROADMAP.md Phase 5
- A Counters destination: create/edit/delete a counter with a name, a free-text unit label (rows, rounds, motifs, or any user-defined term), an optional goal (shown with a progress bar and a "goal reached" accent color), and an optional owning Project (or standalone); increment/decrement controls and a reset action (with confirmation) persist immediately, and counters are included in the JSON backup. Each counter also supports value-specific notes (a note attached to whatever value it read at the time, e.g. "Row 42: switched to smaller needles"), also included in the JSON backup. A counter may optionally link to one other counter: every N increments of the source, the target is bumped by a fixed amount (e.g. every 4 rows, bump the round counter by 1) -- decrementing or resetting the source never triggers the link, and a link that would create a cycle between counters is rejected before it can be saved. A counter with a goal can also be set to automatically reset to 0 the moment it reaches that goal (the classic "row counter resets each repeat" pattern), independently of any link -- both can fire from the same increment. A counter can also be given a repeating reset schedule (every N days); since the app has no background-execution mechanism, a due schedule fires the next time the Counters screen loads, not at the exact scheduled moment. Focus Mode's in-progress screen now shows a compact strip of the current guide's project's counters with inline increment/decrement, so a project's counters can be tracked without leaving the active-crafting session. While Focus Mode is open, a persistent notification shows the project's counters and offers increment/decrement for the first one
- A deterministic PDF pattern-parsing prototype, end to end: digital-text extraction with page/line source references (PdfBox-Android-backed `data/parsing/PdfBoxTextExtractor.kt`), on-device OCR fallback for pages with no text layer (ML Kit's bundled Latin recognizer, no network dependency), deterministic parsing of the resulting text into sections/row-round ranges/repeats with an ambiguity-issue list (`domain/parsing/PatternTextParser.kt`), and a "Create from PDF" action on a Project's guide list that maps the result into a real, editable, unpublished Guide Draft with provenance kept visible in the existing Draft editor -- see ARCHITECTURE.md for what this prototype's small explicit grammar does and doesn't yet cover
- Room schema version 25 with an explicit, non-destructive migration for every step; schemas 1–25 are exported under `app/schemas`

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

- Fields written by a newer app version are dropped on re-export rather than preserved.
- The new Stash weight fields and Library pattern fields (gauge, sizes, yardage required, recommended tools, Ravelry pattern ID) are not in their CSV formats yet; a CSV update preserves them but cannot set them.
- PDFs and photos are referenced where the user keeps them; there is no user-chosen library folder or portable mirroring yet, and a missing PDF is re-attached from the edit form rather than through a dedicated relink flow.
- Statistics have no gauge history or tool-usage figures, and yarn consumption has no per-use date, so yarn statistics are all-time.
- Manufacturer tool-set templates are not built.
- Guide authoring can also start from a PDF's extracted (or, when needed, OCR'd) and parsed text; this prototype's parser only recognizes a small explicit set of phrasings, not general natural-language pattern text.
- Unsaved project-form input survives recomposition and ordinary configuration changes, but not full process death.
- Automatic Android app backup is disabled for this private local milestone; uninstalling the app or clearing its data removes records not covered by a user-initiated JSON backup.
- Ravelry import is optional; automatic AI assistance is not implemented.
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

Project detail now brings photos, linked pattern/PDF actions, recorded time, yarn allocations, tools, guide sections and working notes together. Choose a project photo as its cover; Home uses it too, and the choice travels in JSON/Markdown exports. Follow guide and a project counter remain visible while scrolling.

Add a tagged working note directly from the project page. Its section/row/repeat context is prefilled from the current guide when available and remains editable. Tags and context appear in the journal and travel in JSON/Markdown exports.

Marking a project Completed ends its open crafting sessions as part of the same save. Paused time stays excluded, and timers for other projects remain untouched.

Project → **Manage pieces** keeps named components such as Left sleeve and Right sleeve separate. Each follows its chosen whole guide with its own revision and progress; renaming or archiving keeps the work, and archived pieces can be restored. Home Continue opens the correct active piece. Full-library and project JSON (format 4) preserve pieces and work history; project Markdown includes readable piece summaries. Older backups remain readable, with reviewed Replace protecting work they do not carry. Subsection-only pieces, quantities and mirroring remain roadmap work.

Guide updates: in Focus, open Overview > Guide versions to review matching completed steps before using the latest publication. Previous unfinished progress remains a resumable saved place for that project or piece; finished records remain readable.

Guide versions defaults to the latest publication; Compare a publication also offers earlier revisions. Selecting a different revision builds a fresh carryover proposal before you apply it.

For continuing development, read the repository [session handoff](docs/HANDOFF.md).

Pattern PDFs now set up description, gauge, tools, suggested yarn, techniques and glossary when recognizable at import. Existing values are kept. Open a pattern and choose **Read PDF details** to retry or fill an older record; **Pattern reference** shows shared supporting information. Size guides use that reference without adding definitions to progress. Existing published guides still need reviewed corrections through **Edit guide**.

Focus shows the working row/action above the instruction, with tappable glossary terms and a saved cast-on counter. **Calculators → Photo gauge** lets you align a stitch/row grid over a photo and enter known physical spans. It is a manual measuring aid; a photo alone does not supply scale.
