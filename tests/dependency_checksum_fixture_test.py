#!/usr/bin/env python3
"""Target XML/HTML negatives, independent of Gradle/tool availability."""
import copy
import tempfile
import xml.etree.ElementTree as ET
from pathlib import Path
from dependency_checksum_fixture import ARTIFACT, COORDINATE, NS, ZERO, check_report, mutate, target

ROOT = Path(__file__).resolve().parents[1]
original = ET.fromstring((ROOT / 'gradle/verification-metadata.xml').read_bytes())
expected = target(original).get('value')
with tempfile.TemporaryDirectory(prefix='checksum-parser-') as temporary:
    path = Path(temporary) / 'metadata.xml'
    path.write_bytes(ET.tostring(original))
    if mutate(path) != expected or target(ET.fromstring(path.read_bytes())).get('value') != ZERO:
        raise AssertionError('unique target mutation failed')
    for kind in ('component', 'artifact', 'sha256', 'zero', 'missing-component', 'missing-artifact', 'missing-sha'):
        bad = copy.deepcopy(original)
        component = [c for c in bad.findall('g:components/g:component', NS)
                     if c.attrib.get('group') == 'com.android.tools.build' and c.attrib.get('name') == 'gradle'][0]
        artifact = [a for a in component.findall('g:artifact', NS) if a.get('name') == ARTIFACT][0]
        if kind == 'component':
            bad.find('g:components', NS).append(copy.deepcopy(component))
        elif kind == 'artifact':
            component.append(copy.deepcopy(artifact))
        elif kind == 'sha256':
            artifact.append(copy.deepcopy(target(bad)))
        elif kind == 'missing-component':
            bad.find('g:components', NS).remove(component)
        elif kind == 'missing-artifact':
            component.remove(artifact)
        elif kind == 'missing-sha':
            artifact.remove(target(bad))
        else:
            target(bad).set('value', ZERO)
        path.write_bytes(ET.tostring(bad))
        try:
            mutate(path)
        except ValueError as error:
            if ('unique' if kind != 'zero' else 'invalid') not in str(error):
                raise AssertionError('wrong mutation refusal') from error
        else:
            raise AssertionError('ambiguous/invalid mutation accepted: ' + kind)
row = f'<tr><td>{COORDINATE}</td><td>{ARTIFACT}</td><td>Expected a sha256 checksum of <span>{ZERO}</span> but was <span>{expected}</span></td></tr>'
good = '<table><tbody>' + row + '</tbody></table>'
check_report(good, expected)
for bad in ('<p>Incorrect checksum</p>', good.replace(ARTIFACT, 'wrong.jar'),
            good.replace(COORDINATE, 'wrong:module:1'), good.replace('Expected a sha256 checksum of', 'Missing checksum'),
            good.replace(expected, 'f' * 64), '<tbody>' + row + row + '</tbody>'):
    try:
        check_report(bad, expected)
    except ValueError:
        pass
    else:
        raise AssertionError('non-target checksum report accepted')
check_report(good, expected)
print('PASS unique checksum mutation and target-row expected/actual negatives/recovery (not real Gradle)')
