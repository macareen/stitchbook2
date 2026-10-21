# Stitchbook handoff

This is the canonical, repository-accessible handoff. Read this and AGENTS.md at the start of each session. Keep the current checkpoint and next steps up to date. Historical local journals remain private; this portable record omits host paths, device identifiers, private fixtures and transient tool logs.

## Current checkpoint — 2026-10-21

Starting main: 8b559c0 (PR116). Working branch: codex/concise-guides-glossary-photo-gauge. Tracked tree was clean before this work; pre-existing local agent settings must remain untouched and unstaged. Check current Git and account usage rather than assuming this checkpoint remains current.

The Android application is one module, Kotlin/Compose/Material3/Room, minSdk26. Database26 with explicit migrations1–26, full-library backup5. No destructive fallback. Original SAF files stay user-accessible and untouched. Essential use is offline; optional Ravelry pull uses encrypted personal credentials. See ARCHITECTURE.md and AGENTS.md for storage, exports and craft-neutral requirements.

## User priorities and working preferences

- PDF extraction → selected size → reviewed guide → accurate crafting/step tracking is the primary product chain. Convenience features should not displace known problems in it.
- The user explicitly authorizes future Stitchbook pushes, PRs and merges. Use codex/ branches, named staging, Summary-only PR descriptions, attach each PR, inspect exact head/mergeability/checks, squash-merge with matching head, retain branches, and fast-forward main. Verify the configured destination; never publish personal files, screenshots, credentials or proprietary patterns.
- Batch a couple of coherent features, then run broader checks together. Use focused units while implementing. Reuse the emulator; rerun failed/affected checks only. Do not continually boot an emulator for small changes.
- Freeze source edits while Gradle runs; serialize Gradle invocations. Previously overlapping builds corrupted generated DEX; source edits during lint also coincided with Kotlin analysis failures.
- No subagents are authorized by default. No reset credits used. Usage thresholds refer to weekly usage, not the five-hour window; check actual current limits.
- Provide a full session summary, commands/results/failures, limitations and ranked next steps, and update this file when done.
- A phone installation was explicitly requested and completed on October19 after PR116. Do not assume that a connected phone authorizes future data changes or inspections. Install requested updates with adb -s <verified phone> install -r; never uninstall/clear data to work around signing problems.

## Current request — October21

1. Save the handoff in the repo and make it discoverable (this file).
2. Fix descriptive/front-matter and glossary content leaking into executable steps (phone screenshots include size advice and definition paragraphs in April/Sophie drafts).
3. Concise Focus: clear row/round/action subtitle (including Cast on), a short working instruction, useful repeat context and accessible controls. Preserve conditions, counts, scope and original wording in accessible supporting reference; do not invent semantic summaries or silently rewrite existing published work.
4. Dedicated cast-on stitch counter with large count/goal and accessible +/− controls, persisted in the correct work scope.
5. Separate reference/glossary; terms in instructions should open their definitions. Reference text must not inflate executable progress. Keep terms craft/pattern-specific and portable.
6. Offline photo-assisted gauge: take/select a swatch photo, calibrate against a known physical length, align bands to stitch columns/rows and calculate gauge. A photo alone does not establish physical scale. Preserve originals, handle rotation, keep photo selection user-controlled, and distinguish counted intervals from band/line boundaries.

Use the supplied LooseLoop screenshot and docs/LOOSELOOP_FUNCTIONAL_FINDINGS.md as functional evidence. The screenshot shows separate cycle/row/repeat counts, a measurement checkpoint, short instruction and optional supporting prose. Its target-loop behavior is NOT implemented by the current finite Repeat engine. Implement original code/copy/design. Existing static audits did not execute the reference app/service or establish its server parser accuracy.

## Completed work through PR116

