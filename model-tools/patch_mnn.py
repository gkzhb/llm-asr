#!/usr/bin/env python3
"""Apply narrowly reviewed P0 fixes to the pinned local MNN working copy.

Does NOT add an attention mask or change the upstream eager oracle semantics.
Source diff is saved for review; exact original fragments must match once.
"""
from pathlib import Path
import difflib
import hashlib
import json

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'.work/sources/MNN-a03b005cf6f888ebf092e4753840f935827f9c36'
PATCH=ROOT/'patches/mnn-p0.patch'


def main():
    changes={
        'transformers/llm/export/llmexport.py':[
            ('"{%- if add_generation_prompt and content is string and \'<audio>\' in content and \'</audio>\' in content -%}"', '"{% if add_generation_prompt and content is string and \'<audio>\' in content and \'</audio>\' in content %}"'),
            ('            "{%- endif -%}"\n        )\n\n    @spinner_run', '            "{% endif %}"\n        )\n\n    @spinner_run'),
            ('"<|im_start|>system<|im_end|>"', '"<|im_start|>system\\n<|im_end|>\\n"'),
            ('"<|im_start|>user{{ content }}<|im_end|>"','"<|im_start|>user\\n{{ content }}<|im_end|>\\n"'),
            ('"<|im_start|>assistantlanguage {{ asr_language }}<asr_text>"',
             '"<|im_start|>assistant\\n{% if asr_language %}language {{ asr_language }}<asr_text>{% endif %}"'),
        ],
        'transformers/llm/export/utils/audio.py':[
            ('        chunk_size = self.chunk_size\n        seq_len = input_features.shape[-1]\n',
             '        # P0: preserve official single-short-chunk convolution geometry dynamically.\n'
             '        seq_len = input_features.shape[-1]\n'
             '        chunk_size = torch.minimum(torch._shape_as_tensor(input_features)[-1],\n'
             '                                   torch.tensor(self.chunk_size, dtype=torch.long))\n'),
            ('        cu_seqlens = torch.nn.functional.pad(aftercnn_lens.cumsum(0).to(torch.int32), (1, 0), value=0)\n',
             '        # P0: correct metadata only. Eager still has no mask, matching upstream eager.\n'
             '        total_tokens = hidden_states.shape[0]\n'
             '        window_tokens = self.chunk_output_size * (self.audio_tower.n_window_infer // self.chunk_size)\n'
             '        starts = torch.arange(0, total_tokens, window_tokens, dtype=torch.int32, device=input_features.device)\n'
             '        end = torch._shape_as_tensor(hidden_states)[0:1].to(device=input_features.device, dtype=torch.int32)\n'
             '        cu_seqlens = torch.cat([starts, end])\n'),
            ('''        input_features = self.feature_extractor(
            audio_obj,
            sampling_rate=self.sampling_rate,
            return_attention_mask=True,
            return_tensors='pt'
        )['input_features'][0].to(dtype=torch.float32)
''', '''        features = self.feature_extractor(
            audio_obj,
            sampling_rate=self.sampling_rate,
            padding=True,
            truncation=False,
            return_attention_mask=True,
            return_tensors='pt'
        )
        valid_frames = int(features['attention_mask'][0].sum().item())
        if valid_frames <= 0:
            raise ValueError('P0 requires nonempty audio')
        input_features = features['input_features'][0, :, :valid_frames].to(dtype=torch.float32)
'''),
        ]
    }
    diffs=[]; provenance=[]
    for relative,replacements in changes.items():
        path=SOURCE/relative
        original=path.read_text();updated=original
        for old,new in replacements:
            if updated.count(old)!=1:
                raise RuntimeError(f'Pinned source fragment not unique or already patched: {relative}: {old[:65]!r}')
            updated=updated.replace(old,new)
        # Validate Python syntax before any write.
        compile(updated,str(path),'exec')
        diffs.extend(difflib.unified_diff(original.splitlines(True),updated.splitlines(True),
            fromfile='a/'+relative,tofile='b/'+relative))
        provenance.append({'path':relative,'before_sha256':hashlib.sha256(original.encode()).hexdigest(),
            'after_sha256':hashlib.sha256(updated.encode()).hexdigest()})
        path.write_text(updated)
    PATCH.parent.mkdir(parents=True,exist_ok=True)
    PATCH.write_text(''.join(diffs))
    (ROOT/'reports/p0/patch-provenance.json').write_text(json.dumps(provenance,indent=2)+'\n')
    print(PATCH)


if __name__=='__main__':main()
