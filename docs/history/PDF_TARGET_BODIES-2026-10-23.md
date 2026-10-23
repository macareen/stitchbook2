# Bounded PDF target-body suggestions - 2026-10-23

## Scope and starting state

Requested continuation from PR137: feature `85a94cbdf0d0718b3eeb863f339376ef674a8da4`, main/origin/main `75944ec82c761241039ff4d2432c1a38aa18e6dd`, equal trees `893b700d52c99ec28d232f9e2735929c77cb93c3`. Verified live before editing. New branch `codex/pdf-target-bodies`. Other Stitchbook chats were idle. Pending workflow/documentation/script changes and the original archive remain unpublished and were not included in app work.

Room27 and backup7 verified in live declarations. No schema, dependency, format, essential network, original-file or execution-engine changes.

## Behavior

Only explicit `Repeat row(s)/round(s) N[-M] until ...` references to adjacent already parsed ordered numbered instructions/ranges in the same container qualify. Bounds: at most 32 referenced rows/rounds and the existing 10,000 finite-occurrence guard. Conditions: one positive decimal cm/in/inches length, or an explicit at-least/at-most positive whole stitch count. No inferred stitch direction, cadence, referenced names or cycle total. Missing/out-of-order/interrupted spans, nested unknown-length, dependent/simultaneous conditions and multiple conditions refuse suggestions. Unsupported wording remains ordinary source-linked instruction/review text.

Suggestions are derived only for size-guide drafts. Import makes no targets. Review displays the full selected-size body, finite structure and original condition/provenance; acknowledgment is required. A captured full draft version rejects stale edits. Save adds a Section wrapper around existing nodes; every original instruction ID, wording, provenance and leaf order survives. The original condition is retained as a separate source step after the body, explicitly disclosed in the dialog. Publication is a separate action and old revisions/work remain unchanged.

The screenshot-driven editor adjustment reduces the guide header to two lines, shows warning prose as compact expandable plain text and removes repeat/target/replay actions from warning rows. Ordinary step actions are under Step options; suggestions remain directly visible. Original warning wording remains available. Private screenshots/PDF content were not copied into the repository or fixtures.

## Verification

Inputs frozen for serialized JBR21 Gradle checks. Initial sandbox attempt could not access the Gradle wrapper cache lock; approved rerun used the existing cache. First affected pass: 106 tests / 9 suites, zero failures/errors; debug and test APK assembly succeeded. Lint passed: 0 errors / 53 warnings. Combined pass took 4m45s.

First native run on verified `emulator-5554` (`sdk_gphone16k_x86_64`): 15 cases / 35.937s, 13 passed and 2 failed. Suggested-body flow reached the final Room revision assertion: the raw flat node-list order differed after canonical backup/restore. Live mapper sorts root and child order from stored placement, while its flat list follows query row order; equality of that storage list is not the execution-order contract. Fix will compare exact node maps plus root/child ordering and restored Focus. The large-text warning test collapsed after expansion while retaining a lower scroll position; fix will explicitly scroll back to the control. No production behavior was changed to mask either failure.

Only the emulator was connected. Final phone update remains pending reconnection; verify serial before install -r. No phone inspection, automated tests, reset, uninstall or clear occurred.

## Workflow trial slice three

Startup characters: unknown. Initial bundled reads were truncated before a reliable prospective counter was established. This is a measurement failure, not evidence of savings. Scope was recovered with bounded source reads and scoped Graphify queries; graph truncation was resolved using specific symbols and live source. The three-slice trial cannot establish token/time savings; retain progressive context loading and affected-check reuse because they support reviewable work, and record counts before retrieval in any future measurement. Previous slices: [PR137 evidence](REVIEWED_TARGET_BODIES-2026-10-23.md) and [PDF suggestions](PDF_TARGET_SUGGESTIONS-2026-10-23.md).

## Final checks and evidence reuse

