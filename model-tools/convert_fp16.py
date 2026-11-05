#!/usr/bin/env python3
"""Task-safe decoder-only FP16 export using an externally validated audio gate.

No downloads or trust-manifest writes. Encoder/ORT/MNN gates run separately;
explicit input/source/converter/output/evidence paths avoid historical roots.
"""
import argparse
import gc
import hashlib
import json
import os
from pathlib import Path
import subprocess
import sys


def digest(path):
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(65536), b''):
            value.update(block)
    return value.hexdigest()


def validate_gate(path, output):
    gate = json.loads(path.read_text())
    rows = gate.get('results', [])
    if len(rows) != 6 or {row['frames'] for row in rows} != {20, 99, 100, 101, 800, 801}:
        raise ValueError('complete current six-length audio gate required')
    if not all(row.get('passed') and row.get('finite') and row['max_abs'] <= 0.002
               and row['relative_l2'] <= 0.02 for row in rows):
        raise ValueError('current MNN encoder numerical gate failed')
    assets=gate.get('artifacts',[])
    if len(assets)!=2 or {e['file'] for e in assets}!={'audio.mnn','audio.mnn.weight'}:
        raise ValueError('audio gate must bind current graph and external weight SHA')
    for entry in assets:
        asset=output/entry['file']
        if asset.stat().st_size!=entry['bytes'] or digest(asset)!=entry['sha256']:
            raise ValueError('audio gate refers to different current encoder bytes')


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ('model', 'source', 'converter', 'output', 'audio-gate', 'evidence'):
        parser.add_argument('--' + name, type=Path, required=True)
    args = parser.parse_args()
    for key, value in vars(args).items():
        setattr(args, key, value.resolve())
    validate_gate(args.audio_gate,args.output)
    if not args.converter.is_file():
        raise ValueError('matched converter missing')
    args.evidence.mkdir(parents=True, exist_ok=True)
    args.output.mkdir(parents=True, exist_ok=True)
    forbidden = ['llm.mnn', 'llm.mnn.weight', 'config.json', 'llm_config.json', 'tokenizer.txt', 'tokenizer.mtok']
    if any((args.output / name).exists() for name in forbidden):
        raise ValueError('refusing to overwrite existing decoder output')
    audio_hashes = {name: digest(args.output / name) for name in ('audio.mnn', 'audio.mnn.weight')}
    sys.path.insert(0, str(args.source / 'transformers/llm/export'))
    import torch
    from llmexport import LlmExporter, build_args
    from utils.mnn_converter import MNNConverter
    torch.set_num_threads(2)
    torch.set_num_interop_threads(1)
    cli = argparse.ArgumentParser()
    build_args(cli)
    options = cli.parse_args(['--path', str(args.model), '--export', 'mnn', '--dst_path', str(args.output),
                             '--quant_bit', '16', '--lm_quant_bit', '16', '--embed_bit', '16',
                             '--disable_transformer_c4', '--mnnconvert', str(args.converter)])
    # Replace upstream os.popen shell invocation, which hides converter nonzero exit.
    # Converter math/arguments stay upstream; actual process must succeed and be reaped.
    conversions = []
    def checked_convert(self, convert_args):
        command = [str(args.converter), *map(str, convert_args[1:])]
        with (args.evidence / 'decoder-converter.log').open('ab') as log:
            process = subprocess.run(command, stdout=log, stderr=subprocess.STDOUT, timeout=1200)
        conversions.append({'argv': command, 'exit': process.returncode})
        (args.evidence / 'decoder-converters.json').write_text(json.dumps(conversions, indent=2) + '\n')
        if process.returncode:
            raise RuntimeError('matched converter failed: ' + str(process.returncode))
    MNNConverter.convert = checked_convert
    os.chdir(args.output)
    with torch.inference_mode():
        exporter = LlmExporter(options)
        if not exporter.llm_config.get('is_audio') or exporter.llm_config.get('audio_type') != 'qwen3_asr':
            raise ValueError('Qwen3-ASR audio metadata absent')
        exporter.model.audio = None
        exporter.audio = None
        gc.collect()
        exporter.mnn_converter = MNNConverter(exporter)
        exporter.export_language()
        exporter.export_tokenizer()
        exporter.export_config(True)
    config_path = args.output / 'config.json'
    config = json.loads(config_path.read_text())
    config.update({'backend_type': 'cpu', 'thread_num': 2, 'precision': 'high', 'memory': 'low',
                   'sampler_type': 'greedy', 'max_new_tokens': 128, 'async': False,
                   'mllm': {'backend_type': 'cpu', 'thread_num': 2, 'precision': 'high', 'memory': 'low'}})
    config_path.write_text(json.dumps(config, indent=2) + '\n')
    tokenizer = config.get('tokenizer_file')
    if tokenizer != 'tokenizer.txt':
        raise ValueError('current seven-asset baseline requires tokenizer.txt, got ' + str(tokenizer))
    for name, value in audio_hashes.items():
        if digest(args.output / name) != value:
            raise ValueError('validated encoder changed during decoder export')
    names = ['audio.mnn', 'audio.mnn.weight', 'llm.mnn', 'llm.mnn.weight',
             'tokenizer.txt', 'llm_config.json', 'config.json']
    records = []
    for name in names:
        path = args.output / name
        if not path.is_file() or path.stat().st_size <= 0:
            raise ValueError('missing deployment artifact: ' + name)
        records.append({'file': name, 'bytes': path.stat().st_size, 'sha256': digest(path)})
    report = {'stage': 'decoder-export-only-not-quality-approval', 'bits': 16, 'files': records,
              'model': str(args.model), 'source': str(args.source), 'converter_sha256': digest(args.converter),
              'audio_gate_sha256': digest(args.audio_gate), 'converter_processes': conversions}
    destination = args.evidence / 'exported-assets.json'
    with destination.open('x') as stream:
        json.dump(report, stream, indent=2)
        stream.write('\n')
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
