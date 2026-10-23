# Stitchbook Agent Guide

This file governs work throughout the repository unless a more specific `AGENTS.md` is added in a subdirectory.

## Session handoff

Read [docs/HANDOFF.md](docs/HANDOFF.md) for the current checkpoint, user priorities, verification history and next steps. Update it when handing off work so other sessions can continue from the repository.

## Product guardrails

Stitchbook is a private, local-first Android companion for knitting, crochet, Tunisian crochet, loom knitting, and other fibre crafts. The user owns their data. Essential use must work offline, without a subscription, paid cloud service, or mandatory account.

Treat each supported craft as a first-class craft type. Do not assume that crochet terminology, counters, gauge, tools, or pattern instructions are the same as knitting.

The application must never be the only way a user can access imported patterns, photos, notes, inventory, or project records. Keep original imported files untouched and user-accessible. Do not store irreplaceable content only in Room or application-private storage. Internal cache may hold reproducible thumbnails and temporary working files only.

The product may be inspired by the useful capabilities of existing craft applications, but implementation, branding, copy, prompts, and UI must be original. Never add or redistribute proprietary pattern content.

## Current repository

- The project is an Android application with one `app` module.
- It uses Kotlin, Jetpack Compose, Material 3, Navigation Compose, and Gradle Kotlin DSL.
- The current minimum SDK is 26 and the application ID is `com.macareen.stitchbook2`.
- Room-backed features: projects (with dates, description, and construction method), guides (owned by a project, or by a pattern and size and used by any project, with progress kept per project) with Focus Mode, counters, library patterns with SAF-referenced PDFs and a linked pattern folder, stash with yarn allocations, tools, photo references, journal entries, milestones, crafting sessions with statistics, and PNG share cards. Dependencies are wired manually through `AppContainer`, with screen-level ViewModels and repository interfaces.
- The Room database is schema version 26, with every migration in `ALL_MIGRATIONS` (`data/database/StitchbookDatabase.kt`) and schemas 1–26 exported under `app/schemas`. Future schema changes require explicit migrations and updated schema/test fixtures; do not add a silent destructive-migration fallback.
- The full-library backup is versioned JSON (format 5, including guides, drafts, revisions, pieces, and progress) with reviewed Merge/Replace restore, plus per-project JSON and Markdown export; see ARCHITECTURE.md "Current backup format". PDFs and photos are referenced, never copied.
- Optional network use is limited to the Ravelry pull (personal key, read-only, kept encrypted with the Android Keystore); everything else is offline.
- WorkManager is still unused: nothing runs in the background except the Focus-Mode-scoped counter notification service.
- Avoid premature module splitting and do not create empty package structures.

## Working principles

- Inspect existing code before editing.
- Preserve a buildable state.
- Make small, reviewable changes.
- Do not implement unrelated features.
- Do not silently replace user files.
- Do not store irreplaceable content only in Room.
- Do not introduce paid services or mandatory accounts.
- Do not commit credentials, signing keys, personal files, or pattern PDFs.
- Prefer immutable Kotlin data structures and explicit state.
- Keep composables focused and previewable where practical.
- Keep business logic outside composables.
- Use clear names rather than excessive abbreviations.
- Add tests for nontrivial domain and data logic.
- Run relevant tests and builds after modifications.
- Report commands run and any failures.
- Update documentation when architecture or behavior changes.
- Ask before performing destructive migrations or broad rewrites.

Also:

- Prefer standard Android and Jetpack libraries. Add a framework or abstraction only when a demonstrated need justifies it.
- Keep portable-file concerns separate from database indexing and UI state.
- Give every durable feature a proportionate user-accessible export before treating it as release-ready; full-library backup and continuous portable mirroring may arrive later.
- Mark estimated statistics clearly; never present them as recorded measurements.
- Preserve backwards-compatible, exportable data whenever schemas or storage formats evolve.
- Do not add network access to an essential workflow without an offline path and a documented privacy reason.

## Intended technical direction

Use Kotlin, Compose, Material 3, coroutines and Flow, ViewModels, Navigation Compose, Room, repository interfaces, the Android Storage Access Framework (SAF), WorkManager where appropriate, and the Android share sheet.

Start with a single `app` module organized by feature and clear data/domain/UI boundaries. A likely package shape is documented in [ARCHITECTURE.md](ARCHITECTURE.md). Split modules only when build performance, ownership boundaries, reuse, or test isolation provides measurable value.

## Build and verification commands

Run commands from the repository root. Android Studio's bundled JDK 21 is suitable for the current Gradle configuration.

| Purpose | Windows | macOS/Linux |
| --- | --- | --- |
| Build debug APK | `.\gradlew.bat :app:assembleDebug` | `./gradlew :app:assembleDebug` |
| Run all local unit tests | `.\gradlew.bat :app:test` | `./gradlew :app:test` |
| Run debug unit tests only | `.\gradlew.bat :app:testDebugUnitTest` | `./gradlew :app:testDebugUnitTest` |
| Run Android lint | `.\gradlew.bat :app:lint` | `./gradlew :app:lint` |
| Run connected-device tests | `.\gradlew.bat :app:connectedDebugAndroidTest` | `./gradlew :app:connectedDebugAndroidTest` |

Use the narrowest relevant command while iterating, then run the broader applicable checks before handing off. Connected tests require a running emulator or connected device. If a command cannot run because the SDK, JDK, network, emulator, or another environment prerequisite is missing, report the exact failure rather than implying it passed.

## Change checklist

Before finishing a change:

1. Re-read the diff for scope, privacy, data ownership, and craft-neutral assumptions.
2. Confirm generated files, credentials, local SDK paths, signing material, and personal content are not included.
3. Run relevant unit tests, lint, and a debug build in proportion to the change.
4. Add or update tests for nontrivial domain or data behavior.
5. Update `README.md`, `PRODUCT_SPEC.md`, `ROADMAP.md`, or `ARCHITECTURE.md` when behavior or architectural decisions change.
6. Report files changed, commands run, results, and any remaining risks or skipped checks.

## Graphify code searches and refresh

Use the repository-local Graphify index for all future code searches. See
[docs/GRAPHIFY.md](docs/GRAPHIFY.md) for installation, commands and coverage limits.
The installed upstream skill is [.codex/skills/graphify/SKILL.md](.codex/skills/graphify/SKILL.md).
These repository-specific rules take precedence over its generic defaults:

- Before every new code search, run `.\scripts\graphify.ps1 query "<question or symbols>"`; use `explain`, `path` and `affected` for focused navigation and relationships. Follow returned locations to current source. Prefer scoped queries over reading the full report.
- If the graph is missing, follow docs/GRAPHIFY.md and rebuild it. If results are incomplete, ambiguous, truncated or absent, refine the query, then use targeted `rg`/file reads for gaps and verify live source. XML resources, some Kotlin constructs and build scripts have incomplete coverage; never treat an absent edge as proof that a dependency does not exist.
- After each substantial coherent chunk of work and before handoff, run `.\scripts\refresh-graphify.ps1` and record its result in docs/HANDOFF.md. Refresh after branch changes or stale results as needed. Do not finish substantial code work with a knowingly stale graph without reporting the failure.
- Use local code-only extraction and no LLM labeling. Do not enable semantic/cloud extraction or upload private inputs. Keep generated graphify-out/, the virtual environment and cache ignored; new checkouts regenerate them.
- Use the scripts so the pinned repo environment is selected without global PATH changes. No background service, Git hook, or global Codex configuration is needed. Existing restrictions on subagents remain in force.
