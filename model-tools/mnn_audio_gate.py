#!/usr/bin/env python3
"""FP16-stored MNN encoder gate vs existing FP32 official reference arrays."""
import argparse
import json
from pathlib import Path
import numpy as np

LENGTHS=[801,20,99,100,101,800]

def main():
    p=argparse.ArgumentParser();p.add_argument('stage',choices=['prepare','validate']);args=p.parse_args()
    root=Path('.work/onnx-audio-p0')
    if args.stage=='prepare':
        for n in LENGTHS:np.load(root/f'input-{n}.npy').astype('<f4').tofile(root/f'input-{n}.bin')
        return
    rows=[]
    for n in LENGTHS:
        expected=np.load(root/f'expected-{n}.npy')
        actual=np.fromfile(root/f'actual-{n}.bin',dtype='<f4')
        if actual.size!=expected.size:raise RuntimeError(f'Wrong element count at {n}')
        actual=actual.reshape(expected.shape);delta=actual-expected
        row={'frames':n,'max_abs':float(np.max(np.abs(delta))),
             'relative_l2':float(np.linalg.norm(delta)/max(np.linalg.norm(expected),1e-12)),
             'finite':bool(np.isfinite(actual).all())}
        # Relaxed from FP32 gate specifically for FP16 weight storage; not ASR quality.
        row['passed']=row['finite'] and row['max_abs']<=0.002 and row['relative_l2']<=0.02
        rows.append(row)
    Path('reports/p0/mnn-audio-parity.json').write_text(json.dumps({'reference':'official eager FP32',
        'backend':'matched-commit MNN CPU, high precision, FP16 stored weights',
        'limits':{'max_abs':0.002,'relative_l2':0.02},'results':rows},indent=2)+'\n')
    print(json.dumps(rows),flush=True)
    if not all(r['passed'] for r in rows):raise SystemExit(1)

if __name__=='__main__':main()
