#Requires -Version 7.0
$ErrorActionPreference = 'Stop'
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('remote-check-launchers-' + [guid]::NewGuid().ToString('N'))
$originalHome = $env:ANDROID_CHECK_HOME
$pwsh = (Get-Command pwsh -CommandType Application | Select-Object -First 1).Source

try {
    $fixtureTools = Join-Path $tempRoot 'Android check installation/tools'
    New-Item -ItemType Directory -Path $fixtureTools -Force | Out-Null
    $env:ANDROID_CHECK_HOME = Split-Path $fixtureTools
    $entryPoint = Join-Path $fixtureTools 'InvokeProjectCheck.ps1'
    Set-Content -LiteralPath $entryPoint -Value @'
param($ProjectCheckCommand, $Root, $ProjectId, [switch]$PlanOnly, [switch]$ResolveOnly)
Write-Output "${ProjectCheckCommand}|${ProjectId}|${Root}|${PlanOnly}|${ResolveOnly}"
exit 7
'@
    $launchers = Get-ChildItem (Join-Path $PSScriptRoot '..') -Filter '*.ps1' |
        Where-Object { (Get-Content -Raw -LiteralPath $_.FullName).Contains('tools/InvokeProjectCheck.ps1') }
    if ($launchers.Count -ne 16) { throw "Expected 16 check launchers, found $($launchers.Count)." }
    foreach ($launcher in $launchers) {
        $output = & $pwsh -NoProfile -File $launcher.FullName -PlanOnly -ResolveOnly 2>&1 | Out-String
        if ($LASTEXITCODE -ne 7 -or $output -notmatch '\|remote\|.*\|True\|True') {
            throw "Launcher did not preserve arguments or exit code: $($launcher.Name). $output"
        }
    }
    $env:ANDROID_CHECK_HOME = Join-Path $tempRoot 'missing installation'
    foreach ($launcher in $launchers) {
        $output = & $pwsh -NoProfile -File $launcher.FullName 2>&1 | Out-String
        if ($LASTEXITCODE -ne 2 -or $output -notmatch 'Set ANDROID_CHECK_HOME') {
            throw "Missing installation must fail clearly: $($launcher.Name). $output"
        }
    }
    Write-Output 'All 16 check launchers passed relocated-installation and missing-file checks.'
} finally {
    $env:ANDROID_CHECK_HOME = $originalHome
    if (Test-Path -LiteralPath $tempRoot) {
        $resolved = (Resolve-Path -LiteralPath $tempRoot).Path
        $tempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
        if (-not $resolved.StartsWith($tempParent, [StringComparison]::OrdinalIgnoreCase) -or
            (Split-Path -Leaf $resolved) -notlike 'remote-check-launchers-*') {
            throw 'Unexpected launcher test cleanup path.'
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
