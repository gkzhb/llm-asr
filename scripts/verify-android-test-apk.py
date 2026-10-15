#!/usr/bin/env python3
"""P1-B three-APK build isolation gate. Static package facts, never device acceptance."""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import struct
import subprocess
import sys
import uuid
import xml.etree.ElementTree as ET
import zipfile

from dex_class_inventory import defined_classes

ROOT = Path(__file__).resolve().parents[1]
PREFIX = 'Lorg/llmasr/minimal/testhost/'
SUITE = PREFIX + 'FixtureInstrumentationTest;'
METHODS = sorted(['testEnginePayloadAndOrder', 'testReadinessRejectsBeforeLoad', 'testBadBytesAreSafe',
                  'testSingleConfirmationAndPassword', 'testLateResultIsInvalidated', 'testCleanupRetainsOwner',
                  'testReusedBackendQueuedCancellation', 'testProviderEnumerationClosesCursor',
                  'testProviderQueryErrorIsPrivate', 'testProviderCloseErrorIsPrivate',
                  'testProviderCancellationWaitsForQuery', 'testInputConnectionAndManualInvalidation'])
JVM_METHODS = sorted(['enginePayloadAndOrder', 'readinessRejectsBeforeLoad', 'badBytesAreSafe',
                      'singleConfirmationAndPassword', 'lateResultIsInvalidated', 'cleanupRetainsOwner',
                      'reusedBackendQueuedCancellation'])
PACKAGES = {'product': 'org.llmasr.minimal', 'host': 'org.llmasr.minimal.testhost',
            'test': 'org.llmasr.minimal.testhost.test'}


def require(value, message):
    if not value:
        raise ValueError(message)


def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def module_inputs(root):
    """Exact live maintained inventory, including untracked/new files; no generated output."""
    base = root / 'android/test-host'
    paths = [base / 'build.gradle.kts', base / 'gradle.lockfile']
    for folder in ['src', 'fixtures']:
        directory = base / folder
        require(directory.is_dir() and not directory.is_symlink(), 'module input directory missing/symlink')
        for path in directory.rglob('*'):
            require(not path.is_symlink(), 'module input symlink')
            if path.is_file():
                paths.append(path)
    require(any(p.suffix == '.java' for p in paths), 'module source inventory empty')
    result = {}
    for path in sorted(paths):
        require(path.is_file() and not path.is_symlink(), 'module input missing/symlink: ' + str(path))
        result[str(path.relative_to(root))] = sha(path)
    return result


def check_inputs(root, recorded):
    require(isinstance(recorded, dict) and recorded == module_inputs(root), 'module input SHA/inventory mismatch')


def manifest_nodes(text):
    """Parse actual aapt2 xmltree direct attributes with indentation, not cross-component grep."""
    roots, stack = [], []
    for line in text.splitlines():
        match = re.fullmatch(r'(\s*)E: ([\w-]+)(?:\s+.*)?', line)
        if match:
            depth = len(match[1])
            node = {'tag': match[2], 'attrs': {}, 'children': []}
            while stack and stack[-1][0] >= depth:
                stack.pop()
            (stack[-1][1]['children'] if stack else roots).append(node)
            stack.append((depth, node))
        elif line.lstrip().startswith('A: '):
            require(bool(stack), 'manifest orphan attribute')
            match = re.fullmatch(r'\s*A: ([^=]+)=(.*)', line)
            require(match is not None, 'manifest malformed attribute')
            key = match[1].split('(')[0].rsplit(':', 1)[-1]
            value = match[2].split(' (Raw:', 1)[0]
            if value.startswith('"'):
                require(value.endswith('"'), 'manifest malformed string')
                value = value[1:-1]
            require(key not in stack[-1][1]['attrs'], 'manifest duplicate attribute')
            stack[-1][1]['attrs'][key] = value
    require(len(roots) == 1 and roots[0]['tag'] == 'manifest', 'manifest root missing')
    return roots[0]


def only(nodes, tag):
    found = [n for n in nodes if n['tag'] == tag]
    require(len(found) == 1, 'manifest expected one ' + tag)
    return found[0]


