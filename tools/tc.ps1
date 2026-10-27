#Requires -Version 7.0

[CmdletBinding()]
param(
    [switch]$Full,
    [Alias("List", "ListOnly")]
    [switch]$PlanOnly,
    [switch]$ResolveOnly
)

$ErrorActionPreference = "Stop"
$ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
$ProjectCheckEntryPoint = "C:\Dev\Android-check\tools\InvokeProjectCheck.ps1"
if (-not (Test-Path -LiteralPath $ProjectCheckEntryPoint -PathType Leaf)) {
    Write-Error "ERROR/2: Android-check entry point is missing: $ProjectCheckEntryPoint" -ErrorAction Continue
    exit 2
}

# The shared manifest engine does not forward -Full to its test phase.
# Validate the normal plan first, then run both task groups for a full check.
$global:LASTEXITCODE = 2
& $ProjectCheckEntryPoint -ProjectCheckCommand "test-check" -Root $ProjectRoot -ProjectId "remote" `
    -PlanOnly:($PlanOnly -or $Full) -ResolveOnly:$ResolveOnly
$checkExitCode = $LASTEXITCODE
if ($checkExitCode -ne 0 -or $ResolveOnly -or -not $Full) {
    exit $checkExitCode
}

try {
    $manifest = Get-Content -Raw -LiteralPath (Join-Path $ProjectRoot "config/android-check.json") | ConvertFrom-Json
    $tasks = @($manifest.modules | ForEach-Object { $_.tasks.test.path }) + @($manifest.deviceTestTasks)
    if (@($manifest.deviceTestTasks).Count -eq 0) {
        throw "No device test tasks are configured."
    }
    Write-Output "Full test tasks: $($tasks -join ', ')"
    Write-Output "Device requirement: an authorized Android device or emulator with API 37 or newer."
    if ($PlanOnly) { exit 0 }

    Push-Location -LiteralPath $ProjectRoot
    try {
        # Keep the Gradle exit code and let Gradle report unavailable devices.
        $PSNativeCommandUseErrorActionPreference = $false
        & (Join-Path $ProjectRoot "gradlew.bat") @tasks --continue --console=plain --no-daemon
        $checkExitCode = $LASTEXITCODE
    }
    finally {
        Pop-Location
    }
}
catch {
    Write-Error "ERROR/2: $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}
exit $checkExitCode
