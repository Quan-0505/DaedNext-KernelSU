"""Dependency-free native Android build. Requires SDK 35, build-tools 35 and JDK 17.

The persistent local signing key is stored under android/private/ (gitignored).
Use the same key for future APK updates; never commit or publish it.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import secrets
import subprocess
import zipfile
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parent
parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--unsigned', action='store_true', help='Compile and align only; never create or load signing keys')
args = parser.parse_args()
ANDROID = ROOT.parent
SDK = Path(os.environ.get('ANDROID_HOME', 'C:/Android/sdk'))
JAVA = Path(os.environ.get('JAVA_HOME', '')) / 'bin'
TOOLS = SDK / 'build-tools/35.0.0'
JAR = SDK / 'platforms/android-35/android.jar'
# ASCII staging avoids Windows/Android tool failures under localized paths.
BUILD = Path(os.environ.get('DAEDNEXT_APP_BUILD', str(ANDROID / '.build/manager')))
BUILD.mkdir(parents=True, exist_ok=True)
PRIVATE = ANDROID / 'private'
DIST = ANDROID / 'dist'
DIST.mkdir(exist_ok=True)


def run(*args):
    subprocess.run([str(x) for x in args], check=True)


def binary(directory, name):
    return directory / (name + ('.exe' if os.name == 'nt' else ''))


signing = PRIVATE / 'manager-signing.json'
key = PRIVATE / 'manager-signing.p12'
if not args.unsigned and not signing.exists() and key.exists():
    raise SystemExit('Signing metadata is missing; recover it before rebuilding.')
fresh_signing = not signing.exists()
if not args.unsigned and not fresh_signing and not key.exists():
    raise SystemExit('Signing key is missing; recover the original key to preserve APK update identity.')
if not args.unsigned and fresh_signing:
    PRIVATE.mkdir(exist_ok=True)
    password = secrets.token_urlsafe(32)
    signing.write_text(json.dumps({'password': password}), encoding='utf8')
password_file = PRIVATE / 'manager-signing.password'
if not args.unsigned:
    password = json.loads(signing.read_text(encoding='utf8'))['password']
    password_file.write_text(password, encoding='utf8')
if not args.unsigned and not key.exists():
    run(binary(JAVA, 'keytool'), '-genkeypair', '-keystore', key, '-storetype', 'PKCS12',
        '-storepass:file', password_file, '-keypass:file', password_file, '-alias', 'daednext',
        '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
        '-dname', 'CN=DaedNext Manager, O=DaedNext, C=CN')

resources = BUILD / 'resources.zip'
generated = BUILD / 'generated'
classes = BUILD / 'classes'
dex = BUILD / 'dex'
for p in (generated, classes, dex):
    p.mkdir(exist_ok=True)
    assert p.resolve().is_relative_to(BUILD.resolve()), 'Build output escaped staging directory'
# Remove only generated class/dex files inside verified build directories.
for folder, pattern in ((classes, '*.class'), (dex, '*.dex')):
    for p in folder.rglob(pattern):
        assert p.resolve().is_relative_to(folder.resolve())
        p.unlink()
run(binary(TOOLS, 'aapt2'), 'compile', '--dir', ROOT / 'res', '-o', resources)
unsigned = BUILD / 'unsigned.apk'
run(binary(TOOLS, 'aapt2'), 'link', '-I', JAR, '--manifest', ROOT / 'AndroidManifest.xml',
    '--java', generated, '-o', unsigned, resources)
sources = list((ROOT / 'src').rglob('*.java')) + list(generated.rglob('*.java'))
argfile = BUILD / 'sources.txt'
argfile.write_text('\n'.join('"' + str(p).replace('\\', '/') + '"' for p in sources), encoding='utf8')
run(binary(JAVA, 'javac'), '-encoding', 'UTF-8', '-source', '8', '-target', '8',
    '-bootclasspath', str(JAR) + os.pathsep + str(TOOLS / 'core-lambda-stubs.jar'),
    '-d', classes, '@' + str(argfile))
class_jar = BUILD / 'classes.jar'
with zipfile.ZipFile(class_jar, 'w') as z:
    for p in classes.rglob('*.class'):
        z.write(p, p.relative_to(classes).as_posix())
run(binary(JAVA, 'java'), '-cp', TOOLS / 'lib/d8.jar', 'com.android.tools.r8.D8',
    '--min-api', '26', '--lib', JAR, '--output', dex, class_jar)
with zipfile.ZipFile(unsigned, 'a') as z:
    for p in dex.glob('classes*.dex'):
        z.write(p, p.name)
aligned = BUILD / 'aligned.apk'
run(binary(TOOLS, 'zipalign'), '-P', '16', '-f', '4', unsigned, aligned)
if args.unsigned:
    print(json.dumps({'unsignedApk': str(aligned), 'bytes': aligned.stat().st_size}))
    raise SystemExit(0)
version = ET.parse(ROOT / 'AndroidManifest.xml').getroot().get('{http://schemas.android.com/apk/res/android}versionName')
apk = DIST / f'DaedNext-Manager-v{version}.apk'
run(binary(JAVA, 'java'), '-jar', TOOLS / 'lib/apksigner.jar', 'sign', '--ks', key,
    '--ks-key-alias', 'daednext', '--ks-pass', 'file:' + str(password_file),
    '--out', apk, aligned)
run(binary(JAVA, 'java'), '-jar', TOOLS / 'lib/apksigner.jar', 'verify', '--print-certs', apk)
print(json.dumps({'apk': str(apk), 'bytes': apk.stat().st_size,
                  'sha256': hashlib.sha256(apk.read_bytes()).hexdigest()}))
