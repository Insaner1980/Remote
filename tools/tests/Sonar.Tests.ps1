#Requires -Version 7.0
$ErrorActionPreference = 'Stop'
$tempRoot = Join-Path ([IO.Path]::GetTempPath()) ('remote-sonar-' + [guid]::NewGuid().ToString('N'))
$originalToken = $env:SONAR_TOKEN
$originalHome = $env:ANDROID_CHECK_HOME
$pwsh = (Get-Command pwsh -CommandType Application | Select-Object -First 1).Source

function Assert-True([bool]$Condition, [string]$Message) {
    if (-not $Condition) { throw $Message }
}

function Invoke-Fixture([int]$ExpectedExit, [string[]]$ScriptArgs) {
    $output = & $pwsh -NoProfile -File (Join-Path $tempRoot 'tools/sonar.ps1') @ScriptArgs 2>&1 | Out-String
    Assert-True ($LASTEXITCODE -eq $ExpectedExit) "Expected exit $ExpectedExit, got $LASTEXITCODE. $output"
    return $output
}

try {
    $runtime = & (Join-Path $PSScriptRoot '../ResolveAndroidCheck.ps1') 'tools/CheckRuntime.psm1'
    New-Item -ItemType Directory -Path (Join-Path $tempRoot 'tools') | Out-Null
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../sonar.ps1') -Destination (Join-Path $tempRoot 'tools/sonar.ps1')
    Copy-Item -LiteralPath (Join-Path $PSScriptRoot '../ResolveAndroidCheck.ps1') -Destination (Join-Path $tempRoot 'tools/ResolveAndroidCheck.ps1')
    $env:ANDROID_CHECK_HOME = Join-Path $tempRoot 'Android check installation'
    $runtimeTools = Join-Path $env:ANDROID_CHECK_HOME 'tools'
    New-Item -ItemType Directory -Path $runtimeTools -Force | Out-Null
    Copy-Item -LiteralPath $runtime -Destination (Join-Path $runtimeTools 'CheckRuntime.psm1')
    $gradle = Join-Path $tempRoot 'gradlew.bat'
    Set-Content -LiteralPath $gradle -Value @('@echo off', 'echo invoked>invoked.txt', 'echo %*>arguments.txt', 'echo %SONAR_TOKEN%', 'exit /b 0')
    $env:SONAR_TOKEN = 'sonar-fixture-redaction-value'
    $plan = Invoke-Fixture 0 @('-PlanOnly')
    Assert-True ($plan.Contains($tempRoot)) 'Plan must resolve the root from its script location.'
    Invoke-Fixture 2 @() | Out-Null
    Assert-True (-not (Test-Path (Join-Path $tempRoot 'invoked.txt'))) 'PlanOnly and missing consent must not start Gradle.'
    $env:SONAR_TOKEN = ''
    Invoke-Fixture 2 @('-AllowExternalUpload') | Out-Null
    Assert-True (-not (Test-Path (Join-Path $tempRoot 'invoked.txt'))) 'Missing token must not start Gradle.'
    $env:SONAR_TOKEN = 'sonar-fixture-redaction-value'
    $env:ANDROID_CHECK_HOME = Join-Path $tempRoot 'missing installation'
    $output = Invoke-Fixture 2 @('-AllowExternalUpload')
    Assert-True ($output -match 'Set ANDROID_CHECK_HOME') 'Missing runtime must explain installation configuration.'
    Assert-True (-not (Test-Path (Join-Path $tempRoot 'invoked.txt'))) 'Missing runtime must not start Gradle.'
    $env:ANDROID_CHECK_HOME = Join-Path $tempRoot 'Android check installation'
    $output = Invoke-Fixture 0 @('-AllowExternalUpload')
    $arguments = Get-Content -Raw -LiteralPath (Join-Path $tempRoot 'arguments.txt')
    Assert-True ($arguments -match '\bsonar\b' -and $arguments -match '-Dsonar\.qualitygate\.wait=true') 'Analysis must wait for the quality gate.'
    Assert-True ($arguments -notmatch 'connected|AndroidTest|emulator') 'Sonar must not request device or emulator tests.'
    $report = Get-Content -Raw -LiteralPath (Join-Path $tempRoot 'reports/sonar.txt')
    Assert-True ($output -notmatch $env:SONAR_TOKEN -and $report -notmatch $env:SONAR_TOKEN) 'Token must be redacted from console and report.'
    Assert-True ($report -match '\[REDACTED_SECRET\]') 'Fixture token was not captured for redaction testing.'

    Set-Content -LiteralPath $gradle -Value @('@echo off', 'echo QUALITY GATE STATUS: FAILED', 'exit /b 1')
    Invoke-Fixture 1 @('-AllowExternalUpload') | Out-Null
    Set-Content -LiteralPath $gradle -Value @('@echo off', 'echo Analysis failed', 'exit /b 1')
    Invoke-Fixture 2 @('-AllowExternalUpload') | Out-Null
    Set-Content -LiteralPath $gradle -Value @('@echo off', 'powershell.exe -NoProfile -Command "Start-Sleep -Seconds 30"')
    $output = Invoke-Fixture 2 @('-AllowExternalUpload', '-GradleTimeoutSeconds', '1')
    Assert-True ($output -match 'timed out') 'Timeout must be reported as a technical error.'
    Write-Output 'Sonar wrapper tests passed.'
} finally {
    $env:SONAR_TOKEN = $originalToken
    $env:ANDROID_CHECK_HOME = $originalHome
    if (Test-Path -LiteralPath $tempRoot) {
        $resolved = (Resolve-Path -LiteralPath $tempRoot).Path
        $tempParent = [IO.Path]::GetFullPath([IO.Path]::GetTempPath()).TrimEnd('\', '/') + [IO.Path]::DirectorySeparatorChar
        if (-not $resolved.StartsWith($tempParent, [StringComparison]::OrdinalIgnoreCase) -or
            (Split-Path -Leaf $resolved) -notlike 'remote-sonar-*') {
            throw 'Unexpected Sonar test cleanup path.'
        }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}
