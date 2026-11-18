param([string]$Java=$env:JAVA_HOME)
$ErrorActionPreference='Stop'
function Run([string]$Exe,[string[]]$Arguments) {
    $ErrorActionPreference='Continue'
    & $Exe @Arguments
    if($LASTEXITCODE -ne 0) { throw "$Exe failed: $LASTEXITCODE" }
}
if(!$Java) { throw 'Specify -Java or JAVA_HOME' }
$out="$PSScriptRoot/build/host-tests"
New-Item -ItemType Directory -Force $out,"$out/logs-test" | Out-Null
$src="$PSScriptRoot/src/org/intraportmap/android"
$fixtures=@(Get-ChildItem "$PSScriptRoot/tests-host/network" -Recurse -Filter '*.java' | ForEach-Object FullName)
Run "$Java/bin/javac.exe" (@('-encoding','UTF-8','-d',$out,"$src/NetworkWatcher.java","$src/DnsServers.java","$src/LogStore.java","$PSScriptRoot/tests-host/DnsServersTest.java","$PSScriptRoot/tests/org/intraportmap/android/LogStoreTest.java")+$fixtures)
foreach($api in '21','23','24','26') {
    Run "$Java/bin/java.exe" @('-cp',$out,'org.intraportmap.android.NetworkWatcherTest',$api)
}
Run "$Java/bin/java.exe" @('-cp',$out,'org.intraportmap.android.DnsServersTest')
Run "$Java/bin/java.exe" @('-cp',$out,'org.intraportmap.android.LogStoreTest',"$out/logs-test")
