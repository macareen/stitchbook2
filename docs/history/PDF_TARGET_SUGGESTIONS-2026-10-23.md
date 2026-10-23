# Reviewed PDF target suggestions — verification record

2026-10-23. Original synthetic fixtures only; no private patterns, reports or screenshots are committed.

## Scope and snapshot

Started at `codex/manual-target-loops` / `73fce86dcc67d93e49b34351d6d700d6d95db5b8`. Local/remote main was PR135 squash `24ace5fcf829e1da305b510c2c1048706de6fc66`; committed trees matched. The other chat's workflow/documentation/script cleanup and `HANDOFF-through-2026-10-23.md` archive were already pending. They remain outside this slice's named staging. Current publication state belongs in `../HANDOFF.md`.

The final app delta contains 12 files. Ignored manifest `app/build/reports/pdf-target-snapshot.json` lists their SHA256 hashes; manifest SHA256 is `3df05c990ccc3df2f9b7c66d0829d530f6d2254aee12f74bfdbdc600669318e5`. No app source/resources/build files changed during checks. Final Android-test-only edits were followed by the affected APK rebuild, three native cases and incremental lint; production/unit evidence was reused because those inputs did not change.

Room27 and backup6 were verified in live declarations and are unchanged. Suggestions are derived review UI state. Imports never assign targets; accepted targets reuse existing persistence/execution/export behavior. See ARCHITECTURE.md, “Reviewed PDF target suggestions,” for the deliberately small grammar and context restrictions.

## Commands and results

All Gradle runs were serialized, from repository root, using Android Studio JBR21:

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat :app:testDebugUnitTest --tests 'com.macareen.stitchbook2.domain.parsing.*' --tests '*CreateGuideFromPdfUseCaseTest' --tests '*DraftEditorViewModelTest' --tests '*ManualTargetTest' --tests '*ManualTargetBackupTest' :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --max-workers=2
.\gradlew.bat :app:assembleDebugAndroidTest --max-workers=2
.\gradlew.bat :app:lintDebug --max-workers=2
```

- Combined final pass: **184 tests / 16 suites / 0 failures**, both APKs, lintDebug **0 errors / 53 warnings**, 2m34s.
- Android-test-only rebuild after fixture/visual-capture changes: passed, 8s. Incremental final lint: passed, 25s; production/unit analysis reused, affected Android-test analysis reran.
- Initial sandbox Gradle attempt could not open the existing wrapper-cache lock; the approved cache-access rerun executed normally. The first combined run had 183 tests with two failures: prior range tests expected no condition-review warning. Assertions were updated to require the new warnings, retain exact selected ranges/source and verify targets remain null. That run built both APKs but did not complete lint. No assertions/detectors were disabled.

Native checks used only the existing emulator. `adb devices -l` and `adb -s emulator-5554 shell getprop ro.kernel.qemu` verified it (`1`); no physical device was inspected or changed. `adb` below denotes the existing SDK `platform-tools/adb.exe`:

```powershell
adb -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r -e class 'com.macareen.stitchbook2.data.parsing.PdfTargetWorkflowTest,com.macareen.stitchbook2.feature.draft.ManualTargetDialogTest,com.macareen.stitchbook2.data.parsing.PdfGuideWorkflowTest' com.macareen.stitchbook2.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5554 install -r app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5554 shell am instrument -w -r -e class 'com.macareen.stitchbook2.data.parsing.PdfTargetWorkflowTest,com.macareen.stitchbook2.feature.draft.ManualTargetDialogTest#reviewedSuggestionSavesTheSuggestedValue' com.macareen.stitchbook2.test/androidx.test.runner.AndroidJUnitRunner
```

**10 distinct native cases passed.** The first batch passed eight cases and failed the two new fixtures because they attempted to create another active execution in the same scope. The fixtures now use the existing `applyReviewedRevision` workflow, assert the old state/publication is preserved, and export/restore both old/new work. After rebuilding only the test APK, both corrected cases and the affected large-text dialog passed (3 cases, 4.052s). Native runner exit code alone is insufficient; the final output explicitly says `OK (3 tests)`.

Coverage includes selected S/M/L values and source wording; unresolved ranges, fractions, dual units, offsets, dependent/multi-step/simultaneous conditions; separate-line context suppression; manual targets staying absent until explicit save; checkbox/Cancel behavior and stale draft review; actual PDF extraction → size → editor → publication → reviewed revision → Focus observations/cycle/confirmation/Previous/reload → JSON validation/Room restore and Markdown. Existing native workflows cover finite repeats, page continuity, source regions and section replay. The final dark 180%-text review screenshot was visually inspected: text/checkbox/source and target fields are readable, and Save/Cancel remain reachable. The synthetic capture remains ignored under `app/build/reports/`.

No full unit/lint release matrix or migration suite was rerun: parser/editor changes do not alter schema/backup contracts, and affected parsing/execution/backup tests plus real Room export/restore cover this delta. PR135 full evidence remains historical, not a fresh result. No real-pattern semantic certification or dependent/simultaneous target execution is claimed.

## Graph and review

`.\scripts\refresh-graphify.ps1` passed after final indexed edits: **11 re-extracted / 429 cached files; 6,870 nodes / 22,784 edges / 229 communities**. Code-only, no labels/cloud/HTML. The pinned cluster command's `--no-viz` removed the prior generated HTML; there is no current HTML claim. Kotlin/XML/build coverage remains partial; live source/tests were authoritative. No indexed input changed afterward.

Final review checked scope, original wording/provenance, explicit publication/revision behavior, craft neutrality and local data ownership. `git diff --check` passed with only LF→CRLF advisories. A documentation patch using a sentence instead of its complete line was rejected before mutation and corrected. Startup file-character measurement is unknown because early bundled reads were truncated; the three-slice trial records that limit instead of claiming token/time savings.
