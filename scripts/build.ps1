param([switch]$Release)
$ErrorActionPreference = 'Stop'
Set-Location (Split-Path $PSScriptRoot -Parent)
if (-not $env:JAVA_HOME -or -not (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) {
    $javaCommand = Get-Command java -ErrorAction Stop
    $env:JAVA_HOME = Split-Path (Split-Path $javaCommand.Source -Parent) -Parent
}
if (-not $env:ANDROID_HOME) { $env:ANDROID_HOME = Join-Path $env:LOCALAPPDATA 'Android\Sdk' }
if ($Release -and -not (Test-Path 'keystore.properties')) { throw 'Create keystore.properties using docs/INSTALL.he.md before building a release.' }
$buildTask = if ($Release) { ':app:assembleRelease' } else { ':app:assembleDebug' }
& .\gradlew.bat $buildTask :app:testDebugUnitTest :app:lintDebug --console=plain
if ($LASTEXITCODE -ne 0) { throw 'Build or validation failed.' }
