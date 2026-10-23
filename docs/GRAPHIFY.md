# Repository code graph

Graphify is repository-scoped development tooling, independent of the Android app.
Upstream: https://github.com/Graphify-Labs/graphify. The official PyPI distribution
is `graphifyy`; this repository pins version 0.9.94. The upstream Codex skill is
installed under `.codex/skills/graphify/`, and `AGENTS.md` makes graph-first code
search and end-of-chunk refresh a persistent requirement for future sessions.

## Setup on a new checkout

From the repository root, with Python 3.12 and uv installed:

```powershell
uv --cache-dir .graphify-cache venv .graphify-venv --python 3.12
uv --cache-dir .graphify-cache pip install --python .graphify-venv/Scripts/python.exe -r scripts/graphify-requirements.txt
.\scripts\refresh-graphify.ps1
```

Installation downloads Python packages. Indexing and querying run locally without
an API key, account, cloud service, or subscription. Do not enable semantic LLM
extraction, cloud upload, or global registration for this repository by default.
The environment, package cache, and generated `graphify-out/` are ignored by Git;
rebuild on a fresh checkout rather than committing these generated artifacts.

## Search workflow

```powershell
.\scripts\graphify.ps1 query "PatternSizes CreateGuideFromPdfUseCase" --budget 1500
.\scripts\graphify.ps1 explain "PatternSizes"
.\scripts\graphify.ps1 path "PatternSizes" "CreateGuideFromPdfUseCase"
.\scripts\graphify.ps1 affected "PatternSizes"
```

Begin every code search with a scoped graph query. Use returned paths and locations
to read the current source and verify claims before editing. Narrow ambiguous names
with file paths; narrow or raise the budget for truncated results. When the graph
has no coverage, is incomplete, or cannot find a symbol, record that limitation
and use targeted `rg`/file reads. It is a navigation index, not proof of behavior.

## Refresh after substantial work

Run `.\scripts\refresh-graphify.ps1` after each substantial coherent chunk of work
and before handoff. It rescans code with cached AST extraction, reclusters without
LLM labeling, regenerates the report and HTML, and stops on command failure.
Refresh earlier after branch switches or changed source if search results are stale.
Report refresh failures in `docs/HANDOFF.md`; do not claim the graph is current.
No Git hooks or background watcher are required: the agent checkpoint rule also
covers uncommitted work. Do not rebuild concurrently with source edits.

Outputs:

- `graphify-out/graph.json`: persistent, queryable local graph.
- `graphify-out/GRAPH_REPORT.md`: architecture summary.
- `graphify-out/graph.html`: optional interactive overview; large graphs may be slow.

`.graphifyignore` excludes build output, local agent configuration, tooling,
schemas and common private/binary assets. The normal Git ignore rules also apply.
Only code is extracted; documentation/media semantic extraction is disabled.

## Known coverage limits (initial run, October 23, 2026)

The initial app scan covered 429 code files, producing 6,448 nodes, 21,823 edges
and 219 communities. Android XML resources are unsupported; six files yielded
no symbols (Gradle Kotlin scripts and several top-level Compose/theme values).
The Kotlin grammar reported partial parses for `PatternFolder.kt`,
`GuideFocusViewModel.kt`, `GuideRevisionReviewController.kt`, `ProjectDashboard.kt`,
and `ProjectPiecesViewModel.kt`. These are indexer diagnostics, not Kotlin compiler
errors. Inspect live source and relevant Android tests for behavioral evidence.
