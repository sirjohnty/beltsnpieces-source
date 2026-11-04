param(
    # Optional folder containing the four dependency JARs when restoring a checkout.
    [string]$SourceDirectory
)

$ErrorActionPreference = 'Stop'
$dest = Join-Path $PSScriptRoot 'libs'
$files = @('create-1.21.1-6.0.10.jar', 'ponder-neoforge-1.0.82+mc1.21.1.jar', 'flywheel-neoforge-1.21.1-1.0.6.jar', 'Registrate-MC1.21-1.3.0+67.jar')
if ($SourceDirectory) {
    $source = (Resolve-Path -LiteralPath $SourceDirectory).Path
    foreach ($f in $files) {
        if (-not (Test-Path -LiteralPath (Join-Path $source $f) -PathType Leaf)) {
            throw "Missing library in source folder: $f"
        }
    }
    New-Item -ItemType Directory -Path $dest -Force | Out-Null
    if ($source.TrimEnd('\') -ine $dest.TrimEnd('\')) {
        foreach ($f in $files) {
            Copy-Item -LiteralPath (Join-Path $source $f) -Destination $dest -Force
        }
    }
}
foreach ($f in $files) {
    if (-not (Test-Path -LiteralPath (Join-Path $dest $f) -PathType Leaf)) {
        throw "Missing library: $f. Restore the dependency JARs into '$dest', or run this script with -SourceDirectory pointing to a folder containing all four JARs."
    }
}
Write-Host 'All four Create libraries are ready in libs.'
