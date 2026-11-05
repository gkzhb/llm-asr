"""Real maintained CLI with tiny native-protocol fixtures; never real-model evidence."""
import contextlib
import hashlib
import copy
import importlib.util
import io
import json
import os
from pathlib import Path
import signal
import subprocess
import sys
import tempfile
import unittest
import wave

sys.path.insert(0, str(Path(__file__).resolve().parent))
import compare_mnn_models as tool


class ComparisonTests(unittest.TestCase):
    def test_normalization_and_distances(self):
        self.assertEqual(tool.normalize(' ＡＢＣ， Cat! '), 'abc cat')
        self.assertEqual(tool.edit_counts('A cat sat', 'a dog sat', 'English')['edits'], 1)
        self.assertEqual(tool.edit_counts('甲乙丙', '甲丙', 'Chinese')['edits'], 1)
        self.assertEqual(tool.edit_counts('', 'insert', 'English')['rate'], None)
        self.assertEqual(tool.edit_counts('', 'insert', 'English')['empty_reference_insertions'], 1)
        self.assertEqual(tool.edit_counts('one', '', 'English')['rate'], 1)
        with self.assertRaises(ValueError):
            tool.edit_counts('a', 'b', None)

    def test_spread_and_paired_order(self):
        self.assertEqual(tool.spread([1, 2, 9]), {'n': 3, 'median': 2, 'min': 1, 'max': 9, 'mad': 1})
        self.assertIsNone(tool.spread([]))
        items = tool.schedule(['c1', 'c2'], ['c1'], 3)
        self.assertEqual([r[3] for r in items], list('ABBAABBAAB'))
        self.assertEqual(len(items), 10)

    def fixture(self, root, mode='good'):
        # Exact public audited configuration bytes, not a model/weight fixture.
        base_bytes = '{\n  "llm_model": "llm.mnn",\n  "llm_weight": "llm.mnn.weight",\n  "backend_type": "cpu",\n  "thread_num": 2,\n  "precision": "high",\n  "memory": "low",\n  "sampler_type": "greedy",\n  "temperature": 0.8,\n  "top_k": 40,\n  "top_p": 0.9,\n  "min_p": 0.05,\n  "tfs_z": 1.0,\n  "typical": 0.95,\n  "repetition_penalty": 1.0,\n  "presence_penalty": 0.0,\n  "frequency_penalty": 0.0,\n  "penalty_window": 0,\n  "n_gram": 8,\n  "ngram_factor": 1.0,\n  "tokenizer_file": "tokenizer.txt",\n  "max_new_tokens": 128,\n  "async": false,\n  "mllm": {\n    "backend_type": "cpu",\n    "thread_num": 2,\n    "precision": "high",\n    "memory": "low"\n  }\n}\n'.encode()
        metadata_bytes = '{\n    "model_type": "qwen3_asr",\n    "hidden_size": 1024,\n    "layer_nums": 28,\n    "attention_mask": "float",\n    "attention_type": "full",\n    "is_mrope": true,\n    "mrope_axes": 3,\n    "is_audio": true,\n    "audio_type": "qwen3_asr",\n    "audio_start": 151669,\n    "audio_end": 151670,\n    "audio_pad": 151676,\n    "asr_language": "Chinese",\n    "max_position_embeddings": 65536,\n    "attn_scale": 0.08838834764831845,\n    "jinja": {\n        "chat_template": "{%- set content = messages[-1].content -%}<|im_start|>system\\n<|im_end|>\\n<|im_start|>user\\n{{ content }}<|im_end|>\\n{% if add_generation_prompt and content is string and \'<audio>\' in content and \'</audio>\' in content %}<|im_start|>assistant\\n{% if asr_language %}language {{ asr_language }}<asr_text>{% endif %}{% endif %}",\n        "context": {\n            "asr_language": "Chinese"\n        },\n        "eos": "<|im_end|>"\n    },\n    "tie_embeddings": {\n        "weight_offset": 881328128,\n        "alpha_offset": 1192493056,\n        "alpha_size": 0,\n        "quant_bit": 16,\n        "quant_block": 64,\n        "alpha_dtype": "fp16"\n    }\n}'.encode()
        self.assertEqual(hashlib.sha256(base_bytes).hexdigest(),
                         'fd4127baf9ff5c45aaa2224f65db36e9110088d2aa7223e4853238d31be84e9d')
        self.assertEqual(hashlib.sha256(metadata_bytes).hexdigest(),
                         '8e548c5393334616cffb6e8a751603ad8314d01293aa29765f1f507f0b412b85')
        base = json.loads(base_bytes); metadata = json.loads(metadata_bytes)
        def row(path):
            return {'path': str(path.relative_to(root)), 'bytes': path.stat().st_size, 'sha256': tool.digest(path)}
        models = {}
        for name in ('A', 'B'):
            directory = root / name; directory.mkdir()
            for asset in tool.ASSETS:
                (directory / asset).write_text('tiny fixture not a model')
            (directory / 'config.json').write_bytes(base_bytes)
            (directory / 'llm_config.json').write_bytes(metadata_bytes)
            models[name] = [row(directory / p) for p in sorted(tool.ASSETS)]
        audio = root / 'tiny.wav'
        with wave.open(str(audio), 'wb') as wav:
            wav.setparams((1, 2, 16000, 1600, 'NONE', 'not compressed'))
            wav.writeframes(b'\0\0' * 1600)
        driver = root / 'driver'
        driver.write_text('#!' + sys.executable + '\n' + '''import sys,json,pathlib
config=pathlib.Path(sys.argv[1]);d=json.loads(config.read_text());d.update(json.loads(config.with_name('llm_config.json').read_text()));d['asr_language']=sys.argv[3] if sys.argv[3]!='auto' else ''
print('P0_EFFECTIVE_CONFIG',json.dumps(d))
print('P0_TEXT_BEGIN\\nfixture\\nP0_TEXT_END')
m={'load_s':1.,'inference_s':2.,'audio_us':1,'prefill_us':2,'decode_us':3,'prompt_tokens':4,'generated_tokens':1,'status':1,'truncated':False}
''' + ("sys.exit(7)\n" if mode == 'fail' else "m.update(status=2,truncated=True)\n" if mode == 'truncated' else '') + "print('P0_METRICS',json.dumps(m))\n" + ("sys.exit(6)\n" if mode == 'truncated' else ''))
        driver.chmod(0o700)
        spec = {'schema': 1, 'driver': row(driver), 'runtime_inputs': [row(driver)], 'models': models,
                'base_config': base, 'model_config': metadata, 'repeats': 3,
                'representatives': ['tiny'], 'cases': [{**row(audio), 'id': 'tiny', 'samples': 1600,
                'language': 'Chinese', 'synthetic_silence': True}]}
        path = root / 'spec.json'; path.write_text(json.dumps(spec))
        return spec, path

    def run_cli(self, root, spec_path, name='result'):
        return subprocess.run([sys.executable, '-B', str(Path(tool.__file__).resolve()), '--spec', str(spec_path), '--output', str(root / name)], cwd=root, capture_output=True, text=True, timeout=30)

    def test_real_cli_success_identity_summary_and_reuse_refusal(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); spec, path = self.fixture(root)
            result = self.run_cli(root, path)
            self.assertEqual(result.returncode, 0, result.stderr)
            data = json.loads((root / 'result/results.json').read_text())
            self.assertEqual(data['status'], 'PASS')
            self.assertEqual(len(data['runs']), 8)
            self.assertEqual(data['summary']['agreement']['eligible'], 1)
            self.assertEqual(data['summary']['agreement']['raw_equal'], 1)
            self.assertEqual(data['summary']['models']['A']['performance_stats']['inference_s']['median'], 2)
            self.assertEqual(data['summary']['paired_performance']['tiny']['ratio_stats']['median'], 1)
            self.assertEqual(data['summary']['models']['A']['silence_nonempty'], 1)
            self.assertNotIn('fixture', json.dumps(data))
            for row in data['runs']:
                self.assertTrue(row['execution']['started'])
                self.assertEqual(row['execution']['exit'], 0)
                self.assertTrue(row['execution']['cleanup']['confirmed_observed_terminal'])
                log = root / 'result' / f"{row['phase']}-{row['repeat']:02d}-tiny-{row['model']}.log"
                self.assertEqual(row['execution']['log_sha256'], tool.digest(log))
            original = (root / 'result/results.json').read_bytes()
            self.assertNotEqual(self.run_cli(root, path).returncode, 0)
            self.assertEqual((root / 'result/results.json').read_bytes(), original)

    def test_real_cli_child_failure_is_not_half_success(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); spec, path = self.fixture(root, 'fail')
            result = self.run_cli(root, path)
            self.assertEqual(result.returncode, 1)
            data = json.loads((root / 'result/results.json').read_text())
            self.assertEqual(data['status'], 'COMPLETED_WITH_FAILURES')
            self.assertEqual(len(data['runs']), 8)
            self.assertEqual(data['summary']['agreement']['eligible'], 0)
            self.assertEqual(data['summary']['models']['A']['performance_complete'], 0)
            self.assertTrue(all(r['execution']['exit'] == 7 and not r['complete_eos'] for r in data['runs']))

    def test_real_cli_truncation_is_incomplete(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); spec, path = self.fixture(root, 'truncated')
            self.assertEqual(self.run_cli(root, path).returncode, 1)
            data = json.loads((root / 'result/results.json').read_text())
            self.assertTrue(all(r['status'] == 'INCOMPLETE' for r in data['runs']))
            self.assertEqual(data['summary']['models']['A']['truncated'], 1)

    def test_real_cli_tiny_gold_micro_metrics_are_language_separated(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); spec, path = self.fixture(root)
            truth = root / 'tiny-gold.txt'; truth.write_text('fixturE')
            spec['cases'][0]['language'] = 'English'
            spec['cases'][0]['gold'] = {'path': truth.name, 'bytes': truth.stat().st_size,
                'sha256': tool.digest(truth), 'independently_verified': True,
                'source': 'synthetic test fixture truth, not a dataset', 'license': 'repository test fixture'}
            path.write_text(json.dumps(spec))
            self.assertEqual(self.run_cli(root, path).returncode, 0)
            summary = json.loads((root / 'result/results.json').read_text())['summary']
            self.assertEqual(summary['quality'], 'finite_independent_gold_only')
            self.assertEqual(summary['models']['A']['English_gold']['micro_rate'], 0)
            self.assertEqual(summary['models']['A']['English_gold']['reference_units'], 1)
            self.assertNotIn('Chinese_gold', summary['models']['A'])

    def rebind_metadata(self, root, spec, path, overrides):
        spec['model_config'].update(overrides)
        for name in ('A', 'B'):
            metadata = root / name / 'llm_config.json'
            metadata.write_text(json.dumps(spec['model_config']))
            for entry in spec['models'][name]:
                if entry['path'].endswith('/llm_config.json'):
                    entry.update(bytes=metadata.stat().st_size, sha256=tool.digest(metadata))
        path.write_text(json.dumps(spec))

    def bind_gold(self, root, spec, path, content):
        truth = root / 'gold.txt'; truth.write_bytes(content)
        spec['cases'][0]['gold'] = {'path': truth.name, 'bytes': truth.stat().st_size,
            'sha256': tool.digest(truth), 'independently_verified': True,
            'source': 'synthetic regression truth only', 'license': 'repository test fixture'}
        path.write_text(json.dumps(spec))
        return truth

    def test_metadata_protected_overrides_rejected_before_output_or_spawn(self):
        mutations = [
            ('thread_num', 4), ('backend_type', 'opencl'), ('precision', 'low'),
            ('memory', 'high'), ('sampler_type', 'temperature'), ('max_new_tokens', 2048),
            ('async', True), ('mllm', {'thread_num': 4}),
            ('mllm', {'backend_type': 'opencl'}), ('mllm', {'precision': 'low'}),
            ('mllm', {'memory': 'high'}), ('llm_model', 'unbound-outside-seven.mnn'),
            ('llm_weight', 'unbound.weight'), ('tokenizer_file', 'unbound.txt'),
            ('thread_num', True), ('base_dir', '/unbound/'), ('audio_model', 'unbound.mnn')]
        for field, value in mutations:
            with self.subTest(field=field, value=value), tempfile.TemporaryDirectory() as directory:
                root = Path(directory); spec, path = self.fixture(root)
                self.rebind_metadata(root, spec, path, {field: value})
                # All identities including the mutated metadata and frozen spec are valid.
                for name in ('A', 'B'):
                    entry = next(e for e in spec['models'][name] if e['path'].endswith('/llm_config.json'))
                    self.assertEqual(tool.digest(root / entry['path']), entry['sha256'])
                result = self.run_cli(root, path)
                self.assertNotEqual(result.returncode, 0, 'unsafe metadata admitted: ' + field)
                self.assertFalse((root / 'result').exists(), 'must reject BEFORE mkdir/native launch')
                self.assertIn('config', result.stderr.lower())
                self.assertNotIn('Traceback', result.stderr)

    def test_benign_partial_metadata_now_rejected_by_fixed_policy(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); spec, path = self.fixture(root)
            self.rebind_metadata(root, spec, path, {'thread_num': 2, 'llm_model': 'llm.mnn',
                                                   'mllm': {'thread_num': 2}})
            # Tiny driver mirrors the audited native object/object recursive merge.
            driver = root / 'driver'
            source = driver.read_text().replace("d.update(json.loads(config.with_name('llm_config.json').read_text()))", "metadata=json.loads(config.with_name('llm_config.json').read_text());nested=metadata.pop('mllm',{});d['mllm'].update(nested);d.update(metadata)")
            driver.write_text(source)
            entry = {'path': 'driver', 'bytes': driver.stat().st_size, 'sha256': tool.digest(driver)}
            spec['driver'] = entry; spec['runtime_inputs'] = [entry]; path.write_text(json.dumps(spec))
            result = self.run_cli(root, path)
            # Deliberate compatibility restriction: even benign same-value metadata
            # is no longer supported unless its complete bytes match the trusted tuple.
            self.assertNotEqual(result.returncode, 0)
            self.assertFalse((root / 'result').exists())
            self.assertIn('fixed trusted config policy', result.stderr)
            self.assertNotIn('identity mismatch', result.stderr)
            self.assertNotIn('Traceback', result.stderr)

    def test_bound_non_utf8_gold_rejected_before_output_or_spawn(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); spec, path = self.fixture(root)
            self.bind_gold(root, spec, path, b'\xff\xfe')
            result = self.run_cli(root, path)
            self.assertNotEqual(result.returncode, 0, 'nonUTF8 gold must not produce PASS/scored0')
            self.assertFalse((root / 'result').exists())
            self.assertIn('gold', result.stderr.lower())
            self.assertIn('utf-8', result.stderr.lower())
            self.assertNotIn('Traceback', result.stderr)

    def test_requested_gold_read_error_after_admission_fails_but_retains_eos(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); spec, path = self.fixture(root)
            truth = self.bind_gold(root, spec, path, b'fixture')
            driver = root / 'driver'
            driver.write_text(driver.read_text().replace('import sys,json,pathlib',
                "import sys,json,pathlib\npathlib.Path('gold.txt').unlink(missing_ok=True)"))
            entry = {'path': 'driver', 'bytes': driver.stat().st_size, 'sha256': tool.digest(driver)}
            spec['driver'] = entry; spec['runtime_inputs'] = [entry]; path.write_text(json.dumps(spec))
            result = self.run_cli(root, path)
            self.assertNotEqual(result.returncode, 0, 'requested scoring cannot silently PASS')
            self.assertNotIn('Traceback', result.stderr)
            data = json.loads((root / 'result/results.json').read_text())
            self.assertNotEqual(data['status'], 'PASS')
            self.assertTrue(data['runs'])
            for run in data['runs']:
                self.assertTrue(run['complete_eos'], 'native EOS fact must remain truthful')
                self.assertEqual(run['execution']['exit'], 0)
                self.assertEqual(run['status'], 'FAILED')
                self.assertIn('score_error', run)
                self.assertNotIn('gold_score', run)
            stats = data['summary']['models']['A']['Chinese_gold']
            self.assertEqual(stats['eligible'], 1)
            self.assertEqual(stats['scored'], 0)
            self.assertGreater(stats['score_failures'], 0)
            self.assertFalse(stats['scoring_complete'])

    def rebind_base(self, root, spec, path, overrides):
        spec['base_config'].update(overrides)
        for name in ('A', 'B'):
            config = root / name / 'config.json'
            config.write_text(json.dumps(spec['base_config']))
            for entry in spec['models'][name]:
                if entry['path'].endswith('/config.json'):
                    entry.update(bytes=config.stat().st_size, sha256=tool.digest(config))
        path.write_text(json.dumps(spec))

    def test_ple_presence_rejected_before_output_even_empty_or_overridden(self):
        for layer in ('base', 'metadata', 'base-overridden'):
            for value in ('unbound-outside-seven.bin', '', None):
                with self.subTest(layer=layer, value=value), tempfile.TemporaryDirectory() as directory:
                    root = Path(directory); spec, path = self.fixture(root)
                    change = {'ple_embed_file': value, 'ple_embed_dim': 0 if value != 'unbound-outside-seven.bin' else 4}
                    if layer == 'metadata':
                        self.rebind_metadata(root, spec, path, change)
                    else:
                        self.rebind_base(root, spec, path, change)
                        if layer == 'base-overridden':
                            self.rebind_metadata(root, spec, path, {'ple_embed_file': ''})
                    result = self.run_cli(root, path)
                    self.assertNotEqual(result.returncode, 0, 'PLE presence admitted: ' + layer)
                    self.assertFalse((root / 'result').exists())
                    self.assertIn('config', result.stderr.lower())
                    self.assertNotIn('Traceback', result.stderr)
                    self.assertNotIn('identity mismatch', result.stderr)

    def test_audited_qwen_input_selectors_and_optional_context_refused(self):
        changes = [{'speculative_type': 'lookahead'}, {'has_talker': True},
                   {'is_visual': True}, {'is_embedding': True}, {'is_audio': False},
                   {'audio_type': 'whisper'}, {'model_type': 'other'},
                   {'tie_embeddings': {'weight_offset': 0}}, {'tie_embeddings': None},
                   {'use_mmap': True}, {'kvcache_mmap': True}]
        for change in changes:
            with self.subTest(change=change), tempfile.TemporaryDirectory() as directory:
                root = Path(directory); spec, path = self.fixture(root)
                self.rebind_metadata(root, spec, path, change)
                result = self.run_cli(root, path)
                self.assertNotEqual(result.returncode, 0)
                self.assertFalse((root / 'result').exists())
                self.assertNotIn('Traceback', result.stderr)
                self.assertNotIn('identity mismatch', result.stderr)
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); spec, path = self.fixture(root)
            # Pinned native unconditionally probes this default optional input.
            (root / 'A/context.json').write_text('{}')
            result = self.run_cli(root, path)
            self.assertNotEqual(result.returncode, 0, 'unbound default context admitted')
            self.assertFalse((root / 'result').exists())
            self.assertIn('context', result.stderr.lower())
            self.assertNotIn('Traceback', result.stderr)

    def test_constant_template_and_equivalent_bytes_rejected_by_trusted_pin(self):
        for mutation in ('constant-template', 'format-only', 'base-value', 'talker-type'):
            with self.subTest(mutation=mutation), tempfile.TemporaryDirectory() as directory:
                root = Path(directory); spec, path = self.fixture(root)
                if mutation == 'constant-template':
                    self.rebind_metadata(root, spec, path, {'jinja': {
                        'chat_template': '<audio>/unbound-outside-corpus.wav</audio>',
                        'eos': '<|im_end|>'}})
                elif mutation == 'talker-type':
                    self.rebind_metadata(root, spec, path, {'talker_type': 'qwen3_tts'})
                else:
                    self.rebind_base(root, spec, path, {} if mutation == 'format-only' else {'temperature': 0.7})
                for entries in spec['models'].values():
                    for entry in entries:
                        self.assertEqual(tool.digest(root / entry['path']), entry['sha256'])
                result = self.run_cli(root, path)
                self.assertNotEqual(result.returncode, 0, 'caller-rebound template/config admitted')
                self.assertFalse((root / 'result').exists())
                self.assertIn('fixed trusted config policy', result.stderr)
                self.assertNotIn('identity mismatch', result.stderr)
                self.assertNotIn('Traceback', result.stderr)

    def test_actual_cli_cancellation_cleans_owned_child(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); spec, path = self.fixture(root)
            driver = root / 'driver'
            driver.write_text('#!' + sys.executable + '\nimport os,signal,time\nos.kill(os.getppid(),signal.SIGTERM)\ntime.sleep(30)\n')
            identity = {'path': 'driver', 'bytes': driver.stat().st_size, 'sha256': tool.digest(driver)}
            spec['driver'] = identity; spec['runtime_inputs'] = [identity]
            path.write_text(json.dumps(spec))
            result = self.run_cli(root, path)
            self.assertEqual(result.returncode, 143, result.stderr)
            data = json.loads((root / 'result/results.json').read_text())
            self.assertEqual(data['status'], 'CANCELLED')
            self.assertEqual(len(data['runs']), 1)
            execution = data['runs'][0]['execution']
            self.assertTrue(execution['started'])
            self.assertEqual(execution['status'], 'CANCELLED')
            self.assertTrue(execution['cleanup']['confirmed_observed_terminal'])
            self.assertFalse(Path('/proc', str(execution['pid'])).exists())

    def test_hash_mismatch_refuses_before_output_or_child(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); spec, path = self.fixture(root)
            (root / 'A/audio.mnn').write_text('tampered')
            result = self.run_cli(root, path)
            self.assertNotEqual(result.returncode, 0)
            self.assertIn('identity mismatch', result.stderr)
            self.assertFalse((root / 'result').exists())

    def test_invalid_spec_and_gold_provenance(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory); spec, path = self.fixture(root)
            for mutation in ('duplicate', 'path', 'config', 'gold', 'runtime', 'wav'):
                candidate = copy.deepcopy(spec)
                if mutation == 'duplicate': candidate['cases'] *= 2
                if mutation == 'path': candidate['models']['A'][0]['path'] = 'missing'
                if mutation == 'config': candidate['base_config']['thread_num'] = 4
                if mutation == 'gold': candidate['cases'][0]['gold'] = {'path': 'tiny.wav'}
                if mutation == 'runtime': candidate['runtime_inputs'] = []
                if mutation == 'wav': candidate['cases'][0]['samples'] = 1601
                with self.subTest(mutation=mutation), self.assertRaises(ValueError):
                    tool.validate_spec(candidate, root)

    def native_log(self, metrics=None):
        values = {'load_s': 1, 'inference_s': 2, 'audio_us': 1, 'prefill_us': 1, 'decode_us': 1,
                  'prompt_tokens': 1, 'generated_tokens': 1, 'status': 1, 'truncated': False}
        values.update(metrics or {})
        return 'P0_EFFECTIVE_CONFIG {}\nP0_TEXT_BEGIN\na\nP0_TEXT_END\nP0_METRICS ' + json.dumps(values) + '\n'

    def test_strict_protocol_rejects_plausible_output_failures(self):
        valid = self.native_log()
        self.assertEqual(tool.parse_native(valid, 0, {})[0], 'a')
        for log, code in [(valid, 6), (valid + valid, 0), (valid.replace('P0_TEXT_END', 'END'), 0),
                          (valid, 1), (self.native_log({'status': 2}), 0),
                          (self.native_log({'inference_s': float('nan')}), 0),
                          (self.native_log({'generated_tokens': 129}), 0),
                          (self.native_log({'truncated': 0}), 0),
                          (valid + 'P0_ERROR inference', 0)]:
            with self.subTest(code=code, log=log), self.assertRaises(ValueError):
                tool.parse_native(log, code, {})
        with self.assertRaises(ValueError): tool.parse_native(valid, 0, {'different': True})

    def test_text_diagnostics_do_not_repair_repetitions(self):
        self.assertEqual(tool.text_content('language Chinese<asr_text>甲甲甲<|im_end|>'), '甲甲甲')
        self.assertTrue(tool.repetitions('abcdabcdabcd'))
        self.assertFalse(tool.repetitions('short'))
        with self.assertRaises(ValueError): tool.text_content('unknown<asr_text>text')


if __name__ == '__main__':
    unittest.main()
