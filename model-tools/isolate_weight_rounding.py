#!/usr/bin/env python3
"""Isolate FP16 weight storage rounding; retain official FP32 eager computation.

This does NOT emulate MNN fusion/KV/embedding tying; it tests rounding alone.
No tensor/model files are modified. Round one parameter at a time to limit RAM.
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
    import torch
    import soundfile as sf
    from qwen_asr import Qwen3ASRModel
    torch.set_num_threads(2);torch.set_num_interop_threads(1)
    model=Qwen3ASRModel.from_pretrained('models/raw/Qwen3-ASR-0.6B',dtype=torch.float32,
        device_map='cpu',attn_implementation='eager',low_cpu_mem_usage=True,
        max_inference_batch_size=1,max_new_tokens=128)
    counts={'parameters':0,'changed_elements':0,'max_weight_abs_delta':0.0}
    with torch.no_grad():
        for name,p in model.model.named_parameters():
            rounded=p.detach().to(torch.float16).to(torch.float32)
            if not torch.isfinite(rounded).all():raise RuntimeError('FP16 overflow/nonfinite '+name)
            counts['parameters']+=p.numel()
            counts['changed_elements']+=int((p!=rounded).sum())
            counts['max_weight_abs_delta']=max(counts['max_weight_abs_delta'],float((p-rounded).abs().max()))
            p.copy_(rounded);del rounded
    print(json.dumps(counts),flush=True)
    original_generate=model.model.generate;captured=[]
    def capture(*args,**kwargs):
        output=original_generate(*args,**kwargs)
        sequence=output.sequences if hasattr(output,'sequences') else output
        ids=sequence[0,kwargs['input_ids'].shape[1]:].tolist()
        eos=model.model.generation_config.eos_token_id
        eos=[eos] if isinstance(eos,int) else (eos or [])
        captured.append({'ids':ids,'tokens':len(ids),'last_token_is_eos':bool(ids and ids[-1] in eos)})
        return output
    model.model.generate=capture
    references={r['id']:r for r in map(json.loads,Path('reports/p0/frontend-isolation.jsonl').read_text().splitlines()) if r['branch']=='official_frontend'}
    cases=json.loads(Path('bench/datasets/p0-smoke.json').read_text())['cases']
    with Path('reports/p0/weight-rounding-isolation.jsonl').open('w') as stream:
        stream.write(json.dumps({'event':'weight_rounding','stats':counts,'policy':'all parameters FP32->FP16->FP32; buffers unchanged; not full MNN equivalence'})+'\n');stream.flush()
        for case in cases:
            if case['id'] not in references:continue
            path=Path(case['path'])
            if hashlib.sha256(path.read_bytes()).hexdigest()!=case['sha256']:raise RuntimeError('Audio changed')
            audio,sr=sf.read(path,dtype='float32');captured.clear();start=time.monotonic()
            with torch.inference_mode():result=model.transcribe(audio=(audio,sr),language=case['language'])[0]
            ref=references[case['id']]
            row={'event':'result','id':case['id'],'text':result.text,'generated':list(captured),
                 'same_text_as_original':result.text==ref['text'],
                 'same_ids_as_original':[r['ids'] for r in captured]==[r['ids'] for r in ref['generated']],
                 'seconds':time.monotonic()-start,'audio_sha256':case['sha256']}
            stream.write(json.dumps(row,ensure_ascii=False)+'\n');stream.flush();print(json.dumps(row,ensure_ascii=False),flush=True)


if __name__=='__main__':main()