- PR111 / f6c76cb: regular readable Focus instruction typography, consistent grouped actions, More below scroll affordance, occurrence reading reset and same-screen restoration. Manual swatch gauge with independent horizontal/vertical spans and cm/in; per10cm and per4in use correct conversions. No photo calibration yet.
- PR112 / 03bac11: reviewed Library index removal with affected projects/guides/history and full-snapshot transaction guard. Original files, drafts, publications, progress and pieces survive.
- PR113 / 5338c6e: reviewed PDF attach/replace/unlink and metadata editing with stale-write guards, failure retention and explicit reload. Same URI retains page; changed URI resets page. Replacing a PDF never regenerates existing guides.
- PR114 / 249719b: atomic1–100 independent named pieces and fresh unworked copies. Scope includes project/piece; first start pins the latest eligible publication. Quantity is materialized records, not a persistent group. No subsections or mirroring.
- PR115 / ca1a457: explicit roadmap priority for reliable parsing, reviewed size guides and accurate tracking.
- PR116 / 8b559c0: direct Edit guide from Focus, single-instruction finite repeat shortcut, preserved publications and old progress, return to the same Focus/project/piece, reviewed current-step selection and separate explicit confirmation of earlier completed steps. Default remains first incomplete; choosing a position alone does not infer completion. Done/Return disabled during writes. Existing transactional source/target/scope/version checks remain.
- PR116 also surfaces unreadable PDF pages as leading editable review instructions before size filtering, tags every new instruction with its own PDF page, handles review p.N/l.N suffixes, and marks missing pages in assistant text. Legacy sparse source tags retain look-back behavior. Entirely textless imports are still rejected. Partial text/diagrams/charts can still be missed.
- PR116 resolved41 of95 lint warnings: unused resources, Kotlin helpers, punctuation, reactive share-card resources and AndroidX ExifInterface1.4.2. Original photo bytes remain unchanged; normal/90/180/270 rotation tested. Mirrored orientation remains a limitation.

Earlier foundation: pattern/size reusable guides and per-project work; explicit per-size prose and grouped/alternating numeric resolution; metadata filtering; immutable publications and conservative revision matching; recorded sessions, timer recovery/manual time; export/restore of guides, pieces and progress; original files referenced through SAF. These do not establish complete parser or reference-app parity.

## Previous verification baseline (PR116)

PR116:773 units passed;62 unique targeted native checks passed across the corrected batch and private diagnostic; debug/test APKs built; final lint0errors54warnings. Native checks covered real navigation/authoring/recreation, corrected current-step review,39 execution transaction cases (stale guards, saved-place resume, piece isolation, backup roundtrip), native PDFBox/OCR, synthetic selected-size import→publication→Focus through repeats/Previous/Jump/reload, photo decoding/orientation, and light/dark/180% reading/revision UI.

A private April diagnostic tested9 sizes, each with66 instruction leaves/3 numbered repeats/zero detected long alternating runs. This was a targeted parser/context test, not semantic fidelity proof or a complete real-pattern Follow audit. Private PDF copies were removed from the emulator; no private content committed. Existing phone guides were not silently rebuilt. Screenshots from October19 may show already-published content from earlier extraction; inspect current extraction and pinned revisions separately.

Initial failures: six UI tests still sought the old hub Guides button; helper changed to the inline Guides section. One new test confused an Overview first-incomplete jump target with a later saved occurrence; assertions corrected to persisted address and restored positions. The affected8-case rerun passed. A PowerShell -P option needed quoting. Gradle cleans up its test runner after connected tests, so direct adb instrumentation requires reinstalling it or using Gradle. A lint FIR/ExperimentalDetector crash and a later hot type-mapping stall required a fresh worker. Successful final lint/build used --no-daemon --max-workers=1, all detectors enabled. The final one-line return-button guard got lint/build verification without another emulator run.

Commands from repo root, Android Studio bundled JDK21:

- .\gradlew.bat :app:testDebugUnitTest --max-workers=2
- .\gradlew.bat :app:lint :app:assembleDebug --no-daemon --max-workers=1
- .\gradlew.bat :app:assembleDebugAndroidTest
- .\gradlew.bat :app:connectedDebugAndroidTest --max-workers=2 "-Pandroid.testInstrumentationRunnerArguments.class=<classes>"

