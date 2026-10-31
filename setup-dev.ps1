param(
    [string]$ModsDirectory = 'C:\Users\mjohn\AppData\Roaming\ModrinthApp\profiles\JohntySMP\mods'
)
$ErrorActionPreference = 'Stop'
$devDependencies = @(
    'accessories-neoforge-1.1.0-beta.53+1.21.1.jar',
    'owo-lib-neoforge-0.12.15.5-beta.1+1.21.jar',
    'curios-neoforge-9.5.1+1.21.1.jar',
    'caelus-neoforge-7.0.1+1.21.1.jar',
    'elytraslot-neoforge-9.0.2+1.21.1.jar',
    'tfmg-1.21.1-1.3.2a-community.jar',
    'sodium-neoforge-0.8.13+mc1.21.1.jar',
    'sodium-extra-neoforge-0.9.4+mc1.21.1.jar',
    'reeses-sodium-options-neoforge-2.2.4+mc1.21.1.jar',
    'ImmediatelyFast-NeoForge-1.6.14+1.21.1.jar',
    'entityculling-neoforge-1.11.3-mc1.21.1.jar',
    'jei-1.21.1-neoforge-19.64.0.455.jar'
)
$devDestination = Join-Path $PSScriptRoot 'dev-libs'
foreach ($dependency in $devDependencies) {
    if (-not (Test-Path -LiteralPath (Join-Path $ModsDirectory $dependency))) {
        throw "Missing development dependency: $dependency in $ModsDirectory"
    }
}
New-Item -ItemType Directory -Path $devDestination -Force | Out-Null
# Pin the optional compile/runtime integration, including on profiles without it installed.
$transmissionName = 'createtransmission-1.2.2+neoforge-create6-1.21.1.jar'
$transmissionJar = Join-Path $devDestination $transmissionName
$transmissionSha1 = '60e020b39a243e4eca5d15e3c9299fcdf8ed20d2'
if (-not (Test-Path -LiteralPath $transmissionJar) -or
    (Get-FileHash -LiteralPath $transmissionJar -Algorithm SHA1).Hash -ne $transmissionSha1) {
    $download = "$transmissionJar.download"
    try {
        Invoke-WebRequest -Uri 'https://cdn.modrinth.com/data/QFCkBuIh/versions/dOsq88jX/createtransmission-1.2.2%2Bneoforge-create6-1.21.1.jar' -OutFile $download
        if ((Get-FileHash -LiteralPath $download -Algorithm SHA1).Hash -ne $transmissionSha1) {
            throw 'Create Transmission download hash mismatch'
        }
        Move-Item -LiteralPath $download -Destination $transmissionJar -Force
    } finally {
        if (Test-Path -LiteralPath $download) { Remove-Item -LiteralPath $download }
    }
}
foreach ($dependency in $devDependencies) {
    Copy-Item -LiteralPath (Join-Path $ModsDirectory $dependency) -Destination $devDestination -Force
}
$devMods = Join-Path $PSScriptRoot 'run\mods'
New-Item -ItemType Directory -Path $devMods -Force | Out-Null
# Sodium's bootstrap JAR must enter NeoForge through normal mod discovery.
Copy-Item -LiteralPath (Join-Path $devDestination 'sodium-neoforge-0.8.13+mc1.21.1.jar') -Destination $devMods -Force
Write-Host 'Development integration mods copied to dev-libs. Reload Gradle in IntelliJ, then Debug Client.'
