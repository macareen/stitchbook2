# LooseLoop functional investigation

Date: 2026-10-12. Reference: the user-supplied LooseLoop 1.1.32 XAPK.

## Scope and evidence

This is independently written analysis of functional behavior, not imported implementation. No reference-app code, binaries, artwork, prompts, credentials or pattern content belong in Stitchbook. APKs were inspected as ZIP archives in memory; a temporary Hermes analysis tool decoded the compiled JavaScript without executing it. The user explicitly requested saving this independently written report in the repository on 2026-10-12. Extracted artifacts, binaries and tooling remain outside this repository.

The XAPK contains a base APK, an ARM64 native split, English/French resource splits and an mdpi resource split. The base contains an Expo app configuration/manifest, a Hermes v96 JavaScript bundle, and seven DEX files. The bundle has 184,303 strings and 80,108 functions. The native split includes Hermes, React Native, PDFium, OP-SQLite and PowerSync libraries. Library presence establishes packaging, not which workflow uses each library.

Evidence identifiers below refer to function indices in this exact bundle. They are reproducible investigation anchors, not source to copy. `confirmed` means a field or branch was traced in bytecode; `inferred` means an architectural interpretation of those observations; `unknown` requires runtime or server-side evidence. UI text was examined as supporting evidence and is paraphrased here.

No reference-app account was used and no reference service was called. No identical-input PDF comparison or reference-app runtime test has been performed. The available Pixel_8 AVD is x86_64; this XAPK supplies an ARM64 native split. Do not assume it runs on that emulator. At the initial audit, no permissioned cardigan fixture was available. Subsequent Stitchbook-only checks use the privately supplied April Cardigan PDF and phone screenshots; they still do not establish reference-app runtime parser parity.

## PDF parsing: what the APK can and cannot reveal

**Confirmed:** new pattern parsing is a remote job. The client starts the `auto-fill-pattern-v2` function through Supabase functions, submitting file storage information and a draft snapshot (37164-37166). Follow-up job requests carry a mode, job ID and pattern ID (37168-37170). Status, cancellation and result consumption have separate paths (37171, 37176, 37177). Client phases distinguish uploading/starting from queued/processing and completed/failed/cancelled/expired jobs (37523-37524, 37543-37545). Preparing, analyzing and finalizing are named stages (37544). Recovery and pending-draft snapshot functions are present (37520-37525).

**Inferred:** sophisticated PDF-to-guide interpretation happens behind that remote service, rather than in a complete deterministic parser bundled in the app. The app's own help text also describes an online AI parser. This is not evidence of a specific model, prompt, OCR vendor or section-classification algorithm. PDFium's presence does not prove the parser uses it to extract text. A function named `extractText` turned out to concern SVG text rendering; it is not evidence of PDF extraction.

**Unknown:** server prompts, model and layout interpretation; exact handling of alternating parentheses in a real PDF; title/rule/front-matter exclusion heuristics; scanned-page accuracy; cost and latency; actual cardigan output. An APK cannot establish these server behaviors.

**Stitchbook implication:** reproduce reviewable results and execution capability locally. Keep PDFBox extraction, bundled OCR, source references and an editable draft. An optional assistant/model adapter can populate the same structured format, but must not become necessary for essential use. A small text splitter does not establish full parser parity.

## Structured sizes and instructions

**Confirmed:** reported sizes have IDs, names, display order and finished measurements, with a size configuration and measurement unit (37258-37263). Navigation distinguishes alpha, numeric and measurement notation (37554). Size resolution traverses pieces, setup, sections, rows, repeats, measurements, stitch-count targets and finish instructions (37409-37433).

A size-specific value can be a scalar or an array. The local resolver selects the chosen index; a missing/null entry falls back to the first array value, then to a supplied default (37443). Rows additionally carry explicit per-size instruction strings and row references (37426, 37434). This local code selects already-structured data; it does not demonstrate how raw PDF number runs were recognized. Two instruction-resolution paths have slightly different fallback rules (37434 versus 37580).

**Stitchbook decisions:** read the PDF's own sizes before guide creation; prefer letter labels to measurements; resolve only complete per-size number runs. Preserve ambiguous text and show a review issue rather than silently choosing another size. Stitchbook now selects explicit inline size-only prose and bounded size branches ending at All sizes, using independently written deterministic rules. Unknown selectors and unbounded blocks stay intact with review issues. This is not evidence that the reference parser recognizes those exact raw-text forms. Extend the shared structured format for general size-dependent construction next. Do not copy the reference's first-size fallback as a safety policy.

## Pieces, rows, repeats and conditional work

**Confirmed:** pieces carry quantity, optional mirroring information, setup/finish, target dimensions and sections (37409-37413). Sections have a working method and blocks. The block dispatcher distinguishes rows, fixed repeats, text, measurement loops, stitch-count loops and charts (37415, 37565, 37784).