Quote PowerShell -P options. Target the emulator explicitly and keep physical phones out of native test runs. Report exact environment failures. Do not disable detectors to make lint appear green. Use synthetic/permissioned inputs and keep all private content outside Git.

## Remaining54 lint warnings

30 plural/localization suggestions;16 dependency/tooling/SDK notices;3 trust-manager flags in transitive bcpkix1.72;2 synchronous-preference suggestions plus2 Kotlin helper suggestions at those same credential writes;1 false-positive dash suggestion on a valid ISO date example.

PDFBox2.0.27.0 brings BouncyCastle. Inspected PDFBox classes reference BC from encryption provider/security handlers, with no EST/JcaJceUtils/trust-all helper references found. App networking uses standard HTTPS, without custom trust-manager/hostname-verifier overrides. This is code-path evidence, not a general dependency security guarantee; retain warnings pending compatible upgrade/removal review. Do not remove crypto blindly and break encrypted PDFs.

Credential save/clear run on IO and use synchronous commit. Future work should explicitly handle write failures while preserving durability; do not switch to async apply merely for warning count. Upgrade dependencies/target SDK in compatibility-tested batches.

## Substantial open work and next sequence

1. Finish the current user-requested concise instructions, reference/glossary, correctly scoped cast-on counter and calibrated photo gauge. Test original regressions for metadata definitions, wrapped/multiple-page glossaries, scalar versus size-dependent ordinals/fractions, nested repeat context and saved progress. New durable data needs proportional full/project JSON/Markdown export and compatibility coverage.
2. Continue faithful real-pattern audits: required actions, front matter, partial-page/column extraction, reference rows, simultaneous shaping, chosen-size meaning, readable groups and provenance. Resolve confidently; preserve uncertain wording visibly. Existing publications require reviewed edits/rebuilds, never hidden rewrites.
3. True manual target loops: typed length/stitch-count targets, independent scoped cycles/observations, explicit target-reached confirmation, unknown totals and correct Previous/Jump/restart semantics. Extend revisions, persistence/migrations, exports/restore together. A large fixed Repeat is not an unknown-length loop. Current traversal assumes a finite definition and eagerly enumerates it in several operations.
4. Map parser-recognized target structures only after execution semantics are verified. Preserve ambiguous/conditional/simultaneous instructions instead of inventing counts.
5. Manual charts with legend/direction/offset/row repeat semantics; piece subsections/mirroring; material roles/held-together/alternatives; explicit reminder records; saved technique references. CounterNote is a historical note, not reminder intent. Ravelry PDF403 remains unresolved; browser download plus SAF attachment exists.
6. Bounded localization/credential cleanup and compatible dependency modernization after the core batch.

## Ownership and session hygiene

Do not commit screenshots, extracted pattern prose, PDFs, phone data, credentials/signing files, SDK paths or reference binaries/code. Keep synthetic verification captures outside Git. Preserve pre-existing local settings. Original imported files stay untouched and accessible. Do not silently alter guide definitions with existing work. Pattern-provided glossary definitions must remain tied to their guide/pattern; do not overwrite them with assumed universal craft meanings.

Append a concise checkpoint below after each coherent batch: commit/PR/Git state, features, tests/build/lint outcomes, failures and corrections, storage/export changes, remaining limitations and ranked next steps. Update this canonical record rather than relying on private chat history.


## October21 checkpoint - concise guides, reference, cast-on and photo gauge

Implemented on codex/concise-guides-glossary-photo-gauge, based on main8b559c0/PR116. The canonical handoff is now this repository file, linked from AGENTS.md and README.md. The private historical host journal was not published.

