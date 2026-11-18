param(
    [string]$Sdk = "$env:LOCALAPPDATA\Android\Sdk",
    [string]$Java = 'E:\Program Files\Android\Android Studio\jbr',
    [string]$Ndk = "$PSScriptRoot\..\.build-tools\android-ndk-r27d",
    [string]$Cmake = "$PSScriptRoot\..\.build-tools\python\cmake\data\bin\cmake.exe",
    [string]$Ninja = "$PSScriptRoot\..\.build-tools\python\bin\ninja.exe",
    [string]$Libevent = "$PSScriptRoot\..\.build-tools\libevent-2.1.12-stable",
    [string[]]$Abis = @('arm64-v8a','armeabi-v7a','x86_64')
)
$ErrorActionPreference = 'Stop'
function Run([string]$Exe, [string[]]$Arguments) {
    $ErrorActionPreference = 'Continue'
    & $Exe @Arguments
    if ($LASTEXITCODE -ne 0) { throw "$Exe failed with exit code $LASTEXITCODE" }
}
$root = (Resolve-Path "$PSScriptRoot\..").Path
$out = "$PSScriptRoot\build"
$bt = "$Sdk\build-tools\36.1.0"
$jar = "$Sdk\platforms\android-36\android.jar"
$env:JAVA_HOME = $Java
$ssDeps="$root\.build-tools\ss-deps"
if(!(Test-Path "$ssDeps\shadowsocks\.android-source-sha256")) {
    throw 'Run python android/prepare-shadowsocks.py before building the combined APK.'
}
New-Item -ItemType Directory -Force "$out\classes","$out\dex","$out\package\lib","$root\dist" | Out-Null
foreach($abi in $Abis) {
    $native = "$out\native-api21-$abi"
    Run $Cmake @('-Wno-dev','-DCMAKE_WARN_DEPRECATED=OFF','-S',"$PSScriptRoot\native",'-B',$native,'-G','Ninja',"-DCMAKE_MAKE_PROGRAM=$Ninja", "-DCMAKE_TOOLCHAIN_FILE=$Ndk\build\cmake\android.toolchain.cmake", "-DANDROID_ABI=$abi",'-DANDROID_PLATFORM=android-21','-DANDROID_STL=c++_static','-DCMAKE_BUILD_TYPE=Release',"-DLIBEVENT_SOURCE=$Libevent")
    Run $Cmake @('--build',$native,'--parallel','4','--target','intraportmap','ss_server')
    New-Item -ItemType Directory -Force "$out\package\lib\$abi" | Out-Null
    Copy-Item "$native\libintraportmap.so" "$out\package\lib\$abi\libintraportmap.so" -Force
    Run "$Ndk\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-strip.exe" @("$out\package\lib\$abi\libintraportmap.so")
    Copy-Item "$native\shadowsocks\libss_server.so" "$out\package\lib\$abi\libss_server.so" -Force
    Run "$Ndk\toolchains\llvm\prebuilt\windows-x86_64\bin\llvm-strip.exe" @("$out\package\lib\$abi\libss_server.so")
}
New-Item -ItemType Directory -Force "$out\assets\licenses" | Out-Null
Copy-Item "$root\LICENSE" "$out\assets\licenses\intraportmap-GPL-3.0.txt" -Force
Copy-Item "$Libevent\LICENSE" "$out\assets\licenses\libevent.txt" -Force
Copy-Item "$Ndk\NOTICE" "$out\assets\licenses\android-ndk.txt" -Force
foreach($name in 'shadowsocks','libcork','libipset','libbloom','libev','pcre','cares') {
    Get-ChildItem "$ssDeps\$name" -File | Where-Object Name -Match '^(LICENSE|COPYING|COPYRIGHT)' | ForEach-Object {
        Copy-Item $_.FullName "$out\assets\licenses\$name-$($_.Name).txt" -Force
    }
}
Copy-Item "$ssDeps\shadowsocks\src\vendor\aws-lc\LICENSE" "$out\assets\licenses\aws-lc.txt" -Force
Copy-Item "$PSScriptRoot\prepare-shadowsocks.py" "$out\assets\licenses\shadowsocks-source-versions.py" -Force
Run "$bt\aapt.exe" @('package','-f','-M',"$PSScriptRoot\AndroidManifest.xml",'-S',"$PSScriptRoot\res",'-A',"$out\assets",'-I',$jar,'-F',"$out\base.apk")
$sources = @(Get-ChildItem "$PSScriptRoot\src" -Recurse -Filter '*.java' | ForEach-Object FullName)
Run "$Java\bin\javac.exe" (@('-encoding','UTF-8','-source','8','-target','8','-bootclasspath',"$jar;$bt\core-lambda-stubs.jar",'-d',"$out\classes") + $sources)
$classes = @(Get-ChildItem "$out\classes" -Recurse -Filter '*.class' | ForEach-Object FullName)
Run "$bt\d8.bat" (@('--lib',$jar,'--min-api','21','--output',"$out\dex") + $classes)
Copy-Item "$out\dex\classes.dex" "$out\package\classes.dex" -Force
Copy-Item "$out\base.apk" "$out\unsigned.apk" -Force
Push-Location "$out\package"
try { Run "$Java\bin\jar.exe" @('uf',"$out\unsigned.apk",'classes.dex','lib') } finally { Pop-Location }
Run "$bt\zipalign.exe" @('-f','-p','4',"$out\unsigned.apk","$out\aligned.apk")
$store = "$PSScriptRoot\debug.keystore"
if (!(Test-Path $store)) {
    Run "$Java\bin\keytool.exe" @('-genkeypair','-keystore',$store,'-storepass','android','-keypass','android','-alias','androiddebugkey','-keyalg','RSA','-keysize','2048','-validity','10000','-dname','CN=Android Debug,O=Android,C=US')
}
$apk="$root\dist\intraportmap-shadowsocks-android.apk"
Run "$bt\apksigner.bat" @('sign','--ks',$store,'--ks-pass','pass:android','--key-pass','pass:android','--out',$apk,"$out\aligned.apk")
Run "$bt\apksigner.bat" @('verify','--verbose',$apk)
Copy-Item $apk "$root\dist\intraportmap-android.apk" -Force
Write-Host "APK: $apk"
