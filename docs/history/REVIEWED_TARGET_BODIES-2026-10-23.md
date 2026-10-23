# Reviewed target bodies — 2026-10-23

## Scope and compatibility

Reviewed Section targets retain the existing ordered finite body, instruction IDs,
wording and provenance. Focus traverses every body occurrence each cycle and
requires the final step plus preceding body completion before confirming reached.
Observations stay keyed to the first body's complete revision/finite-ancestry
address, within its owning project/piece execution. Previous, Jump and reload keep
the cycle cursor and observation history. Revision carryover excludes all target
body work; old publications and original source files are unchanged.

This slice supports one independent Section target, including finite Range/Repeat
children and independent outer finite occurrences. Nested targets and unresolved
conditions within the body, dependent/simultaneous wording and bodies exceeding
10,000 occurrences are refused. This is explicit manual review, not additional
PDF prose recognition or real-pattern certification. Unsupported imported wording
remains visible for review.

Room stays **27**: no table/column/schema change or migration is needed. Backup
**7** uses the existing node target and execution-work JSON shapes with explicit
new semantics. Readers retain versions 1–6; Section targets cannot masquerade as
format 6. Drafts may retain unfinished sections outside a valid reviewed body.
JSON/Room/Markdown preserve the body and work, with step IDs in body Markdown.

## Checkpoint and ownership

Startup branch was `codex/pdf-target-suggestions` at
`7762c30b8ee41892132d1b34a51ac90c05dd5888`; main/origin/main were
PR136 squash `77d29121b31f95342644fba5cc8b909d59cd6d3f`. All committed trees
matched `c4839fa7cbb3e411d1b1bc7583ca1728b0c6c75c`. Remote heads and PR136's
merged state were rechecked. Other Stitchbook chats were idle at startup.

Pending AGENTS/CONTRIBUTING/README/ROADMAP/GRAPHIFY/DEVELOPMENT_WORKFLOW,
refresh-script and HANDOFF/history cleanup remains local and unpublished. The
original archive is preserved. Only this slice's app files, ARCHITECTURE and this
evidence record are publication scope. Roadmap behavior/checkpoint updates stay
with the pending documentation. No other chat's work is claimed as verified.

## Verification commands and results

All Gradle commands use Android Studio's bundled JBR21:
`$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'`.
Gradle was serialized; app source/resources/build inputs stayed frozen during
each command. Generated APKs, manifests, reports and synthetic captures stay
ignored. No physical phone is used for tests.

Initial focused command:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*TargetBody*Test' --tests '*ManualTargetTest' --tests '*GuideEntityMappingTest' --max-workers=2
```

The sandbox could not access the existing Gradle wrapper lock under the user's
cache. The approved rerun passed (34s). No test expectations were weakened.

Combined affected pass:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests 'com.macareen.stitchbook2.domain.execution.*' --tests 'com.macareen.stitchbook2.domain.backup.*' --tests 'com.macareen.stitchbook2.data.backup.*' --tests '*GuideEntityMappingTest' --tests '*ExecutionEntityMappingTest' --tests '*DraftEditorViewModelTest' :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --max-workers=2
```

First pass: **262 tests/25 suites**, both APKs and **0 lint errors/53 warnings**
(2m12s). Review then added cached target-body lookups and a regression preserving
legacy instruction-target cast-on counters. The same pass succeeded again:
**263 tests/25 suites**, both APKs, **0 errors/53 warnings** (2m22s).

