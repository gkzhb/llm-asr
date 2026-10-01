#!/usr/bin/env python3
"""Validate frozen product/assets plus the current explicit native artifact before staging."""
import argparse
import hashlib
import json
import shutil
import subprocess
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]


def digest(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()


def validate(root, native_path):
    baseline = json.loads((root / 'gradle/baseline-inputs.json').read_text())
    if baseline.get('schema') != 1:
        raise ValueError('Unknown baseline schema')
    for folder in ('android/app/src', 'android/app/assets', 'native/apk', 'patches'):
        expected_files = {relative for relative in baseline['files']
                          if relative.startswith(folder + '/')}
        actual_files = {str(path.relative_to(root)) for path in (root / folder).rglob('*')
                        if path.is_file() or path.is_symlink()}
        if actual_files != expected_files:
            raise ValueError('Frozen product inventory mismatch: ' + folder)
    for relative, expected in baseline['files'].items():
        path = root / relative
        if path.is_symlink() or not path.is_file() or digest(path) != expected:
            raise ValueError('Frozen product input mismatch: ' + relative)
    for path, expected in [(native_path, baseline['native']['sha256']),
                           (root / baseline['sample']['path'], baseline['sample']['sha256'])]:
        if any(p.is_symlink() for p in [path, *path.parents]) or not path.is_file() or digest(path) != expected:
            raise ValueError('Explicit artifact hash mismatch: ' + str(path))
    # Reuse the existing fail-closed model config verifier; it never loads weights.
    subprocess.run(['python3', str(root / 'scripts/prepare-model-configs.py'), '--check'],
                   cwd=root, check=True)
    return baseline


def stage(root, output, native_path):
    baseline = validate(root, native_path)
    # The output is a generated Gradle directory; never copy arbitrary .work trees.
    if output.is_symlink():
        raise ValueError('Generated output must not be a symlink')
    for parent in [output, *output.parents]:
        if parent.is_symlink():
            raise ValueError('Generated output ancestor must not be a symlink')
    if output.exists() and any(path.is_symlink() for path in output.rglob('*')):
        raise ValueError('Generated output descendants must not be symlinks')
    output.mkdir(parents=True, exist_ok=True)
    assets = output / 'assets'
    native = output / 'jniLibs/arm64-v8a'
    assets.mkdir(exist_ok=True)
    native.mkdir(parents=True, exist_ok=True)
    expected_assets = {path.name for path in (root / 'android/app/assets').iterdir() if path.is_file()}
    expected_assets.update(['model-manifest.json', 'sample.wav'])
    if any(path.name not in expected_assets or not path.is_file() for path in assets.iterdir()):
        raise ValueError('Unexpected stale generated asset')
    if any(path.name != 'libqwen_asr_jni.so' or not path.is_file() for path in native.iterdir()):
        raise ValueError('Unexpected stale generated native input')
    for path in (root / 'android/app/assets').iterdir():
        if path.is_file():
            shutil.copyfile(path, assets / path.name)
    shutil.copyfile(root / 'reports/p0/mnn-model-manifest.json', assets / 'model-manifest.json')
    shutil.copyfile(root / baseline['sample']['path'], assets / 'sample.wav')
    shutil.copyfile(native_path, native / 'libqwen_asr_jni.so')
    # AGP8 requires namespace in DSL, not source manifest package. Keep source
    # manifest frozen for legacy checks; remove only redundant generated attributes.
    ET.register_namespace('android', 'http://schemas.android.com/apk/res/android')
    manifest = ET.parse(root / 'android/app/AndroidManifest.xml')
    android = '{http://schemas.android.com/apk/res/android}'
    for key in ('package', android + 'versionCode', android + 'versionName'):
        manifest.getroot().attrib.pop(key, None)
    sdk = manifest.getroot().find('uses-sdk')
    if sdk is not None:
        manifest.getroot().remove(sdk)
    # AGP owns the packaging switch and generates extractNativeLibs=true from
    # useLegacyPackaging. Remove only the redundant generated-copy attribute.
    manifest.getroot().find('application').attrib.pop(android + 'extractNativeLibs', None)
    manifest.write(output / 'AndroidManifest.xml', encoding='utf-8', xml_declaration=True)
    (output / 'provenance.json').write_text(json.dumps(baseline, indent=2) + '\n')
    print('PASS frozen product/assets and explicit current native artifact')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', required=True, type=Path)
    parser.add_argument('--native', required=True, type=Path)
    args = parser.parse_args()
    stage(ROOT, args.output, args.native)