def dex_suite_methods(data):
    """Bounded class_data parse for this one JUnit3 suite, after full class_def validation."""
    def u32(offset):
        require(0 <= offset <= len(data) - 4, 'DEX integer out of bounds')
        return struct.unpack_from('<I', data, offset)[0]

    def table(header, size):
        count, offset = u32(header), u32(header + 4)
        require(offset <= len(data) and count <= (len(data) - offset) // size, 'DEX table out of bounds')
        return count, offset

    def uleb(offset):
        value = 0
        for shift in range(0, 35, 7):
            require(offset < len(data), 'DEX uleb out of bounds')
            b = data[offset]; offset += 1
            value |= (b & 127) << shift
            if b < 128:
                return value, offset
        raise ValueError('DEX invalid uleb')

    strings = []
    count, offset = table(56, 4)
    for i in range(count):
        _, start = uleb(u32(offset + 4 * i))
        end = data.find(b'\0', start)
        require(end >= 0, 'DEX unterminated string')
        strings.append(data[start:end].decode('utf-8', errors='replace'))
    count, offset = table(64, 4)
    indexes = [u32(offset + 4 * i) for i in range(count)]
    require(all(i < len(strings) for i in indexes), 'DEX type index out of bounds')
    types = [strings[i] for i in indexes]
    prototypes, proto_offset = table(72, 12)
    count, offset = table(88, 8)
    methods = []
    for i in range(count):
        cls, proto, name = struct.unpack_from('<HHI', data, offset + 8 * i)
        require(cls < len(types) and proto < prototypes and name < len(strings), 'DEX method index out of bounds')
        p = proto_offset + 12 * proto
        ret, params = u32(p + 4), u32(p + 8)
        require(ret < len(types), 'DEX return type out of bounds')
        argc = u32(params) if params else 0
        methods.append((types[cls], strings[name], types[ret], argc))
    found = []
    count, offset = table(96, 32)
    for i in range(count):
        p = offset + 32 * i
        cls, parent, cd = u32(p), u32(p + 8), u32(p + 24)
        require(cls < len(types), 'DEX class index out of bounds')
        if types[cls] != SUITE:
            continue
        require(parent < len(types) and types[parent] == 'Landroid/test/InstrumentationTestCase;', 'suite base mismatch')
        require(cd > 0, 'suite class_data missing')
        counts = []
        for _ in range(4):
            value, cd = uleb(cd); counts.append(value)
        require(sum(counts) <= len(data), 'DEX encoded count out of bounds')
        for _ in range(counts[0] + counts[1]):
            _, cd = uleb(cd); _, cd = uleb(cd)
        for number in counts[2:]:
            index = 0
            for _ in range(number):
                delta, cd = uleb(cd); index += delta
                flags, cd = uleb(cd); code, cd = uleb(cd)
                require(index < len(methods), 'DEX encoded method index out of bounds')
                owner, name, ret, argc = methods[index]
                require(owner == SUITE, 'DEX method owner mismatch')
                if name.startswith('test'):
                    require(flags & 1 and not flags & (8 | 256 | 1024) and ret == 'V' and argc == 0
                            and code % 4 == 0 and 0 < code <= len(data) - 16, 'JUnit3 method signature/code mismatch')
                    found.append(name)
    return found


def dex_inventory(dexes):
    classes, methods = [], []
    for data in dexes:
        definitions = sorted(c.decode('ascii') for c in defined_classes(data))
        require(not set(classes).intersection(definitions), 'duplicate APK DEX class definition')
        classes.extend(definitions)
        methods.extend(dex_suite_methods(data))
    require(bool(classes), 'APK DEX missing')
    return classes, methods


def check_packages(packages, certificate):
    require(set(packages) == set(PACKAGES), 'three package inventory mismatch')
    for label, package in PACKAGES.items():
        p = packages[label]
        require(p['certificates'] == [certificate] and p['v3'], 'signing certificate/v3 mismatch')
        root = p['manifest']
        require(root['attrs'].get('package') == package, 'package identity mismatch')
        classes = p['classes']
        require(classes and len(classes) == len(set(classes)), 'duplicate/missing APK class definition')
        require(all(isinstance(c, str) and c.startswith('L') and c.endswith(';') for c in classes),
                'invalid APK class definition')
        forbidden = ('Ljunit/', 'Lorg/junit/', 'Lorg/hamcrest/', 'Landroid/test/', 'Landroidx/test/')
        require(not any(c.startswith(forbidden) for c in classes), 'test runner/JUnit/mock implementation leaked')
        if label == 'product':
            require(not any(c.startswith(PREFIX) for c in classes), 'product fixture class leaked')
            continue  # Existing product identity/native/assets gate remains separate and unchanged.
        require(not any(n.startswith(('assets/', 'lib/')) for n in p['entries']), 'test APK native/assets forbidden')
        require(not any(n['tag'].startswith('uses-permission') or n['tag'] == 'permission'
                        for n in root['children']), 'test APK permission forbidden')
        expected_tags = {'uses-sdk', 'application'} | ({'instrumentation'} if label == 'test' else set())
        require(all(n['tag'] in expected_tags for n in root['children']), 'unexpected test manifest node')
        sdk = only(root['children'], 'uses-sdk')['attrs']
        require(sdk.get('minSdkVersion') == '29' and sdk.get('targetSdkVersion') == '35'
                and root['attrs'].get('compileSdkVersion') == '35', 'test APK SDK mismatch')
        app = only(root['children'], 'application')
        require(app['attrs'].get('allowBackup') == 'false', 'test APK backup policy mismatch')
        libraries = [n for n in app['children'] if n['tag'] == 'uses-library']
        require(len(libraries) == 2 and {n['attrs'].get('name') for n in libraries}
                == {'android.test.base', 'android.test.runner'}
                and all(n['attrs'].get('required') == 'true' for n in libraries), 'test shared libraries mismatch')
        components = [n for n in app['children'] if n['tag'] != 'uses-library']
        if label == 'host':
            require(root['attrs'].get('versionCode') == '1' and root['attrs'].get('versionName') == 'p1b-testhost',
                    'host version mismatch')
            require(len(components) == 1 and components[0]['tag'] == 'activity'
                    and components[0]['attrs'].get('name') == 'org.llmasr.minimal.testhost.ImeHostActivity'
                    and components[0]['attrs'].get('exported') == 'false' and not components[0]['children'],
                    'host component/export/launcher mismatch')
            require(not any(c.startswith(PREFIX) and c not in {PREFIX + 'ImeHostActivity;', PREFIX + 'R;'}
                            and not re.fullmatch(re.escape(PREFIX) + r'R\$[A-Za-z0-9_]+;', c) for c in classes),
                    'host fixture class leaked')
            require(PREFIX + 'ImeHostActivity;' in classes, 'host activity class missing')
            require(not any(n['tag'] == 'instrumentation' for n in root['children']), 'host instrumentation unexpected')
        else:
            require(not components, 'test component/export mismatch')
            instrumentation = only(root['children'], 'instrumentation')['attrs']
            require(instrumentation.get('name') == 'android.test.InstrumentationTestRunner', 'instrumentation runner mismatch')
            require(instrumentation.get('targetPackage') == PACKAGES['host'], 'instrumentation target mismatch')
            for name in ['FixtureInstrumentationTest', 'FakeDocumentsProvider', 'FakePorts', 'FixtureChecks']:
                require(PREFIX + name + ';' in classes, 'required fixture class missing: ' + name)
            require(all(c.startswith(PREFIX) for c in classes) and PREFIX + 'ImeHostActivity;' not in classes,
                    'target production class duplicated into test')
            require(sorted(p['methods']) == METHODS, 'instrumentation method inventory mismatch')
    require(not set(packages['test']['classes']).intersection(packages['host']['classes']), 'host/test class overlap')


def junit_result(path):
    root = ET.parse(path).getroot()
    cases = root.findall('testcase')
    require(sorted(c.get('name') for c in cases) == JVM_METHODS
            and all(c.get('classname') == 'org.llmasr.minimal.testhost.FixtureContractTest' for c in cases),
            'host JUnit exact discovery mismatch')
    require(root.get('tests') == '7' and all(root.get(k) == '0' for k in ['errors', 'failures', 'skipped'])
            and not any(list(c) for c in cases), 'host JUnit failure/error/skip')
    return {'tests': 7, 'methods': JVM_METHODS, 'sha256': sha(path)}


def select_apk(directory, package, variant):
    metadata = directory / 'output-metadata.json'
    data = json.loads(metadata.read_text())
    require(data.get('version') == 3 and data.get('applicationId') == package
            and data.get('variantName') == variant and data.get('artifactType', {}).get('type') == 'APK',
            'AGP output metadata identity mismatch')
    elements = data.get('elements', [])
    require(len(elements) == 1 and elements[0].get('type') == 'SINGLE' and elements[0].get('filters') == [],
            'AGP single APK required')
    if package == PACKAGES['host']:
        require(elements[0].get('versionCode') == 1 and elements[0].get('versionName') == 'p1b-testhost',
                'AGP host version mismatch')
    name = elements[0].get('outputFile', '')
    require(name and Path(name).name == name and name.endswith('.apk'), 'AGP output path unsafe')
    apk = directory / name
    require(apk.is_file() and not apk.is_symlink() and sorted(p.name for p in directory.glob('*.apk')) == [name],
            'AGP output APK missing/extra/symlink')
    return apk, sha(metadata)


def inspect_apk(apk, tools, report, label):
    initial_sha = sha(apk)
    commands = {'signature': ['apksigner', 'verify', '--verbose', '--print-certs', str(apk)],
                'badging': ['aapt2', 'dump', 'badging', str(apk)],
                'permissions': ['aapt2', 'dump', 'permissions', str(apk)],
                'manifest': ['aapt2', 'dump', 'xmltree', str(apk), '--file', 'AndroidManifest.xml'],
                'zipalign': ['zipalign', '-c', '-p', '4', str(apk)]}
    raw, executions = {}, []
    for name, command in commands.items():
        command[0] = str(tools / command[0])
        result = subprocess.run(command, capture_output=True, text=True, check=False)
        path = report / (label + '-' + name + '.txt')
        path.write_text(result.stdout + result.stderr)
        executions.append({'argv': command, 'exit': result.returncode, 'raw_sha256': sha(path)})
        require(result.returncode == 0, 'APK tool failed: ' + name)
        raw[name] = result.stdout
    with zipfile.ZipFile(apk) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), 'duplicate ZIP entry')
        dexes = [archive.read(n) for n in names if re.fullmatch(r'classes(?:\d+)?\.dex', n)]
        classes, methods = dex_inventory(dexes)
    require(sha(apk) == initial_sha, 'APK changed during tool inspection')
    return {'apk': str(apk.relative_to(ROOT)), 'sha256': sha(apk), 'bytes': apk.stat().st_size,
            'manifest': manifest_nodes(raw['manifest']), 'entries': names, 'classes': classes, 'methods': methods,
            'certificates': re.findall(r'Signer #\d+ certificate SHA-256 digest: ([0-9a-f]{64})', raw['signature']),
            'v3': 'Verified using v3 scheme (APK Signature Scheme v3): true' in raw['signature'],
            'commands': executions}


