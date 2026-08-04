# Stitchbook Agent Guide

This file governs work throughout the repository unless a more specific `AGENTS.md` is added in a subdirectory.

## Product guardrails

Stitchbook is a private, local-first Android companion for knitting, crochet, Tunisian crochet, loom knitting, and other fibre crafts. The user owns their data. Essential use must work offline, without a subscription, paid cloud service, or mandatory account.

Treat each supported craft as a first-class craft type. Do not assume that crochet terminology, counters, gauge, tools, or pattern instructions are the same as knitting.

The application must never be the only way a user can access imported patterns, photos, notes, inventory, or project records. Keep original imported files untouched and user-accessible. Do not store irreplaceable content only in Room or application-private storage. Internal cache may hold reproducible thumbnails and temporary working files only.

The product may be inspired by the useful capabilities of existing craft applications, but implementation, branding, copy, prompts, and UI must be original. Never add or redistribute proprietary pattern content.

## Current repository

- The project is an early Android application shell with one `app` module.
- It uses Kotlin, Jetpack Compose, Material 3, Navigation Compose, and Gradle Kotlin DSL.
- The current minimum SDK is 26 and the application ID is `com.macareen.stitchbook`.
- The current UI has five placeholder top-level destinations: Home, Projects, Library, Stash, and Settings.
- Basic local project CRUD is implemented with Room, KSP, Flow, screen-level ViewModels, a project repository, and manual dependency wiring through `AppContainer`.
- The Room database is schema version 1. Future schema changes require explicit migrations and updated schema/test fixtures; do not add a silent destructive-migration fallback.
- WorkManager, portable project export, and all non-project persistence remain planned.
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
