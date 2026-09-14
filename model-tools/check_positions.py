#!/usr/bin/env python3
"""Assert single-audio Qwen3-ASR positions are ordinary sequential text positions."""
import json
from pathlib import Path
import re

text=Path('reports/p0/position-trace.txt').read_text()
rows=[]
for gen,allseq,axes,seq,values in re.findall(r'P0_POSITIONS gen=(\d+) all=(\d+) axes=(\d+) seq=(\d+) values=([\d,]+)',text):
    gen,allseq,axes,seq=map(int,(gen,allseq,axes,seq));actual=list(map(int,values.split(',')))
    expected=list(range(allseq,allseq+seq))*axes
    rows.append({'generated':gen,'all_seq_len':allseq,'axes':axes,'seq':seq,'equal':actual==expected,
                 'first_difference':next((i for i,(a,b) in enumerate(zip(actual,expected)) if a!=b),None),
                 'actual':actual,'expected':expected})
if not rows:raise RuntimeError('No runtime position trace captured')
if sum(line.startswith('P0_POSITIONS ') for line in text.splitlines())!=len(rows):
    raise RuntimeError('Malformed or dropped position trace line')
terminal=re.findall(r'^P0_METRICS (.*)$',text,re.M)
if len(terminal)!=1:
    raise RuntimeError('Missing unique terminal metrics')
metrics=json.loads(terminal[0])
if metrics.get('status')!=1 or metrics.get('truncated') is not False:
    raise RuntimeError('Trace run did not finish normally')
prefill=[r for r in rows if r['generated']==0]
decode=[r for r in rows if r['generated']>0]
if len(prefill)!=1 or prefill[0]['seq']<=1 or len(decode)<2:
    raise RuntimeError('Incomplete trace: one prefill and at least two decode steps required')
if [r['generated'] for r in decode]!=list(range(1,len(decode)+1)):
    raise RuntimeError('Missing or reordered decode steps')
for i,row in enumerate(rows):
    if row['axes']!=3 or len(row['actual'])!=row['axes']*row['seq']:
        raise RuntimeError('Invalid position tensor dimensions')
    if i and (row['seq']!=1 or row['all_seq_len']!=prefill[0]['all_seq_len']+prefill[0]['seq']+i-1):
        raise RuntimeError('Inconsistent decode history length')
Path('reports/p0/position-parity.json').write_text(json.dumps(rows,indent=2)+'\n')
print(json.dumps([{k:v for k,v in r.items() if k not in ('actual','expected')} for r in rows]))
if not all(r['equal'] for r in rows):raise SystemExit(1)
