#!/usr/bin/env python3
"""Pure exact-identity negatives on approved actual graph, not Lint execution."""
import collections
import copy
import hashlib
import json
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]
APPROVED = json.loads((ROOT / 'gradle/lint-tool-identity.json').read_text())


def bag(rows):
    return collections.Counter(json.dumps(row, sort_keys=True, separators=(',', ':')) for row in rows)


def check(actual, files, artifacts):
    for key in ('root', 'roots', 'flags', 'attributes', 'components'):
        if actual[key] != APPROVED[key]:
            raise ValueError('identity ' + key)
    for key in ('edges', 'artifacts'):
        if bag(actual[key]) != bag(APPROVED[key]):
            raise ValueError('identity ' + key)
    if (bag(files) != bag(artifacts) or len({row['path'] for row in files}) != len(files) or
            len({row['path'] for row in artifacts}) != len(artifacts)):
        raise ValueError('identity currentfiles')


def check_record(task, current_fc, passed):
    record = passed.get(task)
    if record is None or record['classpath'] is not current_fc:
        raise ValueError('identity successful-record')


task, other_task, fc, other_fc = object(), object(), object(), object()
passed = {task: {'classpath': fc}}
check_record(task, fc, passed)
for bad_task, bad_fc, bad_passed in ((other_task, fc, passed), (task, other_fc, passed), (task, fc, {})):
    try:
        check_record(bad_task, bad_fc, bad_passed)
    except ValueError as error:
        if str(error) != 'identity successful-record':
            raise AssertionError('wrong task/fc record rejection') from error
    else:
        raise AssertionError('bad task/fc/failed gate record accepted')
check_record(task, fc, passed)

files = [{'path': '/pure-fixture/' + row['name'], 'sha256': row['sha256']} for row in APPROVED['artifacts']]
check(APPROVED, files, files)
cases = []
for domain in ('root', 'roots', 'flags', 'attributes', 'components', 'edges', 'artifacts'):
    actual = copy.deepcopy(APPROVED)
    if domain == 'root':
        actual[domain] = '<other>'
    elif domain == 'flags':
        actual[domain]['transitive'] = False
    elif domain == 'attributes':
        actual[domain]['unexpected'] = 'true'
    else:
        actual[domain].pop()
    cases.append((domain + '-missing', domain, actual, files, files))
for domain in ('components', 'edges', 'artifacts'):
    for mutation in ('extra', 'duplicate', 'version'):
        actual = copy.deepcopy(APPROVED)
        if mutation == 'duplicate':
            actual[domain].append(copy.deepcopy(actual[domain][0]))
        elif mutation == 'extra':
            if domain == 'components':
                actual[domain].append('unapproved:tool:0')
            else:
                row = copy.deepcopy(actual[domain][0])
                row['selected' if domain == 'edges' else 'component'] = 'unapproved:tool:0'
                actual[domain].append(row)
        elif domain == 'components':
            actual[domain][1] = 'unapproved:tool:0'
        else:
            actual[domain][0]['selected' if domain == 'edges' else 'component'] = 'unapproved:tool:0'
        cases.append((domain + '-' + mutation, domain, actual, files, files))
actual = copy.deepcopy(APPROVED)
actual['artifacts'][0]['sha256'] = '0' * 64
cases.append(('artifact-sha', 'artifacts', actual, files, files))
for mutation in ('missing', 'extra', 'replacement', 'samepath-content', 'duplicatecanonical'):
    changed = copy.deepcopy(files)
    if mutation == 'missing':
        changed.pop()
    elif mutation == 'extra':
        changed.append({'path': '/pure-fixture/unknown.jar', 'sha256': '0' * 64})
    elif mutation == 'replacement':
        changed[0]['path'] = '/pure-fixture/replacement.jar'
    elif mutation == 'samepath-content':
        changed[0]['sha256'] = '0' * 64
    else:
        changed.append(copy.deepcopy(changed[0]))
    cases.append(('files-' + mutation, 'currentfiles', APPROVED, changed, files))
# Duplicate canonical artifacts must fail even if both multisets are equal.
duplicate = files + [files[0]]
cases.append(('matching-duplicatecanonical', 'currentfiles', APPROVED, duplicate, duplicate))
for label, domain, actual, current_files, artifact_files in cases:
    try:
        check(actual, current_files, artifact_files)
    except ValueError as error:
        if str(error) != 'identity ' + domain:
            raise AssertionError('wrong rejection: ' + label) from error
    else:
        raise AssertionError('accepted: ' + label)
check(APPROVED, files, files)
# If main only-resolve exports exist, validate all 13 typed debug consumer graphs.
paths = list((ROOT / 'android/app/build/lint-identity').glob('*.json')) + list(
    (ROOT / 'android/asr-runtime/build/lint-identity').glob('*.json'))
if paths:
    if len(paths) != 13:
        raise ValueError('expected 13 real main consumer exports')
    for path in paths:
        report = json.loads(path.read_text())
        actual = report['actual']
        check(actual, files, files)
        if 'classpathFiles' in report:
            current = report['classpathFiles']
            expected = [{'path': row['path'], 'sha256': hashlib.sha256(Path(row['path']).read_bytes()).hexdigest()}
                        for row in current]
            if bag(current) != bag(expected) or bag(
                    [{'name': Path(row['path']).name, 'sha256': row['sha256']} for row in current]) != bag(
                    [{'name': row['name'], 'sha256': row['sha256']} for row in actual['artifacts']]):
                raise ValueError('real main classpath path/content coverage mismatch')
            check(actual, current, current)
        if report['manifest_sha256'] != hashlib.sha256((ROOT / 'gradle/lint-tool-identity.json').read_bytes()).hexdigest():
            raise ValueError('main export manifest SHA drift')
print('PASS pure identity: ' + str(len(cases)) + ' graph/files negatives + 3 task/fc/record negatives/restore; '
      'main exports=' + str(len(paths)))
