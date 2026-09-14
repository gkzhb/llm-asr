#!/usr/bin/env python3
"""Export only decoder/tokenizer/config, keeping validated audio graph untouched.

Use reviewed upstream exporter object, remove audio module after its configuration
has been captured. Preserve ONNX evidence; no all-model export() cleanup step.
"""
import argparse
import gc
import hashlib
import json
import os
from pathlib import Path
import sys

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'.work/sources/MNN-a03b005cf6f888ebf092e4753840f935827f9c36'
OUT=ROOT/'models/mnn-16'


def digest(path):
    h=hashlib.sha256()
    with path.open('rb') as f:
        for chunk in iter(lambda:f.read(4*1024*1024),b''):h.update(chunk)
    return h.hexdigest()


def finalize(audio_hashes=None):
    config=json.loads((OUT/'config.json').read_text())
    tokenizer=config.get('tokenizer_file')
    if not isinstance(tokenizer,str) or Path(tokenizer).name!=tokenizer:
        raise RuntimeError('Invalid tokenizer path in runtime config')
    required=['audio.mnn','audio.mnn.weight','llm.mnn','llm.mnn.weight',
              tokenizer,'llm_config.json','config.json']
    for name in required:
        if not (OUT/name).is_file() or (OUT/name).stat().st_size==0:
            raise RuntimeError('Missing artifact: '+name)
    cfg=json.loads((OUT/'llm_config.json').read_text())
    if not cfg.get('is_audio') or cfg.get('audio_type')!='qwen3_asr':
        raise RuntimeError('Missing Qwen3-ASR audio configuration')
    if audio_hashes:
        for name,expected in audio_hashes.items():
            if digest(OUT/name)!=expected:raise RuntimeError('Validated audio artifact changed')
    records=[{'file':name,'bytes':(OUT/name).stat().st_size,'sha256':digest(OUT/name)} for name in required]
    (ROOT/'reports/p0/mnn-model-manifest.json').write_text(json.dumps({'kind':'P0-float-baseline-not-yet-device-validated','files':records},indent=2)+'\n')
    print('P0_ARTIFACT_CHECK_COMPLETE',flush=True)


def main():
    cli=argparse.ArgumentParser()
    cli.add_argument('--finalize-existing',action='store_true',help='Validate existing conversion output without loading/exporting model')
    options=cli.parse_args()
    if options.finalize_existing:
        finalize()
        return
    # An incremental/empty result file must never satisfy the gate.
    gate=json.loads((ROOT/'reports/p0/mnn-audio-parity.json').read_text())
    if {r['frames'] for r in gate['results']}!={20,99,100,101,800,801} or not all(r['passed'] for r in gate['results']):
        raise RuntimeError('MNN audio gate incomplete or failed')
    audio_paths=[OUT/'audio.mnn',OUT/'audio.mnn.weight']
    audio_hashes={p.name:digest(p) for p in audio_paths}
    converter=ROOT/'.work/build/mnn-host/MNNConvert'
    if not converter.is_file():raise RuntimeError('Matched converter missing')
    sys.path.insert(0,str(SOURCE/'transformers/llm/export'))
    import torch
    from llmexport import LlmExporter, build_args
    from utils.mnn_converter import MNNConverter
    torch.set_num_threads(2);torch.set_num_interop_threads(1)
    parser=argparse.ArgumentParser();build_args(parser)
    args=parser.parse_args(['--path',str(ROOT/'models/raw/Qwen3-ASR-0.6B'),
        '--export','mnn','--dst_path',str(OUT),'--quant_bit','16','--lm_quant_bit','16',
        '--embed_bit','16','--disable_transformer_c4','--mnnconvert',str(converter)])
    # Upstream writes .export.log in cwd. Keep it in ignored output, not project root.
    os.chdir(OUT)
    with torch.inference_mode():
        exporter=LlmExporter(args)
        if not exporter.llm_config.get('is_audio') or exporter.llm_config.get('audio_type')!='qwen3_asr':
            raise RuntimeError('Audio config not captured before freeing encoder')
        exporter.model.audio=None
        exporter.audio=None
        gc.collect()
        print('P0: audio module released; exporting decoder only',flush=True)
        exporter.mnn_converter=MNNConverter(exporter)
        exporter.export_language()
        exporter.export_tokenizer()
        exporter.export_config(True)
    config_path=OUT/'config.json'
    config=json.loads(config_path.read_text())
    config.update({'backend_type':'cpu','thread_num':2,'precision':'high','memory':'low',
                   'sampler_type':'greedy','max_new_tokens':128,'async':False,
                   'mllm':{'backend_type':'cpu','thread_num':2,'precision':'high','memory':'low'}})
    config_path.write_text(json.dumps(config,indent=2)+'\n')
    finalize(audio_hashes)
    print('P0_DECODER_EXPORT_COMPLETE',flush=True)


if __name__=='__main__':main()
