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
| Add manual crafting time without briefly starting a timer | Add finished manual records with overlap review and portable source | Implemented independently; reference helper verified, full reference UI unknown. Stitchbook validates finish time, duration and repeated daylight-saving hours. |
| Preserve uncertain timer recovery for review | Prefer explicit user choices and portable correction history over silent truncation | Stitchbook retains the original interval and prior pauses; timing caps are evidence to investigate, not a policy to copy. |
| Piece-aware target loops and measurement checkpoints | Continue the previously identified execution-format work with scoped cycles, user confirmation and exports | Still a functional gap; do not fabricate fixed repeats for unknown-length work. |
| Conflicting extraction suggestions and held-together/alternative yarn | Prioritize actionable material suggestions and role-aware yarn planning when those screens are extended | Previously confirmed capabilities remain useful even outside a phase checklist. |

This report is a living capability list. A useful observed behaviour is not excluded merely because it was absent from the old roadmap. Each implementation still needs original code, craft-neutral semantics, offline essential use, portable data and proportionate validation. Completed parser/metadata/UI slices should not be re-investigated just to rerun the audit; focus subsequent passes on the remaining execution, recovery and planning gaps.

## Revisit: pattern, guide and project relationships (2026-10-13)

This targeted static pass follows the user's request to examine relationships and other useful capabilities. It inspected safe function identifiers, field names and branches from the same bundle in memory. The reference application and its services were not executed. No reference implementation or personal data was saved here.

**Confirmed static evidence:**

- Project insertion carries `pattern_id`, `selected_size_id`, a legacy `selected_size_index` and initial progress (30522). Linking writes pattern and size identifiers to the project (30689); an unlink path exists (30690-30693), but its full cleanup semantics were not established.
- Guide reads select `pattern_guides` by both `pattern_id` and `size_id` (37341). Guide writes carry that pair plus executable pieces, parse/review state and edit statistics (37380, 70349). Migration from older pattern-contained pieces produces size-addressed guide records (37407).
- Size-reference repair compares the selected ID with available IDs and retains diagnostic fields identifying repair source and prior index (30701, 30708). This is evidence of reference repair, not a policy to silently choose a different size in Stitchbook.
- Progress and work-tracking updates live on projects, including loop cycles, completed cycles, progress snapshots and last-worked time (30813, 30821, 30832). Shared guide content and individual work progress are separate.
- Pattern deletion checks projects referencing the pattern and produces dependent-project IDs, names and statuses plus a can-delete result (37035-37036). The safe deletion path inspects that result and server confirmation before storage cleanup (37049). Size removal looks up projects using that size before altering available sizes and deleting its guide (37112, 37133). Exact UI decisions and transactional guarantees remain unverified.
- Copying a guide between sizes carries source/target size IDs and checks same-size, target guide, target draft and target-processing conditions before writing target pieces (72756). This does not prove automatic recalculation of stitch counts for the new size.

**Inferred relationship model:** a pattern supplies metadata and original documents; each selected size addresses reusable executable guide content; a project references the pattern/size and owns its work history. A separately named guide is not necessarily an additional project-owned copy. Static inspection has not established immutable revision pinning, how active projects react to guide edits, full unlink/reset behavior, or every deletion failure case.

### Comparison and independently implementable follow-ups

Stitchbook already supports reusable pattern-and-size guides, project-owned guides, explicit project-guide links and separate per-project execution. Published definitions are immutable revisions; an execution remains pinned to its revision. Removing a Library index entry clears a guide's pattern reference rather than deleting the guide; original SAF documents remain untouched. Project deletion removes project-owned guides and its links/progress while reusable pattern guides survive. These existing safeguards should be retained.

Useful next slices, including capabilities outside the old roadmap:

