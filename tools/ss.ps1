#Requires -Version 7.0

$ProjectCheckCommand = "secret-scan"
$ProjectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot "..")).Path
try {
    $ProjectCheckEntryPoint = & (Join-Path $PSScriptRoot 'ResolveAndroidCheck.ps1') 'tools/InvokeProjectCheck.ps1'
} catch {
    Write-Error "ERROR/2: $($_.Exception.Message)" -ErrorAction Continue
    exit 2
}
$global:LASTEXITCODE = 2
& $ProjectCheckEntryPoint -ProjectCheckCommand $ProjectCheckCommand -Root $ProjectRoot -ProjectId "remote" @args
exit $LASTEXITCODE
