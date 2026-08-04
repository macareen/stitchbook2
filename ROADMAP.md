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

**Status:** In progress. The initial shell milestone is implemented: a warm Material 3 light/dark theme, a single Navigation Compose host, and placeholder Home, Projects, Library, Stash, and Settings destinations with bottom navigation. A first version of a lightweight design-system foundation (semantic color/typography roles, spacing, corner radius, and a couple of shared button components) now backs that theme and is documented in [DESIGN_SYSTEM.md](DESIGN_SYSTEM.md); it covers only what Focus Mode currently needs, not a final design system or extensive component library. Preference behavior and larger-screen adaptation remain future Phase 1 work.

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

**Status:** In progress. Room schema version 1 and basic local CRUD are implemented for project UUID, name, craft, project type, status, optional notes, and created/updated timestamps. The repository uses manual dependency wiring, Flow, and screen-level ViewModels. Description, construction method, project dates, custom project-type labels, relationships, and the required portable safety export remain deferred, so Phase 2 is not complete.

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

**Status: basic Stash CRUD and CSV import/export implemented; the richer field set below is not yet.** The current Stash schema (name, category, brand, colorway, dye lot, weight category, fibre content, quantity, unit label, yardage/unit, notes) supports full CRUD with search/category filtering, plus a documented, versioned CSV format (`data/csv/StashCsv.kt`, schema v1): export, a downloadable one-row template, and import with a pre-commit validation report (missing name, unrecognized category, non-numeric/negative quantity or yardage are all reported by row number without discarding the file's other valid rows) and duplicate handling by matching the `id` column (a matched id updates in place and preserves its original `createdAt`; blank or unmatched ids create new items). Not yet implemented: full skein/partial-skein tracking, measured-vs-estimated remaining length, allocations/consumption against projects, storage location, purchase data, care instructions, and Ravelry ID -- the field set below remains the target.

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

**Status: domain model, Room schema (v6), repository, individual-item CRUD UI, and size-range/list bulk creation implemented; manufacturer/user templates and CSV import/export are not yet.** `ToolItem` (one physically countable component) and `ToolSet` (a named grouping such as a complete commercial interchangeable set) cover the full type list in the Scope below, with sizing/length stored canonically in millimeters plus an optional free-text convenience label, and interchangeable-cable fields (stated length, its definition, approximate assembled length) and informational (non-validated) connector-family/compatibility-notes fields. `ToolItem.setId` references its owning `ToolSet` without duplicating stock -- availability is always the component's own `quantity`, never a set-level count -- and deleting a set returns its components to standalone items (`ON DELETE SET NULL`) rather than deleting them. Tool sets and items are included in the versioned safety export alongside Projects, Library, and Stash. A Tools top-level destination offers search, category filtering, and a single-item add/edit form whose fields adapt to the selected category (for example, cable-length fields only appear for interchangeable cables) -- backed by `ToolsViewModel`/`ToolsScreen`. A "Bulk Create Tools" screen (`BulkToolCreationViewModel`/`BulkToolCreationScreen`) generates one item per size from either a numeric range (start/end/increment) or a comma-separated custom list -- scoped to the categories with a meaningful numeric size (needles, hooks, interchangeable tips) since categories without one (markers, stoppers, notions...) already have adequate quantity support through the single-item form -- with a live preview before committing (deduplicated and capped so a bad increment can't generate an unbounded number of items) and an optional "group as a new set" toggle that creates one `ToolSet` and assigns every generated item to it. Not yet built: manufacturer-set and reusable user templates, any UI for browsing/renaming an existing `ToolSet` or reassigning an already-created item to one, many-to-many project assignment, and CSV import/export.

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

