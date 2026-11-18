"""Exercise the Android core on a connected emulator (not a physical phone).

Usage: python android/smoke_test.py PATH_TO_ADB
Requires the x86_64 build. Uses loopback ports 18081 / 22120 / 15353 / 25353.
"""
import os
from pathlib import Path
import socket
import subprocess
import sys
import threading
import time

adb = sys.argv[1]
root = Path(__file__).resolve().parent
def run(*args, check=True):
    return subprocess.run([adb, *args], check=check, capture_output=True, text=True, timeout=30)

tcp = socket.socket()
tcp.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
tcp.bind(('0.0.0.0', 18081))
tcp.listen()
udp = socket.socket(socket.AF_INET, socket.SOCK_DGRAM)
udp.bind(('0.0.0.0', 18081))

def echo_tcp():
    while True:
        connection, _ = tcp.accept()
        def echo(c):
            with c:
                while True:
                    data = c.recv(65536)
                    if not data: break
                    c.sendall(data)
        threading.Thread(target=echo, args=(connection,), daemon=True).start()

def echo_udp():
    while True:
        data, address = udp.recvfrom(65535)
        udp.sendto(data, address)

threading.Thread(target=echo_tcp, daemon=True).start()
threading.Thread(target=echo_udp, daemon=True).start()
remote = '/data/local/tmp/ipm_android_smoke'
processes = []
files = []
try:
    run('push', str(root/'build/package/lib/x86_64/libintraportmap.so'), remote)
    run('shell', 'chmod', '755', remote)
    run('forward', 'tcp:25353', 'tcp:15353')
    run('emu', 'redir', 'add', 'udp:25353:15353')
    for name, args in [('server', ['-s','[::]:22120','-u']),
                       ('client', ['-c','-s','localhost:22120','-t','0.0.0.0:15353','-f','10.0.2.2:18081','-u','-w','1'])]:
        f = open(root/'build'/f'smoke-{name}.log', 'w')
        files.append(f)
        processes.append(subprocess.Popen([adb,'shell',remote,*args],stdout=f,stderr=subprocess.STDOUT))
        time.sleep(1)
    for attempt in range(20):
        try:
            with socket.create_connection(('127.0.0.1',25353),timeout=2) as connection:
                payload = os.urandom(32768)
                connection.sendall(payload)
                result = b''
                while len(result)<len(payload):
                    part = connection.recv(65536)
                    if not part: raise RuntimeError('TCP closed before full echo')
                    result += part
                assert result==payload
            break
        except (OSError,RuntimeError):
            if attempt==19: raise
            time.sleep(1)
    print('PASS: TCP 32768-byte binary round trip; hostname resolution localhost')
    with socket.socket(socket.AF_INET,socket.SOCK_DGRAM) as probe:
        probe.settimeout(4)
        for size in (32,1400,8192):
            payload = os.urandom(size)
            probe.sendto(payload,('127.0.0.1',25353))
            result, _ = probe.recvfrom(65535)
            assert result==payload, f'UDP mismatch size {size}'
            print(f'PASS: UDP {size}-byte binary round trip')
finally:
    run('shell','pkill','-f','^/data/local/tmp/ipm_android_smoke',check=False)
    for process in processes:
        try: process.wait(timeout=5)
        except subprocess.TimeoutExpired: process.terminate()
    for f in files: f.close()
    run('forward','--remove','tcp:25353',check=False)
    run('emu','redir','del','udp:25353',check=False)
    tcp.close()
    udp.close()
