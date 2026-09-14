"""Single-utterance contracts derived from the pinned Qwen3-ASR config.

These are specification helpers, not claims that an inference backend uses a mask.
"""


def encoded_length(frames: int) -> int:
    if not isinstance(frames, int) or isinstance(frames, bool) or frames <= 0:
        raise ValueError('positive integer valid-frame count required')
    chunks, tail = divmod(frames, 100)
    return chunks * 13 + (tail + 7) // 8


def attention_boundaries(frames: int) -> list[int]:
    length = encoded_length(frames)
    # A short single CNN block remains a single attention block.
    return list(range(0, length, 104)) + [length]


def cnn_boundaries(frames: int) -> list[int]:
    length = encoded_length(frames)
    return list(range(0, length, 13)) + [length]


def same_window(query: int, key: int) -> bool:
    if query < 0 or key < 0:
        raise ValueError('negative token index')
    return query // 104 == key // 104


def render_prompt(language: str | None, audio: str = '<|audio_start|><|audio_pad|><|audio_end|>') -> str:
    prompt = '<|im_start|>system\n<|im_end|>\n<|im_start|>user\n' + audio
    prompt += '<|im_end|>\n<|im_start|>assistant\n'
    if language:
        if language not in ('Chinese', 'English'):
            raise ValueError('P0 prompt contract covers Chinese/English/automatic only')
        prompt += f'language {language}<asr_text>'
    return prompt
