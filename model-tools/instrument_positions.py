#!/usr/bin/env python3
"""Install/remove opt-in P0 position diagnostics, no semantic changes.

Enable only with P0_TRACE_POSITIONS=1 in a diagnostic host run. Remove before
final runtime build. No phone/user audio captured; only integer positions.
"""
import argparse
from pathlib import Path

SOURCE=Path('.work/sources/MNN-a03b005cf6f888ebf092e4753840f935827f9c36/transformers/llm/engine/src/omni.cpp')
ANCHOR='    // // dump position ids\n'
BLOCK='''    // P0_POSITION_TRACE_BEGIN (temporary, opt-in host diagnostics)
    if (mConfig->audio_type() == "qwen3_asr" && std::getenv("P0_TRACE_POSITIONS")) {
        std::fprintf(stderr, "P0_POSITIONS gen=%d all=%d axes=%d seq=%d values=",
                     mContext->gen_seq_len, mContext->all_seq_len, axes, seq_len);
        for (int axis = 0; axis < axes; ++axis) {
            for (int i = 0; i < seq_len; ++i) {
                std::fprintf(stderr, "%s%d", (axis || i) ? "," : "", ptr[axis * seq_len + i]);
            }
        }
        std::fprintf(stderr, "\\n");
    }
    // P0_POSITION_TRACE_END
'''


def main():
    p=argparse.ArgumentParser();p.add_argument('action',choices=['install','remove']);args=p.parse_args()
    text=SOURCE.read_text()
    includes='#include <cstdlib> // P0_POSITION_TRACE\n#include <cstdio> // P0_POSITION_TRACE\n'
    if args.action=='install':
        if 'P0_POSITION_TRACE_BEGIN' in text:raise RuntimeError('Already instrumented')
        if text.count(ANCHOR)!=1:raise RuntimeError('Anchor not unique')
        text=text.replace('#include <regex>\n','#include <regex>\n'+includes).replace(ANCHOR,BLOCK+ANCHOR)
    else:
        if text.count(BLOCK)!=1:raise RuntimeError('Unexpected trace block')
        text=text.replace(BLOCK,'').replace(includes,'')
    SOURCE.write_text(text)
    print(args.action,'position trace')


if __name__=='__main__':main()
