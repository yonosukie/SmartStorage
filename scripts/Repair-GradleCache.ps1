param([string]$GradleVersion = '8.13')
$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path -Parent $PSScriptRoot
$allowedRoot = [IO.Path]::GetFullPath((Join-Path $projectRoot '.tools\gradle-home\caches'))
$cacheRoot = [IO.Path]::GetFullPath((Join-Path $allowedRoot "$GradleVersion\transforms"))
if (-not $cacheRoot.StartsWith($allowedRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Invalid cache root' }
if (-not (Test-Path -LiteralPath $cacheRoot)) { exit 0 }
# Run only after Gradle has exited. Do not overwrite existing canonical cache entries.
Get-ChildItem -LiteralPath $cacheRoot -Directory | Where-Object { $_.Name -match '^[a-f0-9]{32}-[a-f0-9-]{36}$' } | ForEach-Object {
    $cacheSource = [IO.Path]::GetFullPath($_.FullName)
    $cacheTarget = [IO.Path]::GetFullPath((Join-Path $cacheRoot $_.Name.Substring(0,32)))
    if (-not $cacheSource.StartsWith($cacheRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase) -or
        -not $cacheTarget.StartsWith($cacheRoot + [IO.Path]::DirectorySeparatorChar, [StringComparison]::OrdinalIgnoreCase)) { throw 'Unexpected cache path' }
    if ((Test-Path -LiteralPath (Join-Path $cacheSource 'metadata.bin')) -and (Test-Path -LiteralPath (Join-Path $cacheSource 'results.bin')) -and -not (Test-Path -LiteralPath $cacheTarget)) {
        Move-Item -LiteralPath $cacheSource -Destination $cacheTarget
        Write-Output ('Recovered completed transform ' + (Split-Path -Leaf $cacheTarget))
    }
}
