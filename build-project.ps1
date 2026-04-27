$ErrorActionPreference = 'Stop'

$projectRoot = Split-Path -Parent $MyInvocation.MyCommand.Path
$gradleHome = Join-Path $projectRoot '.gradle-home'

if (-not (Test-Path -LiteralPath $gradleHome)) {
    New-Item -ItemType Directory -Path $gradleHome | Out-Null
}

$env:GRADLE_USER_HOME = $gradleHome

if (-not $env:ANDROID_HOME -and -not $env:ANDROID_SDK_ROOT) {
    throw "Set ANDROID_HOME or ANDROID_SDK_ROOT before running this script."
}

& (Join-Path $projectRoot 'gradlew.bat') @args
