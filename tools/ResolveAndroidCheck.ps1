#Requires -Version 7.0
param([Parameter(Mandatory)][string]$RelativePath)

$installationRoot = if ([string]::IsNullOrWhiteSpace($env:ANDROID_CHECK_HOME)) {
    Join-Path $PSScriptRoot '../../Android-check'
} else {
    $env:ANDROID_CHECK_HOME
}
$requiredPath = Join-Path $installationRoot $RelativePath
if (-not (Test-Path -LiteralPath $requiredPath -PathType Leaf)) {
    throw "Android-check file is missing: $requiredPath. Set ANDROID_CHECK_HOME to the Android-check installation directory."
}
(Resolve-Path -LiteralPath $requiredPath).Path
