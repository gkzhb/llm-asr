#!/usr/bin/env python3
"""Fail-closed helpers for one isolated AGP checksum mutation and its actual report."""
import argparse
import copy
import hashlib
import re
import xml.etree.ElementTree as ET
from html.parser import HTMLParser
from pathlib import Path

NS = {'g': 'https://schema.gradle.org/dependency-verification'}
COORDINATE = 'com.android.tools.build:gradle:8.6.1'
ARTIFACT = 'gradle-8.6.1.jar'
ZERO = '0' * 64


def target(root):
    components = [c for c in root.findall('g:components/g:component', NS)
                  if c.attrib == {'group': 'com.android.tools.build', 'name': 'gradle', 'version': '8.6.1'}]
    if len(components) != 1:
        raise ValueError('checksum target component must be unique')
    artifacts = [a for a in components[0].findall('g:artifact', NS) if a.get('name') == ARTIFACT]
    if len(artifacts) != 1:
        raise ValueError('checksum target artifact must be unique')
    hashes = artifacts[0].findall('g:sha256', NS)
    if len(hashes) != 1:
        raise ValueError('checksum target sha256 node must be unique')
    return hashes[0]


def mutate(path):
    root = ET.fromstring(path.read_bytes())
    original = target(root).get('value', '')
    if not re.fullmatch(r'[0-9a-f]{64}', original) or original == ZERO:
        raise ValueError('checksum original target value invalid')
    before = copy.deepcopy(root)
    target(root).set('value', ZERO)
    # Restore in a copy: everything except the unique target value must match.
    restored = copy.deepcopy(root)
    target(restored).set('value', original)
    if ET.tostring(restored) != ET.tostring(before):
        raise ValueError('checksum mutation changed unrelated metadata')
    ET.register_namespace('', NS['g'])
    path.write_bytes(ET.tostring(root, encoding='utf-8', xml_declaration=True))
    reread = ET.fromstring(path.read_bytes())
    if target(reread).get('value') != ZERO:
        raise ValueError('checksum mutation did not persist')
    target(reread).set('value', original)
    if ET.tostring(reread) != ET.tostring(before):
        raise ValueError('checksum serialized mutation changed unrelated metadata')
    return original


class Rows(HTMLParser):
    def __init__(self):
        super().__init__()
        self.in_body = False
        self.row = None
        self.cell = None
        self.rows = []
        self.links = []

    def handle_starttag(self, tag, attrs):
        if tag == 'tbody':
            self.in_body = True
        elif tag == 'tr' and self.in_body:
            self.row = []
        elif tag == 'td' and self.row is not None:
            self.cell = []
        elif tag == 'a' and self.cell is not None:
            href = dict(attrs).get('href', '')
            if href.startswith('file:'):
                self.links.append(href)

    def handle_endtag(self, tag):
        if tag == 'td' and self.cell is not None:
            self.row.append(' '.join(''.join(self.cell).split()))
            self.cell = None
        elif tag == 'tr' and self.row is not None:
            self.rows.append(self.row)
            self.row = None
        elif tag == 'tbody':
            self.in_body = False

    def handle_data(self, data):
        if self.cell is not None:
            self.cell.append(data)


def check_report(text, expected):
    parser = Rows()
    parser.feed(text)
    # Exactly one artifact verification failure, not a target hidden among others.
    if len(parser.rows) != 1 or len(parser.rows[0]) != 3:
        raise ValueError('checksum report must have one target failure row')
    module, artifact, problem = parser.rows[0]
    if module != COORDINATE or artifact != ARTIFACT:
        raise ValueError('checksum report wrong target coordinate/artifact')
    match = re.fullmatch(r'Expected a sha256 checksum of ([0-9a-f]{64}) but was ([0-9a-f]{64})', problem)
    if not match or match[1] != ZERO or match[2] != expected or expected == ZERO:
        raise ValueError('checksum report missing specific expected/actual mismatch')
    return parser.links


def verify_fixture(fixture, expected):
    log = (fixture / 'rejection.log').read_text()
    if 'Dependency verification failed' not in log or ARTIFACT not in log:
        raise ValueError('checksum log missing target dependency verification failure')
    reports = list((fixture / 'build/reports/dependency-verification').glob('*/dependency-verification-report.html'))
    if len(reports) != 1:
        raise ValueError('checksum requires unique fresh fixture report')
    links = check_report(reports[0].read_text(), expected)
    from urllib.parse import unquote, urlparse
    artifacts = [Path(unquote(urlparse(link).path)) for link in links if link.endswith('/' + ARTIFACT)]
    if len(artifacts) != 1 or not artifacts[0].is_file():
        raise ValueError('checksum report artifact path missing/ambiguous')
    if hashlib.sha256(artifacts[0].read_bytes()).hexdigest() != expected:
        raise ValueError('checksum report actual differs from cached artifact SHA')
    print('PASS specific checksum mismatch expected=' + ZERO + ' actual=' + expected + ' report=' + str(reports[0]))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('mode', choices=['mutate', 'verify'])
    parser.add_argument('path', type=Path)
    parser.add_argument('--expected')
    args = parser.parse_args()
    if args.mode == 'mutate':
        print(mutate(args.path))
    else:
        verify_fixture(args.path, args.expected)
