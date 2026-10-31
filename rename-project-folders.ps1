param([string]$SourceRoot = $PSScriptRoot)
$ErrorActionPreference = 'Stop'
# Run after closing this project in IntelliJ. All saves, checkpoints, recovery
# folders and Git history move with their containing directories unchanged.
$sourceRoot = [IO.Path]::GetFullPath($SourceRoot)
$container = Split-Path -Parent $sourceRoot
$desktopRoot = Split-Path -Parent $container
$targetContainer = Join-Path $desktopRoot 'create-beltsnpieces'
$targetRoot = Join-Path $targetContainer 'beltsnpieces-source'
if ($sourceRoot -eq $targetRoot) {
    Write-Host "Already at $targetRoot"
    exit 0
}
if ((Split-Path -Leaf $desktopRoot) -ne 'Desktop') {
    throw 'Expected the project container directly under Desktop; no folders were moved.'
}
$intermediateRoot = Join-Path $container 'beltsnpieces-source'
foreach ($target in @($intermediateRoot, $targetContainer)) {
    if ((Test-Path -LiteralPath $target) -and $target -ne $sourceRoot -and $target -ne $container) {
        throw "Destination already exists; refusing to merge or overwrite: $target"
    }
}
# Leave the directory being renamed before moving it.
Set-Location -LiteralPath $desktopRoot
[Environment]::CurrentDirectory = $desktopRoot
if ($sourceRoot -ne $intermediateRoot) {
    Move-Item -LiteralPath $sourceRoot -Destination $intermediateRoot
}
if ($container -ne $targetContainer) {
    try { Move-Item -LiteralPath $container -Destination $targetContainer }
    catch {
        # Restore the original source path if the outer folder was locked.
        if ($sourceRoot -ne $intermediateRoot) {
            Move-Item -LiteralPath $intermediateRoot -Destination $sourceRoot
        }
        throw
    }
}
$workspace = Join-Path $targetRoot '.idea\workspace.xml'
if (Test-Path -LiteralPath $workspace) {
    $backup = Join-Path $targetRoot 'build\rename-backup'
    New-Item -ItemType Directory -Path $backup -Force | Out-Null
    Copy-Item -LiteralPath $workspace -Destination (Join-Path $backup 'workspace.xml')
    $text = [IO.File]::ReadAllText($workspace)
    foreach ($pair in @(
        @($sourceRoot, $targetRoot),
        @($sourceRoot.Replace('\','/'), $targetRoot.Replace('\','/')),
        @($sourceRoot.Replace('\','\\'), $targetRoot.Replace('\','\\'))
    )) { $text = $text.Replace($pair[0], $pair[1]) }
    # Repair live debugger source URLs without rewriting Git's deleted-file history.
    $oldPackage = @('bits','n','belts') -join ''
    $text = [regex]::Replace($text,
        ('(<url>[^<]*/com/sirjohnty/)' + [regex]::Escape($oldPackage) + '/'),
        '\${1}beltsnpieces/')
    [IO.File]::WriteAllText($workspace, $text)
}
Write-Host "Renamed project: $targetRoot"
Write-Host 'Reopen this folder in IntelliJ and refresh Gradle to regenerate absolute launch paths.'
