param(
    [Parameter(ValueFromRemainingArguments = $true)]
    [string[]] $GraphifyArguments
)

$ErrorActionPreference = 'Stop'
$repoRoot = Split-Path -Parent $PSScriptRoot
$python = Join-Path $repoRoot '.graphify-venv/Scripts/python.exe'
if (-not (Test-Path -LiteralPath $python)) {
    throw 'Graphify environment missing. Follow docs/GRAPHIFY.md to install it.'
}
Push-Location $repoRoot
try {
    & $python -m graphify @GraphifyArguments
    $graphifyExitCode = $LASTEXITCODE
} finally {
    Pop-Location
}
exit $graphifyExitCode
