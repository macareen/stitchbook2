# Handoff: progress per project (step 2c), work in progress

Branch `wip/progress-per-project`. **Not merged and not ready to merge.**

## Done (main code compiles)
- Schema v20 (`MIGRATION_19_20`): `executions.project_id`, filled from each guide's project. `active_executions` is rebuilt with key (`guide_id`, `project_key`), where "" means a guide opened on its own. `20.json` is exported.
- DAO and repository: `createExecution(guideId, revisionId, projectId = null)`, `getActiveExecution(guideId, projectId = null)`, `hasActiveExecutionAnywhere(guideId)`. Transitions take the key from `execution.projectId`.
- The Focus and Draft editor routes take an optional `?projectId=`. Project detail, assisted import, the Draft editor's Start, and Home resume pass it. `ResumeGuide.projectId` was added, and Home's `onResumeGuide` now takes a `ResumeGuide`.

## Left to do
1. Update the four unit-test fakes of `ExecutionRepository` (DraftEditor, GuideFocus, Home, ProjectDetail tests) so their overrides take the new `projectId` parameter. Unit tests don't compile until this is done.
2. Migration tests:
   - Bump `CURRENT_SCHEMA_VERSION` to 20.
   - Add `MIGRATION_19_20` to the 14 → current helper test.
   - Add a 19 → 20 test: an active execution survives with `project_key` equal to the guide's project, and `executions.project_id` is filled in.
3. A device test: one pattern guide used by two projects keeps separate progress.
4. Run the unit, lint and connected tests. Update docs (AGENTS/ARCHITECTURE/README schema 20). Then PR and merge.

`gradle/libs.versions.toml` (AGP 9.3.1 → 9.3.3) is the user's own change and is intentionally not committed.
