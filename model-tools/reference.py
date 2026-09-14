#!/usr/bin/env python3
"""Sequential low-memory official eager reference; preserve raw outputs and timing."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import resource
import time

os.environ.setdefault('TOKENIZERS_PARALLELISM','false')
os.environ.setdefault('HF_HUB_OFFLINE','1')
os.environ.setdefault('TRANSFORMERS_OFFLINE','1')


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--model',default='models/raw/Qwen3-ASR-0.6B')
    parser.add_argument('--manifest',default='bench/datasets/p0-smoke.json')
    parser.add_argument('--output',default='reports/p0/reference.jsonl')
    parser.add_argument('--limit',type=int,default=0)
    parser.add_argument('--threads',type=int,default=2)
    parser.add_argument('--max-tokens',type=int,default=128)
    args=parser.parse_args()
    import torch
    import transformers
    import soundfile as sf
    from qwen_asr import Qwen3ASRModel
    torch.set_num_threads(args.threads)
    torch.set_num_interop_threads(1)
    cases=json.loads(Path(args.manifest).read_text())['cases']
    if args.limit: cases=cases[:args.limit]
    output=Path(args.output);output.parent.mkdir(parents=True,exist_ok=True)
    t=time.monotonic()
    # CPU fp32 is explicit: do not assume this host has performant bfloat16.
    model=Qwen3ASRModel.from_pretrained(args.model, dtype=torch.float32,
        device_map='cpu', attn_implementation='eager', low_cpu_mem_usage=True,
        max_inference_batch_size=1, max_new_tokens=args.max_tokens)
    load_s=time.monotonic()-t
    metadata={'event':'reference_config','backend':'official-unmodified-eager',
        'window_mask_applied':False,'dtype':'float32','torch':torch.__version__,
        'transformers':transformers.__version__,'threads':args.threads,'load_s':load_s,
        'model_lock':json.loads(Path('model-tools/model-lock.json').read_text()),
        'max_new_tokens':args.max_tokens}
    with output.open('w') as f:
        f.write(json.dumps(metadata,ensure_ascii=False)+'\n');f.flush()
        for case in cases:
            path=Path(case['path'])
            if hashlib.sha256(path.read_bytes()).hexdigest()!=case['sha256']:
                raise ValueError(f"Audio hash changed: {path}")
            audio,sr=sf.read(path,dtype='float32')
            if sr!=16000 or audio.ndim!=1: raise ValueError('Expected mono 16kHz')
            start=time.monotonic()
            with torch.inference_mode():
                results=model.transcribe(audio=(audio,sr),language=case['language'])
            elapsed=time.monotonic()-start
            result={'event':'result','id':case['id'],'audio_sha256':case['sha256'],
                'duration_s':len(audio)/sr,'inference_s':elapsed,
                'rtf':elapsed/(len(audio)/sr),'text':results[0].text,
                'language':results[0].language,'host_maxrss_kib':resource.getrusage(resource.RUSAGE_SELF).ru_maxrss,
                'note':'host CPU; not phone performance, no human-label accuracy claim'}
            f.write(json.dumps(result,ensure_ascii=False)+'\n');f.flush()
            print(json.dumps(result,ensure_ascii=False),flush=True)


if __name__=='__main__':main()
