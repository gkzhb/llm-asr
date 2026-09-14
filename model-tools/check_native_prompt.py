#!/usr/bin/env python3
"""Compare actual native Omni prompt/audio expansion to official processor IDs."""
import json
from pathlib import Path
import re
import subprocess
import soundfile as sf
from qwen_asr.core.transformers_backend.processing_qwen3_asr import Qwen3ASRProcessor

processor=Qwen3ASRProcessor.from_pretrained('models/raw/Qwen3-ASR-0.6B',local_files_only=True)
rows=[]
for case in json.loads(Path('bench/datasets/p0-smoke.json').read_text())['cases']:
    if case['id'] not in ['en-original','zh-480000','zh-en']:continue
    audio,sr=sf.read(case['path'],dtype='float32')
    msgs=[{'role':'system','content':''},{'role':'user','content':[{'type':'audio','audio':''}]}]
    prompt=processor.apply_chat_template(msgs,add_generation_prompt=True,tokenize=False)
    if case['language']:prompt+=f"language {case['language']}<asr_text>"
    inputs=processor(text=[prompt],audio=[audio],return_tensors='np',padding=True)
    expected=inputs['input_ids'][0].tolist()
    run=subprocess.run(['.work/bin/host_prompt_gate','models/mnn-16/config.json',case['path'],case['language'] or 'auto'],
                       capture_output=True,text=True,timeout=180,check=True)
    match=re.search(r'P0_NATIVE_IDS (\[.*\])',run.stdout)
    if not match:raise RuntimeError('Native token evidence missing')
    actual=json.loads(match.group(1));first=next((i for i,(a,b) in enumerate(zip(actual,expected)) if a!=b),None)
    row={'id':case['id'],'actual_count':len(actual),'expected_count':len(expected),'equal':actual==expected,
         'first_difference':first,'native_ids':actual,'official_ids':expected}
    rows.append(row);print(json.dumps({k:v for k,v in row.items() if not k.endswith('_ids')}),flush=True)
    Path('reports/p0/native-prompt-parity.json').write_text(json.dumps(rows,indent=2)+'\n')
if not all(r['equal'] for r in rows):raise SystemExit(1)
