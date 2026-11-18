"""Independent host JCE interoperability test against the Android native core.

Pass adb path, emulator serial and JDK directory. Uses local-only test ports.
"""
from pathlib import Path
import subprocess
import sys
import time

adb, serial, jdk = sys.argv[1:]
root = Path(__file__).resolve().parent
out = root / "build" / "host-crypto-tests"
out.mkdir(parents=True, exist_ok=True)
java = str(Path(jdk) / "bin/java.exe")
javac = str(Path(jdk) / "bin/javac.exe")
subprocess.run([javac,"-encoding","UTF-8","-d",str(out),str(root/"tests/org/intraportmap/android/ShadowsocksProbe.java"),str(root/"tests/org/intraportmap/android/HostCryptoProbe.java")],check=True)
ndk=root.parent/".build-tools/android-ndk-r27d/toolchains/llvm/prebuilt/windows-x86_64"
bridge_binary=out/"ipm_udp_probe_bridge"
subprocess.run([str(ndk/"bin/clang.exe"),"--target=x86_64-linux-android26","--sysroot="+str(ndk/"sysroot"),str(root/"tests/udp_probe_bridge.c"),"-o",str(bridge_binary)],check=True)

def run(*args, check=True):
    return subprocess.run([adb,"-s",serial,*args],check=check,capture_output=True,text=True,timeout=30)

remote = "/data/local/tmp/ipm_ss_probe"
methods = ["chacha20-ietf-poly1305","aes-128-gcm","aes-192-gcm","aes-256-gcm","xchacha20-ietf-poly1305"]
password = "local-native-test-only"
run("shell","pkill","-f","^"+remote,check=False)
run("push",str(root/"build/package/lib/x86_64/libss_server.so"),remote)
run("shell","chmod","755",remote)
run("forward","tcp:32190","tcp:32190")
run("reverse","tcp:32181","tcp:32181")
remote_bridge="/data/local/tmp/ipm_udp_probe_bridge"
run("push",str(bridge_binary),remote_bridge)
run("shell","chmod","755",remote_bridge)
run("forward","tcp:32182","tcp:32182")
bridge_log=(out/"udp-bridge.log").open("w")
bridge=subprocess.Popen([adb,"-s",serial,"shell",remote_bridge],stdout=bridge_log,stderr=subprocess.STDOUT)
try:
    for attempt in range(100):
        if bridge.poll() is not None: raise RuntimeError("UDP test bridge exited")
        if "READY" in Path(bridge_log.name).read_text(): break
        time.sleep(0.1)
    else: raise RuntimeError("UDP test bridge did not become ready")
    for method in methods:
        with (out/(method+".log")).open("w") as log:
            process = subprocess.Popen([adb,"-s",serial,"shell",remote,"-s","127.0.0.1","-p","32190","-k",password,"-m",method,"-u","--mtu","9000","-d","127.0.0.1","-v"],stdout=log,stderr=subprocess.STDOUT)
            try:
                for attempt in range(80):
                    if process.poll() is not None:
                        raise RuntimeError(method+" Android core exited; see "+str(log.name))
                    # ADB accepts forwarded TCP even before the remote listener
                    # exists. Wait for the Android core's own readiness message.
                    if "tcp server listening at" in Path(log.name).read_text(errors="replace"):
                        break
                    time.sleep(0.1)
                else:
                    raise RuntimeError(method+" Android core did not become ready")
                subprocess.run([java,"-cp",str(out),"org.intraportmap.android.HostCryptoProbe",method,password,"32190","32181","32182"],check=True,timeout=20)
            finally:
                run("shell","pkill","-f","^"+remote,check=False)
                try: process.wait(timeout=5)
                except subprocess.TimeoutExpired: process.terminate()
finally:
    run("shell","pkill","-f","^"+remote_bridge,check=False)
    try: bridge.wait(timeout=5)
    except subprocess.TimeoutExpired: bridge.terminate()
    bridge_log.close()
    run("forward","--remove","tcp:32182",check=False)
    run("forward","--remove","tcp:32190",check=False)
    run("reverse","--remove","tcp:32181",check=False)
