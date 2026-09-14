#!/usr/bin/env python3
"""Convert using reviewed pinned MNN source and matched native converter.

Run only after reference/parity gates; full float16 weights first (not INT4).
"""
import argparse
import json
from pathlib import Path
import subprocess
import sys

ROOT=Path(__file__).resolve().parents[1]


def main():
    p=argparse.ArgumentParser();p.add_argument('--bits',type=int,choices=[4,8,16],default=16)
    p.add_argument('--format',choices=['onnx','mnn'],default='mnn')
    args=p.parse_args()
    source=ROOT/'.work/sources/MNN-a03b005cf6f888ebf092e4753840f935827f9c36'
    converter=ROOT/'.work/build/mnn-host/MNNConvert'
    if args.format=='mnn' and not converter.is_file():raise RuntimeError('Build matched MNNConvert first')
    parity=json.loads((ROOT/'reports/p0/audio-parity-patched.json').read_text())
    expected_lengths={20,99,100,101,800,801}
    if {r['frames'] for r in parity['results']} != expected_lengths:
        raise RuntimeError('Incomplete Python encoder parity evidence')
    if not all(r['max_abs']<1e-5 and r['finite'] for r in parity['results']):
        raise RuntimeError('Patched encoder Python parity not cleared')
    onnx=json.loads((ROOT/'reports/p0/onnx-audio-parity.json').read_text())
    if [r['frames'] for r in onnx['results']] != [801,20,99,100,101,800,801]:
        raise RuntimeError('Incomplete ONNX dynamic-shape evidence')
    if not all(r['passed'] for r in onnx['results']):
        raise RuntimeError('ONNX encoder parity not cleared')
    prompts=json.loads((ROOT/'reports/p0/prompt-parity.json').read_text())
    if len(prompts)!=3 or not all(r['equal'] for r in prompts):
        raise RuntimeError('Prompt token parity not cleared')
    output=ROOT/f'models/mnn-{args.bits}'
    output.mkdir(parents=True,exist_ok=True)
    command=[sys.executable,str(source/'transformers/llm/export/llmexport.py'),
        '--path',str(ROOT/'models/raw/Qwen3-ASR-0.6B'), '--export',args.format,
        '--dst_path',str(output),'--quant_bit',str(args.bits),'--lm_quant_bit',str(args.bits),
        '--embed_bit','16','--disable_transformer_c4','--mnnconvert',str(converter)]
    print('Running:',command,flush=True)
    subprocess.run(command,cwd=source/'transformers/llm/export',check=True)
    if args.format=='mnn':
        config_path=output/'config.json';config=json.loads(config_path.read_text())
        config.update({'backend_type':'cpu','thread_num':2,'precision':'high','memory':'low',
                       'sampler_type':'greedy','max_new_tokens':128,'async':False,
                       'mllm':{'backend_type':'cpu','thread_num':2,'precision':'high','memory':'low'}})
        config_path.write_text(json.dumps(config,indent=2)+'\n')
        if not (output/'audio.mnn').exists():raise RuntimeError('Audio graph missing')
        if not (output/'llm.mnn').exists():raise RuntimeError('Decoder graph missing')
    print('Export completed:',output,flush=True)


if __name__=='__main__':main()
