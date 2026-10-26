"""Check tracked Android integration files before pushing public source."""
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
files = subprocess.check_output(['git', 'ls-files', '-z', 'android'], cwd=ROOT).decode().split('\0')
errors = []
for name in filter(None, files):
    p = ROOT / name
    relative = p.relative_to(ROOT / 'android')
    if any(part in ('private', 'backups', 'validation', '.build', 'dist') for part in relative.parts) or p.suffix in ('.db', '.sqlite', '.pcap', '.log', '.png'):
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
