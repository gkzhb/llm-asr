#!/usr/bin/env python3
"""Real temp Git trees exercise the fixture's actual input collector, no Gradle."""
import argparse
import hashlib
import importlib.util
from pathlib import Path
import shutil
import subprocess
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SPEC = importlib.util.spec_from_file_location('actual_fixture', ROOT / 'tests/android_test_fixture_test.py')
FIXTURE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(FIXTURE)
CONFIGS = ('settings.gradle.kts', 'build.gradle.kts', 'gradle.properties', 'buildscript-gradle.lockfile',
           'gradlew', 'gradlew.bat', 'core/build.gradle.kts', 'core/gradle.lockfile',
           'android/app/build.gradle.kts', 'android/app/gradle.lockfile',
           'android/asr-runtime/build.gradle.kts', 'android/asr-runtime/gradle.lockfile',
           'android/test-host/build.gradle.kts', 'android/test-host/gradle.lockfile',
           'gradle/wrapper/gradle-wrapper.jar', 'gradle/wrapper/gradle-wrapper.properties',
           'gradle/verification-metadata.xml')
ROOTS = ('android/app/src', 'android/asr-runtime/src', 'core/src', 'android/test-host/src')


def require(v, message):
    if not v:
        raise RuntimeError(message)


def put(root, name, content='maintained input\n'):
    p = root / name
    p.parent.mkdir(parents=True, exist_ok=True)
    p.write_text(content)
    return p


def git(root, *args):
    subprocess.run(['git', *args], cwd=root, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.PIPE)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--migration-only', action='store_true')
    parser.add_argument('--audio-only', action='store_true')
    args = parser.parse_args()
    with tempfile.TemporaryDirectory(prefix='fixture-input-git-') as temporary:
        root = Path(temporary)
        git(root, 'init', '-q')
        for name in CONFIGS:
            put(root, name)
        put(root, 'gradle/baseline-inputs.json', '{}\n')
        old = 'android/app/src/example/Old.java'
        put(root, old, 'enum Old { A }\n')
        for source_root in ROOTS:
            put(root, source_root + '/example/Kept.java', 'class Kept {}\n')
        put(root, 'android/test-host/fixtures/java/example/FakePorts.java', 'class FakePorts {}\n')
        git(root, 'add', '--all')  # Only isolated synthetic Git tree, never parent index.
        (root / old).unlink()
        fresh = 'android/app/src/example/Old.kt'
        put(root, fresh, 'enum class Old { A }\n')
        inventory = FIXTURE.fixture_inputs(root)
        require(old not in inventory, 'F1 deleted tracked Java remains in fixture inventory')
        require(fresh in inventory, 'F1 current untracked Kotlin missing from fixture inventory')
        require(len(inventory) == len(set(inventory)), 'duplicate fixture inputs')
        print('PASS actual collector deleted tracked Java excluded; untracked Kotlin included')
        if args.migration_only:
            return
        maintained = []
        for i, source_root in enumerate(ROOTS):
            maintained.extend([source_root + '/example/New' + str(i) + '.kt',
                               source_root + '/example/New' + str(i) + '.java',
                               source_root + '/example/New' + str(i) + '.xml'])
        maintained.extend(('android/test-host/fixtures/java/example/Added.kt',
                           'android/app/src/org/llmasr/minimal/audio/AsrText.kt',
                           'android/app/src/org/llmasr/minimal/audio/WaveInput.java'))
        for name in maintained:
            put(root, name)
        excluded = ('android/app/build/Generated.kt', 'core/src/build/Generated.java',
                    'core/src/.cache/Hidden.kt', 'core/src/private/Secret.java',
                    'android/test-host/build/Generated.java', 'android/test-host/.gradle/cache.kt',
                    'android/test-host/assets/Private.xml', 'android/test-host/models/Weights.java',
                    'android/test-host/signing.key', 'android/test-host/local.properties',
                    'android/test-host/fixtures/java/private/Secret.java',
                    'gradle/.cache/cache.bin', 'gradle/build/Generated.kt', 'gradle/private/token.txt',
                    '.cache/global.kt', 'models/Weights.kt', 'bench/audio/secret.wav', 'keys/signing.jks',
                    'android/app/src/org/llmasr/minimal/audio/private.wav',
                    'android/app/src/org/llmasr/minimal/audio/private.pcm')
        for name in excluded:
            put(root, name, 'excluded synthetic sentinel\n')
        # Generated/private data stays excluded even if accidentally tracked.
        git(root, 'add', '--all')
        linked = root / 'core/src/example/Linked.kt'
        linked.symlink_to(root / 'keys/signing.jks')
        linkdir = root / 'core/src/linked-private'
        linkdir.symlink_to(root / 'keys', target_is_directory=True)
        inventory = FIXTURE.fixture_inputs(root)
        require('android/app/src/org/llmasr/minimal/audio/AsrText.kt' in inventory and
                'android/app/src/org/llmasr/minimal/audio/WaveInput.java' in inventory,
                'F1 legitimate production audio Kotlin/Java package omitted')
        require(all(name not in inventory for name in excluded), 'generated/private/audio input selected')
        if args.audio_only:
            print('PASS actual collector legitimate audio Kotlin/Java package, no audio data')
            return
        require(all(name in inventory for name in maintained), 'current maintained four-root source missing')
        require(all(name not in inventory for name in excluded), 'generated/private input selected')
        require(str(linked.relative_to(root)) not in inventory, 'source symlink selected')
        require(all(not name.startswith('core/src/linked-private/') for name in inventory), 'symlink directory traversed')
        require(set(CONFIGS) <= set(inventory), 'required config omitted')
        with tempfile.TemporaryDirectory(prefix='fixture-input-copy-') as dest:
            for name in inventory:
                p = Path(dest) / name
                p.parent.mkdir(parents=True, exist_ok=True)
                shutil.copyfile(root / name, p)
                require(hashlib.sha256(p.read_bytes()).digest() == hashlib.sha256((root / name).read_bytes()).digest(),
                        'copied source identity mismatch')
        for missing in ('settings.gradle.kts', 'gradle/verification-metadata.xml', 'android/test-host/gradle.lockfile'):
            p = root / missing
            data = p.read_bytes()
            p.unlink()
            try:
                try:
                    FIXTURE.fixture_inputs(root)
                except FileNotFoundError as error:
                    require(missing in str(error), 'wrong missing-required-config diagnostic')
                else:
                    raise RuntimeError('missing required config silently skipped: ' + missing)
            finally:
                p.write_bytes(data)
        print('PASS actual collector four roots/configs/fixture source byte identities; generated/private/symlink exclusion; missing required config fails')


if __name__ == '__main__':
    main()
