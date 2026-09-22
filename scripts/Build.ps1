param([switch]$Mirror, [string[]]$Tasks = @(':core:test', ':app:testDebugUnitTest', ':app:lintDebug', ':app:assembleDebug'))
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
Push-Location -LiteralPath $projectRoot
try {
    $env:GRADLE_USER_HOME = Join-Path $projectRoot '.tools\gradle-home'
    $env:ANDROID_USER_HOME = Join-Path $projectRoot '.tools\android-user'
    New-Item -ItemType Directory -Force -Path $env:ANDROID_USER_HOME | Out-Null
    $bundledGradle = Join-Path $projectRoot '.tools\gradle-8.13\bin\gradle.bat'
    $gradleCommand = if (Test-Path -LiteralPath $bundledGradle) { $bundledGradle } else { Join-Path $projectRoot 'gradlew.bat' }
    $gradleArgs = @('--no-daemon', '--no-watch-fs', '--console', 'plain')
    if ($Mirror) { $gradleArgs += '-PuseMirror=true' }
    & $gradleCommand @gradleArgs @Tasks
    $buildExit = $LASTEXITCODE
} finally { Pop-Location }
exit $buildExit
