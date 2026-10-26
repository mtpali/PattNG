"""Stage unchanged native inputs from the exact upstream P69 release.

No downloaded executable is run here. The APK is used as a ZIP containing the
unchanged Aether, Psiphon and transport binaries; the Go AAR matches its gitlink.
HEV can subsequently be rebuilt from the pinned submodule with compile-hevtun.sh.
"""

from pathlib import Path
import hashlib
import os
import subprocess
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
NATIVE = ROOT / 'V2rayNG/app/libs'
INPUTS = {
    'pattng-p69-arm64.apk': (
        'https://github.com/patterniha/PattNG/releases/download/2.3.13-P69/PattNG_2.3.13-P69_arm64-v8a.apk',
        'b70a5a90a62c6bfd64219abdd886e03a45c2d3e778fa6a14e798e9ec2f9a0522',
    ),
    'libv2ray.aar': (
        'https://github.com/patterniha/AndroidLibXrayLite/releases/download/v26.10.22/libv2ray.aar',
        '0c6e93c5905c8f03d5e0df05721aa308ccb5e39f8e626a8d4ef7644ee62d3b65',
    ),
}
REVISIONS = {
    'AndroidLibXrayLite': '161a86dc18a97adac49bcf4a817768d7715fdfde',
    'aether': 'd58f9d568083129ce2c767c85054021eb3ee6a38',
    'hev-socks5-tunnel': 'c5584feca8f6df70ee53eb2879e034b849a3d15a',
}
for name, expected in REVISIONS.items():
    actual = subprocess.check_output(['git', 'rev-parse', f'HEAD:{name}'], cwd=ROOT, text=True).strip()
    if actual != expected:
        raise SystemExit(f'{name} revision changed; update the verified native inputs before building.')

cache = Path(os.environ.get('PATTNG_NATIVE_CACHE', str(Path(tempfile.gettempdir()) / 'pattng-native-p69')))
cache.mkdir(parents=True, exist_ok=True)
for name, (url, digest) in INPUTS.items():
    path = cache / name
    if not path.exists():
        temporary = path.with_suffix(path.suffix + '.download')
        urllib.request.urlretrieve(url, temporary)
        temporary.replace(path)
    if hashlib.file_digest(path.open('rb'), 'sha256').hexdigest() != digest:
        raise SystemExit(f'SHA-256 verification failed: {name}')
NATIVE.mkdir(parents=True, exist_ok=True)
(NATIVE / 'libv2ray.aar').write_bytes((cache / 'libv2ray.aar').read_bytes())
libraries = ('libaether.so', 'libpsiphon-tunnel-core.so', 'liblyrebird.so', 'libhev-socks5-tunnel.so', 'libhevsockstun.so')
with zipfile.ZipFile(cache / 'pattng-p69-arm64.apk') as apk:
    for name in libraries:
        target = NATIVE / 'arm64-v8a' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_bytes(apk.read(f'lib/arm64-v8a/{name}'))
    for name in ('psiphon_servers.dat', 'psiphon_servers.stamp'):
        (ROOT / 'V2rayNG/app/src/main/assets' / name).write_bytes(apk.read(f'assets/{name}'))
print('Verified and staged the pinned arm64 native inputs.')