1. Show dependency counts and named affected projects before guide, pattern or size operations. Distinguish removing an association from permanently deleting a guide and its revisions/progress.
2. Make use-of-pattern-guide plus its project-pattern association atomic; audit failure/retry so a project cannot acquire only half of the relationship.
3. Offer copy-to-another-size as a new editable draft with new node IDs, source provenance and a clear size-review requirement. Never pretend copied stitch counts have been adapted.
4. Present a newly published revision as an explicit choice for existing projects; preserve each project's pinned progress and do not silently remap it.
5. Keep missing-reference recovery reviewable: preserve orphaned content and explain missing patterns/sizes instead of silently falling back to a different size.

These are reference-informed follow-ups, not claims that every corresponding reference runtime behavior has been verified. Chart review flags, metadata-conflict suggestions, piece instances and materials alternatives remain additional useful capabilities from the earlier audit.

The user expanded authorization on 2026-10-13 to incorporate every useful capability feasible for Stitchbook, beyond the original checklist. Treat this report as an active backlog while respecting offline use, original implementation, accessible originals, exports, migrations and verification. Static or remote-only evidence still requires independent design; it does not justify claiming undocumented reference behavior.

2026-10-13: Using a reusable guide now resolves its current owner/pattern in one Room transaction and attaches both project-guide and project-pattern associations atomically. Failure leaves both associations unchanged; retry is idempotent. A guide owned by another project is rejected. Orphaned reusable guides stay usable; stopping use removes only the guide link, preserving independent pattern links and other projects. Existing revision and per-project execution data are untouched.

2026-10-13: Published reusable pattern guides can be copied to another size as a new unpublished draft. The source is the latest published revision, not unfinished draft edits. New guide/draft/node IDs preserve independent progress; tree structure and original instruction text are retained. Existing target sizes (including normalized labels) are blocked. One transaction creates guide, draft and nodes or rolls back all of them. Portable guide notes and a review instruction retain source size/revision and the requirement to adjust counts, repeats and measurements before publishing. This is a manual adaptation aid, not automatic grading. The form requires a review acknowledgment and retains input after failed writes. No schema/backup format change; existing format-3 guide exports include the new draft and provenance.

2026-10-13: The reusable-guide picker now includes preserved guides whose Library reference was removed, labels that condition clearly, and excludes project-owned guides. A kept guide can be attached to another project without inventing a missing pattern association. Pattern removal wording states that original files, guides and progress survive while project-pattern links are removed. Existing immutable revisions/drafts remain exportable. Named dependency previews remain a separate follow-up.

2026-10-13: Timer starts now check and write atomically. Focus starts only while idle and cannot displace another timer through a stale check. Explicit Project Sessions start atomically finalizes all unfinished records before creating a new timer; failures roll back the entire switch and existing IDs cannot be overwritten. Recovered overdue sessions are reviewed individually (oldest first) with independent Later deferrals, rather than hiding all but the latest timer. Compare-and-save protects reviews from concurrent timer actions/corrections. Restore retains uncertain records for review; this does not infer exact duplicate work or silently discard recorded intervals. No schema or export-format change.

Manual-time and guide-copy dialog regression checks support opt-in emulator screenshot capture in light, dark and actual 180% system text. Screenshots use synthetic test inputs, remain outside Git, and do not establish reference-app runtime visual parity.

Visual review found a multiline size label at 180% system text and offscreen save errors in both forms. The copy label is now New size; failed-save messages request visibility inside the scrollable form. Screenshot tests assert the error is displayed before capture. Font-scale checks wait for Android configuration recreation instead of requesting a second recreation.

2026-10-14: Targeted static inspection identifies row/repeat/text/measurement/stitch-count/chart dispatch, with repeat contexts carrying cycle/totalCycles, repeatPath, containerContext and nestedContext (37565, 37568, 37680, 37716). This supports keeping structural position separate from leaf instructions. Exact color selection and the remote parsing algorithm remain unverified. Stitchbook independently presents section, repeat and row containers using its theme colors and explicit type labels; this is functional inspiration, not a claim of runtime parity.

