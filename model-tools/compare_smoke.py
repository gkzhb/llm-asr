#!/usr/bin/env python3
"""Compare all declared engineering cases to official reference, without labels.

Each native process loads one model; language comes from each case. Results keep
failures and truncation rather than filtering them from the comparison.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re
import subprocess
import tempfile

from official_text_parser import parser as official_parser

ROOT=Path(__file__).resolve().parents[1]
MODEL=ROOT/'models/mnn-16'


def edit_distance(a,b):
    previous=list(range(len(b)+1))
    for i,x in enumerate(a,1):
        current=[i]
        for j,y in enumerate(b,1):
            current.append(min(current[-1]+1,previous[j]+1,previous[j-1]+(x!=y)))
        previous=current
    return previous[-1]


def main():
    cli=argparse.ArgumentParser()
    cli.add_argument('--ids',default='',help='Comma-separated subset for targeted retest')
    cli.add_argument('--tag',default='language-fixed')
    args=cli.parse_args()
    parse_output,parser_sha=official_parser()
    if not re.fullmatch(r'[a-zA-Z0-9_-]+',args.tag):raise ValueError('Invalid report tag')
    manifest=json.loads((ROOT/'bench/datasets/p0-smoke.json').read_text())
    refs={r['id']:r for r in map(json.loads,(ROOT/'reports/p0/reference-smoke-20.jsonl').read_text().splitlines()) if r.get('event')=='result'}
    if len(refs)!=20 or set(refs)!={c['id'] for c in manifest['cases']}:
        raise RuntimeError('Official reference incomplete')
    for case in manifest['cases']:
        if refs[case['id']].get('audio_sha256')!=case['sha256']:
            raise RuntimeError('Reference waveform identity mismatch: '+case['id'])
    def digest_file(path):
        h=hashlib.sha256()
        with path.open('rb') as f:
            for block in iter(lambda:f.read(4*1024*1024),b''):h.update(block)
        return h.hexdigest()
    provenance={str(p.relative_to(ROOT)):digest_file(p) for p in [
        ROOT/'.work/bin/host_p0_asr',ROOT/'.work/build/mnn-host/libMNN.so',
        ROOT/'reports/p0/reference-smoke-20.jsonl',ROOT/'reports/p0/mnn-model-manifest.json',
        *sorted((ROOT/'patches').glob('*.patch'))]}
    cases=manifest['cases']
    if args.ids:
        selected=set(args.ids.split(','))
        if not selected.issubset(refs):raise ValueError('Unknown case IDs')
        cases=[c for c in cases if c['id'] in selected]
    config=json.loads((MODEL/'config.json').read_text())
    logs=ROOT/f'reports/p0/mnn-smoke-logs-{args.tag}';logs.mkdir(parents=True,exist_ok=True)
    results=[]
    output=ROOT/f'reports/p0/mnn-smoke-{args.tag}.jsonl'
    with output.open('w') as stream:
        for case in cases:
            audio=ROOT/case['path']
            if hashlib.sha256(audio.read_bytes()).hexdigest()!=case['sha256']:
                raise RuntimeError('Audio changed')
            # Base directory must stay beside model assets; do not edit baseline config.
            effective={**config,'asr_language':case['language'] or ''}
            with tempfile.NamedTemporaryFile(mode='w',prefix='p0-smoke-',suffix='.json',dir=MODEL) as cfg:
                json.dump(effective,cfg);cfg.flush()
                try:
                    run=subprocess.run([str(ROOT/'.work/bin/host_p0_asr'),cfg.name,str(audio),case['language'] or 'auto'],
                        cwd=ROOT,capture_output=True,text=True,timeout=240)
                    text=run.stdout+'\n'+run.stderr;code=run.returncode
                except subprocess.TimeoutExpired as exc:
                    def decode(v):return v.decode(errors='replace') if isinstance(v,bytes) else (v or '')
                    text=decode(exc.stdout)+'\n'+decode(exc.stderr);code=124
            (logs/(case['id']+'.txt')).write_text(text)
            matched=re.search(r'P0_TEXT_BEGIN\n(.*?)\nP0_TEXT_END',text,re.S)
            metrics_match=re.search(r'P0_METRICS (.*)',text)
            raw=matched.group(1).strip() if matched else None
            parsed_language,actual=parse_output(raw,case['language']) if raw is not None else ('',None)
            metrics=json.loads(metrics_match.group(1)) if metrics_match else None
            expected=refs[case['id']]['text']
            row={'id':case['id'],'audio_sha256':case['sha256'],'language':case['language'],
                'exit_code':code,'reference_text':expected,'mnn_text':actual,'raw_mnn_output':raw,
                'parsed_language':parsed_language,'parser_sha256':parser_sha,'metrics':metrics,
                'complete':code==0 and metrics is not None and metrics['status']==1 and not metrics['truncated'],
                'exact_text_match':actual==expected,
                'reference_char_distance':edit_distance(expected,actual) if actual is not None else None,
                'note':'Backend agreement, NOT human-label CER or phone performance'}
            results.append(row);stream.write(json.dumps(row,ensure_ascii=False)+'\n');stream.flush()
            print(json.dumps(row,ensure_ascii=False),flush=True)
    summary={'provenance_sha256':provenance,'parser_sha256':parser_sha,'cases':len(results),'complete':sum(r['complete'] for r in results),
             'exact_matches':sum(r['exact_text_match'] for r in results),
             'different_or_failed':[r['id'] for r in results if not r['complete'] or not r['exact_text_match']],
             'limitations':['Two independent speech sources plus derived fixtures','No human labels',
                            'Official reference does not expose token-limit termination; long reference may be truncated',
                            'Native host processes, not APK or device batch']}
    (ROOT/f'reports/p0/mnn-smoke-summary-{args.tag}.json').write_text(json.dumps(summary,indent=2)+'\n')
    print(json.dumps(summary),flush=True)


if __name__=='__main__':main()