def verify(root, output):
    inputs = module_inputs(root)
    spec = importlib.util.spec_from_file_location('product_build_inventory', root / 'scripts/verify-gradle-apk.py')
    product_checker = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(product_checker)
    build_inputs = product_checker.collect_build_inputs(root)
    tools = Path(os.environ['ANDROID_HOME']) / 'build-tools/35.0.0'
    report = output / ('run-' + uuid.uuid4().hex)
    report.mkdir(parents=True, exist_ok=False)
    packages = {}
    directories = {'product': 'android/app/build/outputs/apk/debug', 'host': 'android/test-host/build/outputs/apk/debug',
                   'test': 'android/test-host/build/outputs/apk/androidTest/debug'}
    for label, directory in directories.items():
        apk, metadata_sha = select_apk(root / directory, PACKAGES[label], 'debugAndroidTest' if label == 'test' else 'debug')
        packages[label] = inspect_apk(apk, tools, report, label)
        packages[label]['metadata_sha256'] = metadata_sha
    certificate = json.loads((root / 'gradle/baseline-inputs.json').read_text())['signing_certificate_sha256']
    check_packages(packages, certificate)
    junit = junit_result(root / 'android/test-host/build/test-results/testDebugUnitTest/TEST-org.llmasr.minimal.testhost.FixtureContractTest.xml')
    check_inputs(root, inputs)  # Reject source/inventory drift during tool execution.
    require(build_inputs == product_checker.collect_build_inputs(root), 'build input SHA/inventory drift')
    sdk = tools.parents[1] / 'platforms/android-35/optional'
    tool_paths = [tools / 'aapt2', tools / 'zipalign', tools / 'lib/apksigner.jar',
                  sdk / 'android.test.base.jar', sdk / 'android.test.runner.jar']
    receipt = {'schema': 1, 'head': subprocess.check_output(['git', 'rev-parse', 'HEAD'], cwd=root, text=True).strip(),
               'git_status': subprocess.check_output(['git', 'status', '--porcelain=v1', '--untracked-files=all'], cwd=root, text=True),
               'module_inputs': inputs, 'build_inputs': build_inputs, 'checker_sha256': sha(Path(__file__)),
               'tool_inputs': {str(p): sha(p) for p in tool_paths},
               'packages': packages, 'junit': junit, 'device': 'not run', 'runner_discovery': 'not run',
               'limitations': ['Compiled/static packages only; no Binder/SAF grant/InputConnection execution',
                               'Product full/JNI/Lint acceptance is a separate required gate']}
    (report / 'receipt.json').write_text(json.dumps(receipt, indent=2) + '\n')
    print('PASS three APK current signature/Manifest/all-DEX/test isolation; 7 JVM, 12 compiled-only methods')
    print('ANDROID_TEST_REPORT ' + str(report))
    return report


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--report-dir', type=Path, required=True)
    args = parser.parse_args()
    try:
        verify(ROOT, args.report_dir)
    except (ValueError, OSError, KeyError, struct.error, zipfile.BadZipFile, ET.ParseError) as error:
        print('FATAL: ' + str(error), file=sys.stderr)
        sys.exit(1)
