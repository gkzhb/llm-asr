#!/usr/bin/env python3
"""Compare prompt bytes and IDs using actual pinned tokenizer assets (no weights)."""
import ast
import json
from pathlib import Path

from audio_contract import render_prompt


def main():
    from transformers import AutoTokenizer
    model=Path('models/raw/Qwen3-ASR-0.6B')
    tokenizer=AutoTokenizer.from_pretrained(model,trust_remote_code=False,local_files_only=True)
    official=json.loads((model/'chat_template.json').read_text())['chat_template']
    source=Path('.work/sources/MNN-a03b005cf6f888ebf092e4753840f935827f9c36/transformers/llm/export/llmexport.py')
    tree=ast.parse(source.read_text())
    cls=next(x for x in tree.body if isinstance(x,ast.ClassDef) and x.name=='LlmExporter')
    func=next(x for x in cls.body if isinstance(x,ast.FunctionDef) and x.name=='qwen3_asr_chat_template')
    mnn_template=ast.literal_eval(func.body[0].value)
    rows=[]
    for language in ['Chinese','English',None]:
        messages=[{'role':'system','content':''},{'role':'user','content':[{'type':'audio','audio':''}]}]
        prompt=tokenizer.apply_chat_template(messages,chat_template=official,tokenize=False,add_generation_prompt=True)
        if language:prompt+=f'language {language}<asr_text>'
        expected=render_prompt(language)
        if prompt!=expected:raise AssertionError('Contract differs from official assets')
        # MNN substitutes audio tags after template rendering. Compare after equivalent replacement.
        mnn=tokenizer.apply_chat_template([{'role':'user','content':'<audio>sample.wav</audio>'}],
            chat_template=mnn_template,tokenize=False,add_generation_prompt=True,asr_language=language or '')
        mnn=mnn.replace('<audio>sample.wav</audio>','<|audio_start|><|audio_pad|><|audio_end|>')
        expected_ids=tokenizer.encode(prompt,add_special_tokens=False)
        actual_ids=tokenizer.encode(mnn,add_special_tokens=False)
        rows.append({'language':language,'equal':expected_ids==actual_ids,'expected':prompt,'mnn':mnn,
                     'expected_ids':expected_ids,'mnn_ids':actual_ids})
    output=Path('reports/p0/prompt-parity.json')
    output.write_text(json.dumps(rows,ensure_ascii=False,indent=2)+'\n')
    print(json.dumps([{'language':r['language'],'equal':r['equal']} for r in rows]))
    if not all(r['equal'] for r in rows):raise SystemExit(1)


if __name__=='__main__':main()
