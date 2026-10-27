import hashlib,json,re,sqlite3,stat,zipfile
from pathlib import Path
root=Path(__file__).resolve().parents[1]
lock=json.loads((root/'upstream.lock.json').read_text(encoding='utf8'))
module=root/'module'
out=root/'dist'
out.mkdir(exist_ok=True)
required=['module.prop','service.sh','action.sh','customize.sh','uninstall.sh','skip_mount','bin/daed','bin/daenextctl','lib/libc++_shared.so','defaults/daed.db','defaults/wifi-policy.json','webroot/index.html','panel/index.html','assets/geoip.dat','assets/geosite.dat']
assert all((module/f).is_file() for f in required)
db=sqlite3.connect(f'file:{module / "defaults/daed.db"}?mode=ro',uri=True)
for table in ('users','nodes','subscriptions'):
 assert db.execute('select count(*) from '+table).fetchone()[0]==0,table
assert db.execute('select running from systems').fetchone()[0]==0
db.close()
info={
 'coreCommit':lock['upstream']['commit'],
 'panelCommit':(root/'panel-commit.txt').read_text().strip(),
 'corePatchSha256':hashlib.sha256((root/'patches/android-complete.patch').read_bytes()).hexdigest(),
 'daemonSha256':hashlib.sha256((module/'bin/daed').read_bytes()).hexdigest(),
 'androidControlMark':16777216,
 'googleNetworkCompatibility':{'defaultEnabled':False,'frameworkSha256':'d6da8dd5cb56b780a98e1ab26c7284a974295eee0b8ad0107765c4949a62a36f','sdk':37},
 'kernelSUJavaScriptSdk':'3.0.2',
 'target':'aarch64-linux-android',
}
(module/'build-info.json').write_text(json.dumps(info,ensure_ascii=False,indent=2)+'\n',encoding='utf8',newline='\n')
for path in module.rglob('*'):
 if path.is_file() and (path.suffix in ('.sh','.prop','.js','.css','.html') or path.name=='daenextctl'):
  assert b'\r\n' not in path.read_bytes(),str(path)
manifest={str(p.relative_to(module)).replace('\\','/'):hashlib.sha256(p.read_bytes()).hexdigest() for p in sorted(module.rglob('*')) if p.is_file() and p.name!='SHA256SUMS'}
(module/'SHA256SUMS').write_text(''.join(f'{sha}  {name}\n' for name,sha in manifest.items()),encoding='utf8',newline='\n')
properties=dict(line.split('=',1) for line in (module/'module.prop').read_text(encoding='utf8').splitlines() if '=' in line)
version=properties['version']
assert re.fullmatch(r'[A-Za-z0-9._-]+',version)
dest=out/f'DaedNext-KernelSU-{version}-arm64.zip'
with zipfile.ZipFile(dest,'w',compression=zipfile.ZIP_DEFLATED,compresslevel=6) as z:
 for p in sorted(module.rglob('*')):
  if not p.is_file():continue
  name=p.relative_to(module).as_posix()
  info=zipfile.ZipInfo(name,(2026,10,25,12,0,0))
  executable=p.suffix=='.sh' or name.startswith('bin/')
  info.create_system=3
  info.external_attr=(stat.S_IFREG|(0o755 if executable else 0o644))<<16
  info.compress_type=zipfile.ZIP_DEFLATED
  z.writestr(info,p.read_bytes())
sha=hashlib.sha256(dest.read_bytes()).hexdigest()
print(json.dumps({'zip':str(dest),'bytes':dest.stat().st_size,'sha256':sha,'files':len(manifest)},ensure_ascii=False))
