"""Load ONLY reviewed pure text parsing functions from installed pinned qwen-asr.

Avoid importing PyTorch/optional alignment to parse saved native output. Preserve
raw output separately; this is reference postprocessing, not a model output fix.
"""
import ast
import hashlib
from importlib.metadata import distribution, version
from pathlib import Path
from typing import Optional, Tuple


def parser():
    if version('qwen-asr') != '0.0.6':
        raise RuntimeError('Expected locked qwen-asr 0.0.6 parser')
    source=Path(distribution('qwen-asr').locate_file('qwen_asr/inference/utils.py'))
    text=source.read_text();tree=ast.parse(text)
    names={'normalize_language_name','detect_and_fix_repetitions','parse_asr_output'}
    nodes=[n for n in tree.body if isinstance(n,ast.FunctionDef) and n.name in names]
    constants={}
    for node in tree.body:
        if isinstance(node,ast.AnnAssign) and isinstance(node.target,ast.Name):
            if node.target.id=='SUPPORTED_LANGUAGES': constants[node.target.id]=ast.literal_eval(node.value)
        if isinstance(node,ast.Assign):
            for target in node.targets:
                if isinstance(target,ast.Name) and target.id in {'_ASR_TEXT_TAG','_LANG_PREFIX'}:
                    constants[target.id]=ast.literal_eval(node.value)
    if len(nodes)!=3:raise RuntimeError('Upstream parser function set changed')
    ns={'Optional':Optional,'Tuple':Tuple,**constants}
    exec(compile(ast.Module(body=nodes,type_ignores=[]),str(source),'exec'),ns)
    return ns['parse_asr_output'],hashlib.sha256(text.encode()).hexdigest()