2026-10-14: April Cardigan private diagnostics found that its single-row instructions become instruction leaves inside fixed repeats rather than Range nodes. Stitchbook derives row/round display context only from a repeat whose direct children have consecutive, unique numbered labels in the same unit; it does not change execution addresses or counted steps. One printed numeric run has ten identical alternatives for nine sizes. Oversized identical alternatives now collapse because the result is invariant; differing oversized alternatives stay unchanged. This is an independent conservative rule, not confirmed reference-app handling. Private PDF and extracted content remain outside Git.

2026-10-14 scope clarification: LooseLoop is the user's former craft app and Stitchbook is their new project. The goal is migration of most useful crafting capabilities, with changes and improvements, rather than a few isolated inspirations. Exclude community/social features and reference aesthetics. Keep Stitchbook's own cozy design, offline essential workflows, portable originals and exports. Typed nesting colors are functional reading cues implemented with Stitchbook theme tokens. Maintain confirmed/inferred/unknown evidence and a feature-by-feature migration backlog; remote-only parsing remains a capability gap to close independently, not a mandate to recreate the reference service.

## Functional migration checklist (2026-10-14)

This is a capability migration from the user's former app, with original Stitchbook implementation and design. Static evidence is not a runtime comparison. Community/social features and reference aesthetics are excluded; local exports and the Android share sheet remain ownership tools.

| Crafting capability | Stitchbook status | Remaining acceptance work |
| --- | --- | --- |
| PDF-to-guide interpretation | Local PDFBox/OCR, editable drafts, source pages and optional structured assisted input | Broad layout/language/chart accuracy and differential comparison; the reference server algorithm is unavailable |
| Sizes and size-specific work | PDF-derived labels, numeric resolution, explicit bounded size selectors; all nine April sizes checked on Android | General size-dependent construction and more permissioned patterns |
| Nested rows and fixed repeats | Stable execution addresses, per-project progress, explicit row/repeat context, typed colors and scoped inline action groups | More complex conditional sequences and parser recovery cases |
| Pattern–guide–project reuse | Reusable size guides, atomic use plus pattern association, immutable revision pinning, selective reviewed carryover to latest/earlier publications, resumable prior places, draft copy and preserved orphan guides | Named dependency previews and side-by-side full revision browsing |
| Old guide management | Confirmed unused removal and reversible archives with schema-24 migration, portable metadata and retained progress | Named dependency previews and further runtime comparison |
| Timer recovery and statistics | Configurable overdue review, correction/manual time, recorded sessions, atomic starts and version guards | Additional crash/background scenarios; no claim of exact reference reconciliation parity |
| Multiple pieces/instances | Named project pieces with separate whole-guide revision pins/progress, archive/restore, Home resume and portable format-4 records | Subsection selection, quantity and mirroring; further runtime comparison |
| Until-length/stitch-count loops | Original wording can be reviewed in instruction steps | Executable targets, scoped cycle persistence, back/jump/restart and measured-versus-estimated totals |
| Charts | Original PDF remains accessible; chart interpretation is not declared complete | Manual chart model first: symbols, direction, offsets/repeats, correction and exports; recognition afterward |
| Materials | Raw pattern details, stash, allocations and tools | Structured held-together strands, roles/alternatives and specification freshness |
| Metadata correction | Blank-field filling preserves user edits | Reviewable conflict suggestions with provenance |
| Covers, journals and sharing | User-accessible photo references, project cover/dashboard, tagged contextual notes and PNG exports | Further requested page refinements and source-photo workflows |
| Counter companions | Project counters, Focus-scoped notification service and atomic persisted-state forward/link/reset rules | Further notification lifecycle checks; watch snapshot names alone do not prove wearable runtime parity |

Acceptance requires tests for the underlying state and portable data, followed by native visual checks for presentation. A smaller parser or placeholder instruction is not evidence that a richer reference capability has been migrated.

2026-10-14: Instruction presentation keeps page citations such as `(p.3, p.4)` together instead of treating them as purl actions or nested stitch groups. Compact actual actions such as `p3, p4` still receive separate lines. This is presentation only; stored guide text and execution counts are unchanged.

## Next migration implementation sequence (user choices, 2026-10-14)

