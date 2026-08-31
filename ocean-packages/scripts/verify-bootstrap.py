#!/usr/bin/env python3
import hashlib,json,pathlib,subprocess,sys,tarfile,tempfile
manifest_path,archive_path=map(pathlib.Path,sys.argv[1:3]); m=json.loads(manifest_path.read_text()); data=archive_path.read_bytes()
assert m['packageName']=='studio.ocean.app' and m['prefix']=='/data/data/studio.ocean.app/files/usr'
assert m['architecture']=='aarch64' and m['archiveSize']==len(data)
assert m['archiveSha256']==hashlib.sha256(data).hexdigest()
with tempfile.NamedTemporaryFile(suffix='.tar') as expanded:
 subprocess.run(['zstd','-q','-d','-c',archive_path],stdout=expanded,check=True); expanded.flush()
 tar=tarfile.open(expanded.name)
 names=set(tar.getnames()); assert len(names)==m['entryCount']
 for path in ('usr/bin/bash','usr/bin/apt','usr/bin/dpkg','usr/bin/pkg'): assert path in names, path
 for member in tar.getmembers():
  p=pathlib.PurePosixPath(member.name); assert not p.is_absolute() and '..' not in p.parts
assert {'bash','apt','dpkg','ocean-pkg','ocean-hello'} <= set(m['packageList'])
print(f"verified Ocean bootstrap {m['bootstrapVersion']} ({len(data)} bytes)")
