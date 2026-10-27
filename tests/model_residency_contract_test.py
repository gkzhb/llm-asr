#!/usr/bin/env python3
"""Real production ModelResidency/adapter mutant contracts, ordinary and -O.
No Android/JNI runtime; native port is deliberately controlled.
"""
from pathlib import Path
import argparse,hashlib,json,os,sys
ROOT=Path(__file__).resolve().parents[1];sys.path.insert(0,str(ROOT/'tests'))
from mixed_compiler_lifecycle import run
p=argparse.ArgumentParser();p.add_argument('--classes',type=Path,required=True);p.add_argument('--evidence',type=Path,required=True);args=p.parse_args()
out=args.evidence.resolve();out.mkdir(parents=True,exist_ok=False);classes=args.classes.resolve();base=ROOT/'android/app/src/org/llmasr/minimal'
def require(v,m):
 if not v:raise RuntimeError(m)
names=['model/ModelResidency.kt','asr/InferenceAdapter.kt','asr/LoggingInferencePhaseListener.java']
source=out/'source';source.mkdir();sources=[]
for n in names:
 target=source/Path(n).name;target.write_bytes((base/n).read_bytes());sources.append(target)
main=source/'ResidencyContractEntry.java';main.write_text('public class ResidencyContractEntry {public static void main(String[] args){org.llmasr.minimal.InferenceAdapterTest.residencyContracts();}}\n');sources.append(main)
stdlib=Path(os.environ['KOTLIN_HOME'])/'lib/kotlin-stdlib.jar';count=0;results=[]
def compile_and_run(label,diagnostic=None):
 global count
 count+=1;require(count<=12,'compile budget exceeded')
 dest=out/('classes-'+label)
 result=run(['bash',str(ROOT/'scripts/compile-jvm-sources.sh'),str(dest),str(classes),*map(str,sources)],out,label+'-compile',180,dependency=classes)
 require(result.returncode==0,'compile/tool failure not red '+label+': '+result.stderr.decode())
 result=run(['java','-Xmx256m','-cp',str(dest)+':'+str(classes)+':'+str(stdlib),'ResidencyContractEntry'],out,label+'-jvm',30,dependency=classes)
 if diagnostic is None:require(result.returncode==0,'green failed '+label+': '+result.stderr.decode())
 else:require(result.returncode==1 and 'java.lang.AssertionError: '+diagnostic in result.stderr.decode(),'not exact red '+label+': '+result.stderr.decode())
 results.append({'label':label,'compile_exit':0,'jvm_exit':result.returncode,'diagnostic':diagnostic})
compile_and_run('green')
mutants=[
 ('admission','ModelResidency.kt','if (pending || maintenance != 0 || state != State.LOADED || generation != expected)','if (false)','resident admission missing'),
 ('pending','ModelResidency.kt','fun requestRelease() { synchronized(monitor) { releaseEpoch++; pending = true } }','fun requestRelease() { synchronized(monitor) { /* MUTANT pending ignored */ } }','finish not outer runner release'),
 ('parse','InferenceAdapter.kt','session!!.finish(owned, false)','session!!.finish(owned, true)','parse failure evicts'),
 ('warm','InferenceAdapter.kt','token = session.enter(); phases.residentReady()','token = session.enter()','warm3/4 no load telemetry'),
 ('asset','ModelResidency.kt','        try { drainRelease() }\n        catch (failure: Throwable) { synchronized(monitor) { maintenance-- }; throw failure }','        // MUTANT asset maintenance skips native release','maintenance unload before first asset write'),
]
for name,file,old,new,diag in mutants:
 path=source/file;original=path.read_bytes();s=original.decode();require(s.count(old)==1,'mutant anchor '+name);path.write_text(s.replace(old,new))
 (out/(name+'-input.json')).write_text(json.dumps({'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'path':str(path),'diagnostic':diag})+'\n')
 compile_and_run(name+'-mutant',diag);path.write_bytes(original);compile_and_run(name+'-restored')
(out/'verification.json').write_text(json.dumps({'results':results,'compile_count':count,'native_Android':False},indent=2)+'\n');print('PASS real residency/adapter five compiled mutants red/restored')
