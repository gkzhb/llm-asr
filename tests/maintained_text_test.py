#!/usr/bin/env python3
"""Real checker negative fixtures: require the specific format diagnostic."""
import importlib.util
import tempfile
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('maintained_text', ROOT / 'scripts/check-maintained-text.py')
checker = importlib.util.module_from_spec(spec)
spec.loader.exec_module(checker)
checker.check_bytes('中文 text\n    nested\n'.encode(), 'good')
for data, diagnostic in ((b'text ', 'missing final newline'),
                         (b'text \n', 'trailing whitespace'),
                         (b'\ttext\n', 'tab indentation'),
                         (b'text\r\n', 'CR line ending'),
                         (b'\xff\n', 'invalid UTF-8'),
                         (b'text\x00\n', 'NUL byte')):
    try:
        checker.check_bytes(data, 'negative')
    except ValueError as error:
        if diagnostic not in str(error):
            raise AssertionError(f'wrong format rejection: {error}') from error
    else:
        raise AssertionError(f'format checker accepted: {diagnostic}')
with tempfile.TemporaryDirectory(prefix='maintained-text-') as temporary:
    root = Path(temporary)
    for relative in checker.INVENTORY:
        path = root / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text('clean\n')
    raw = root / 'reports/historical-readelf.txt'
    raw.parent.mkdir(exist_ok=True)
    raw.write_bytes(b'raw evidence with trailing spaces   \n')
    checker.check_inventory(root)
    if raw.read_bytes() != b'raw evidence with trailing spaces   \n':
        raise AssertionError('raw historical evidence modified')
    path = root / checker.INVENTORY[0]
    path.unlink()
    path.symlink_to(raw)
    try:
        checker.check_inventory(root)
    except ValueError as error:
        if 'missing file or symlink' not in str(error):
            raise AssertionError(f'wrong inventory rejection: {error}') from error
    else:
        raise AssertionError('format inventory accepted symlink')
print('PASS maintained-text target negatives and raw evidence exclusion (no writes to repository)')
