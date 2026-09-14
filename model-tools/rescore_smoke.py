#!/usr/bin/env python3
"""Re-score saved raw native outputs using the same text parser as reference."""
import json
from pathlib import Path
from official_text_parser import parser
from compare_smoke import edit_distance

parse,sha=parser()
base=[json.loads(l) for l in Path('reports/p0/mnn-smoke-20.jsonl').read_text().splitlines()]
updates={r['id']:r for r in map(json.loads,Path('reports/p0/mnn-smoke-language-fixed-subset.jsonl').read_text().splitlines())}
rows=[]
for old in base:
    row=dict(updates.get(old['id'],old))
    raw=row['mnn_text'];lang,text=parse(raw,row['language']) if raw is not None else ('',None)
    row.update(raw_mnn_output=raw,parsed_mnn_text=text,parsed_language=lang,
               parsed_exact_match=text==row['reference_text'],
               parsed_reference_char_distance=edit_distance(row['reference_text'],text) if text is not None else None,
               source_run='language-fixed-subset' if old['id'] in updates else 'initial-chinese')
    rows.append(row)
summary={'cases':20,'parsed_exact_matches':sum(r['parsed_exact_match'] for r in rows),
         'remaining_differences':[r['id'] for r in rows if not r['parsed_exact_match']],
         'parser_sha256':sha,'note':'Composite evidence from initial Chinese cases and language-fixed subset; not a single fresh full run, not human CER'}
Path('reports/p0/mnn-smoke-parsed.json').write_text(json.dumps({'summary':summary,'results':rows},ensure_ascii=False,indent=2)+'\n')
print(json.dumps(summary,ensure_ascii=False))
