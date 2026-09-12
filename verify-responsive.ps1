$ErrorActionPreference = 'Stop'
Set-Location -LiteralPath $PSScriptRoot
& './build-responsive.ps1'
$verificationDir = 'verification-0.5.8'
New-Item -ItemType Directory -Force $verificationDir | Out-Null
& javac -encoding UTF-8 --release 8 -Xlint:-options -cp 'build/FlightPlot-responsive-0.5.8.jar' -d $verificationDir tests/ReaderRegression.java tests/ReaderBenchmark.java tests/UiRegression.java tests/InteractionRegression.java tests/LargeBinBenchmark.java tests/FormatRegression.java tests/ProductRegression.java tests/FieldCatalogRegression.java tests/ArchitectureRegression.java tests/LocalizationRegression.java
if ($LASTEXITCODE -ne 0) { throw 'Test compilation failed' }
$cp = 'build/FlightPlot-responsive-0.5.8.jar;' + $verificationDir
& java -Xmx128m -cp $cp FormatRegression 2>&1 | Tee-Object "$verificationDir/format-tests.txt"
if ($LASTEXITCODE -ne 0) { throw 'DataFlash format regression failed' }
if (!(Test-Path -LiteralPath '日志示例')) {
    & java -Xmx128m -cp $cp ReaderRegression 2>&1 | Tee-Object "$verificationDir/reader-tests.txt"
    if ($LASTEXITCODE -ne 0) { throw 'Synthetic reader regression failed' }
    & java -Xmx128m -cp $cp ProductRegression 2>&1 | Tee-Object "$verificationDir/product-tests.txt"
    if ($LASTEXITCODE -ne 0) { throw 'Product regression failed' }
    & java -Xmx128m -cp $cp me.drton.flightplot.ArchitectureRegression 2>&1 | Tee-Object "$verificationDir/architecture-tests.txt"
    if ($LASTEXITCODE -ne 0) { throw 'Architecture regression failed' }
    & java -Xmx128m -cp $cp LocalizationRegression 2>&1 | Tee-Object "$verificationDir/localization-tests.txt"
    if ($LASTEXITCODE -ne 0) { throw 'Localization compatibility failed' }
    Write-Host 'PASS self-contained tests. Copy real logs into 日志示例 to run format, UI and large-log coverage.'
    return
}
$big = (Get-ChildItem '日志示例' -Filter '*.bin' | Sort-Object Length -Descending | Select-Object -First 1).FullName
$small = (Get-ChildItem '日志示例' -Filter '*实飞测试.bin' | Select-Object -First 1).FullName
$alternate = (Resolve-Path -LiteralPath '日志示例/28 1980-1-1 8-00-00.bin').Path
$latestBin = (Resolve-Path -LiteralPath '日志示例/3 1980-1-1 8-00-00.bin').Path
$textLog = (Resolve-Path -LiteralPath '日志示例/00000108.log').Path
$ulog = (Resolve-Path -LiteralPath '日志示例/00000156.ulg').Path
$secondUlog = (Resolve-Path -LiteralPath '日志示例/00000157.ulg').Path
& java -Xmx256m -cp $cp ReaderRegression $big '日志示例/28 1980-1-1 8-00-00.bin' $small 2>&1 | Tee-Object "$verificationDir/reader-tests.txt"
if ($LASTEXITCODE -ne 0) { throw 'Reader regression failed' }
$schemaLogs = @(
    '日志示例/00000108.log',
    '日志示例/2 1980-1-1 8-00-00.log',
    '日志示例/00000156.ulg',
    '日志示例/00000157.ulg'
)
$ErrorActionPreference = 'Continue' # two supplied ULogs intentionally report truncated-tail warnings on stderr
& java -Xmx256m -cp $cp FieldCatalogRegression @schemaLogs 2>&1 | Tee-Object "$verificationDir/dynamic-fields.txt"
$dynamicFieldsExitCode = $LASTEXITCODE
$ErrorActionPreference = 'Stop'
if ($dynamicFieldsExitCode -ne 0) { throw 'Dynamic LOG/ULG field catalog regression failed' }
$logs = @(Get-ChildItem '日志示例' -Filter '*.bin' | ForEach-Object FullName)
& java -Xmx256m -cp $cp ReaderBenchmark @logs 2>&1 | Tee-Object "$verificationDir/all-bin-files.txt"
if ($LASTEXITCODE -ne 0) { throw 'Real BIN compatibility failed' }
& java -Xmx128m -cp $cp LargeBinBenchmark 2>&1 | Tee-Object "$verificationDir/1g-stress.txt"
if ($LASTEXITCODE -ne 0) { throw 'Large-file test failed' }
foreach ($scale in @('1','1.25','1.5','2')) {
    $ErrorActionPreference = 'Continue' # supplied truncated logs print nonfatal parser warnings on stderr
    & java "-Dsun.java2d.uiScale=$scale" "-Dflightplot.testOutput=$verificationDir" -Xmx512m -cp $cp me.drton.flightplot.UiRegression $big $small $alternate $latestBin $textLog $ulog $secondUlog 2>&1 | Tee-Object "$verificationDir/ui-scale-$scale.txt"
    $uiExitCode = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($uiExitCode -ne 0) { throw "UI regression failed at scale $scale" }
}
foreach ($scale in @('1','2')) {
    $ErrorActionPreference = 'Continue'
    & java "-Dsun.java2d.uiScale=$scale" "-Dflightplot.testOutput=$verificationDir" -Xmx512m -cp $cp me.drton.flightplot.InteractionRegression '日志示例' 2>&1 | Tee-Object "$verificationDir/interactions-$scale.txt"
    $interactionExit = $LASTEXITCODE
    $ErrorActionPreference = 'Stop'
    if ($interactionExit -ne 0) { throw "Interaction regression failed at scale $scale" }
}
& java -Xmx128m -cp $cp ProductRegression 2>&1 | Tee-Object "$verificationDir/product-tests.txt"
if ($LASTEXITCODE -ne 0) { throw 'Product regression failed' }
& java -Xmx128m -cp $cp me.drton.flightplot.ArchitectureRegression 2>&1 | Tee-Object "$verificationDir/architecture-tests.txt"
if ($LASTEXITCODE -ne 0) { throw 'Architecture regression failed' }
& java -Xmx128m -cp $cp LocalizationRegression 2>&1 | Tee-Object "$verificationDir/localization-tests.txt"
if ($LASTEXITCODE -ne 0) { throw 'Localization compatibility failed' }