All Gradle commands used Android Studio JBR21 (`$env:JAVA_HOME='C:\Program Files\Android\Android Studio\jbr'`) from the repository root, serialized with app inputs frozen:

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*PdfBodySuggestionsTest' --tests '*PdfTargetSuggestionsTest' --tests '*PatternTextParserTest' --tests '*CreateGuideFromPdfUseCaseTest' --tests '*ParsedPatternMapperTest' --tests '*DraftEditorViewModelTest' --tests '*TargetBodyTest' --tests '*TargetBodyBackupTest' --tests '*ManualTargetBackupTest' :app:assembleDebug :app:assembleDebugAndroidTest :app:lint
.\gradlew.bat :app:testDebugUnitTest --tests '*GuideBackupJsonCodecTest' :app:assembleDebugAndroidTest
.\gradlew.bat :app:lint
.\gradlew.bat :app:assembleDebugAndroidTest :app:lint
```

Results: first pass 106 tests / 9 suites; additional old-data JSON pass 9 / 1, build successful in 11s. The two subsequent lint passes succeeded in 31s and 22s, with 0 errors / 53 warnings. No production inputs changed after the first passing units/build/lint. Only the two instrumentation test files changed; therefore production evidence and the six unchanged native cases were reused. No full-suite or schema migration run is claimed: schema/persistence implementation is unchanged.

Native commands (each installation targeted verified `emulator-5554`):

```powershell
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -e class 'com.macareen.stitchbook2.data.parsing.PdfTargetBodyWorkflowTest,com.macareen.stitchbook2.data.parsing.PdfTargetWorkflowTest,com.macareen.stitchbook2.feature.draft.ManualTargetDialogTest,com.macareen.stitchbook2.feature.draft.DraftEditorScreenTest' com.macareen.stitchbook2.test/androidx.test.runner.AndroidJUnitRunner
# After each test-only rebuild, install the test APK again, then rerun affected classes:
adb -s emulator-5554 shell am instrument -w -e class 'com.macareen.stitchbook2.data.parsing.PdfTargetBodyWorkflowTest,com.macareen.stitchbook2.feature.draft.DraftEditorScreenTest' com.macareen.stitchbook2.test/androidx.test.runner.AndroidJUnitRunner
```

Second native attempt: 9 tests / 16.105s, 2 failures. Random Room node IDs exposed the same flat-list assumption earlier at the pre/post-save instruction assertion. The first UI test correction also missed scrolling Show less into view *before* tapping; tapping offscreen did not collapse it. Final correction compares exact ordered instruction traversal through root/child IDs and scrolls both toggles before assertions. Restored old/new revision metadata, root IDs, exact node maps (including child order), and restored Focus body position/observations are asserted. No production changes masked these failures.

Final native run: **all 9 tests passed / 15.182s**, including both manual and suggested PDF-body paths and all seven editor cases. Together with the six unchanged native cases passed on the same production APK, all **15 affected native cases** have current evidence. Coverage includes real synthetic PDF extraction, M-size resolution, import without targets, full body/condition review, published-old-work preservation, cycle navigation/Previous/Jump/reload/reached, revision saved-place recovery, piece isolation, JSON/Markdown/Room restore, Cancel and large-text UI. Synthetic fidelity is not real-pattern certification.

The final 180% light editor capture was pulled from the emulator and visually inspected: warning text and expansion action, two-line guide title and editor actions are legible and fit. The existing large-text dark dialog/controls coverage passed unchanged. Capture remains ignored at `app/build/reports/pdf-body-editor-large.png`.

Final changed-app manifest: `app/build/reports/pdf-body-snapshot.json`, 9 files, SHA256 `550e2038b19e50769b8246576a69bc277c7e22937f4ca1c396a64ccda40d6fa0`, base `85a94cbdf0d0718b3eeb863f339376ef674a8da4`. Production APK SHA256 `37126c00eaafb0dc565177bed70bbbdace286b55c5ff96ba4aeb6a26df0ed624`.

Graphify refreshed twice because the native tests changed after the first refresh. Final ` .\scripts\refresh-graphify.ps1`: 2 re-extracted / 443 cached, **6,907 nodes / 22,943 edges / 265 communities**. Local code-only, no labels/cloud/HTML; no indexed input changed afterward. Initial refresh reported partial Kotlin extraction for DraftEditorViewModel; XML/build and earlier Kotlin coverage limits remain. Missing graph edges are not behavioral evidence.

Final scope/privacy/craft/data-ownership review and `git diff --check` passed (existing LF/CRLF advisories). The automatic approval reviewer rejected optional `git reset --soft main` normalization due branch-history/index mutation risk. No reset happened; the feature retains the verified equal-tree PR137 base. Publication uses ordinary review/squash with exact head verification instead. No permission or history workaround was attempted.
