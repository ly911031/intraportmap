"""Download pinned sources for the Android ss-server build (no global installs)."""
from concurrent.futures import ThreadPoolExecutor
import hashlib
from pathlib import Path
import shutil
import tarfile
import urllib.request

ROOT = Path(__file__).resolve().parent.parent / ".build-tools" / "ss-deps"
SOURCES = [
    ("shadowsocks", "0c6d7376c433cba3f7eec931a3769d0c01b7da69e927fc901b21115037c3c1fe", ["https://codeload.github.com/ly911031/shadowsocks-libev/tar.gz/6c3d281d228c5f9de210015124e18e77e2f89c60"]),
    ("libcork", "f8e4b637f35bbe0bc5d893164e439aefa61d2b6fe58b0a79c30e42f5f54f08e0", ["https://codeload.github.com/shadowsocks/libcork/tar.gz/074e074b26e9e372e90e6ade215217763c8644aa"]),
    ("libipset", "89be31c635814948c0887f660d2a98f4bc4b1dc6691a57d653c2466a8098f887", ["https://codeload.github.com/shadowsocks/ipset/tar.gz/3ea7fe30adf4b39b27d932e5a70a2ddce4adb508"]),
    ("libbloom", "6e0131faa238c5dd15da4b43931d2f628590ba07e1177a47ead8e4dd18752459", ["https://codeload.github.com/shadowsocks/libbloom/tar.gz/437e1add5a2b9a87797d8c648df7cf5f3ee155a8"]),
    ("libev", "507eb7b8d1015fbec5b935f34ebed15bf346bed04a11ab82b8eee848c4205aea", ["https://sources.openwrt.org/libev-4.33.tar.gz", "https://dist.schmorp.de/libev/Attic/libev-4.33.tar.gz"]),
    ("pcre", "4e6ce03e0336e8b4a3d6c2b70b1c5e18590a5673a98186da90d4f33c23defc09", ["https://ftp.exim.org/pub/pcre/pcre-8.45.tar.gz", "https://sources.openwrt.org/pcre-8.45.tar.gz"]),
    ("cares", "1a7d52a8a84a9fbffb1be9133c0f6e17217d91ea5a6fa61f6b4729cda78ebbcf", ["https://sources.openwrt.org/c-ares-1.18.1.tar.gz", "https://github.com/c-ares/c-ares/releases/download/cares-1_18_1/c-ares-1.18.1.tar.gz"]),
]

def prepare(source):
    name, expected, urls = source
    archive = ROOT / "downloads" / (name + ".tar.gz")
    target = ROOT / name
    archive.parent.mkdir(parents=True, exist_ok=True)
    def valid():
        return archive.is_file() and (not expected or hashlib.sha256(archive.read_bytes()).hexdigest() == expected)
    if not valid():
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        last = None
        for url in urls:
            try:
                temporary = archive.with_suffix(".part")
                with opener.open(url, timeout=30) as response, temporary.open("wb") as out:
                    shutil.copyfileobj(response, out)
                if expected and hashlib.sha256(temporary.read_bytes()).hexdigest() != expected:
                    raise RuntimeError("SHA-256 mismatch: " + name)
                temporary.replace(archive)
                break
            except Exception as error:
                last = error
        else:
            raise RuntimeError(f"Cannot download {name}: {last}")
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    marker = target / ".android-source-sha256"
    if not marker.is_file() or marker.read_text().strip() != digest:
        target.mkdir(parents=True, exist_ok=True)
        # Only regular files: dependency test symlinks are not needed by this build.
        with tarfile.open(archive) as bundle:
            for member in bundle:
                if not member.isfile():
                    continue
                relative = Path(*Path(member.name).parts[1:])
                destination = (target / relative).resolve()
                if not destination.is_relative_to(target.resolve()):
                    raise RuntimeError("Unsafe archive path: " + member.name)
                destination.parent.mkdir(parents=True, exist_ok=True)
                with bundle.extractfile(member) as data, destination.open("wb") as out:
                    shutil.copyfileobj(data, out)
        marker.write_text(digest + "\n")
    print(name + " " + digest, flush=True)

if __name__ == "__main__":
    with ThreadPoolExecutor(max_workers=4) as pool:
        list(pool.map(prepare, SOURCES))
