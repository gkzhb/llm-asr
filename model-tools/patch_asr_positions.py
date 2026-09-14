#!/usr/bin/env python3
"""Count ASR boundary tokens in mRoPE state; leave other audio models unchanged.

Regression: model-tools/check_positions.py on opt-in runtime trace must fail
before patch (decode position 42 vs44) and pass afterward.
"""
from pathlib import Path
import difflib
import hashlib
import json
ROOT=Path(__file__).resolve().parents[1]
REL='transformers/llm/engine/src/omni.cpp'
p=ROOT/'.work/sources/MNN-a03b005cf6f888ebf092e4753840f935827f9c36'/REL
old='''    int embed_len = audio_embedding->getInfo()->dim[0];
    addPositionIds(embed_len);
    std::vector<int> audio_ids(embed_len, mAudioPad);'''
new='''    int embed_len = audio_embedding->getInfo()->dim[0];
    // Qwen3-ASR uses sequential positions for audio embeddings AND boundary tokens.
    // Missing boundaries left decode mRoPE two positions behind the KV sequence.
    int position_count = embed_len;
    if (audio_type == "qwen3_asr") {
        position_count += (mAudioStart >= 0 ? 1 : 0) + (mAudioEnd >= 0 ? 1 : 0);
    }
    addPositionIds(position_count);
    std::vector<int> audio_ids(embed_len, mAudioPad);'''

def main():
    before=p.read_text()
    if before.count(old)!=1:raise RuntimeError('Pinned audio position fragment missing/nonunique')
    after=before.replace(old,new);p.write_text(after)
    (ROOT/'patches/mnn-asr-boundary-positions.patch').write_text(''.join(difflib.unified_diff(
        before.splitlines(True),after.splitlines(True),fromfile='a/'+REL,tofile='b/'+REL,n=3)))
    (ROOT/'reports/p0/position-patch-provenance.json').write_text(json.dumps({
        'file':REL,'before_sha256':hashlib.sha256(before.encode()).hexdigest(),
        'after_sha256':hashlib.sha256(after.encode()).hexdigest(),
        'note':'Hashes include temporary diagnostic trace; production trace removal is separate'},indent=2)+'\n')
    print('Applied ASR-only audio boundary position fix')

if __name__=='__main__':main()
