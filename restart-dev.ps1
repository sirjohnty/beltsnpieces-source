param([Parameter(Mandatory=$true)][int]$GameProcessId)
$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$gameProcess = Get-Process -Id $GameProcessId -ErrorAction SilentlyContinue
if ($gameProcess) { $gameProcess.WaitForExit() }
$env:GRADLE_USER_HOME = Join-Path $env:USERPROFILE '.gradle'
# Use the established Java 21 runtime even if another java.exe is first on PATH.
$java21 = 'C:\Program Files\Eclipse Adoptium\jdk-21.0.12.101-hotspot'
if (-not (Test-Path -LiteralPath (Join-Path $java21 'bin\java.exe'))) { throw "Java 21 installation missing: $java21" }
$env:JAVA_HOME = $java21
# Recompile only; preserve all versioned build checkpoints.
& (Join-Path $PSScriptRoot 'gradlew.bat') classes prepareClientRun -PskipQueuedClear *> (Join-Path $PSScriptRoot 'run\restart-build.log')
if ($LASTEXITCODE -ne 0) { throw 'Development compilation failed. See run/restart-build.log.' }
# Gradle supplies the complete development launch configuration, including mods.
# Its debug mode exposes a loopback-only JVM debugger for IntelliJ to attach to.
& (Join-Path $PSScriptRoot 'gradlew.bat') runClient -PdevRestartDebug -PskipQueuedClear *> (Join-Path $PSScriptRoot 'run\restart-client.log')
if ($LASTEXITCODE -ne 0) { throw 'Development launch failed. See run/restart-client.log.' }
