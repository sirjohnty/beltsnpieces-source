$ErrorActionPreference = 'Stop'
$source = Join-Path $PSScriptRoot '..\build036\deps\create-libs'
$dest = Join-Path $PSScriptRoot 'libs'
if (-not (Test-Path -LiteralPath $source)) { throw "Cannot find Create libraries at $source. Extract the project directly into the existing create-bitsnbelts folder." }
New-Item -ItemType Directory -Path $dest -Force | Out-Null
$files = @('create-1.21.1-6.0.10.jar', 'ponder-neoforge-1.0.82+mc1.21.1.jar', 'flywheel-neoforge-1.21.1-1.0.6.jar', 'Registrate-MC1.21-1.3.0+67.jar')
foreach ($f in $files) { $p = Join-Path $source $f; if (-not (Test-Path -LiteralPath $p)) { throw "Missing library: $p" }; Copy-Item -LiteralPath $p -Destination $dest -Force }
Write-Host 'Create libraries copied successfully.'
