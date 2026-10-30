"""Exercise Root selection in an isolated filesystem; never touches /data/adb."""
import os
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
shell = shutil.which('bash') or shutil.which('sh')
if os.name == 'nt':
    git = shutil.which('git')
    candidate = Path(git).resolve().parents[1] / 'bin/bash.exe' if git else None
    if candidate and candidate.is_file():
        shell = str(candidate)
if not shell:
    raise SystemExit('A POSIX shell is required')

with tempfile.TemporaryDirectory(prefix='daednext-root-') as scratch:
    base = Path(scratch)
    adb = base / 'adb'
    module = base / 'module'
    (module / 'scripts').mkdir(parents=True)
    source = (ROOT / 'module/scripts/root-env.sh').read_text(encoding='utf8')
    # Rewrite trusted absolute paths only in this private test copy.
    source = source.replace('/data/adb', adb.as_posix())
    helper = module / 'scripts/root-env.sh'
    helper.write_text(source, encoding='utf8', newline='\n')
    paths = {'kernelsu': 'ksu/bin/busybox', 'magisk': 'magisk/busybox', 'apatch': 'ap/bin/busybox'}
    for relative in paths.values():
        binary = adb / relative
        binary.parent.mkdir(parents=True, exist_ok=True)
        binary.write_text('#!/bin/sh\n[ "$1" = true ]\n', encoding='utf8', newline='\n')
        binary.chmod(0o755)
    marker = module / 'root-manager'
    env = {k: v for k, v in os.environ.items() if k not in ('KSU', 'APATCH', 'MAGISK_VER_CODE', 'ROOT_MANAGER', 'BB', 'MODDIR')}
    env['MODDIR'] = module.as_posix()
    script = '. "$MODDIR/scripts/root-env.sh" || exit 1; printf "%s|%s" "$ROOT_MANAGER" "$BB"'

    def run(extra=None, expected='kernelsu', binary=None, success=True):
        result = subprocess.run([shell, '-c', script], env=dict(env, **(extra or {})), capture_output=True, text=True)
        assert (result.returncode == 0) == success, result.stderr
        if success:
            assert result.stdout == expected + '|' + (adb / paths[binary or expected]).as_posix(), result.stdout

    run({'KSU': 'true'})
    run({'APATCH': 'true', 'MAGISK_VER_CODE': '27000'}, expected='apatch')
    run({'MAGISK_VER_CODE': '30000'}, expected='magisk')
    for manager in paths:
        marker.write_text(manager + '\n', encoding='utf8', newline='\n')
        run(expected=manager)
    marker.write_text('magisk\n', encoding='utf8', newline='\n')
    # Recognized manager must not borrow another manager's BusyBox.
    (adb / paths['magisk']).unlink()
    run(success=False)
    # A marker is data, never sourced or executed.
    sentinel = base / 'must-not-exist'
    marker.write_text('$(touch ' + sentinel.as_posix() + ')\n', encoding='utf8', newline='\n')
    run(expected='unknown', binary='kernelsu')
    assert not sentinel.exists()
    marker.unlink()
    run(expected='unknown', binary='kernelsu')
    for binary in adb.rglob('busybox'):
        binary.unlink()
    run(success=False)

    # Source the actual installer with only filesystem/permission boundaries mocked.
    # This verifies APatch's MAGISK_VER_CODE compatibility value cannot mislabel it.
    installer = (ROOT / 'module/customize.sh').read_text(encoding='utf8')
    installer = installer.replace('/data/adb/modules/daenext', (base / 'absent-old-module').as_posix())
    installer_file = base / 'customize.sh'
    installer_file.write_text(installer, encoding='utf8', newline='\n')
    for relative in paths.values():
        binary = adb / relative
        binary.write_text('#!/bin/sh\ncase "$1" in true) exit 0 ;; sha256sum) shift; exec sha256sum "$@" ;; *) exit 1 ;; esac\n', encoding='utf8', newline='\n')
        binary.chmod(0o755)
    import hashlib
    (module / 'SHA256SUMS').write_text(hashlib.sha256(helper.read_bytes()).hexdigest() + '  scripts/root-env.sh\n', encoding='utf8', newline='\n')
    install_script = '''ui_print() { :; }; abort() { echo "$1" >&2; exit 1; }
set_perm() { :; }; set_perm_recursive() { :; }
. "$1" || exit 1
printf '%s|%s' "$ROOT_MANAGER" "${MODULE_HOT_INSTALL_REQUEST:-false}"
'''
    for flags, manager in [({'KSU': 'true'}, 'kernelsu'), ({'APATCH': 'true', 'MAGISK_VER_CODE': '27000'}, 'apatch'), ({'MAGISK_VER_CODE': '30000'}, 'magisk')]:
        result = subprocess.run([shell, '-c', install_script, 'installer-test', installer_file.as_posix()],
                                env=dict(env, ARCH='arm64', API='37', MODPATH=module.as_posix(), **flags), capture_output=True, text=True)
        assert result.returncode == 0, result.stderr
        assert result.stdout == manager + '|' + ('true' if manager == 'kernelsu' else 'false'), repr((manager, result.stdout))
        assert marker.read_text(encoding='utf8').strip() == manager

    # Verify the source enforces the eBPF contract independently of caller env.
    common = ROOT / 'module/scripts/common.sh'
    (module / 'scripts/common.sh').write_text(common.read_text(encoding='utf8'), encoding='utf8', newline='\n')
    check = '. "$MODDIR/scripts/common.sh" || exit 1; printf "%s|%s|%s" "$RESIDENT_NATIVE_EBPF" "$RESIDENT_DATAPLANE" "$RESIDENT_NATIVE_EBPF_BACKEND"'
    result = subprocess.run([shell, '-c', check], env=dict(env, RESIDENT_NATIVE_EBPF='0', RESIDENT_DATAPLANE='0', RESIDENT_NATIVE_EBPF_BACKEND='tun'), capture_output=True, text=True)
    assert result.returncode == 0 and result.stdout == '1|1|auto', result.stderr + result.stdout

print('Root selection, installer matrix, missing binary, untrusted marker and eBPF-only policy passed')
