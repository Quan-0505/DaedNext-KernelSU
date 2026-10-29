"""Restore verified build inputs from a published, data-free module ZIP."""
import argparse
import hashlib
import json
from pathlib import Path, PurePosixPath
import shutil
import tempfile
import urllib.request
import zipfile

ROOT = Path(__file__).resolve().parents[1]
lock = json.loads((ROOT / 'upstream.lock.json').read_text(encoding='utf8'))['runtimeSeed']
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--archive', type=Path, help='Use an existing ZIP instead of downloading it')
args = parser.parse_args()
with tempfile.TemporaryDirectory() as scratch:
    archive = args.archive
    if archive is None:
        archive = Path(scratch) / lock['asset']
        url = f"https://github.com/{lock['repository']}/releases/download/{lock['tag']}/{lock['asset']}"
        with urllib.request.urlopen(url, timeout=90) as response, archive.open('wb') as out:
            shutil.copyfileobj(response, out)
    digest = hashlib.sha256(archive.read_bytes()).hexdigest()
    if digest != lock['sha256']:
        raise SystemExit('Runtime archive SHA256 does not match android/upstream.lock.json')
    restored = 0
    with zipfile.ZipFile(archive) as z:
        if z.testzip() is not None:
            raise SystemExit('Runtime archive CRC check failed')
        for entry in z.infolist():
            name = PurePosixPath(entry.filename)
            if name.is_absolute() or '..' in name.parts or '\\' in entry.filename:
                raise SystemExit('Unsafe archive path')
            wanted = entry.filename in ('bin/daed', 'lib/libc++_shared.so', 'assets/geoip.dat', 'assets/geosite.dat', 'manager/DaedNext-Manager.apk') or entry.filename.startswith('panel/')
            if not wanted or entry.is_dir():
                continue
            target = ROOT / 'module' / name
            target.parent.mkdir(parents=True, exist_ok=True)
            with z.open(entry) as source, target.open('wb') as out:
                shutil.copyfileobj(source, out)
            if entry.filename == 'bin/daed':
                target.chmod(0o755)
            restored += 1
    print(f'Restored {restored} runtime inputs; no accounts, configuration or subscriptions imported.')
