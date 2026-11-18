param([string]$Serial, [switch]$Combined, [string]$Sdk="$env:LOCALAPPDATA\Android\Sdk", [string]$Java='E:\Program Files\Android\Android Studio\jbr')
$ErrorActionPreference='Stop'
function Run([string]$Exe,[string[]]$Arguments) {
    $ErrorActionPreference='Continue'
    & $Exe @Arguments
    if($LASTEXITCODE -ne 0) { throw "$Exe failed: $LASTEXITCODE" }
}
if(!$Serial) { throw 'Specify the test emulator with -Serial; this test starts/stops a local test mapping.' }
$bt="$Sdk\build-tools\36.1.0"
$jar="$Sdk\platforms\android-36\android.jar"
$out="$PSScriptRoot\build\device-tests"
$env:JAVA_HOME=$Java
New-Item -ItemType Directory -Force "$out\classes","$out\dex" | Out-Null
$sources=@(Get-ChildItem "$PSScriptRoot\tests\org\intraportmap\android" -Filter '*.java' | Where-Object Name -NE 'LogStoreTest.java' | ForEach-Object FullName)
Run "$Java\bin\javac.exe" (@('-encoding','UTF-8','-source','8','-target','8','-bootclasspath',"$jar;$bt\core-lambda-stubs.jar",'-d',"$out\classes")+$sources)
$classes=@(Get-ChildItem "$out\classes" -Recurse -Filter '*.class' | ForEach-Object FullName)
Run "$bt\d8.bat" (@('--lib',$jar,'--min-api','26','--output',"$out\dex")+$classes)
Run "$bt\aapt.exe" @('package','-f','-M',"$PSScriptRoot\tests\AndroidManifest.xml",'-I',$jar,'-F',"$out\unsigned.apk")
Push-Location "$out\dex"
try { Run "$Java\bin\jar.exe" @('uf',"$out\unsigned.apk",'classes.dex') } finally { Pop-Location }
Run "$bt\zipalign.exe" @('-f','4',"$out\unsigned.apk","$out\aligned.apk")
Run "$bt\apksigner.bat" @('sign','--ks',"$PSScriptRoot\debug.keystore",'--ks-pass','pass:android','--out',"$out\test.apk","$out\aligned.apk")
Run "$Sdk\platform-tools\adb.exe" @('-s',$Serial,'install','--no-incremental','-r',"$out\test.apk")
$test=if($Combined) { 'CombinedSmokeTest' } else { 'DeviceSmokeTest' }
$output=& "$Sdk\platform-tools\adb.exe" -s $Serial shell am instrument -w -r "org.intraportmap.android.tests/org.intraportmap.android.$test"
$output | Write-Output
$expected=if($Combined) { 'PASS: combined pages' } else { 'PASS: recents' }
if($LASTEXITCODE -ne 0 -or ($output -join "`n") -notmatch $expected) { throw 'Device smoke test failed' }