Measurement blocks contain a target length/unit, where to measure from, an instruction, estimated progress, notes and nested blocks (37421). Stitch-count blocks use a target count with nested blocks (37423). Flattening preserves loop context and marks first/last steps (37570, 37575). Fixed repeats carry their own cycle, total cycles and repeat path rather than merely duplicating visible text (37568). Stitch-level repetition distinguishes a fixed count, across a count, to the last stitches, to end, to marker and around the row (37433).

**Stitchbook gap:** today's execution model has Section, Range, fixed Repeat and Instruction. It can already represent fixed nested repetition, but it has no typed piece instance, measurement loop, stitch-count loop or chart block. Plain instructions may retain this content, but cannot execute it equivalently.

**Incremental plan:** introduce manual, exportable target loops first, with an explicit user action for reaching the target; then piece quantity/mirroring and explicit size-dependent prose. Parser recognition should follow verified execution semantics. Extend revisions, backup/export and progress together. Never invent a fixed repeat count for a target whose number of cycles is unknown.

## Charts and correction

**Confirmed:** chart blocks carry row/stitch counts, repeats per row, starting row number/side, working method, legend, placements and optional written rows (37425). Flattened chart steps retain chart row index/number, row repeat index/count, side, read direction and written instructions (37619). Chart autofill has dedicated crop, legend, grid-review, warning and correction paths in the bundle. Field names include autofill provenance and attempt IDs.

**Inferred:** chart recognition is a separate review workflow; a chart is not simply an image attached to an ordinary text step. Static evidence does not establish recognition accuracy or every row-direction edge case.

**Stitchbook plan:** build a manual chart attachment/editor and readable row execution first. Verify flat RS/WS direction, rounds, starting offsets, repeated chart rows and written-row fallback with permissioned examples. If recognition is later added, retain source, confidence/warnings and editable legend/grid before applying it.

## Progress and navigation

**Confirmed:** progress stores position, active and completed loop cycles, and cast-on stitch counts (31410). Loop scopes include piece, instance and section; repeat paths distinguish nested contexts (37680, 37716-37718, 37741). Work tracking has per-item state and estimate overrides (37675-37676). Completion can replace an estimate with actual total units; status, completion timestamps and last position are separate fields (37696, 37705, 37713). Overall progress aggregates completed/total units and clamps/rounds the result (37701). Advancing distinguishes normal movement, a checkpoint and looping back (48592).

**Stitchbook plan:** retain per-project progress and stable node identity. For future target loops, persist each loop's cycle state in its own repeat/piece scope, preserve it through pause/restart/jump/back, and distinguish estimated remaining work from recorded rows or time. Test two sleeve instances and nested loops so one does not inherit another's completion. Static evidence does not prove every reference-app undo/recovery behavior.

## Details, materials and project records

**Confirmed:** metadata is separate from executable pieces: designer, category, craft, difficulty, description, techniques, gauge, materials, tools and charts (36962, 37554, 37589). Autofill merge tracks touched fields, applied fields and suggestions. Conflicting extracted metadata is retained as a suggestion rather than blindly overwriting the current field (37537-37539).

Yarn specifications include roles, strand counts, held-together groups, alternative groups, ranges of yardage, grams, skeins, per-skein amounts, fibre, colour and raw text (37210). Materials can track whether parsed specifications are fresh (37649). Project-photo normalization distinguishes storage/display locations and covers (30534-30548). Timer code explicitly handles persisted state, recovered time and reconciliation (31203, 31244, 31497, 31503). These names/fields identify promising behavior, not proof of complete UI or crash-recovery parity.

**Stitchbook plan:** keep original wording; join wrapped details; route measurements and abbreviations to details. Preserve user edits and offer extraction suggestions for conflicts. Model held-together yarn and alternatives when the materials page is built, with portable exports. Continue using recorded crafting sessions, user-accessible photos and reproducible cache thumbnails. Study timer reconciliation separately before changing session accounting.

## Prioritized follow-up and acceptance evidence

1. Complete PDF-derived size choices and alternating-run resolution, with every size tested and ambiguous runs preserved. Completed in PR #67; the next slice repairs metadata separation.
2. Repair metadata separation using a realistic multi-page permissioned fixture, including title/rules, wrapped gauge tails, measurements and abbreviation definitions.
3. Extend the common manual/assistant guide format to explicit size-only instructions and structured piece instances. Validate imported data before draft creation.
4. Add target loops with persisted, independently scoped cycles and portable export, then parser support. Test previous/jump/restart and two piece instances.
5. Add manual charts with direction/offset/repeat semantics and correction; evaluate recognition only afterward.
6. Build the already-requested pattern/project page mockups around these capabilities. Obtain the user's mockup approval before coding the bigger pages.

For each feature, label static evidence separately from observed runtime output. Runtime comparison should use the same permissioned PDF and chosen size, checking metadata, pieces, step text, nested repeats, navigation and persistence. Current regression tests establish Stitchbook behavior only; they do not claim LooseLoop's server produced the same result.

## Stitchbook application ledger

- PR #67: PDF size discovery and grouped/alternating numeric resolution.
- PR #68: known-title/rule filtering, wrapped metadata boundaries, and portable measurements/abbreviation notes; 582 unit tests, lint and debug build passed. This reproduces the separation of details and executable content; it does not claim the reference server uses these heuristics.
- Automatic naming follows the user's explicit naming decisions; it is not presented as a discovered reference algorithm.