**Status: PDF attachment foundation implemented; many-to-many project links and portable pattern-metadata export remain open.** A Library item can attach one PDF selected through SAF with a persisted (`takePersistableUriPermission`) `content://` URI and captured display name -- never a blob, never a copy. A built-in viewer (`android.graphics.pdf.PdfRenderer`, a platform API, so no new dependency) renders pages on demand and remembers the last-viewed page per item; "open in another app" is offered alongside it via `ACTION_VIEW`. Revoked/missing access (file moved, deleted, or permission lost) surfaces a clear, recoverable message rather than crashing. Room migration 4→5 adds the three PDF columns non-destructively; existing rows are unaffected. Not yet built: many-to-many pattern-to-project links, tags/gauge/yardage/purchase metadata beyond what Library already tracks, and portable pattern-metadata export/relinking across devices.

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
3. **Manual guide editor — minimal version complete:** Let users create and review valid guide trees without parsing, preserve stable node identity, and prevent unsupported or invalid structures from entering execution. Delivered as two slices. Draft Editor Foundation covers Guide creation and Draft editing: a Project's "Add Guide" action creates a Guide (with its one empty Draft) and opens the Draft editor directly; the editor renders the Draft's node tree as a flattened outline supporting Section, Row range, Repeat, and Instruction, with add/edit/delete/reorder each persisting immediately through `GuideRepository.saveDraft`. Publish and Knit followed: a Publish action calls the existing `GuideRepository.publishDraft`, translating every publish-time failure into the same `DraftValidationException`/`DraftVersionConflictException` domain exceptions saving already uses -- no new exception type, and no UI code independently judges a Draft's validity. Publishing reloads the Draft (so a later edit never spuriously conflicts against the version publishing just bumped) and re-derives whether to offer "Start Knitting" or "Continue Knitting" from `GuideRepository.getLatestRevision`/`ExecutionRepository.getActiveExecution`, the same authoritative sources Focus Mode's own entry point already uses; the Draft stays fully editable afterward, and a later correction publishes as a new Revision the same way. Publish and Knit also shipped a small set of manual-testing-driven usability corrections -- plain-language empty-state and node-type-chooser copy, relabeled fields, and a clearer primary-action hierarchy -- scoped to copy and layout only, no new behavior. This is a deliberately minimal editor, not a finished one: no drag-and-drop, templates, or general jump-to-any-occurrence picker (that remains Pattern Map's job, item 5). This wiring is backed by passing focused JVM tests (every node type's add/edit/delete/reorder, publish success, publish-time validation feedback, publication leaving the Draft editable, Start/Continue exposure, duplicate-submission guarding, conflict recovery) plus a presentation-only Compose test for the revised copy. Three production-navigation instrumented tests (authoring a step; publishing and reaching Focus Mode's Ready-to-start screen; the Draft-only entry point) were added and compile, but have not executed successfully in this environment -- blocked by the same pre-existing Espresso/emulator incompatibility affecting every Compose-based instrumented test here, not by anything specific to this wiring.
4. **Focus mode — MVP in progress:** An initial Focus Mode screen presents the current Instruction with Section breadcrumbs and Range/Repeat position, and delegates Complete, Previous, and a narrow "resume at earliest incomplete step" Jump to the persisted execution engine. It was built ahead of item 3's full editor, so it operates only on Guides and Revisions that already exist and never authors content itself -- that is item 3's job, described above. A general jump-to-any-occurrence picker remains Pattern Map's job (item 5), not Focus Mode's. Focus Mode's presentation was subsequently reworked against the first version of the Stitchbook design system ([DESIGN_SYSTEM.md](DESIGN_SYSTEM.md)) so the current instruction reads as the dominant, reading-first element; this was a rendering-only pass and did not change execution, persistence, or navigation behavior. The entry and resume path was completed next: a Project's Guide list now states Continue, Start, or not-executable per Guide from real persisted state (never both Continue and Start at once), Start pins its Execution to `GuideRepository.getLatestRevision` rather than a ViewModel-computed ordering, and reopening the app and navigating back to a Guide restores its persisted Execution Address and completion records from Room. A not-yet-executable Guide's entry now opens the Draft editor (item 3) instead of Focus Mode's own empty-state message, since Focus Mode has nothing to execute for a Guide that has never published a Revision. Interactive Execution Controls followed: Complete, Previous, and Jump's production wiring against a shared `isBusy` guard (a duplicate tap cannot submit a second transition), optimistic-concurrency conflict recovery (authoritative state reloads and a recoverable error surfaces instead of an automatic retry), and repository/engine-only terminal completion and active-execution removal were confirmed by inspection to already exist, and are backed by passing focused JVM and Compose tests. A production-navigation instrumented test exercising this wiring end to end was added and compiles, but has not executed successfully -- it is blocked by the pre-existing Espresso/emulator incompatibility affecting all Compose-based instrumented tests in this project, not by anything specific to this wiring. The general Jump picker remains Pattern Map's job (item 5).
5. **Pattern Map:** Present the guide hierarchy with derived progress, current-location highlighting, expansion/collapse, and explicit leaf jump targets.
6. **Parser foundation:** Map reviewed deterministic parser output into the same versioned guide definition, retain source references and issues, and never bypass validation or user review.

The normative behavior and v1 limitations for these increments are defined in [docs/EXECUTION_ENGINE_SPEC.md](docs/EXECUTION_ENGINE_SPEC.md). Parts, conditions, measurement-based steps, and simultaneous work remain future extensions rather than hidden v1 complexity.

Guide-definition persistence follows these v1 constraints: a Guide belongs to one Project, a Project may contain multiple Guides, and Guides are not reusable across Projects. Definition Revisions are immutable, so correcting a published definition creates a new Revision. Executions remain attached to the Revision on which they began even after the Guide publishes newer Revisions; progress reconciliation between revisions is deferred. A Guide may retain multiple historical Executions, with at most one ACTIVE Execution per Guide in v1. The completed persistence work does not by itself imply a guide editor; Focus Mode's MVP and the Draft Editor Foundation are the first UI built on top of it, described above.

## Phase 12 — Deterministic PDF pattern parsing

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
