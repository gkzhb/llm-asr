#!/usr/bin/env python3
"""Pinned MNN Whisper periodic-Hann patch; other frontends retain defaults.

Adds a field to SpectrogramParams: rebuild all native consumers from source;
never mix patched headers and old binaries. Emits reviewable diff and hashes.
"""
from pathlib import Path
import difflib
import hashlib
import json

ROOT=Path(__file__).resolve().parents[1]
SOURCE=ROOT/'.work/sources/MNN-a03b005cf6f888ebf092e4753840f935827f9c36'
changes={
 'tools/audio/include/audio/audio.hpp':[
  ('    float power = 2.0;\n};','    float power = 2.0;\n\n    /** Periodic Hann opt-in; false preserves other frontend defaults. */\n    bool periodic_hann = false;\n};')],
 'tools/audio/source/audio.cpp':[
  ('    bool center = false, normalized = false;','    bool center = false, normalized = false, periodic_hann = false;'),
  ('        power = params->power;','        power = params->power;\n        periodic_hann = params->periodic_hann;'),
  ('            window = hann_window(win_length);','            window = hann_window(win_length, periodic_hann);'),
  ('    spec_params.center = true;\n    auto mel_specgram = mel_spectrogram(waveform, &mel_params, &spec_params);',
   '    spec_params.center = true;\n    // WhisperFeatureExtractor uses torch.hann_window(..., periodic=True).\n    spec_params.periodic_hann = true;\n    auto mel_specgram = mel_spectrogram(waveform, &mel_params, &spec_params);')]
}

def main():
    pending=[];diff=[];hashes=[]
    for rel,edits in changes.items():
        p=SOURCE/rel;before=p.read_text();after=before
        for old,new in edits:
            count=2 if old=='            window = hann_window(win_length);' else 1
            if after.count(old)!=count:raise RuntimeError(f'Unexpected pinned source: {rel}: {old}')
            after=after.replace(old,new)
        pending.append((p,after))
        diff.extend(difflib.unified_diff(before.splitlines(True),after.splitlines(True),fromfile='a/'+rel,tofile='b/'+rel))
        hashes.append({'file':rel,'before_sha256':hashlib.sha256(before.encode()).hexdigest(),'after_sha256':hashlib.sha256(after.encode()).hexdigest()})
    for p,s in pending:p.write_text(s)
    (ROOT/'patches/mnn-whisper-periodic-hann.patch').write_text(''.join(diff))
    (ROOT/'reports/p0/hann-patch-provenance.json').write_text(json.dumps(hashes,indent=2)+'\n')
    print('Patched Whisper periodic Hann; full native consumer rebuild required')

if __name__=='__main__':main()