Final compatibility review found full publication validation would incorrectly
reject an otherwise valid target draft with an unfinished unrelated section.
Draft-specific validation now preserves that state; a regression covers restore.
The final affected compatibility/editor/export rerun is:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*TargetBody*Test' --tests '*ManualTargetBackupTest' --tests '*GuideBackupJsonCodecTest' --tests '*LocalBackupServiceTest' --tests '*BackupValidationTest' --tests '*PieceBackupTest' --tests '*ProjectMarkdownTest' --tests '*GuideEntityMappingTest' --tests '*DraftEditorViewModelTest' :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --max-workers=2
```

Final execution tests from the 263-test pass remain applicable: subsequent changes
only affect draft-body validation and backup/Markdown. No full unit suite or
migration suite is claimed for this schema-unchanged slice.

The final compatibility/editor/export command passed **119 tests/10 suites**,
both debug APKs and **0 lint errors/53 warnings** (2m38s).

## Native results and explicit pause

The existing emulator was verified as `emulator-5554`, `ro.kernel.qemu=1`, model
`sdk_gphone16k_x86_64`. Both final APKs were installed there with `adb -s
emulator-5554 install -r`. Native command (output retained in ignored
`app/build/reports/target-body-native.txt`):

```powershell
adb -s emulator-5554 shell am instrument -w -e class 'com.macareen.stitchbook2.data.parsing.PdfTargetBodyWorkflowTest,com.macareen.stitchbook2.data.parsing.PdfTargetWorkflowTest,com.macareen.stitchbook2.feature.draft.ManualTargetDialogTest,com.macareen.stitchbook2.feature.focus.ManualTargetControlsTest,com.macareen.stitchbook2.data.database.ExecutionRepositoryTest#manualTargetSavedPlaceRetainsCyclesAcrossReviewedRevisionChanges,com.macareen.stitchbook2.data.database.ExecutionRepositoryTest#manualTargetsPersistGuardWritesAndSurviveJsonRestoreAndFreshPieceWork' com.macareen.stitchbook2.test/androidx.test.runner.AndroidJUnitRunner
```

**13 cases/12 passed/1 failed** (29.807s). The new PdfTargetBodyWorkflowTest
fails at line 51 expecting `(p.2 l.1)` in the complete body review text. Its prior
selected-size `10 stitches` assertion passed. It aborts before setting the target:
the new full PDF/body/Focus/Room-restore workflow is **not verified**. Determine
actual extracted source text/provenance before fixing the mapping or test; do not
weaken source preservation. The runner process exits zero despite JUnit failure;
the explicit `FAILURES!!! Tests run: 13, Failures: 1` is the authoritative result.

The other PDF target workflows, review/Cancel, light/dark/180% Focus controls and
legacy saved-place/piece/JSON/Room cases passed. A synthetic large-text screenshot
was captured but has not been visually inspected. No private patterns were used.

The user requested **pause safely after install** while checks were running.
Checks were allowed to finish, the failure was reported, and the current debug
APK was installed on the verified physical phone using explicit-serial
`adb install -r`: **Success**. This preserved user data; no phone tests/content
inspection/uninstall/clear were performed. APK SHA256:
`e90335e9c4363d01a0da1150e0dea99f1418bea20fd159a371e01fa7065f63f6`.

The implementation is incomplete, local, uncommitted, unstaged and unpublished.
No PR exists for this slice. New branch `codex/reviewed-target-bodies` remains at
the initial feature HEAD; before eventual publication, normalize its base to the
equal-tree main without disturbing dirty work. HANDOFF records the explicit pause
and concrete resumption work. No app changes were made after final verification.

Final dirty app snapshot: 29 files, manifest
`app/build/reports/target-body-snapshot.json`, SHA256
`5af2055d8abbefdd7b01791f00532e466a17af0c204b13796e2c5a67d1efc8b8`.

## Graph freshness at the initial pause

`.\scripts\refresh-graphify.ps1` passed after the coherent code changes, then
reran after the final five-file compatibility fix: **5 re-extracted/439 cached;
6,909 nodes/22,966 edges/253 communities**. Code-only, no labels/cloud/HTML.
The earlier refresh reported partial Kotlin extraction for GuideFocusViewModel
(44 symbols extracted); XML/build/other Kotlin coverage remains incomplete.
Live source and tests are authoritative. No indexed code changed afterward.

## Workflow trial slice two

Startup retrieval was overbundled and truncated again; prospective character
counting was missed, so the count remains unknown. No token/time savings claim.
The first green pass was invalidated by a legacy-counter compatibility correction;
the second by a partial-draft restore correction. These are review-driven rework,
not failed assertions. Carry the table to slice three and archive the conclusion.

Prior evidence: [PDF suggestions](PDF_TARGET_SUGGESTIONS-2026-10-23.md).

## Resumed completion

The user explicitly resumed this slice. Live Git and other-chat status matched
the pause; other Stitchbook chats remained idle. The newly created feature branch
was normalized to main `77d29121b31f95342644fba5cc8b909d59cd6d3f` using
`git switch -C codex/reviewed-target-bodies main`; before/after SHA256 checks found
**zero changes** to any dirty implementation or pending-cleanup file.

Live `ParsedPatternMapper.withProvenance` confirms ordinary rows receive `(p.N)`;
only condition/review text receives `(p.N l.N)`. The new fixture's line-number
expectation was incorrect. It now checks the actual page marker and retains exact
before/after equality for all instruction nodes, including their existing text,
IDs and provenance. No production parser/source mapping changed. The test also
exercises Jump, immutable reviewed revision changes and returning to an unfinished
body saved place with its cycle and observations intact. Its corrected native run
passed (0.747s). `:app:assembleDebugAndroidTest --max-workers=2` passed (8s), and
incremental `:app:lintDebug --max-workers=2` passed (23s).

The dark 180%-text target-body review capture was pulled from the synthetic
emulator and visually inspected: full body text, acknowledgment, unit/comparison
controls, value, Remove/Cancel/Save were readable and reachable without clipping.
The image remains ignored and is not publication content.

Final refusal review added coverage for conditions hidden in finite Repeat labels
or ancestor headings. Draft saves, publication and backup restore reject these
alongside nested/other target cases. The affected pass succeeded: **64 tests/5
suites**, both APKs and **0 lint errors/53 warnings** (2m14s):

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*TargetBodyTest' --tests '*TargetBodyBackupTest' --tests '*GuideEntityMappingTest' --tests '*DraftEditorViewModelTest' --tests '*BackupValidationTest' :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --max-workers=2
```

