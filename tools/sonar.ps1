#Requires -Version 7.0
[CmdletBinding(PositionalBinding = $false)]
param(
    [switch]$PlanOnly,
    [switch]$AllowExternalUpload,
    [ValidateRange(1, 86400)][int]$GradleTimeoutSeconds = 3600
)

$ErrorActionPreference = 'Stop'
$projectRoot = (Resolve-Path -LiteralPath (Join-Path $PSScriptRoot '..')).Path
$gradleArguments = @(
    '--no-daemon', '--no-configuration-cache', '--console=plain',
    'sonar', '-Dsonar.qualitygate.wait=true'
)
if ($PlanOnly) {
    Write-Output @(
        'sonar',
        "  - root: $projectRoot",
        '  - project: Insaner1980_Remote (organization: insaner1980)',
        '  - server: https://sonarcloud.io',
        '  - local working tree -> SonarCloud project main branch',
        '  - debug build, JVM tests and coverage report',
        '  - includes existing Android test coverage data without running device tests',
        "  - Gradle: $($gradleArguments -join ' ')",
        '  - uploads source and analysis metadata; waits for the quality gate',
        '  - report: reports/sonar.txt',
        '  - actual execution requires -AllowExternalUpload and SONAR_TOKEN'
    )
    exit 0
}
if (-not $AllowExternalUpload) {
    Write-Error 'EXTERNAL_UPLOAD_APPROVAL_REQUIRED: use -AllowExternalUpload after reviewing sonar -PlanOnly.' -ErrorAction Continue
    exit 2
}
if ([string]::IsNullOrWhiteSpace($env:SONAR_TOKEN)) {
    Write-Error 'SONAR_TOKEN_MISSING: run sonar through the configured PowerShell profile.' -ErrorAction Continue
    exit 2
}

$reportPath = Join-Path $projectRoot 'reports/sonar.txt'
try {
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $reportPath) | Out-Null
    Set-Content -LiteralPath $reportPath -Encoding utf8 -Value @(
        'sonar', "Root: $projectRoot", 'Project: Insaner1980_Remote', "Started: $(Get-Date -Format o)"
    )
    $runtime = & (Join-Path $PSScriptRoot 'ResolveAndroidCheck.ps1') 'tools/CheckRuntime.psm1'
    Import-Module $runtime -Force -ErrorAction Stop
    $result = Invoke-ManagedProcess -Executable (Join-Path $projectRoot 'gradlew.bat') `
        -Arguments $gradleArguments -WorkingDirectory $projectRoot -TimeoutSeconds $GradleTimeoutSeconds
    foreach ($stream in @($result.StandardOutput, $result.StandardError)) {
        if (-not [string]::IsNullOrWhiteSpace($stream)) {
            $safeOutput = $stream.Replace($env:SONAR_TOKEN, '[REDACTED_SECRET]')
            Add-Content -LiteralPath $reportPath -Encoding utf8 -Value $safeOutput
            Write-Output $safeOutput
        }
    }
    if ($result.TimedOut) {
        throw "Sonar analysis timed out after $GradleTimeoutSeconds seconds."
    }
    if ($result.ExitCode -ne 0) {
        if (($result.StandardOutput + "`n" + $result.StandardError) -match '(?m)^QUALITY GATE STATUS: FAILED\b') {
            $summary = 'sonar: FINDINGS - analysis completed, but the SonarCloud quality gate failed.'
            Add-Content -LiteralPath $reportPath -Encoding utf8 -Value $summary
            Write-Output $summary
            exit 1
        }
        throw "Sonar analysis failed (exit $($result.ExitCode))."
    }
    Write-Output "sonar: analysis and quality gate passed -> $reportPath"
    exit 0
} catch {
    $message = $_.Exception.Message.Replace($env:SONAR_TOKEN, '[REDACTED_SECRET]')
    Add-Content -LiteralPath $reportPath -Encoding utf8 -Value "ERROR: $message" -ErrorAction SilentlyContinue
    Write-Error $message -ErrorAction Continue
    exit 2
}