- New PDF and structured-assistant guides keep description/materials/glossary outside executable steps. Mixed-case metadata headings, wrapped/compound definitions, abbreviated R1 row labels, per-size ordinals and Unicode fractions have regression coverage. Existing drafts offer an explicit reviewed Move reference text action; metadata/draft snapshot checks are transactional, and old publications/work remain intact. The existing publish/review-place flow is used to adopt changes.
- Focus shows row/round/action subtitles above regular22sp working text, underlined pattern-term links with definition dialogs, separate Reference and explicit Note/Tip support. Conditions are preserved rather than guessed or semantically rewritten. Existing finite repeat context remains; measurement-target loops are still future work.
- Cast-on progress has a large saved count/goal and accessible plus/minus, scoped by immutable occurrence address inside the owning execution/project/piece. Counting preserves step timing, version checks and saved place; Complete/Previous/Jump retain counts. A fresh execution or reviewed revision starts a separate counter; old work is retained. Schema26 adds default-empty cast_on_counts; backup5 carries structured addresses/counts with validation; full/project JSON and project Markdown include counts and reference notes. Older backups remain readable.
- Calculators -> Photo gauge selects an original or takes a photo into a user-chosen document. Independent measured spans in cm/in calibrate an adjustable rectangle; stitch/row grids, fractional counts and alignment offsets support manual measurement. Counts are capped at200 for rendering. Moving an edge clears the affected length; changing units clears both. Results share as text. Settings are temporary, restored across same-screen recreation. No automatic recognition/perspective correction/durable gauge history. Bounded derivatives support all8 EXIF orientations, without modifying originals.

Verification:789 unit tests pass; debug and test APKs build. The94-case native batch had93 passes and one migration-test fixture failure: its hard-coded14->current list stopped at25, although the application's ALL_MIGRATIONS and new25->26 test were correct. Adding the missing fixture migration and rerunning the affected migration plus both photo UI checks passed3/3. Thus all94 targeted native cases are resolved. Coverage includes schema/data preservation, stale execution guards, piece/occurrence isolation, timing origin, JSON/Room backup restore, synthetic PDF->size->publication->Focus/repeats/Previous/Jump/reload, reference-cleanup history, term links, cast-on goal controls and light/dark/180% reading/photo UI. Synthetic captures were visually reviewed outside Git. System camera/provider UI was not exercised end-to-end; cancelled capture may leave the user-chosen document, never silently deleted.

Private April/Sophie checks initially hit automatic approval review for copying private PDFs to the emulator. The user explicitly approved that transfer; both then passed and the temporary file's absence was verified. April: all9 sizes,55 instruction leaves/3 numbered repeats each, no detected alternating or ordinal size runs; Sophie: both2 sizes,33 leaves/2 numbered repeats each. Known definition prefixes were absent from steps and reference notes retained. These are targeted regression checks, not full semantic fidelity proof. No private content, screenshots or host paths were committed.

Final lint:0errors54warnings (same composition as prior baseline). Two long FIR/type-mapping runs were cancelled; a fresh full lint run after replacing range-based Float.coerceIn with explicit numeric bounds passed in2m51s, all detectors enabled. The initial compile had incorrectly assumed the serialization-json dependency; the occurrence codec now uses the repository's existing org.json runtime/test support. An initial783-case unit run caught R1 being treated as a definition plus an obsolete hard-coded backup-version assertion; both corrected, then full789 passed.

Commands: :app:testDebugUnitTest; :app:assembleDebug :app:assembleDebugAndroidTest; :app:lint --no-daemon --max-workers=1; direct emulator-only AndroidJUnitRunner with class filters. Native reruns were limited to failed/affected cases. No phone was connected in this session's device inventory; only the emulator received this build.

### Latest user steering - pattern ownership and whole collection audit

The user clarified that general description, suggested gauge/tools/yarns and techniques belong to the pattern and should be extracted at pattern import, rather than requiring guide creation or being duplicated per size. Implement pattern-owned setup, preserve manual fields, expose it clearly and keep glossary access from Focus. Guide-specific adjustments remain on the guide; do not rewrite existing guide work.

The user explicitly authorized auditing all patterns and confirmed the existing mapped Drive Patterns folder. Read-only inventory found13 PDFs (~93MB). The collection audit/report is still pending at this checkpoint. Keep PDFs, extracted text, filenames/host paths and detailed private reports outside Git. Use generic opt-in native diagnostics for actual extractor/parser checks; report heuristic metrics separately from visual/semantic fidelity review. These tasks extend the current batch and must be completed before final handoff.
