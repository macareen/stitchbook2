$ErrorActionPreference = 'Stop'
$runner = Join-Path $PSScriptRoot 'graphify.ps1'

# Reuse AST caches but rescan membership, including additions and deletions.
# Explicit code-only/no-label flags keep every refresh local and free of LLM calls.
& $runner extract . --code-only --max-workers 2
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
& $runner cluster-only . --no-label --no-viz
if ($LASTEXITCODE -ne 0) { exit $LASTEXITCODE }
Write-Warning 'Large graph: the optional HTML view may be slow; prefer scoped CLI queries.'
& $runner export html --node-limit 10000
exit $LASTEXITCODE