- PR #69: automatic pattern/size and PDF-stem names, following the user's decisions.
- Calculator feedback uses independent partial results and clear missing-input prompts. It is a user-requested usability fix; reference timer/progress algorithms remain separate follow-up work.

- PR #70: partial calculator feedback and aligned fields; 587 unit tests, lint/build and three emulator UI checks passed. Compact time labels and adaptive large-font controls follow the user's phone feedback; session arithmetic is unchanged.

- PR #71: compact/scalable time tiles and adaptive action buttons; 587 unit tests, lint/build and thirteen focused emulator checks passed. Standalone project PDF title filtering now uses the generated filename-stem name. Pattern details remain portable through JSON and Library CSV; linked-pattern details are not currently expanded in project Markdown.

## Revisit: timer recovery and additional useful behaviours (2026-10-13)

The user reported LooseLoop asking what to do with time recovered from a timer left running, and requested this behaviour plus a fresh audit beyond the existing roadmap. The same XAPK was read again in memory with the existing Hermes tool. No app/service was executed, no reference account was used, and no source, binary, artwork, credential or arbitrary bundle string was added to the repository.

**New confirmed static evidence:**

- Initial timer state includes `inactivityTimeoutMinutes` and `pendingInactivityDecision` (21504).
- `calculateRecoveredTime` has branches referencing the inactivity setting, `MAX_RECOVERY_MS`, and reason tags for automatic pause/inactivity timeout (31244). Exact defaults and every runtime edge case are not established by this inspection.
- Stale-running recovery carries a recovery checkpoint and pending state (31165); recovery handles running/paused state and start/pause/end timestamps (31192).
- Live display construction references `MAX_LIVE_TIMER_GROWTH_MS` and total elapsed time (31305). A timer projection is distinct from the session records that account for time.
- Reconciliation builds a canonical scope/source and distinguishes completed elapsed time from a resumable session (31497, 31503). Project timer scopes and standalone-counter timer maps have separate paths (30561-30581, 21378-21379).
- Dedicated project-completion timer finalization and open-session closing paths exist (31347-31349, 30755-30763). Their full failure and retry behaviour is not runtime-verified.
- A manual-entry elapsed-time helper distinguishes manual records by source (31316-31318). The complete manual-entry UI is not verified.

**Observed by the user:** the reference app asks about unpaused/recovered time. **Inferred:** the pending decision and timeout/recovery branches support that experience. Static names alone do not prove identical dialog choices, automatic pause timing or an exact inactivity algorithm.

### Independent Stitchbook application and next priorities

| Useful capability | Stitchbook action | Evidence / status |
| --- | --- | --- |
| Review suspicious running time instead of silently trusting an overnight clock | Configurable foreground/return check; Count all time, correct crafting minutes and pause, or defer | Implemented independently; 900-to-10-minute and real database/UI tests. Uses existing portable session fields. |
| Protect grouped written instructions while making actions easy to scan | Separate clear actions visually; retain bracketed/quoted/starred sequences and the tracked row | PR #87; user-requested presentation, not claimed reference UI parity. |
| Close timers when a project is completed | Completion transitions atomically finalize all unfinished project-owned sessions through the existing timer engine | Implemented independently from named completion-path evidence. Tests cover pause accounting, other scopes, rollback and later metadata edits; exact reference runtime semantics remain unverified. |
| Reconcile elapsed projections with canonical sessions | Keep session records as accounting truth; audit restored/multiple-active timers and prevent duplicate contributions | Existing single-timer model partly covers this; recovery/restore edge cases deserve separate fixtures. |
| Standalone-counter time with clear ownership | Consider counter-scoped sessions and intentional project attribution | Additional capability beyond current project timers; never double-count counter and project time. |
| Add manual crafting time without briefly starting a timer | Evaluate a direct manual-entry workflow using the existing session record and exports | Helper verified, full reference UI unknown; Stitchbook currently corrects existing sessions. |
| Preserve uncertain timer recovery for review | Prefer explicit user choices and portable correction history over silent truncation | Stitchbook retains the original interval and prior pauses; timing caps are evidence to investigate, not a policy to copy. |
| Piece-aware target loops and measurement checkpoints | Continue the previously identified execution-format work with scoped cycles, user confirmation and exports | Still a functional gap; do not fabricate fixed repeats for unknown-length work. |
| Conflicting extraction suggestions and held-together/alternative yarn | Prioritize actionable material suggestions and role-aware yarn planning when those screens are extended | Previously confirmed capabilities remain useful even outside a phase checklist. |

This report is a living capability list. A useful observed behaviour is not excluded merely because it was absent from the old roadmap. Each implementation still needs original code, craft-neutral semantics, offline essential use, portable data and proportionate validation. Completed parser/metadata/UI slices should not be re-investigated just to rerun the audit; focus subsequent passes on the remaining execution, recovery and planning gaps.
