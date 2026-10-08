# Stitchbook Roadmap

This roadmap sequences product work; it is not a promise of dates. Each phase should leave the application buildable, tested in proportion to risk, and documented. Later-phase data should not be modeled prematurely unless an earlier feature requires it.

Every phase that adds durable user records must also extend the current versioned, user-accessible safety export and its compatibility tests. This incremental export is intentionally smaller than Phase 10's complete library backup/restore. Every UI phase must also meet the existing accessibility baseline—scalable text, meaningful semantics, usable focus order, adequate contrast, and appropriate touch targets—rather than deferring accessibility to Phase 14.

## Phase 0 — Working Compose app and repository setup

**Status:** Complete.

**Goal:** Establish a reproducible, documented baseline without adding product features.

**Scope:**

- Preserve the working single-module Compose starter app.
- Document product requirements, architecture, contribution guidance, and commands.
- Confirm debug build, local test, and lint tasks.
- Establish lightweight review and privacy guardrails.

**Explicit non-goals:**

- Application features, persistence, navigation, or new major dependencies
- Final branding, production UI, or release distribution

**Acceptance criteria:**

- Starter app remains buildable and runnable.
- Foundational documentation is internally consistent and reflects current versus planned behavior.
- Build/test/lint commands are documented.
- Repository contains no credentials, signing keys, or personal pattern content.

**Dependencies:** None.

## Phase 1 — Application shell, theme, navigation, and basic settings

**Status:** Mostly complete. The shell has a warm Material 3 light/dark theme, one Navigation Compose host, bottom navigation, and the design-system foundation in [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md). Basic local preferences now exist: theme behaviour (system, light, or dark) and measurement display (imperial or metric), stored on the device with `SharedPreferences` behind `UserPreferencesRepository`, edited from a Settings card, and provided to every screen through `LocalMeasurementSystem`. Display conversion only changes how lengths are shown; stored values keep their original unit. Shared surfaces, date fields, photo thumbnails, and duration text live in `ui/components`. Still open: larger-screen adaptation. The Compose instrumented tests run and pass on an API 36 emulator.

**Goal:** Create an accessible offline shell ready to host features.

**Scope:**

- Material 3 theme and top-level navigation, with larger-screen adaptation when justified
- Home, Projects, Library, Stash, and Settings destinations with honest empty states
- Navigation Compose and centralized route definitions
- Basic local preferences such as theme behavior and measurement display defaults
- Accessibility baseline and UI test foundation

**Explicit non-goals:**

- Project persistence, counters, inventories, account creation, or synchronization
- Final design system or extensive component library

**Acceptance criteria:**

- All initial destinations are reachable and restore navigation state appropriately.
- Settings persist locally without network access.
- Core screens have previews where practical and basic accessibility semantics.
- Unit tests, lint, and debug build pass.

**Dependencies:** Phase 0.

## Phase 2 — Initial Room database and project CRUD

**Status:** Complete. Projects carry a UUID, name, craft, project type with an optional custom type label (shown when the type is Other), status, private notes, a longer description, a free-text construction method, and optional start, target, and completed local dates (`yyyy-MM-dd`). Dates are validated together: each must parse, and a completion before the start is rejected, while a target before the start is allowed. Room v14 → v15 adds the six columns as nullable `ALTER TABLE ADD COLUMN` steps. Project detail exports one project as restorable JSON or as readable Markdown through a user-chosen document (see Phase 10). The 14 → 18 migration test passes on an API 36 emulator.

**Goal:** Let users reliably create and manage core local project records.

**Scope:**

- Room setup, migration test harness, repository interface, and implementation
- Project identity, name, description, first-class craft, project type, construction method, status, and relevant dates
- Project list, detail, create, edit, archive/delete behavior, and empty/error states
- Stable UUIDs and version-ready timestamps
- A small, versioned JSON project export through a user-selected document

**Explicit non-goals:**

- Counters, patterns, yarn, tools, photos, sessions, statistics, synchronization, or comprehensive project fields
- Complete-library backup/restore, continuous portable mirroring, or a permanent library-folder layout
- Storing irreplaceable files or the only exportable copy of durable records in Room

**Acceptance criteria:**

- Project CRUD persists across process restarts.
- All five craft categories and five project statuses are representable.
- Custom project types are supported without schema changes.
- Craft-specific presentation does not expose knitting-only terminology as universal; crochet, Tunisian crochet, loom knitting, and custom craft labels are covered by tests.
- Project records export without network access, the format version is documented, and export errors are visible.
- Validation, DAO behavior, repository behavior, and migrations have automated tests.
- Destructive actions are explicit and recoverable where practical.

**Dependencies:** Phases 0–1.

## Phase 3 — Configurable counters and active crafting screen

