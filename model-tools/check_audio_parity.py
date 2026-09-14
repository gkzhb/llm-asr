#!/usr/bin/env python3
"""Test real audio encoder weights without loading the full language model.

Compares unmodified official eager and unmodified MNN export wrapper using the
same tower sequentially. No claim of FA2/windowed equivalence is made.
"""
import argparse
import ast
import hashlib
import json
from pathlib import Path
import time


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('--model',default='models/raw/Qwen3-ASR-0.6B')
    parser.add_argument('--source',default='.work/sources/MNN-a03b005cf6f888ebf092e4753840f935827f9c36/transformers/llm/export/utils/audio.py')
    parser.add_argument('--lengths',default='20,99,100,101,799,800,801')
    parser.add_argument('--output',default='reports/p0/audio-parity.json')
    args=parser.parse_args()
    import torch
    import torch.nn.functional as F
    from safetensors import safe_open
    from qwen_asr.core.transformers_backend.configuration_qwen3_asr import Qwen3ASRAudioEncoderConfig
    from qwen_asr.core.transformers_backend.modeling_qwen3_asr import Qwen3ASRAudioEncoder
    torch.set_num_threads(2);torch.set_num_interop_threads(1);torch.manual_seed(42)
    model_dir=Path(args.model)
    config_dict=json.loads((model_dir/'config.json').read_text())['thinker_config']['audio_config']
    config=Qwen3ASRAudioEncoderConfig(**config_dict)
    config._attn_implementation='eager'
    # Meta construction avoids allocating a randomly initialized full encoder first.
    with torch.device('meta'):
        tower=Qwen3ASRAudioEncoder(config)
    state={}
    prefix='thinker.audio_tower.'
    with safe_open(model_dir/'model.safetensors',framework='pt',device='cpu') as weights:
        for key in weights.keys():
            if key.startswith(prefix):state[key[len(prefix):]]=weights.get_tensor(key).float()
    tower.load_state_dict(state,strict=True,assign=True)
    del state
    # Non-persistent sinusoidal buffer is not part of safetensors state.
    from qwen_asr.core.transformers_backend.modeling_qwen3_asr import SinusoidsPositionEmbedding
    tower.positional_embedding=SinusoidsPositionEmbedding(config.max_source_positions,config.d_model)
    tower.eval()
    # Isolate the reviewed wrapper class; do not import the whole heavyweight exporter.
    text=Path(args.source).read_text()
    tree=ast.parse(text)
    node=next(n for n in tree.body if isinstance(n,ast.ClassDef) and n.name=='Qwen3ASRAudioExportModel')
    namespace={'torch':torch}
    exec(compile(ast.Module(body=[node],type_ignores=[]),args.source,'exec'),namespace)
    wrapper=namespace['Qwen3ASRAudioExportModel'](tower).eval()
    results=[]
    for length in map(int,args.lengths.split(',')):
        features=torch.randn(128,length)
        begin=time.monotonic()
        with torch.inference_mode():
            expected=tower(features,feature_lens=torch.tensor([length])).last_hidden_state.unsqueeze(0)
            actual=wrapper(features)
        delta=actual-expected
        metrics={'frames':length,'shape':list(expected.shape),'max_abs':delta.abs().max().item(),
            'rmse':delta.square().mean().sqrt().item(),
            'relative_l2':(delta.norm()/expected.norm().clamp_min(1e-12)).item(),
            'cosine':F.cosine_similarity(actual.flatten(),expected.flatten(),dim=0).item(),
            'seconds':time.monotonic()-begin,'finite':bool(torch.isfinite(actual).all())}
        print(json.dumps(metrics),flush=True);results.append(metrics)
        Path(args.output).parent.mkdir(parents=True,exist_ok=True)
        Path(args.output).write_text(json.dumps({'backend':'official-unmodified-eager-vs-MNN-wrapper-eager',
            'dtype':'float32','features':'seeded random mel tensors, not recognition accuracy',
            'source_sha256':hashlib.sha256(text.encode()).hexdigest(),'results':results},indent=2)+'\n')


if __name__=='__main__':main()
