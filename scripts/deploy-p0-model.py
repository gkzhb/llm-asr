#!/usr/bin/env python3
"""Deploy only manifest-verified P0 model files to the authorized temp directory."""
import hashlib
import json
from pathlib import Path
import subprocess
import tempfile

SERIAL='100.64.0.3:33317'
REMOTE='/data/local/tmp/qwen-asr-p0'


def adb(*args, timeout=30):
    return subprocess.run(['adb','-s',SERIAL,*args],check=True,text=True,capture_output=True,timeout=timeout).stdout


def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda:f.read(4*1024*1024),b''):h.update(chunk)
    return h.hexdigest()


def remote_hash(path):
    result=adb('shell',f'if [ -f {path} ]; then sha256sum {path}; fi',timeout=180).split()
    return result[0] if result else None


def transfer(local, dest, expected):
    if remote_hash(dest)==expected:
        print('REMOTE CACHED',local.name,flush=True)
        return
    if local.stat().st_size < 64*1024*1024:
        print(adb('push','-Z',str(local),dest,timeout=180),flush=True)
    else:
        # Persistent chunks allow safe restart; never infer completion from size alone.
        chunk_dir=dest+'.p0chunks'
        adb('shell',f'mkdir -p {chunk_dir}')
        chunks=[]
        with local.open('rb') as stream:
            index=0
            while True:
                data=stream.read(16*1024*1024)
                if not data:break
                remote=f'{chunk_dir}/{index:05d}'
                chunk_hash=hashlib.sha256(data).hexdigest()
                if remote_hash(remote)!=chunk_hash:
                    with tempfile.NamedTemporaryFile(prefix='p0-transfer-',suffix='.bin') as temp:
                        temp.write(data);temp.flush()
                        print('CHUNK PUSH',local.name,index,len(data),flush=True)
                        adb('push','-Z',temp.name,remote,timeout=240)
                    if remote_hash(remote)!=chunk_hash:
                        raise RuntimeError(f'Chunk verification failed: {remote}')
                print('CHUNK VERIFIED',local.name,index,flush=True)
                chunks.append(remote);index+=1
        # Explicit file list excludes stale chunks from earlier, larger model versions.
        adb('shell',f"cat {' '.join(chunks)} > {dest}.p0assembling",timeout=180)
        if remote_hash(dest+'.p0assembling')!=expected:
            raise RuntimeError('Assembled artifact mismatch: '+local.name)
        adb('shell',f'mv {dest}.p0assembling {dest}',timeout=30)
        # Retain chunks for now; no deletion outside the dedicated test directory.
    if remote_hash(dest)!=expected:
        raise RuntimeError('Remote artifact mismatch: '+local.name)


def main():
    manifest=json.loads(Path('reports/p0/mnn-model-manifest.json').read_text())
    subprocess.run(['adb','connect',SERIAL],check=True,timeout=20)
    if adb('get-state').strip()!='device':raise RuntimeError('Device not authorized')
    print(adb('shell','df -k /data/local/tmp'),flush=True)
    # Keep >3 GiB headroom besides files to deploy.
    fields=adb('shell','df -k /data/local/tmp').strip().splitlines()[-1].split()
    available=int(fields[3])*1024
    total=sum(f['bytes'] for f in manifest['files'])
    # Allow assembled files plus retained chunks and 3GiB safety headroom.
    if available<2*total+3*1024**3:raise RuntimeError('Insufficient phone free space')
    adb('shell',f'mkdir -p {REMOTE}/model')
    records=[]
    for item in manifest['files']:
        name=item['file']
        if not name or any(c not in 'abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789._-' for c in name):
            raise ValueError('Invalid manifest filename')
        local=Path('models/mnn-16')/name
        if local.stat().st_size!=item['bytes'] or digest(local)!=item['sha256']:
            raise RuntimeError('Local artifact mismatch: '+name)
        dest=f'{REMOTE}/model/{name}'
        transfer(local,dest,item['sha256'])
        actual=item['sha256']
        records.append({'file':name,'sha256':actual,'verified':True})
        print('REMOTE VERIFIED',name,flush=True)
    for local,dest in [(Path('.work/bin/p0_asr'),'p0_asr'),(Path('.work/bin/libMNN.so'),'libMNN.so'),
                       (Path('bench/audio/zh-original.wav'),'sample.wav')]:
        expected=digest(local)
        print(adb('push',str(local),f'{REMOTE}/{dest}',timeout=60),flush=True)
        actual=adb('shell',f'sha256sum {REMOTE}/{dest}',timeout=30).split()[0]
        if actual!=expected:raise RuntimeError('Remote runtime/audio mismatch')
        records.append({'file':dest,'sha256':actual,'verified':True})
    adb('shell',f'chmod 700 {REMOTE}/p0_asr')
    Path('reports/p0/device-deployment.json').write_text(json.dumps({'serial':SERIAL,'remote':REMOTE,'files':records},indent=2)+'\n')
    print('P0_DEPLOYMENT_VERIFIED',flush=True)


if __name__=='__main__':main()
