$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
$compiler = (Get-Command javac.exe -ErrorAction Stop).Source
$taskBuild = Join-Path $PSScriptRoot 'build/responsive'
if (Test-Path -LiteralPath $taskBuild) {
    $resolvedBuild = (Resolve-Path -LiteralPath $taskBuild).Path
    $expectedBuild = [IO.Path]::GetFullPath((Join-Path $PSScriptRoot 'build/responsive'))
    if ($resolvedBuild -ne $expectedBuild -or !$resolvedBuild.StartsWith($PSScriptRoot + '\')) { throw 'Unexpected build output path' }
    Remove-Item -LiteralPath $resolvedBuild -Recurse -Force
}
New-Item -ItemType Directory -Force $taskBuild | Out-Null
$sources = Get-ChildItem 'src/me','src/net','src/org','jMAVlib/src' -Recurse -Filter '*.java' | ForEach-Object { '"' + $_.FullName.Replace('\','/') + '"' }
[IO.File]::WriteAllLines((Join-Path $taskBuild 'sources.txt'), $sources, (New-Object Text.UTF8Encoding($false)))
& $compiler '-J-Dfile.encoding=UTF-8' --release 8 -encoding UTF-8 -Xlint:-options -cp 'lib/*' -d $taskBuild ('@' + (Join-Path $taskBuild 'sources.txt'))
if ($LASTEXITCODE -ne 0) { throw 'Compilation failed' }
Add-Type -AssemblyName System.IO.Compression
Add-Type -AssemblyName System.IO.Compression.FileSystem
$jarPath = Join-Path $PSScriptRoot 'build/FlightPlot-responsive-0.5.8.jar'
$stream = [IO.File]::Open($jarPath,[IO.FileMode]::Create)
$zip = New-Object IO.Compression.ZipArchive($stream,[IO.Compression.ZipArchiveMode]::Create)
try {
    $fixedTime = [DateTimeOffset]::Parse('2000-01-01T00:00:00Z')
    $manifest = $zip.CreateEntry('META-INF/MANIFEST.MF')
    $manifest.LastWriteTime = $fixedTime
    $writer = New-Object IO.StreamWriter($manifest.Open())
    $writer.Write("Manifest-Version: 1.0`r`nMain-Class: me.drton.flightplot.FlightPlot`r`nImplementation-Version: 0.5.8`r`n`r`n")
    $writer.Dispose()
    $names = New-Object 'System.Collections.Generic.HashSet[string]'
    Get-ChildItem $taskBuild -Recurse -Filter '*.class' | Sort-Object FullName | ForEach-Object {
        $name = $_.FullName.Substring($taskBuild.Length+1).Replace('\','/')
        $entry = $zip.CreateEntry($name, [IO.Compression.CompressionLevel]::Optimal)
        $entry.LastWriteTime = $fixedTime
        $inputStream = [IO.File]::OpenRead($_.FullName)
        $outputStream = $entry.Open()
        try { $inputStream.CopyTo($outputStream) } finally { $inputStream.Dispose(); $outputStream.Dispose() }
        $names.Add($name) | Out-Null
    }
    foreach ($library in @('jfreechart-1.0.14.jar','jcommon-1.0.17.jar','vecmath.jar')) {
        $source = [IO.Compression.ZipFile]::OpenRead((Join-Path $PSScriptRoot ('lib/'+$library)))
        try { foreach ($entry in ($source.Entries | Sort-Object FullName)) {
            if ($entry.FullName.EndsWith('/') -or $entry.FullName -match '^META-INF/(MANIFEST.MF|.*\.(SF|RSA|DSA))$' -or !$names.Add($entry.FullName)) { continue }
            $target = $zip.CreateEntry($entry.FullName)
            $target.LastWriteTime = $fixedTime
            $inputStream = $entry.Open(); $outputStream = $target.Open()
            try { $inputStream.CopyTo($outputStream) } finally { $inputStream.Dispose(); $outputStream.Dispose() }
        } } finally { $source.Dispose() }
    }
} finally { $zip.Dispose(); $stream.Dispose() }
Get-Item -LiteralPath $jarPath | Select-Object FullName,Length
