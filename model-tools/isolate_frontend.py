#!/usr/bin/env python3
"""Isolate residual frontend differences: official weights+decoder, swapped mel only.

Sequential single model. Both branches same greedy settings and input waveform.
Record generated token IDs/EOS rather than assuming reference wasn't truncated.
"""
import hashlib
import json
import os
from pathlib import Path
import time

os.environ.setdefault('HF_HUB_OFFLINE','1')
os.environ.setdefault('TRANSFORMERS_OFFLINE','1')
os.environ.setdefault('TOKENIZERS_PARALLELISM','false')


def main():
    import numpy as np
    import soundfile as sf
    import torch
    from qwen_asr import Qwen3ASRModel
    torch.set_num_threads(2);torch.set_num_interop_threads(1)
    model=Qwen3ASRModel.from_pretrained('models/raw/Qwen3-ASR-0.6B',dtype=torch.float32,
        device_map='cpu',attn_implementation='eager',low_cpu_mem_usage=True,
        max_inference_batch_size=1,max_new_tokens=128)
    extractor=model.processor.feature_extractor
    original_features=extractor._torch_extract_fbank_features
    original_generate=model.model.generate
    generated=[]
    def capture(*args,**kwargs):
        output=original_generate(*args,**kwargs)
        sequences=output.sequences if hasattr(output,'sequences') else output
        prompt_length=kwargs['input_ids'].shape[1]
        ids=sequences[0,prompt_length:].tolist()
        eos=model.model.generation_config.eos_token_id
        eos=[eos] if isinstance(eos,int) else (eos or [])
        generated.append({'ids':ids,'tokens':len(ids),'last_token_is_eos':bool(ids and ids[-1] in eos),
                          'hit_limit_without_eos':len(ids)>=128 and not (ids and ids[-1] in eos)})
        return output
    model.model.generate=capture
    cases=json.loads(Path('bench/datasets/p0-smoke.json').read_text())['cases']
    output=Path('reports/p0/frontend-isolation.jsonl')
    with output.open('w') as stream:
        for case in cases:
            if case['id'] not in ['en-original','zh-480000','zh-en']:continue
            path=Path(case['path'])
            if hashlib.sha256(path.read_bytes()).hexdigest()!=case['sha256']:raise RuntimeError('Audio hash mismatch')
            audio,sr=sf.read(path,dtype='float32')
            feature_path=Path('.work/feature-gate')/(case['id']+'.bin')
            feature_bytes=feature_path.read_bytes()
            injected=np.frombuffer(feature_bytes,dtype=np.float32).copy().reshape(1,128,-1)
            def swap(waveform,device='cpu'):
                # The official processor's single-item waveform is preserved; only mel changes.
                expected_frames=waveform.shape[-1]//extractor.hop_length
                if waveform.shape[0]!=1 or injected.shape!=(1,128,expected_frames):
                    raise RuntimeError(f'Feature injection shape mismatch {waveform.shape} vs {injected.shape}')
                return injected.copy()
            for branch in ['official_frontend','mnn_periodic_frontend']:
                extractor._torch_extract_fbank_features=original_features if branch=='official_frontend' else swap
                generated.clear();start=time.monotonic()
                with torch.inference_mode():
                    result=model.transcribe(audio=(audio,sr),language=case['language'])[0]
                row={'id':case['id'],'branch':branch,'text':result.text,'language':result.language,
                     'seconds':time.monotonic()-start,'generated':list(generated),'audio_sha256':case['sha256'],
                     'injected_feature_sha256':hashlib.sha256(feature_bytes).hexdigest() if branch!='official_frontend' else None,
                     'reference':'official FP32 eager; weights/decoder unchanged; max_new_tokens=128'}
                stream.write(json.dumps(row,ensure_ascii=False)+'\n');stream.flush()
                print(json.dumps(row,ensure_ascii=False),flush=True)
            extractor._torch_extract_fbank_features=original_features


if __name__=='__main__':main()
