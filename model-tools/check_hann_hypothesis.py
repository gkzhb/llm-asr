#!/usr/bin/env python3
"""Test one frontend hypothesis: symmetric versus periodic Hann, same audio/mel."""
import json
from pathlib import Path
import numpy as np
import soundfile as sf
import torch
from transformers import WhisperFeatureExtractor

torch.set_num_threads(2)
f=WhisperFeatureExtractor.from_pretrained('models/raw/Qwen3-ASR-0.6B',local_files_only=True)
rows=[]
for case in json.loads(Path('bench/datasets/p0-smoke.json').read_text())['cases']:
    path=Path('.work/feature-gate')/(case['id']+'.bin')
    if not path.exists():continue
    audio,sr=sf.read(case['path'],dtype='float32');x=torch.from_numpy(audio)
    actual=np.fromfile(path,dtype=np.float32).reshape(128,-1)
    for periodic in (True,False):
        spec=torch.stft(x,400,160,window=torch.hann_window(400,periodic=periodic),return_complex=True)
        power=spec[...,:-1].abs().square()
        mel=torch.from_numpy(f.mel_filters.astype(np.float32)).T@power
        log=mel.clamp_min(1e-10).log10();log=torch.maximum(log,log.max()-8)
        expected=((log+4)/4).numpy();delta=actual-expected
        rows.append({'id':case['id'],'periodic':periodic,'max_abs':float(np.max(np.abs(delta))),
                     'rmse':float(np.sqrt(np.mean(delta**2))),
                     'relative_l2':float(np.linalg.norm(delta)/np.linalg.norm(expected))})
Path('reports/p0/hann-hypothesis.json').write_text(json.dumps(rows,indent=2)+'\n')
print(json.dumps(rows),flush=True)