The user requests work on counter reliability, April guide usability, reviewable revision upgrades, and the broader crafting migration. For revision updates, propose carrying over matching completed occurrences for explicit review. For pieces, prioritize named pieces with independent progress (for example Left sleeve and Right sleeve).

1. Counter reliability: fresh stored configuration, atomic source/link/reset writes, no deleted-source resurrection, concurrency and rollback tests. This is separate from numbered guide row/repeat context.
2. April usability: keep the permissioned PDF outside Git; regenerate all nine sizes using Android extraction and inspect all fixed-repeat contexts and unresolved multi-size runs. Preserve stored source text and review ambiguous alternatives instead of guessing.
3. Revision upgrades: build a read-only candidate comparison first. Match stable node identity, exact instruction content and unchanged semantic ancestry (section, range bounds/unit, repeat count/label, occurrence values). Changed or ambiguous steps remain uncompleted. Review proposed carried and unmatched work; retain the old execution and pin. Applying must guard the old execution version and target revision and commit a new execution atomically. No automatic migration on publication.
4. Named pieces: add explicit project piece identity and a free-text name, distinct from guide sections and fixed repeats. Each piece selects a guide/revision and keeps separate current/completed occurrences. Existing project progress maps to the default piece without loss. Provide rename/archive/recover, explicit Room migration and full/project JSON plus Markdown export before release.
5. Until-target repeats: independently typed length or stitch-count target, unit and user-entered measurements. A measurement confirms the target; estimates never imply completion. Persist each cycle and observation, including Previous/Jump/restart semantics and exports. Do not pretend a large fixed-repeat count implements this.
6. Manual charts: editable cells/symbol legend, explicit craft/direction, offsets and repeat boundaries, independent row position, PDF source reference and portable model. Validate manual chart execution before attempting recognition.
7. Materials: named project material roles and held-together groups with explicit strand counts, optional alternatives and provenance. Keep these specifications separate from stash reservations and actual consumption so an alternative cannot reserve or consume stock implicitly. Include backup/export and reviewable extraction conflicts.

These are implementation and acceptance plans, not declarations of shipped functionality. Finish each bounded slice with domain/data checks and native presentation review where applicable.

2026-10-14 revision migration foundation: a read-only comparison proposes only completed occurrences with unchanged stable identity, exact instruction text and semantic container context. Changed/removed work is listed separately and an oversized comparison fails without a partial result. This is not yet a user-facing upgrade flow; review UI and guarded transactional application remain pending. Named pieces, target loops, charts and material specifications remain in the implementation sequence above.

2026-10-14 April rerun after the counter/revision foundation: emulator Android extraction passed all nine size checks, each retaining 66 instruction leaves and three numbered repeats, with zero detected long alternating size runs. The seven native action/group layout checks also passed. These checks do not establish universal parser accuracy or verify the physical phone build. The temporary private PDF copy was removed and the original remained untouched.

2026-10-14 named-piece acceptance: 732 unit tests pass; 59 relevant emulator checks pass across repaired full/targeted runs, covering migration, independent default/left/right progress, stale metadata, archive/Home refresh and restore rollback. Final light/dark/180% system-text screen tests pass, with clean captures reviewed. Done clears the dialog focus and keyboard; failed entries and action buttons stay reachable. Lint reports 0 errors and 96 warnings, with no findings in new piece files/resources; debug APK builds. Emulator availability, System UI obstruction and an ADB reset were resolved before final captures. No physical phone installation.

### Stitchbook follow-through: reviewed guide revisions (2026-10-14)

The pattern / size guide / project-piece relationship now supports explicit revision review and selective carryover of unchanged completed steps. Previous unfinished work stays resumable, scoped independently for each project or piece, with immutable revision history in existing exports. This is an original local implementation; LooseLoop’s private reconciliation algorithm was not available. Remaining larger capabilities include subsection/quantity/mirroring for pieces, measurement-target execution, manual charts and structured material groups.

Publication choice follow-through: the native guide review can target earlier immutable publications as well as the latest. Candidate addresses are recomputed for each target; only applying changes the selected execution. Existing revision history and portable exports already carry the required definitions.
