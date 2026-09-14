#!/usr/bin/env python3
"""Single-encoder ONNX dynamic-shape gate, split into export and ORT processes.

Uses real pinned weights, declared unmodified eager semantics, not FA2/windowed
semantics. Never loads a decoder. Reference arrays are saved outside Git.
"""
import argparse
import ast
import hashlib
import json
from pathlib import Path
import time

DEFAULT_SOURCE='.work/sources/MNN-a03b005cf6f888ebf092e4753840f935827f9c36/transformers/llm/export/utils/audio.py'
LENGTHS=[20,99,100,101,800,801]


def export(args):
    import torch
    import numpy as np
    from safetensors import safe_open
    from qwen_asr.core.transformers_backend.configuration_qwen3_asr import Qwen3ASRAudioEncoderConfig
    from qwen_asr.core.transformers_backend.modeling_qwen3_asr import Qwen3ASRAudioEncoder, SinusoidsPositionEmbedding
    torch.set_num_threads(2);torch.set_num_interop_threads(1);torch.manual_seed(42)
    out=Path(args.directory);out.mkdir(parents=True,exist_ok=True)
    model=Path('models/raw/Qwen3-ASR-0.6B')
    cfg=Qwen3ASRAudioEncoderConfig(**json.loads((model/'config.json').read_text())['thinker_config']['audio_config'])
    cfg._attn_implementation='eager'
    with torch.device('meta'):tower=Qwen3ASRAudioEncoder(cfg)
    state={};prefix='thinker.audio_tower.'
    with safe_open(model/'model.safetensors',framework='pt',device='cpu') as f:
        for key in f.keys():
            if key.startswith(prefix):state[key[len(prefix):]]=f.get_tensor(key).float()
    tower.load_state_dict(state,strict=True,assign=True);del state
    tower.positional_embedding=SinusoidsPositionEmbedding(cfg.max_source_positions,cfg.d_model)
    tower.eval()
    source=Path(args.source).read_text()
    node=next(n for n in ast.parse(source).body if isinstance(n,ast.ClassDef) and n.name=='Qwen3ASRAudioExportModel')
    ns={'torch':torch};exec(compile(ast.Module(body=[node],type_ignores=[]),args.source,'exec'),ns)
    wrapper=ns['Qwen3ASRAudioExportModel'](tower).eval()
    with torch.inference_mode():
        for n in LENGTHS:
            x=torch.randn(128,n)
            y=tower(x,feature_lens=torch.tensor([n])).last_hidden_state.unsqueeze(0)
            np.save(out/f'input-{n}.npy',x.numpy());np.save(out/f'expected-{n}.npy',y.numpy())
        started=time.monotonic()
        torch.onnx.export(wrapper,(torch.randn(128,801),),str(out/'audio.onnx'),
            input_names=['input_features'],output_names=['audio_embeds'],
            dynamic_axes={'input_features':{1:'valid_frames'},'audio_embeds':{1:'audio_tokens'}},
            do_constant_folding=True,opset_version=15,dynamo=False)
    metadata={'stage':'onnx-export','source_sha256':hashlib.sha256(source.encode()).hexdigest(),
        'dtype':'float32','opset':15,'trace_frames':801,'test_lengths':LENGTHS,
        'seconds':time.monotonic()-started,'reference':'official eager without explicit window mask'}
    (out/'export.json').write_text(json.dumps(metadata,indent=2)+'\n');print(json.dumps(metadata),flush=True)


def validate(args):
    import numpy as np
    import onnxruntime as ort
    directory=Path(args.directory)
    opts=ort.SessionOptions();opts.intra_op_num_threads=2;opts.inter_op_num_threads=1
    opts.graph_optimization_level=ort.GraphOptimizationLevel.ORT_DISABLE_ALL
    session=ort.InferenceSession(str(directory/'audio.onnx'),opts,providers=['CPUExecutionProvider'])
    rows=[]
    graph_hash=filehash(directory/'audio.onnx')
    # Repeat changing shapes on the SAME exported graph and session.
    for n in [801,20,99,100,101,800,801]:
        x=np.load(directory/f'input-{n}.npy');expected=np.load(directory/f'expected-{n}.npy')
        start=time.monotonic();actual=session.run(None,{'input_features':x})[0]
        if actual.shape!=expected.shape:raise RuntimeError(f'Shape mismatch at {n}: {actual.shape} != {expected.shape}')
        delta=actual-expected
        row={'frames':n,'shape':list(actual.shape),'max_abs':float(np.max(np.abs(delta))),
             'rmse':float(np.sqrt(np.mean(delta**2))),'relative_l2':float(np.linalg.norm(delta)/max(np.linalg.norm(expected),1e-12)),
             'finite':bool(np.isfinite(actual).all()),'seconds':time.monotonic()-start}
        # Conservative FP32 conversion gate, declared before running ORT.
        row['passed']=row['finite'] and row['max_abs']<=1e-4 and row['relative_l2']<=1e-3
        rows.append(row);print(json.dumps(row),flush=True)
        Path('reports/p0/onnx-audio-parity.json').write_text(json.dumps({
            'backend':'ORT CPU unoptimized vs official eager FP32','onnx_sha256':graph_hash,
            'limits':{'max_abs':1e-4,'relative_l2':1e-3},'results':rows},indent=2)+'\n')
    if not all(r['passed'] for r in rows):raise SystemExit(1)


def filehash(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for block in iter(lambda:f.read(4*1024*1024),b''):h.update(block)
    return h.hexdigest()


def main():
    p=argparse.ArgumentParser();p.add_argument('stage',choices=['export','validate'])
    p.add_argument('--directory',default='.work/onnx-audio-p0');p.add_argument('--source',default=DEFAULT_SOURCE)
    args=p.parse_args();globals()[args.stage](args)


if __name__=='__main__':main()