Revision comparison now checks already-expanded occurrence paths for target
ancestry, avoiding re-expanding a whole body for every completed occurrence. Its
affected pass succeeded: **25 tests/3 suites**, both APKs and **0 errors/53
warnings** (2m14s):

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*RevisionProgressPreviewTest' --tests '*ReviewedRevisionChangeTest' --tests '*TargetBodyTest' :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --max-workers=2
```

Unchanged execution/data/editor results from the preceding 263/119/64-test passes
remain applicable; only invalidated checks were rerun. The final APKs were
installed on `emulator-5554` and the same 13-class/method native selection above
reran: **OK (13 tests)**, **30.855s**. The output is retained in ignored
`app/build/reports/target-body-native-final.txt`. This final run includes the
new full extraction/size/editor/body/publication/Focus/Jump/reload/saved-place/
JSON/Room/Markdown path and the prior 12 regression cases. No failure remains.

Final app manifest (29 files):
`app/build/reports/target-body-snapshot.json`, SHA256
`e698afba32771894309c8df7185d8893093bdd0a7a21d7aba46387f9b48e73d6`.
Final APK SHA256:
`7b474fef866cd2927feb31d8bd680d94947c8666239512439acd5d5971af97e1`.
The earlier phone install remains successful; the phone disconnected before the
last validation fixes. A reconnect request was sent. The final APK is available
for a data-preserving update once the phone reconnects; no phone tests occurred.

Final graph refresh passed: **1 re-extracted/443 cached; 6,912 nodes/22,971
edges/252 communities**. It follows the four-file refusal refresh. Code-only,
no labels/cloud/HTML; the recorded partial Kotlin coverage remains. No indexed
input changed afterward. Final scoped correctness/privacy/craft-neutrality/data
ownership review and `git diff --check` passed (LF-to-CRLF advisories only).

Publication status and exact Git heads are recorded in the compact HANDOFF.
The pending workflow cleanup and original archive remain local and unpublished;
this record, ARCHITECTURE and the 29 app files are the only intended publication
scope. Trial slice two records the fixture-expectation correction, guard coverage
and preview traversal correction without claiming token/time savings.
