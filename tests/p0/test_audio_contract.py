import importlib.util
import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('contract', ROOT / 'model-tools/audio_contract.py')
contract = importlib.util.module_from_spec(spec)
spec.loader.exec_module(contract)


class AudioContractTest(unittest.TestCase):
    def test_boundaries(self):
        examples = {1: 1, 8: 1, 9: 2, 99: 13, 100: 13, 101: 14, 799: 104,
                    800: 104, 801: 105, 1600: 208, 3000: 390, 3001: 391}
        for frames, expected in examples.items():
            with self.subTest(frames=frames):
                self.assertEqual(contract.encoded_length(frames), expected)
                boundaries = contract.attention_boundaries(frames)
                self.assertEqual(boundaries[0], 0)
                self.assertEqual(boundaries[-1], expected)
                self.assertTrue(all(a < b for a, b in zip(boundaries, boundaries[1:])))
        self.assertEqual(contract.attention_boundaries(3000), [0, 104, 208, 312, 390])
        self.assertNotEqual(contract.cnn_boundaries(101), contract.attention_boundaries(101))

    def test_mask_contract_not_runtime(self):
        self.assertTrue(contract.same_window(12, 13))
        self.assertFalse(contract.same_window(103, 104))
        for q in range(210):
            self.assertTrue(contract.same_window(q, q))
            for k in (0, 13, 103, 104, 209):
                self.assertEqual(contract.same_window(q, k), contract.same_window(k, q))

    def test_empty_invalid(self):
        for frames in [0, -1, 1.5, True]:
            with self.assertRaises(ValueError):
                contract.encoded_length(frames)

    def test_actual_config(self):
        config = json.loads((ROOT / 'docs/research/qwen-asr-config.txt').read_text())['thinker_config']
        self.assertEqual(config['audio_config']['n_window'], 50)
        self.assertEqual(config['audio_config']['n_window_infer'], 800)
        self.assertEqual(config['audio_config']['num_mel_bins'], 128)
        self.assertEqual(config['audio_config']['output_dim'], 1024)
        text = config['text_config']
        kv = 2 * text['num_hidden_layers'] * text['num_key_value_heads'] * text['head_dim'] * 2
        self.assertEqual(kv, 114688)

    def test_prompt_newlines_and_mode(self):
        chinese = contract.render_prompt('Chinese')
        self.assertIn('system\n', chinese)
        self.assertIn('assistant\nlanguage Chinese<asr_text>', chinese)
        self.assertTrue(contract.render_prompt(None).endswith('assistant\n'))
        self.assertNotIn('language', contract.render_prompt(None))


if __name__ == '__main__':
    unittest.main()