**Status: complete -- CRUD, increment/decrement/reset, JSON backup inclusion, value-specific notes, a goal-reached progress indicator, linked-counter behavior, automatic reset-on-goal, repeating reset schedules, an active-crafting counters section in Focus Mode, and Focus-Mode-scoped persistent notifications all now exist.** `Counter` (`domain/model/Counter.kt`) has a current value, an optional goal, and an optional owning Project (`projectId` -- null for a standalone counter); `name` and `unitLabel` are both free text rather than a closed category enum, since PRODUCT_SPEC.md 6.3 explicitly wants "user-defined purposes" and craft-language labels ("rows, rounds, motifs, ... and user-defined terms") rather than a fixed taxonomy to validate against. `CounterRepository`/`LocalCounterRepository` follow the same shape as `StashRepository`. A Counters top-level destination (`feature/counters`) offers search, create/edit/delete, an optional Project picker in the form, and increment/decrement/reset (reset gated behind a confirmation dialog, matching the risk PRODUCT_SPEC.md 6.3 calls for), plus a `LinearProgressIndicator` and tertiary accent once a goal is reached. `CounterNote` (`domain/model/CounterNote.kt`) attaches a note to whatever value a counter read at the time (PRODUCT_SPEC.md 6.3, "Notes attached to particular values") -- a snapshot, not a live reference, so a note survives the counter changing or resetting later; `counter_notes` uses a required `counter_id` FK with ON DELETE CASCADE, unlike a Counter's own optional `project_id`. A counter may optionally link to exactly one other counter (`linkedCounterId`/`linkIncrementInterval`/`linkIncrementAmount`): every N increments of the source, the target is bumped by a fixed amount -- only forward increments trigger this, never decrement/reset, and `wouldCreateCycle()` (`domain/model/Counter.kt`) rejects any link that would complete a cycle, checked both in the dialog before save and defensively in `CountersViewModel.saveCounter`. `linked_counter_id` is a self-referencing FK with ON DELETE SET NULL (a link is a pointer, not ownership, the same relationship `tool_items` has to `tool_sets`); adding it required recreating the `counters` table (SQLite can't add a FK column via `ALTER TABLE ADD COLUMN`), following Android's documented migration pattern for that case. Counters, Counter notes, and counter links are all included in the JSON backup (`LocalBackupService`; a counter linking to another is saved link-stripped first, then re-saved with its real link, since import order isn't guaranteed to put a target before the counter that links to it; a backup's link is also sanitized against cycles/dangling references before it's ever written, since a backup file is untrusted input). `Counter.autoResetOnGoal` (PRODUCT_SPEC.md 6.3, "Automatic reset rules"): if true and a goal is set, reaching the goal via increment resets the counter back to 0 in the same step -- the "row counter resets each repeat" pattern -- independently of any outgoing link, so a single increment can both bump a linked target and auto-reset. This needed only a plain `ALTER TABLE ADD COLUMN` (v9->v10), unlike the link column's table recreation, since it isn't a foreign key. `Counter.repeatIntervalDays`/`lastRepeatResetAt` (PRODUCT_SPEC.md 6.3, "Repeating schedules"): a counter can reset itself every N days, measured from `lastRepeatResetAt` (or `createdAt` if the schedule has never fired). `dueForRepeatingReset()` (`domain/model/Counter.kt`) is a pure function of a counter and a timestamp; `CountersViewModel` calls it once per counter in an `init` block when the ViewModel is created, since **this app has no background-execution mechanism (no WorkManager/AlarmManager anywhere)** -- a due schedule only actually fires the next time Counters loads, not at the exact scheduled moment. Saving a *new or changed* interval sets the baseline to the save time (not `createdAt`), so an old counter can't look instantly "overdue" the moment a schedule is added to it; saving with the *same* interval preserves the existing baseline. v10->v11 added two more plain nullable columns via `ALTER TABLE ADD COLUMN`. Focus Mode's `InProgress` state (`feature/focus/GuideFocusScreen.kt`) now shows a compact, always-reachable strip of the current guide's project's counters with inline increment/decrement (PRODUCT_SPEC.md 6.3's "active crafting screen": tracking counters without leaving the crafting session) -- fetched once per load/refresh rather than observed live, matching this screen's existing non-reactive-snapshot style, and carried forward unchanged across a Complete/Previous transition rather than re-fetched, since a guide-execution transition never changes counters on its own. The increment behavior itself (link-trigger + auto-reset) was extracted out of `CountersViewModel` into `IncrementCounterUseCase` (`domain/usecase/`) so both this screen and the Counters screen share one implementation instead of duplicating it -- this also fixed a latent bug where a counter with goal=1 and auto-reset enabled would silently skip persisting an increment that landed back on its original value. `CounterFocusNotificationService` (`data/notification/`) is a foreground service scoped to one Focus Mode session: `GuideFocusScreen` starts it (requesting Android 13+'s `POST_NOTIFICATIONS` permission first if needed) whenever `InProgress` has at least one project counter, and stops it when the screen is left or the state stops qualifying -- there is deliberately no always-on background tracking, matching this app's complete absence of WorkManager/AlarmManager elsewhere. The persistent notification shows every counter's current value/goal as text and offers increment/decrement actions for the first counter only (Android's action-button limits made one action pair per counter impractical); tapping the notification body reopens the app. This closes out Phase 3.

**Goal:** Deliver a dependable low-friction experience for tracking active work.

**Scope:**

- Project-specific and standalone counters
- Increment, decrement, goal, reset, and value-specific notes
- Multiple named counter types and craft-appropriate terminology
- Deterministic linked behavior, automatic resets, and repeating schedules
- Active crafting screen and persistent notification actions
- Process-restart and lifecycle resilience
- Inclusion of counters and value-specific notes in the versioned MVP export

**Explicit non-goals:**

- Timed sessions and full statistics
- Pattern-generated counters or PDF parsing
- Arbitrary scripting of counter rules

**Acceptance criteria:**

- Counter actions persist promptly and remain correct after restart.
- Linked/reset rules reject cycles and have comprehensive unit tests.
- Goals and scheduled actions are explained in the UI.
- Rows, rounds, motifs, Tunisian forward/return passes, loom-oriented labels, and user-defined terminology are representable without changing the schema.
- Notification actions reach the intended counter and handle stale projects safely.
- Main controls meet accessibility and touch-target requirements.

**Dependencies:** Phases 0–2.

## Phase 4 — Yarn inventory

**Status: Mostly complete; two gaps remain.** Implemented: Stash CRUD with search and category filtering; storage, purchase, care, and Ravelry-ID fields; the versioned Stash CSV (`data/csv/StashCsv.kt`, schema v2) with a pre-commit validation report and `id`-based updates; and, from Room v16, yarn allocations and consumption. A `YarnAllocation` (`domain/model/YarnAllocation.kt`) reserves part of a stash item for a project in the item's own unit, so no conversion is ever implied. Reservations can't exceed the unallocated quantity, and consumption draws on the reservation first, then unreserved stock, and never drives a quantity below zero. The stash item and allocation are written in one transaction (`MaterialsRepository.recordConsumption`). Stash items gain a manufacturer's weight per unit and a *measured* remaining weight for partial skeins. Remaining length derived from that weight is always labelled as an estimate, and consuming yarn scales a weighed remainder proportionally. Allocations are managed from a project's Materials screen (`feature/materials`) and included in the JSON backup. Stash items can have photos (Phase 7). Still open: the two weight fields are not in the Stash CSV yet (a CSV update preserves their existing values but cannot set them), and allocation history has no per-use date, so yarn statistics are all-time.

**Goal:** Track usable yarn quantities and their relationship to projects.

**Scope:**

- Yarn identity, fibre, weight category, colourway, dye lot, skein measures, purchase/storage data, notes, care, and optional Ravelry ID
- Full and partial skeins
- Measured weight, estimated remaining length, allocations, and consumption
- Unit conversion and clear estimate labels

**Explicit non-goals:**

- Retail ordering, price comparison, marketplace features, or Ravelry synchronization
- Computer-vision identification of yarn
- Long-lived yarn photo attachment before the shared user-accessible photo-storage work in Phase 7

**Acceptance criteria:**

- Users can create, edit, search/filter, allocate, consume, and correct yarn records.
- Quantities remain consistent through transactional operations.
- Original and converted units are preserved or traceable.
- Estimates and recorded measures are visually distinct and tested.

**Dependencies:** Phases 0–2; counters from Phase 3 are not required.

## Phase 5 — Tools, grouped sets, ranges, and compatibility

**Status: complete except manufacturer-set templates.** Domain model, Room schema (v13), repository, individual-item CRUD UI, size-range/list bulk creation, CSV import/export, ToolSet browsing/renaming/deleting UI, reusable user templates, and many-to-many project-tool assignment are all implemented. `ToolItem` (one physically countable component) and `ToolSet` (a named grouping such as a complete commercial interchangeable set) cover the full type list in the Scope below, with sizing/length stored canonically in millimeters plus an optional free-text convenience label, and interchangeable-cable fields (stated length, its definition, approximate assembled length) and informational (non-validated) connector-family/compatibility-notes fields. `ToolItem.setId` references its owning `ToolSet` without duplicating stock -- availability is always the component's own `quantity`, never a set-level count -- and deleting a set returns its components to standalone items (`ON DELETE SET NULL`) rather than deleting them. Tool sets and items are included in the versioned safety export alongside Projects, Library, and Stash. A Tools top-level destination offers search, category filtering, and a single-item add/edit form whose fields adapt to the selected category (for example, cable-length fields only appear for interchangeable cables) -- backed by `ToolsViewModel`/`ToolsScreen`. A "Bulk Create Tools" screen (`BulkToolCreationViewModel`/`BulkToolCreationScreen`) generates one item per size from either a numeric range (start/end/increment) or a comma-separated custom list -- scoped to the categories with a meaningful numeric size (needles, hooks, interchangeable tips) since categories without one (markers, stoppers, notions...) already have adequate quantity support through the single-item form -- with a live preview before committing (deduplicated and capped so a bad increment can't generate an unbounded number of items) and an optional "group as a new set" toggle that creates one `ToolSet` and assigns every generated item to it. A documented, versioned CSV format (`data/csv/ToolsCsv.kt`, schema v1) mirrors Stash's: export, a downloadable one-row template, and import with a pre-commit validation report and duplicate handling by matching the `id` column. It also reconstitutes grouped-set membership without a separate set row -- a `setId`/`setName` column pair per item row, where a blank `setId` with a non-blank `setName` resolves to (or creates, once per distinct name) a set by case-insensitive name match, so hand-typed CSVs can group rows into a set without knowing its id in advance. A `ToolSets` screen (`ToolSetsViewModel`/`ToolSetsScreen`, reached via a "Manage Tool Sets" link on the Tools screen) lists every set with its live-derived item count, and offers rename (name/brand/notes) and delete (confirmation-gated, same `ON DELETE SET NULL` fallout as before) directly -- closing the gap where CSV re-import was the only way to affect an existing set. Reassigning an already-created item to a different (or no) set is a per-item action instead, via a new "Set" dropdown in the existing add/edit `ToolItemDialog`. `ToolTemplate` (`domain/model/ToolTemplate.kt`, Room v11->v12, a standalone table with no foreign key -- the simplest kind of migration) is a saved, reusable preset of Bulk Create Tools' own form fields: category, brand, material, size-input mode plus its range/custom-sizes fields, quantity per size, storage location, notes, and the optional "create as a set" toggle plus its set name. `BulkToolCreationScreen` gained a "Load a saved template" dropdown (applying one only pre-fills the form -- it never creates a `ToolItem` on its own, matching PRODUCT_SPEC.md 6.8's "Templates describe what to create; they are not the authoritative inventory after creation") and a "Save as Template" action; each saved template can also be deleted from the same dropdown. `BulkSizeInputMode` moved from `feature/tools` into `domain/model` since a persisted `ToolTemplate` now needs the same enum the bulk-creation form already used. Deliberately excludes manufacturer-set templates (curated presets for specific commercial products) -- that is a content-curation problem (sourcing and maintaining real manufacturer size/quantity data), not a schema one, and is left for a separate future slice. Tool templates were first left out of the JSON safety export; the version-2 backup (Phase 10) now includes them. `ProjectToolAssignmentEntity` (Room v12->v13) is this schema's first genuine many-to-many junction table (ARCHITECTURE.md §9's "explicit join entities" for projects-tools) -- a pure membership record (composite primary key, both FKs cascading, since a join row means nothing once either parent is gone), carrying no metadata of its own per PRODUCT_SPEC.md 6.8. Assigning happens from the Tools screen only: `ToolItemCard` gained an "Assign to projects" action opening a multi-select checklist dialog (`ToolsViewModel` now also depends on `ProjectRepository` for this) that replaces a tool's entire project-assignment set atomically on Save. `ProjectDetailScreen` gained a read-only-with-unassign `ToolsSection` mirroring `GuidesSection`'s shape -- there is deliberately no "assign a tool" entry point from the Project side, since tools already have one on their own screen. Project-tool assignments are likewise included in the version-2 backup.

**Goal:** Represent real tool collections, including interchangeable systems, without flattening grouped sets.

**Scope:**

- All specified needle, hook, loom, component, and notion types
- Individual components, quantities, storage, and project assignments
- Grouped sets with retained component identities
- Bulk creation by ranges, selections, quantities, manufacturer templates, and reusable user templates
- Connector families, adapters, and compatibility information

**Explicit non-goals:**

- Commerce, manufacturer scraping, or an exhaustive global compatibility database
- Automatic physical inventory detection

**Acceptance criteria:**

- A commercial interchangeable set can be displayed as a set and queried as components.
- Set membership does not duplicate stock; availability and assignment derive from underlying component quantities.
- Bulk creation previews changes and avoids unintended duplicates.
- Connector-family, adapter, cable-length-definition, and approximate assembled-length rules are transparent and tested for needle-tip and Tunisian-hook systems.
- Assigned components cannot appear falsely available.

**Dependencies:** Phases 0–2; project relationships use Phase 2 IDs.

## Phase 6 — Pattern library and portable PDF storage

**Status: Mostly complete; storage-folder and CSV gaps remain.** Implemented: one SAF-referenced PDF per Library item (persisted `content://` permission and captured display name, never a copy), a built-in `PdfRenderer` viewer that remembers the last page, "open in another app", recoverable messages for revoked or missing files, and the versioned Library CSV (`data/csv/LibraryCsv.kt`, schema v1). From Room v16, patterns also carry gauge, sizes, yardage required, recommended tools, and a Ravelry pattern ID. Many-to-many project–pattern links (`ProjectPatternLink`) are managed from a project's Materials screen, so one pattern can serve many projects. Pattern metadata, links, and PDF references (with display names for relinking on another device) are in the version-2 JSON backup. Still open: the new pattern fields are not in the Library CSV yet (an `id`-matched CSV update preserves them but cannot set them); there is no dedicated relink flow for a missing PDF (the user re-attaches it from the edit form); and managed (copy-into-folder) imports are not built. A linked pattern folder is done: choose a folder once, and its PDFs, including subfolders, appear in Library automatically, read-only. Files are never moved, and gone files are kept as entries (see ARCHITECTURE.md §8).

**Goal:** Catalog patterns while keeping original PDFs untouched and user-accessible.

**Scope:**

- PDF, web, publication, personal-design, and manual-instruction records
- Metadata, tags, sizes, gauge, yardage, recommended tools, notes, source/purchase data, and optional Ravelry IDs
- Many-to-many project links
- SAF library-folder selection, persisted access, import copy/reference policy, opening in other apps, and relinking
- Portable pattern metadata

**Explicit non-goals:**

- PDF parsing, guide generation, content redistribution, DRM circumvention, or cloud upload

**Acceptance criteria:**

- Imported originals are never overwritten and remain accessible outside Stitchbook.
- One pattern links to multiple projects.
- Missing permission or missing files produce a recoverable relink flow.
- Storage behavior is tested across fakes and representative SAF providers.

**Dependencies:** Phases 0–2 and foundational storage decisions from `ARCHITECTURE.md`.

## Phase 7 — Project photos, journal, and milestones

**Status: Implemented.** Room v17 adds `photos`, `journal_entries`, and `milestones`. A `Photo` is a *reference*: a persisted-permission SAF `content://` URI plus the display name captured at attach time. The original is never copied into app storage, and deleting the record never deletes the file. Photos belong to a project or to a stash item, and carry an optional caption, taken date, milestone association, and before/after role. When several photos share a role, the most recently updated one is used. Journal entries are private and dated, shown newest first. Milestones keep the user's order through an explicit `position`; reordering renumbers positions to 0..n-1 so gaps or duplicates from older data are repaired. A project's Journal screen (`feature/journal`) shows photos, milestones, and entries. A photo that can no longer be opened is flagged and can be relinked to a newly picked file. Thumbnails are decoded off the main thread and cached only in memory (`data/photos/PhotoThumbnailLoader.kt`), so the only private copies are reproducible derivatives. All three types are in the version-2 JSON backup; photos are referenced there, never embedded. Still open: SAF library-folder storage (shared with Phase 6), and performance testing with large photo collections.

**Goal:** Build a private visual and written history of a project.

**Scope:**

- Progress photos with captions and dates
- Yarn and other inventory photos that use the same user-accessible storage policy
- Project milestones and photo associations
- Private journal entries and modifications
- Before-and-after selection
- User-accessible storage and thumbnail/cache handling

**Explicit non-goals:**

- Social feed, public profiles, automatic sharing, or advanced photo editing

**Acceptance criteria:**

- Photo originals remain accessible and are not silently deleted with an app record.
- No long-lived original is stored only in an application-private directory; private cache contains reproducible derivatives only.
- Entries and milestones preserve order and dates.
- Missing external photos are reported and can be relinked.
- Large photo collections do not block common project screens.

**Dependencies:** Phases 0–2 and SAF patterns established in Phase 6.

## Phase 8 — Timers, sessions, and statistics

**Status: Implemented except gauge history and tool-usage statistics.** Room v18 adds `crafting_sessions`. A session stores only wall-clock timestamps and its starting time zone, so a running or paused timer survives process death without any background service. Start, pause, resume, stop, correction (start time and worked duration), and delete are pure transitions in `SessionTimer` (`domain/model/CraftingSession.kt`). Day-boundary rule: a session counts entirely toward the local date it started on, in its recorded zone (see ARCHITECTURE.md). `computeStatistics` is a pure, deterministic function over the selected range (last 7 or 30 days, this year, or all time). It covers time by project, craft, and month; an 8-week trend chart; session count and average; recorded rows and time per row; estimated stitches; current and longest streaks; projects started and completed; average project duration; and yarn used per unit, with estimated yards. Every figure is labelled recorded, derived, or estimated. Sessions are in the version-2 JSON backup. Still open: gauge history and tool-usage statistics (no data model yet), and per-use dates for yarn consumption.

**Goal:** Record crafting time and produce trustworthy, explainable insights.

**Scope:**

- Start, pause/stop, edit, and delete sessions
- Aggregation by project, craft, and time period
- Rows/rounds, estimated stitches, time-per-unit, streaks, project duration, starts/completions, yarn consumption, gauge history, tool usage, and trends
- Provenance and recorded-versus-estimated labelling

**Explicit non-goals:**

- Medical/productivity claims, social leaderboards, or opaque scoring
- Always-on background execution when platform-safe timestamps suffice

**Acceptance criteria:**

- Sessions recover safely from lifecycle and process interruption.
- Time-zone and day-boundary behavior is specified and tested.
- Every statistic explains its range, inputs, and recorded/estimated status.
- Corrections cause deterministic recomputation.

**Dependencies:** Phases 0–3; richer yarn/tool statistics benefit from Phases 4–5.

## Phase 9 — Exportable visual project cards

**Status: Implemented.** Project cards (progress, completed, milestone, before/after, yarn) and summary cards (weekly, annual) are built from a pure `CardContent` model (`domain/cards`) and drawn to a 1080 × 1350 PNG by `ui/cards/ShareCardRenderer.kt`, entirely offline. The user picks fields and a photo and sees the exact preview before saving or sharing. Private fields (project notes) are never on by default. A PNG is saved through `ACTION_CREATE_DOCUMENT`, or shared through the Android share sheet from a `FileProvider`-exposed cache folder (`res/xml/share_paths.xml`, `cache/cards/` only). The card is drawn fresh, so no camera EXIF or location metadata is carried over. Each card offers a copyable text description as its accessible alternative. Still open: a Compose UI test for the preview.

**Goal:** Let users deliberately create attractive, private-by-default PNG summaries.

**Scope:**

- Progress, completion, milestone, weekly, yarn, annual, and before/after card templates
- Field and photo selection
- Preview, PNG generation, save, and Android share sheet
- Accessible text alternatives in adjacent export metadata where practical

**Explicit non-goals:**

- Built-in social network, direct automatic posting, or cloud rendering

**Acceptance criteria:**

- Users see exactly what will be exported before sharing.
- Cards render consistently at documented sizes without network access.
- Private fields and location metadata are excluded unless explicitly selected.
- Exported PNGs are user-accessible.

**Dependencies:** Phases 0–2 and 7; specific summary cards may also depend on Phases 4 and 8.

## Phase 10 — Backup, restore, and portable metadata

**Status: Core complete; some exports and format guarantees remain open.** The version-2 JSON backup covers projects, library, stash, tool sets, items, templates, and project assignments, counters and notes, yarn allocations, pattern links, milestones, photo references, journal entries, and sessions, with a manifest of record counts and referenced files. Version 1 files still restore. Restore is reviewed: Settings first previews per-type counts of new, identical, conflicting, and local-only records, or lists validation issues; nothing is written unless the whole file is valid. **Merge** adds only new records and never overwrites a conflict. **Replace** asks a second time, naming what it will remove, and upserts so a kept project's children survive. Afterwards Settings shows the records written, any conflicts kept, and files that can't be opened on this device and need relinking. Project detail exports one project as restorable JSON or readable Markdown. The format and its file-reference rule are documented in ARCHITECTURE.md. Covered by JVM tests for validation, comparison, merge selection, v1 compatibility, conflict safety, REPLACE child safety, and Markdown output. Still open: guides, drafts, revisions, and executions are not in the backup; unknown fields from a newer writer are dropped rather than preserved on re-export; new Stash and Library fields are not in their CSVs; and there are no PDF exports or SAF library-folder mirroring.

**Goal:** Extend the early per-feature safety exports into a complete, verifiable, and recoverable library format.

**Scope:**

- Versioned JSON manifest and portable domain records
- Inclusion or reference rules for PDFs, photos, and exports
- Backup validation, restore into an empty library, and reviewed merge
- JSON/CSV/Markdown/PDF exports where appropriate
- Missing-file and conflict reporting

**Explicit non-goals:**

- Mandatory cloud backup, invisible background upload, or a proprietary-only archive

**Acceptance criteria:**

- A complete representative library survives backup/restore round-trip tests.
- Phase 2–3 safety exports remain importable or have a documented migration path.
- Invalid or partial archives fail safely with actionable detail.
- Existing content is never silently overwritten during merge.
- Format version and migration behavior are documented.

**Dependencies:** Phases 0–9 as applicable, especially portable storage from Phase 6.

## Phase 11 — Ravelry import and carefully controlled synchronization

**Status: In progress.** One-way pull of stash, needles, projects, and the pattern library is done. Still to come: downloading library PDFs into a folder the person chooses (Ravelry's `generate_download_link`), photos, project–yarn links, and any write-back, which would need explicit consent per operation.

- **Slice 1, stash and needles pull (done).** Connect from Settings → Ravelry with a "Basic Auth: personal account access" key. The read-only key cannot call Ravelry's authenticated stash, needle, or project methods. The key is typed on the phone, encrypted with an Android Keystore key, and kept out of every backup. *Check Ravelry* reads `current_user`, all pages of `stash/list`, and `needles/list` (GET only), then shows a review: new items, and items changed on Ravelry. *Add new only* or *Add new and update changed* saves exactly that. Records use stable ids, so repeat pulls are idempotent. Updates replace only Ravelry's fields (name, maker, colourway, dye lot, weight, skeins and per-skein measures, location, Ravelry yarn ID). Local notes, purchase details, care, and weighed remainders are kept. Nothing local is ever deleted, and nothing is written to Ravelry. Forgetting the key leaves everything pulled in place.
- **Slice 2, projects and library (done).**
  - `projects/list` becomes Projects (`ravelry-project-<id>`), with craft, status (In progress → Active, Hibernating → Paused, Frogged → Abandoned, Finished → Completed), and start, target, and finish dates. The pattern name fills an empty description.
  - Every volume in `library/search` (purchases, downloads, books) becomes a Library entry (`ravelry-volume-<id>`) with title, author, and Ravelry pattern ID. Craft starts as Other, because volumes don't carry one.
  - Updates keep the local project type, description, notes, and any PDF the person attached.

**Goal:** Offer optional interoperability without surrendering local authority.

**Scope:**

- Officially supported authentication and APIs
- Import of selected library metadata, stash, and projects
- External ID links, provenance, controlled synchronization, conflicts, and optional supported write-back
- Per-operation preview and privacy controls

**Explicit non-goals:**

- Treating Ravelry as master storage
- Scraping, unsupported APIs, automatic PDF/guide upload, or background write-back without consent

**Acceptance criteria:**

- Users choose records and direction before changes.
- Repeat imports are idempotent or produce reviewed matches.
- Conflicts preserve both sides until resolved.
- Revoking access leaves the local library usable.
- Behavior complies with current official API terms.

**Dependencies:** Phases 2, 4, 6, and 10.

## Execution-engine delivery sequence

The execution engine is a prerequisite for reliable manual and parsed guides. Deliver it as six reviewable increments rather than combining traversal, persistence, editing, and parsing in one change:

1. **Domain model and traversal â€” complete:** Stable IDs, Section/Range/Repeat/Instruction nodes, structural execution addresses, accepted-definition and execution-state validation, deterministic lazy leaf traversal, derived container progress, and Complete/Previous/Jump state transitions are implemented as pure JVM-tested domain behavior.
2. **Persistence and atomic progress — in progress:** Room schema version 2 persists project-owned Guides, one editable Draft per Guide, immutable numbered Definition Revisions, and normalized ordered node trees; publishing is atomic and validated, and the same Draft remains editable with the new Revision as its base. Room schema version 3 now also persists Execution state: each Execution belongs to one Guide, permanently references the exact immutable Definition Revision it began with, and is ACTIVE or COMPLETED, with at most one ACTIVE Execution per Guide enforced at the database level. Complete, Previous, and Jump are applied by loading persisted state into the existing pure execution-engine functions and persisting the result in one atomic Room transaction, so this increment's persisted transitions and crash-safety behavior are implemented. No Focus Mode, Pattern Map, guide editor, or other UI consumes this persistence yet.
3. **Manual guide editor — minimal version complete:** Let users create and review valid guide trees without parsing, preserve stable node identity, and prevent unsupported or invalid structures from entering execution. Delivered as two slices. Draft Editor Foundation covers Guide creation and Draft editing: a Project's "Add Guide" action creates a Guide (with its one empty Draft) and opens the Draft editor directly; the editor renders the Draft's node tree as a flattened outline supporting Section, Row range, Repeat, and Instruction, with add/edit/delete/reorder each persisting immediately through `GuideRepository.saveDraft`. Publish and Knit followed: a Publish action calls the existing `GuideRepository.publishDraft`, translating every publish-time failure into the same `DraftValidationException`/`DraftVersionConflictException` domain exceptions saving already uses -- no new exception type, and no UI code independently judges a Draft's validity. Publishing reloads the Draft (so a later edit never spuriously conflicts against the version publishing just bumped) and re-derives whether to offer "Start Knitting" or "Continue Knitting" from `GuideRepository.getLatestRevision`/`ExecutionRepository.getActiveExecution`, the same authoritative sources Focus Mode's own entry point already uses; the Draft stays fully editable afterward, and a later correction publishes as a new Revision the same way. Publish and Knit also shipped a small set of manual-testing-driven usability corrections -- plain-language empty-state and node-type-chooser copy, relabeled fields, and a clearer primary-action hierarchy -- scoped to copy and layout only, no new behavior. This is a deliberately minimal editor, not a finished one: no drag-and-drop, templates, or general jump-to-any-occurrence picker (that remains Pattern Map's job, item 5). This wiring is backed by passing focused JVM tests (every node type's add/edit/delete/reorder, publish success, publish-time validation feedback, publication leaving the Draft editable, Start/Continue exposure, duplicate-submission guarding, conflict recovery) plus a presentation-only Compose test for the revised copy. Three production-navigation instrumented tests (authoring a step; publishing and reaching Focus Mode's Ready-to-start screen; the Draft-only entry point) run and pass on an API 36 emulator.
4. **Focus mode — MVP in progress:** An initial Focus Mode screen presents the current Instruction with Section breadcrumbs and Range/Repeat position, and delegates Complete, Previous, and a narrow "resume at earliest incomplete step" Jump to the persisted execution engine. It was built ahead of item 3's full editor, so it operates only on Guides and Revisions that already exist and never authors content itself -- that is item 3's job, described above. A general jump-to-any-occurrence picker remains Pattern Map's job (item 5), not Focus Mode's. Focus Mode's presentation was subsequently reworked against the first version of the Stitchbook design system ([DESIGN_SYSTEM.md](DESIGN_SYSTEM.md)) so the current instruction reads as the dominant, reading-first element; this was a rendering-only pass and did not change execution, persistence, or navigation behavior. The entry and resume path was completed next: a Project's Guide list now states Continue, Start, or not-executable per Guide from real persisted state (never both Continue and Start at once), Start pins its Execution to `GuideRepository.getLatestRevision` rather than a ViewModel-computed ordering, and reopening the app and navigating back to a Guide restores its persisted Execution Address and completion records from Room. A not-yet-executable Guide's entry now opens the Draft editor (item 3) instead of Focus Mode's own empty-state message, since Focus Mode has nothing to execute for a Guide that has never published a Revision. Interactive Execution Controls followed: Complete, Previous, and Jump's production wiring against a shared `isBusy` guard (a duplicate tap cannot submit a second transition), optimistic-concurrency conflict recovery (authoritative state reloads and a recoverable error surfaces instead of an automatic retry), and repository/engine-only terminal completion and active-execution removal were confirmed by inspection to already exist, and are backed by passing focused JVM and Compose tests. A production-navigation instrumented test exercising this wiring end to end runs and passes on an API 36 emulator. The general Jump picker remains Pattern Map's job (item 5).
5. **Pattern Map:** Present the guide hierarchy with derived progress, current-location highlighting, expansion/collapse, and explicit leaf jump targets.
6. **Parser foundation — prototype complete:** Map reviewed deterministic parser output into the same versioned guide definition, retain source references and issues, and never bypass validation or user review. PDF digital-text extraction with page/line source references (`domain/parsing`, `data/parsing/PdfBoxTextExtractor.kt`), deterministic section/row/repeat parsing of that text into a `ParsedPattern` with an issue list (`domain/parsing/PatternTextParser.kt`), mapping that output into a real, editable `GuideDraft` (`domain/parsing/ParsedPatternMapper.kt`, `domain/usecase/CreateGuideFromPdfUseCase.kt`, reachable via a Project's "Create from PDF" action), and on-device OCR fallback for pages with no text layer (`data/parsing/PdfPageOcr.kt`/`MlKitPdfPageOcr.kt`) are all implemented; see Phase 12 above and ARCHITECTURE.md's "Current PDF text extraction" for what exists and what remains beyond this prototype's small explicit grammar.

The normative behavior and v1 limitations for these increments are defined in [docs/EXECUTION_ENGINE_SPEC.md](docs/EXECUTION_ENGINE_SPEC.md). Parts, conditions, measurement-based steps, and simultaneous work remain future extensions rather than hidden v1 complexity.

Guide-definition persistence follows these v1 constraints: a Guide belongs to one Project, a Project may contain multiple Guides, and Guides are not reusable across Projects. Definition Revisions are immutable, so correcting a published definition creates a new Revision. Executions remain attached to the Revision on which they began even after the Guide publishes newer Revisions; progress reconciliation between revisions is deferred. A Guide may retain multiple historical Executions, with at most one ACTIVE Execution per Guide in v1. The completed persistence work does not by itself imply a guide editor; Focus Mode's MVP and the Draft Editor Foundation are the first UI built on top of it, described above.

## Phase 12 — Deterministic PDF pattern parsing

**Status: the PDF-parsing prototype (all four planned increments) is complete; deterministic step generation for real-world phrasing beyond this prototype's scope remains open.** PDF digital-text extraction with page/line source references is implemented (see ARCHITECTURE.md's "Current PDF text extraction"): `domain/parsing`'s `ExtractedDocument`/`ExtractedLine`/`SourceReference` model and `PdfTextExtractor` contract, backed by a PdfBox-Android implementation in `data/parsing`. Deterministic parsing of that extracted text into sections, row/round ranges, and repeats -- with an issue list for anything ambiguous -- is also implemented (`domain/parsing/PatternTextParser.kt`/`ParsedPattern.kt`), for a small explicit subset of pattern-text phrasing. Mapping that parsed output into a real, editable `GuideDraft` is implemented too (`domain/parsing/ParsedPatternMapper.kt`, `domain/usecase/CreateGuideFromPdfUseCase.kt`), reachable from a Project's guide list via a "Create from PDF" action -- provenance and ambiguity are kept visible as annotated/flagged text directly in the existing Draft editor rather than a new schema field or review screen, and nothing is ever auto-published. A page with no digital text layer at all now falls back to on-device OCR (`data/parsing/PdfPageOcr.kt`/`MlKitPdfPageOcr.kt`, ML Kit's bundled Latin recognizer, no network dependency) before being reported as unreadable. Abbreviations, simultaneous/conditional instructions, sizes, size selection, and step generation for phrasing beyond this prototype's small explicit grammar remain later Phase 12 work.

**Real-world clean-up (done).** `PatternTextCleanup` runs before parsing:
- It drops bare page numbers, and headers or footers repeated at the top or bottom of at least half the pages. Lines that look like pattern structure are never dropped.
- It rejoins sentences a PDF wrapped across lines, including hyphenated words.

The parser now also recognises:
- `R1`, `Rnd`/`Rd`, en-dash and "to" ranges, and sides such as "(RS)"/"(WS)".
- "Rep" lines.
- Headings, either all-capital titles or short phrases ending in a colon, which become sections.

Single rows keep their number in the step text ("Row 3 (RS): Knit."), and generated steps show their page as "(p.N)" instead of a page and line. For hard patterns, the assistant round trip (Phase 13) produces the same draft format.

**Goal:** Create a reviewable structured guide from supported PDFs using deterministic techniques first.

**Scope:**

- Text and layout extraction
- Sections, sizes, abbreviations, repeats, simultaneous and conditional instructions
- Size selection and step generation
- Source-page/region references, confidence/issue reporting, and manual corrections
- Format fixtures and regression suite

**Explicit non-goals:**

- Universal accuracy, silent automation, proprietary-content redistribution, or AI dependency

**Acceptance criteria:**

- Supported fixtures produce reproducible output.
- Every generated step retains source references.
- Ambiguity is surfaced; the user must review before using a guide.
- Original PDFs remain untouched and private.
- Unsupported documents fail safely without inventing instructions.

**Dependencies:** Phases 6 and 10; project linking from Phase 2.

## Phase 13 — Optional local AI-assisted parsing

**Status: In progress.** The shared structured guide format and the assistant round trip are done. On-device models are next, then an optional user-supplied API key.

- **Structured guide format (done).** `StructuredGuide` (`domain/parsing`) is the one exchange shape every helper produces. It has sections, rows or rounds (`from`/`to`), repeats, instructions, materials, abbreviations, notes, and a `review` list. `StructuredGuideJsonDecoder` (`data/parsing`) reads a reply that may be wrapped in prose or a code fence. It accepts common spellings ("rnd", "rounds", bare-string steps) and rejects anything that changes the work, reporting each problem with its path (`steps[2].from`). It enforces depth, step-count, text-length and number limits. `StructuredGuideMapper` turns a valid guide into an ordinary draft: materials, abbreviations and notes go into a "Before you start" section, and each review item becomes a visible "Review needed" step. The result is never auto-published.
- **Ask an assistant (done).** From the hub's Guides sheet, open *Import with an assistant*. Read a PDF (or paste text), then copy or share a prompt (`StructuredGuidePrompt`) that carries the craft, the guide name, the page-marked pattern text, and the format with rules (use the pattern's own words, never invent, put anything uncertain in `review`). Paste the reply back, check it, see a summary (steps, sections, items to review), and create a draft. This works with a free Claude account or any other assistant, because a Claude.ai subscription does not include API access. **Privacy decision:** the app itself makes no network call. The text goes only where the person pastes or shares it, as an explicit user action. The owner approved this on 2026-10-08.
- **Next:** on-device model (Gemini Nano via ML Kit GenAI where the device supports it) producing the same format; then an optional user-supplied API key, stored on the device only and off by default.

**Goal:** Experimentally assist with ambiguous pattern interpretation while retaining privacy and user control.

**Scope:**

- Optional local model/runtime feasibility
- Suggestions layered on deterministic extraction
- Provenance, confidence, correction, and explicit review
- Device capability, performance, battery, and storage controls

**Explicit non-goals:**

- Required AI, automatic cloud upload, silent replacement of source instructions, or unsupported safety claims

**Acceptance criteria:**

- Deterministic parsing remains available without AI.
- Private PDF content is not sent to a remote service.
- AI output is clearly labelled, source-linked where possible, editable, and untrusted until accepted.
- Users can disable and remove local model data.

**Dependencies:** Phase 12 and privacy/performance findings from earlier phases.

## Phase 14 — Accessibility, performance, release signing, and broader testing

**Status: Not started as a phase.** The accessibility baseline is applied screen by screen as features land; release signing and performance budgets are still open.

**Goal:** Prepare a stable release candidate and harden the complete experience.

**Scope:**

- Full accessibility audit and remediation
- Performance profiling with representative large libraries
- Migration, backup/restore, process-death, and device-matrix testing
- Security/privacy review
- Release signing and reproducible release procedure
- Documentation and recovery-path review

**Explicit non-goals:**

- Adding broad new feature categories during hardening
- Committing private signing keys

**Acceptance criteria:**

- Critical workflows meet defined accessibility checks.
- Performance targets are measured and documented.
- Supported migration and recovery scenarios pass.
- Release signing is configured securely outside version control.
- Known limitations and privacy behavior are documented.

**Dependencies:** All phases intended for the target release; this work should also occur incrementally rather than being deferred entirely.
