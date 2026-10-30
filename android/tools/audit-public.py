"""Check tracked Android integration files before pushing public source."""
from pathlib import Path
import hashlib
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
# Reviewed UI source assets only; device screenshots and other PNGs remain denied.
SOURCE_ARTWORK = {
    'manager/res/drawable-nodpi/goose_core_light.png': '2a39a8ed2e4123676316525c9265bcb2398dcac8e8f38c413b05d178a6571bcf',
    'manager/res/drawable-nodpi/goose_core_dark.png': '83dcc11347140303e44860e471d7f70886baad3b05dc906e70105c0db26f7941',
}
files = subprocess.check_output(['git', 'ls-files', '-z', 'android'], cwd=ROOT).decode().split('\0')
errors = []
for name in filter(None, files):
    p = ROOT / name
    relative = p.relative_to(ROOT / 'android')
    reviewed_art = (relative.as_posix() in SOURCE_ARTWORK and p.is_file()
                    and hashlib.sha256(p.read_bytes()).hexdigest() == SOURCE_ARTWORK[relative.as_posix()])
    if any(part in ('private', 'backups', 'validation', '.build', 'dist') for part in relative.parts) or (p.suffix in ('.db', '.sqlite', '.pcap', '.log', '.png', '.apk', '.p12', '.jks', '.keystore') and not reviewed_art):
        errors.append((name, 'private/generated artifact tracked'))
    if not p.is_file():
        continue
    data = p.read_bytes()
    if p.suffix in ('.sh', '.py', '.js', '.html', '.prop') and b'\r\n' in data:
        errors.append((name, 'CRLF in build/runtime source'))
    if re.search(rb'(?:vless|vmess|trojan|hysteria2?|hy2|tuic)://', data):
        errors.append((name, 'proxy URI in public Android source'))
    if re.search(rb'gh[pousr]_[A-Za-z0-9]{30,}', data):
        errors.append((name, 'possible GitHub credential'))
    if data.startswith((b'\x7fELF', b'SQLite format 3')):
        errors.append((name, 'binary runtime/database belongs in release assets'))
for name, reason in errors:
    print(f'{name}: {reason}')
if errors:
    raise SystemExit(1)
print(f'Public Android source audit passed ({len(list(filter(None, files)))} tracked paths).')
