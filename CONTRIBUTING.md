# Contributing to Stitchbook

Stitchbook is initially a personal project, but changes should follow a lightweight professional workflow so they remain understandable and reversible.

Read [AGENTS.md](AGENTS.md), [PRODUCT_SPEC.md](PRODUCT_SPEC.md), [ROADMAP.md](ROADMAP.md), and [ARCHITECTURE.md](ARCHITECTURE.md) before changing behavior or structure.

## Workflow

1. **Choose a focused change.** Confirm the relevant roadmap phase and avoid bundling unrelated cleanup or features.
2. **Create a focused branch.** Use a descriptive name such as `feature/project-status` or `fix/counter-reset`.
3. **Inspect before editing.** Read the affected code, tests, Gradle configuration, and nearby documentation.
4. **Make a small change.** Prefer a reviewable vertical slice over broad scaffolding or speculative abstractions.
5. **Build and test.** Run the narrowest useful checks during development and the relevant broader checks before handoff.
6. **Review generated code.** Treat generated or AI-assisted code as untrusted until its behavior, licenses, error paths, privacy impact, and tests have been checked.
7. **Commit descriptively.** Explain the outcome, for example `Add validation for project completion dates`.
8. **Open a pull request when using GitHub.** Summarize behavior, testing, screenshots for UI changes, data/storage implications, and known limitations.

No commit or pull request should include credentials, signing keys, local SDK paths, personal files, pattern PDFs, or build output.

## Build and test

From the repository root on Windows:

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:test
.\gradlew.bat :app:lint
```

Use `./gradlew` instead of `.\gradlew.bat` on macOS or Linux.

For device-dependent behavior:

```powershell
.\gradlew.bat :app:connectedDebugAndroidTest
```

Connected tests require an emulator or physical device. Report every relevant command and result in the pull request or handoff. If a check was skipped, state why.

## Change expectations

- Keep the app buildable at each review boundary.
- Prefer standard Android and Jetpack APIs.
- Add dependencies only for a demonstrated need and explain the tradeoff.
- Keep business logic out of composables.
- Use immutable state and clear Kotlin names.
- Add unit tests for nontrivial domain, calculation, data, migration, import/export, and conflict behavior.
- Add UI or instrumented tests for critical platform interactions and user flows.
- Preserve user access to original and portable content.
- Do not use application-private files as the only long-lived copy of user content; reserve cache for reproducible or temporary data.
- Clearly distinguish recorded values from estimates.
- Check terminology and defaults against knitting, crochet, Tunisian crochet, and loom knitting rather than validating only a knitting path.
- Update documentation when architecture, commands, storage, privacy, or behavior changes.

## Data and migration safety

Room is not a substitute for user-accessible originals and portable records. Do not place an irreplaceable PDF, photo, note, or library record only in a database or cache.

For schema or storage changes:

- Define invariants and failure behavior first.
- Include migration or round-trip tests with representative data.
- Preserve stable IDs and provenance.
- Never silently overwrite user files or resolve conflicts by discarding one side.
- Keep early versioned exports compatible or provide and test a migration path into later full-library backups.
- Ask before a destructive migration, bulk rewrite, or incompatible portable-format change.

## Pull request checklist

- [ ] The change is focused and matches a documented product goal.
- [ ] Current behavior and planned behavior are described honestly.
- [ ] Debug build succeeds.
- [ ] Relevant unit and device tests succeed.
- [ ] Lint succeeds or every existing/new issue is explained.
- [ ] New nontrivial logic has tests.
- [ ] UI changes include previews or screenshots where practical and an accessibility review.
- [ ] No secrets, signing material, local configuration, personal content, or pattern PDFs are included.
- [ ] Data ownership, offline behavior, and file portability remain intact.
- [ ] Documentation is updated where needed.
- [ ] Generated code has been reviewed before merge.
