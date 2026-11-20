"""Pinned org.json library for JVM tests only; never included in the APK."""
import hashlib
from pathlib import Path
import urllib.request

target=Path(__file__).resolve().parent.parent/'.build-tools/test-json.jar'
expected='3cf6cd6892e32e2b4c1c39e0f52f5248a2f5b37646fdfbb79a66b46b618414ed'
if not target.exists() or hashlib.sha256(target.read_bytes()).hexdigest()!=expected:
    data=urllib.request.urlopen('https://repo.maven.apache.org/maven2/org/json/json/20240303/json-20240303.jar',timeout=60).read()
    if hashlib.sha256(data).hexdigest()!=expected: raise SystemExit('JSON test dependency checksum mismatch')
    target.parent.mkdir(parents=True,exist_ok=True)
    target.write_bytes(data)
print('Verified org.json 20240303 test dependency')
