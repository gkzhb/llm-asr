#!/usr/bin/env python3
"""Create a 20-case engineering smoke corpus from public Qwen example audio.

Derived cases are NOT 20 independent human-labeled accuracy samples. Oracle
transcripts measure backend parity, not human CER/WER. Raw files are gitignored.
"""
import hashlib
import json
from pathlib import Path
import subprocess
import wave
import numpy as np

ROOT = Path(__file__).resolve().parents[1]
OUT = ROOT / 'bench/audio'
MANIFEST = ROOT / 'bench/datasets/p0-smoke.json'


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def load_pcm(path):
    import math
    import soundfile as sf
    from scipy.signal import resample_poly
    audio, rate = sf.read(path, dtype='float64', always_2d=True)
    audio = audio.mean(axis=1)
    if rate != 16000:
        divisor = math.gcd(rate, 16000)
        audio = resample_poly(audio, 16000 // divisor, rate // divisor)
    return np.clip(np.rint(audio * 32768), -32768, 32767).astype('<i2')


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    sources = []
    signals = {}
    for lang in ('zh', 'en'):
        url = f'https://qianwen-res.oss-cn-beijing.aliyuncs.com/Qwen3-ASR-Repo/asr_{lang}.wav'
        path = OUT / f'upstream-{lang}.wav'
        if not path.exists():
            subprocess.run(['curl', '-fLsS', '--connect-timeout', '15', '--max-time', '90',
                            url, '-o', str(path)], check=True)
        signals[lang] = load_pcm(path)
        sources.append({'id':lang, 'url':url, 'sha256':sha(path),
                        'purpose':'public official inference example, local engineering testing only',
                        'normalization':'PCM16 mono; scipy resample_poly to 16kHz if needed'})
    cases = []

    def save(name, data, language, derivation):
        path = OUT / f'{name}.wav'
        with wave.open(str(path), 'wb') as w:
            w.setnchannels(1); w.setsampwidth(2); w.setframerate(16000)
            w.writeframes(np.asarray(data, dtype='<i2').tobytes())
        cases.append({'id':name, 'path':str(path.relative_to(ROOT)), 'language':language,
                      'samples':len(data), 'duration_s':len(data)/16000, 'sha256':sha(path),
                      'derivation':derivation, 'human_transcript':None})

    for lang, language in [('zh','Chinese'),('en','English')]:
        save(f'{lang}-original', signals[lang], language, f'public {lang} example normalized to mono16k PCM16')
    for n in [3200, 15840, 16000, 16160, 127840, 128000, 128160, 240000, 480000]:
        audio = np.resize(signals['zh'], n)
        save(f'zh-{n}', audio, 'Chinese', 'crop/tile public zh audio; boundary fixture, not independent speech')
    for n in [3200, 16000, 128160]:
        save(f'silence-{n}', np.zeros(n, dtype=np.int16), 'Chinese', 'synthetic silence')
    rng = np.random.default_rng(42)
    for sigma in [100, 500]:
        mixed = np.clip(signals['zh'].astype(np.float32)+rng.normal(0,sigma,len(signals['zh'])), -32768,32767)
        save(f'zh-noise-{sigma}', mixed, 'Chinese', 'public zh example plus seeded Gaussian noise (not music)')
    gap = np.zeros(4000,dtype=np.int16)
    save('zh-repeat', np.concatenate([signals['zh'], gap, signals['zh']]), 'Chinese', 'same phrase repeated with 250ms gap')
    save('zh-en', np.concatenate([signals['zh'], gap, signals['en']]), None, 'concatenated public zh/en examples, automatic language')
    save('zh-tail-silence', np.concatenate([signals['zh'], np.zeros(8000,dtype=np.int16)]), 'Chinese', 'real 500ms appended silence, valid audio')
    save('en-2s', np.resize(signals['en'],32000), 'English', 'cropped/tiled public English example')
    assert len(cases) == 20
    MANIFEST.parent.mkdir(parents=True, exist_ok=True)
    MANIFEST.write_text(json.dumps({'schema':1,'kind':'engineering-smoke-not-independent-accuracy',
        'limitations':['Only two independent public speech sources', 'No human labels',
                       'Does not cover music, dialect or independently labeled numbers/proper names'],
        'sources':sources,'cases':cases},ensure_ascii=False,indent=2)+'\n')
    print(f'Created {len(cases)} cases: {MANIFEST}')


if __name__ == '__main__':
    main()
