#!/usr/bin/env python3
"""Compare actual MNN whisper_fbank vs locked official feature extractor."""
import json
from pathlib import Path
import subprocess
import numpy as np
import soundfile as sf
from transformers import WhisperFeatureExtractor

root=Path('.work/feature-gate');root.mkdir(parents=True,exist_ok=True)
feature=WhisperFeatureExtractor.from_pretrained('models/raw/Qwen3-ASR-0.6B',local_files_only=True)
manifest=json.loads(Path('bench/datasets/p0-smoke.json').read_text())
rows=[]
for case in manifest['cases']:
    if case['id'] not in ['zh-original','en-original','zh-480000','zh-en','zh-3200']:continue
    audio,sr=sf.read(case['path'],dtype='float32')
    output=feature(audio,sampling_rate=sr,padding=True,truncation=False,return_attention_mask=True,return_tensors='np')
    length=int(output['attention_mask'][0].sum());expected=output['input_features'][0,:,:length]
    binary=root/(case['id']+'.bin')
    proc=subprocess.run(['.work/bin/host_feature_gate',case['path'],str(binary)],capture_output=True,text=True,timeout=60,check=True)
    actual=np.fromfile(binary,dtype=np.float32)
    row={'id':case['id'],'expected_shape':list(expected.shape),'mnn_elements':int(actual.size),'native_log':proc.stdout.strip()}
    if actual.size==expected.size:
        actual=actual.reshape(expected.shape);delta=actual-expected
        row.update(max_abs=float(np.abs(delta).max()),rmse=float(np.sqrt(np.mean(delta**2))),
                   relative_l2=float(np.linalg.norm(delta)/max(np.linalg.norm(expected),1e-12)))
        row['passed']=row['max_abs']<1e-4 and row['relative_l2']<1e-4
    else:row['passed']=False
    rows.append(row);print(json.dumps(row),flush=True)
    Path('reports/p0/feature-parity.json').write_text(json.dumps({'thresholds':{'max_abs':1e-4,'relative_l2':1e-4},'results':rows},indent=2)+'\n')
if not all(r['passed'] for r in rows):raise SystemExit(1)
