#!/usr/bin/env python3
"""Keep official decoder; inject real MNN audio-encoder embeddings for 3 cases.

Run native encoder first in separate process, then one official model. Record
both upstream-frontend and MNN-frontend embeddings to distinguish stages.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import subprocess
import time
os.environ.setdefault('HF_HUB_OFFLINE','1')
os.environ.setdefault('TRANSFORMERS_OFFLINE','1')
os.environ.setdefault('TOKENIZERS_PARALLELISM','false')
IDS={'en-original':1505,'zh-480000':3000,'zh-en':1950}
ROOT=Path('.work/encoder-isolation')


def prepare():
    import numpy as np
    import soundfile as sf
    from transformers import WhisperFeatureExtractor
    f=WhisperFeatureExtractor.from_pretrained('models/raw/Qwen3-ASR-0.6B',local_files_only=True)
    cases={c['id']:c for c in json.loads(Path('bench/datasets/p0-smoke.json').read_text())['cases']}
    for name,n in IDS.items():
        case=cases[name];path=Path(case['path'])
        if hashlib.sha256(path.read_bytes()).hexdigest()!=case['sha256']:raise RuntimeError('Audio changed')
        audio,sr=sf.read(path,dtype='float32')
        features=f(audio,sampling_rate=sr,padding=True,truncation=False,return_attention_mask=True,return_tensors='np')
        length=int(features['attention_mask'][0].sum())
        if length!=n:raise RuntimeError('Feature length changed')
        for branch in ['official_mel','mnn_mel']:
            directory=ROOT/name/branch;directory.mkdir(parents=True,exist_ok=True)
            inp=directory/f'input-{n}.bin'
            if branch=='official_mel':features['input_features'][0,:,:n].astype('<f4').tofile(inp)
            else:inp.write_bytes((Path('.work/feature-gate')/(name+'.bin')).read_bytes())
            subprocess.run(['.work/bin/host_audio_gate','models/mnn-16/audio.mnn',str(directory),str(n)],check=True,timeout=180)


def infer():
    import numpy as np
    import torch
    import soundfile as sf
    from qwen_asr import Qwen3ASRModel
    from transformers.modeling_outputs import BaseModelOutput
    from audio_contract import encoded_length
    torch.set_num_threads(2);torch.set_num_interop_threads(1)
    model=Qwen3ASRModel.from_pretrained('models/raw/Qwen3-ASR-0.6B',dtype=torch.float32,
        device_map='cpu',attn_implementation='eager',low_cpu_mem_usage=True,
        max_inference_batch_size=1,max_new_tokens=128)
    tower=model.model.thinker.audio_tower
    upstream=tower.forward
    references={r['id']:r for r in map(json.loads,Path('reports/p0/frontend-isolation.jsonl').read_text().splitlines()) if r['branch']=='official_frontend'}
    original_generate=model.model.generate;generated=[]
    def capture(*args,**kwargs):
        out=original_generate(*args,**kwargs)
        ids=out.sequences[0,kwargs['input_ids'].shape[1]:].tolist()
        generated.append(ids);return out
    model.model.generate=capture
    cases={c['id']:c for c in json.loads(Path('bench/datasets/p0-smoke.json').read_text())['cases']}
    with Path('reports/p0/encoder-isolation.jsonl').open('w') as stream:
        for name,n in IDS.items():
            case=cases[name];audio,sr=sf.read(case['path'],dtype='float32')
            for branch in ['official_mel','mnn_mel']:
                file=ROOT/name/branch/f'actual-{n}.bin'
                data=np.fromfile(file,dtype='<f4').reshape(encoded_length(n),1024)
                embedding=torch.from_numpy(data.copy());calls=[]
                def injected(input_features,feature_lens=None,**kwargs):
                    if feature_lens is None or feature_lens.tolist()!=[n]:raise RuntimeError('Injection length mismatch')
                    with torch.inference_mode():ref=upstream(input_features,feature_lens=feature_lens).last_hidden_state
                    d=embedding-ref
                    calls.append({'max_abs':float(d.abs().max()),'relative_l2':float(d.norm()/ref.norm())})
                    return BaseModelOutput(last_hidden_state=embedding)
                tower.forward=injected;generated.clear();start=time.monotonic()
                with torch.inference_mode():result=model.transcribe(audio=(audio,sr),language=case['language'])[0]
                row={'id':name,'branch':'MNN_encoder_'+branch,'text':result.text,'generated_ids':list(generated),
                     'same_text_as_official':result.text==references[name]['text'],
                     'same_ids_as_official':generated==[r['ids'] for r in references[name]['generated']],
                     'encoder_delta_vs_official_input':calls,'seconds':time.monotonic()-start,
                     'embedding_sha256':hashlib.sha256(file.read_bytes()).hexdigest(),
                     'note':'official decoder retained; MNN FP16-stored encoder output injected'}
                stream.write(json.dumps(row,ensure_ascii=False)+'\n');stream.flush();print(json.dumps(row,ensure_ascii=False),flush=True)
            tower.forward=upstream


if __name__=='__main__':
    p=argparse.ArgumentParser();p.add_argument('stage',choices=['prepare','infer']);args=p.parse_args()
    globals()[args.stage]()
