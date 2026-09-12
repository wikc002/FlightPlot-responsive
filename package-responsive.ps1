$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot

& './build-responsive.ps1'

Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem

$version = '0.5.8'
$releaseDir = Join-Path $PSScriptRoot "release/$version"
if (Test-Path -LiteralPath $releaseDir) {
    $resolvedRelease = (Resolve-Path -LiteralPath $releaseDir).Path
    $expectedRelease = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot "release/$version"))
    if ($resolvedRelease -ne $expectedRelease -or !$resolvedRelease.StartsWith($PSScriptRoot + '\')) {
        throw 'Unexpected release output path'
    }
    Remove-Item -LiteralPath $resolvedRelease -Recurse -Force
}
New-Item -ItemType Directory -Force -Path $releaseDir | Out-Null

$jarName = "FlightPlot-responsive-$version.jar"
$jarPath = Join-Path $releaseDir $jarName
Copy-Item -LiteralPath (Join-Path $PSScriptRoot "build/$jarName") -Destination $jarPath

$launcherPath = Join-Path $releaseDir 'Start-FlightPlot.cmd'
$launcher = @'
@echo off
setlocal
cd /d "%~dp0"
where javaw.exe >nul 2>nul
if errorlevel 1 (
  echo Java 8 or newer is required.
  pause
  exit /b 1
)
start "FlightPlot" javaw.exe -Xms64m -Xmx512m -XX:+UseG1GC -XX:MaxGCPauseMillis=50 -jar "FlightPlot-responsive-0.5.8.jar" %*
'@
[IO.File]::WriteAllText($launcherPath, $launcher, (New-Object Text.UTF8Encoding($false)))

function Add-ZipFile($archive, $sourcePath, $entryName) {
    $entry = $archive.CreateEntry($entryName.Replace('\', '/'), [IO.Compression.CompressionLevel]::Optimal)
    $entry.LastWriteTime = [DateTimeOffset]::Parse('2000-01-01T00:00:00Z')
    $inputStream = [IO.File]::OpenRead((Resolve-Path -LiteralPath $sourcePath).Path)
    $outputStream = $entry.Open()
    try { $inputStream.CopyTo($outputStream) } finally { $inputStream.Dispose(); $outputStream.Dispose() }
}

$windowsZip = Join-Path $releaseDir "FlightPlot-responsive-$version-Windows.zip"
$stream = [IO.File]::Open($windowsZip, [IO.FileMode]::Create)
$archive = New-Object IO.Compression.ZipArchive($stream, [IO.Compression.ZipArchiveMode]::Create)
try {
    Add-ZipFile $archive $jarPath $jarName
    Add-ZipFile $archive $launcherPath 'Start-FlightPlot.cmd'
    foreach ($file in @('LICENSE', 'README.md', 'docs/USER_GUIDE.md', 'docs/RELEASE_NOTES_0.5.8.md', 'docs/THIRD_PARTY_NOTICES.md')) {
        Add-ZipFile $archive (Join-Path $PSScriptRoot $file) ([IO.Path]::GetFileName($file))
    }
} finally {
    $archive.Dispose()
    $stream.Dispose()
}

$sourceZip = Join-Path $releaseDir "FlightPlot-responsive-$version-source.zip"
$stream = [IO.File]::Open($sourceZip, [IO.FileMode]::Create)
$archive = New-Object IO.Compression.ZipArchive($stream, [IO.Compression.ZipArchiveMode]::Create)
try {
    foreach ($root in @('.github', 'docs', 'src', 'jMAVlib/src', 'tests', 'lib', 'packaging')) {
        $rootPath = Join-Path $PSScriptRoot $root
        if (!(Test-Path -LiteralPath $rootPath)) { continue }
        Get-ChildItem -LiteralPath $rootPath -Recurse -File | Where-Object { $_.Extension -ne '.class' } | Sort-Object FullName | ForEach-Object {
            $relative = $_.FullName.Substring($PSScriptRoot.Length + 1)
            Add-ZipFile $archive $_.FullName $relative
        }
    }
    foreach ($file in @('.gitignore', 'build-responsive.ps1', 'verify-responsive.ps1', 'package-responsive.ps1', 'build.xml', 'CHANGELOG.md', 'CONTRIBUTING.md', 'LICENSE', 'README.md', 'SECURITY.md', 'flightplot.icns', 'generate_csv.sh', 'jMAVlib/README.md')) {
        Add-ZipFile $archive (Join-Path $PSScriptRoot $file) $file
    }
} finally {
    $archive.Dispose()
    $stream.Dispose()
}

$checksumPath = Join-Path $releaseDir 'SHA256SUMS.txt'
$checksumLines = @($jarPath, $windowsZip, $sourceZip) | ForEach-Object {
    $item = Get-Item -LiteralPath $_
    $hash = (Get-FileHash -Algorithm SHA256 -LiteralPath $item.FullName).Hash.ToLowerInvariant()
    "$hash  $($item.Name)"
}
[IO.File]::WriteAllLines($checksumPath, $checksumLines, (New-Object Text.UTF8Encoding($false)))

Get-Item -LiteralPath $jarPath, $windowsZip, $sourceZip, $checksumPath | Select-Object FullName, Length
