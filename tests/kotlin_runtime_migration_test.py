#!/usr/bin/env python3
"""Four real runtime adapters: frozen Java consumer, finite fakes and production mutants.
Host/fake only; Android SDK/module compilation is an independent acceptance layer.
"""
import argparse
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import re
import shutil
import signal
import tempfile

ROOT = Path(__file__).resolve().parents[1]
_spec = importlib.util.spec_from_file_location('runtime_owned', ROOT / 'tests/logs_activity_picker_test.py')
owned = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(owned)
FAMILY = ('audio/ForegroundRecorder', 'ime/ImeBackend', 'model/SafModelSource', 'platform/OperationContext')
FAKES = {'android/annotation/NonNull.java': 'package android.annotation; '
                                    '@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) '
                                    '@java.lang.annotation.Target({java.lang.annotation.ElementType.METHOD,java.lang.annotation.ElementType.PARAMETER}) '
                                    'public @interface NonNull {}',
 'android/annotation/Nullable.java': 'package android.annotation; '
                                     '@java.lang.annotation.Retention(java.lang.annotation.RetentionPolicy.CLASS) '
                                     '@java.lang.annotation.Target({java.lang.annotation.ElementType.METHOD,java.lang.annotation.ElementType.PARAMETER}) '
                                     'public @interface Nullable {}',
 'android/content/ContentResolver.java': 'package android.content;\n'
                                         'public abstract class ContentResolver {\n'
                                         ' public static String order="";public static android.net.Uri '
                                         'seen;public static int queryCalls,openCalls;public static boolean '
                                         'failQuery=true;public static android.database.Cursor cursor;\n'
                                         ' public static final SecurityException PRIVATE=new '
                                         'SecurityException("content://PRIVATE/account");\n'
                                         ' @android.annotation.Nullable public final android.database.Cursor '
                                         'query(@android.annotation.NonNull android.net.Uri '
                                         'uri,@android.annotation.Nullable String[] '
                                         'projection,@android.annotation.Nullable String '
                                         'selection,@android.annotation.Nullable String[] '
                                         'args,@android.annotation.Nullable String '
                                         'sort){queryCalls++;seen=uri;order+="query;";if(failQuery)throw '
                                         'PRIVATE;return cursor;}\n'
                                         ' @android.annotation.Nullable public final java.io.InputStream '
                                         'openInputStream(@android.annotation.NonNull android.net.Uri '
                                         'uri)throws '
                                         'java.io.FileNotFoundException{openCalls++;seen=uri;order+="openInput;";throw '
                                         'PRIVATE;}\n'
                                         '}',
 'android/content/Context.java': 'package android.content;\n'
                                 'public abstract class Context {\n'
                                 ' public static String order="";public static String seen="unset";public '
                                 'static int assetCalls;\n'
                                 ' public static final java.io.IOException SENTINEL=new '
                                 'java.io.IOException("asset-sentinel");\n'
                                 ' public abstract Context getApplicationContext();\n'
                                 ' public abstract java.io.File getFilesDir();public abstract java.io.File '
                                 'getCacheDir();\n'
                                 ' public abstract ContentResolver getContentResolver();\n'
                                 ' public abstract android.content.res.AssetManager getAssets();\n'
                                 '}',
 'android/content/res/AssetManager.java': 'package android.content.res;\n'
                                          'public final class AssetManager implements '
                                          'java.lang.AutoCloseable {\n'
                                          ' @android.annotation.NonNull public java.io.InputStream '
                                          'open(@android.annotation.NonNull String name)throws '
                                          'java.io.IOException { android.content.Context.order+="open;"; '
                                          'android.content.Context.seen=name;android.content.Context.assetCalls++;throw '
                                          'android.content.Context.SENTINEL; }\n'
                                          ' public void close(){}\n'
                                          '}',
 'android/database/Cursor.java': 'package android.database;\n'
                                 'public interface Cursor extends java.io.Closeable {boolean '
                                 'moveToNext();String getString(int n);boolean isNull(int n);long '
                                 'getLong(int n);void close();}\n',
 'android/media/AudioFormat.java': 'package android.media;\n'
                                   'public class AudioFormat { public static final int '
                                   'CHANNEL_IN_MONO=1,ENCODING_PCM_16BIT=2; }\n',
 'android/media/AudioRecord.java': 'package android.media;\n'
                                   'import org.llmasr.minimal.audio.RecordingControl;\n'
                                   'public class AudioRecord {\n'
                                   'public static final int '
                                   'STATE_INITIALIZED=1,RECORDSTATE_RECORDING=3,READ_NON_BLOCKING=1;\n'
                                   'public static String fail=""; public static int '
                                   'made,starts,reads,stops,releases;\n'
                                   'public static RecordingControl control; public static final '
                                   'AssertionError fatal=new AssertionError("fixture fatal");\n'
                                   'static void hit(String phase){if(fail.equals(phase))throw new '
                                   'SecurityException("content://PRIVATE/secret");}\n'
                                   'public static void reset(String phase,RecordingControl '
                                   'c){fail=phase;control=c;made=starts=reads=stops=releases=0;}\n'
                                   'public static int getMinBufferSize(int a,int b,int '
                                   'c){hit("minimum");return 6400;}\n'
                                   'public AudioRecord(int a,int b,int c,int d,int '
                                   'e){hit("constructor");made++;}\n'
                                   'public int getState(){hit("state");return STATE_INITIALIZED;} public int '
                                   'getSampleRate(){return 16000;}\n'
                                   'public void startRecording(){starts++;hit("start");}\n'
                                   'public int getRecordingState(){hit("recordingState");return '
                                   'starts>0?RECORDSTATE_RECORDING:0;}\n'
                                   'public int read(short[] b,int o,int n,int '
                                   'mode){reads++;if(fail.equals("fatal"))throw fatal;\n'
                                   'hit("read");control.stop();return n;}\n'
                                   'public void stop(){stops++;if(fail.equals("illegalStop"))throw new '
                                   'IllegalStateException();hit("stop");}\n'
                                   'public void release(){releases++;hit("release");}\n'
                                   '}\n',
 'android/media/MediaRecorder.java': 'package android.media;\n'
                                     'public class MediaRecorder { public static class AudioSource { public '
                                     'static final int MIC=1; } }\n',
 'android/net/Uri.java': 'package android.net; public abstract class Uri {}',
 'android/os/SystemClock.java': 'package android.os;\n'
                                'public class SystemClock { public static long elapsedRealtime(){return 1;} '
                                'public static void sleep(long n){} }\n',
 'android/provider/DocumentsContract.java': 'package android.provider;\n'
                                            'public final class DocumentsContract {\n'
                                            ' public static android.net.Uri '
                                            'buildChildDocumentsUriUsingTree(android.net.Uri tree,String '
                                            'id){android.content.ContentResolver.order+="build;";return '
                                            'null;}\n'
                                            ' public static String getTreeDocumentId(android.net.Uri '
                                            'tree){android.content.ContentResolver.order+="tree;";return '
                                            '"id";}\n'
                                            ' public static android.net.Uri '
                                            'buildDocumentUriUsingTree(android.net.Uri tree,String '
                                            'id){return tree;}\n'
                                            ' public static final class Document {\n'
                                            ' public static final String '
                                            'COLUMN_DOCUMENT_ID="document_id",COLUMN_DISPLAY_NAME="_display_name",COLUMN_MIME_TYPE="mime_type",COLUMN_FLAGS="flags",MIME_TYPE_DIR="vnd.android.document/directory";\n'
                                            ' public static final int FLAG_VIRTUAL_DOCUMENT=512;\n'
                                            ' }\n'
                                            '}'}


def require(value, message):
    if not value:
        raise RuntimeError(message)


def identity(path):
    data = path.read_bytes()
    return {'path': str(path), 'bytes': len(data), 'sha256': hashlib.sha256(data).hexdigest()}


def source(name):
    base = ROOT / 'android/app/src/org/llmasr/minimal' / name
    paths = [base.with_suffix(ext) for ext in ('.java', '.kt') if base.with_suffix(ext).is_file()]
    require(len(paths) == 1, 'missing/duplicate runtime source ' + name)
    return paths[0]


def prepare(directory):
    body, = re.findall(r'^host_classes=\(\n(.*?)^\)', (ROOT / 'scripts/test-minimal-apk.sh').read_text(), re.M | re.S)
    production = [source(name.replace('.', '/')) for name in body.split()]
    for name in FAMILY:
        if source(name) not in production:
            production.append(source(name))
    paths = []
    for path in production:
        target = directory / 'production' / path.relative_to(ROOT)
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(path, target)
        paths.append(target)
    caller = ROOT / 'tests/fixtures/kotlin-runtime/RuntimeContractTest.java'
    target = directory / 'callers' / caller.name
    target.parent.mkdir(parents=True)
    shutil.copyfile(caller, target)
    paths.append(target)
    for name, text in FAKES.items():
        target = directory / 'fakes' / name
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(text + '\n')
        paths.append(target)
    return paths


def suite(directory, green_only, mutation_names=None):
    directory.mkdir(parents=True, exist_ok=False)
    sources = prepare(directory)
    owned.write_json(directory / 'inputs.json', [identity(path) for path in sources])
    stdlib = Path(os.environ['KOTLIN_HOME']) / 'lib/kotlin-stdlib.jar'
    require(stdlib.is_file(), 'fixed Kotlin stdlib missing')

    def compile_run(label, mode='all', diagnostic=None):
        classes = directory / ('classes-' + label)
        require(not classes.exists(), 'fresh class output required')
        command = ['bash', str(ROOT / 'scripts/compile-jvm-sources.sh'), str(classes), '', *map(str, sources)]
        result = owned.execute(command, directory, label + '-compile', 270)
        require(result.returncode == 0, 'Compilation failure NOT behavioral red: ' + label + '\n' + result.stderr.decode())
        result = owned.execute(['java', '-cp', str(classes) + ':' + str(stdlib), 'RuntimeContractTest', mode],
                               directory, label + '-run', 45)
        if diagnostic is None:
            require(result.returncode == 0 and b'PASS runtime four production contracts' in result.stdout,
                    'runtime contract green failed ' + label + '\n' + result.stderr.decode())
        else:
            require(result.returncode == 1 and ('java.lang.AssertionError: ' + diagnostic).encode() in result.stderr,
                    'not exact external runtime behavioral red ' + label + '\n' + result.stderr.decode())
        return classes

    compile_run('green')
    if not green_only:
        mutations = [
            ('ForegroundRecorder', 'recorder', 'control!!.captureReleased()', 'Unit', 'captureReleased acknowledgement must survive every exit'),
            ('ImeBackend', 'backend', 'val target = progress!!', 'val target = progress', 'IME null progress must fail before capture'),
            ('SafModelSource', 'saf', 'resolver!!.query(list,', 'resolver!!.query(list!!,', 'SAF null Uri must reach receiver inside boundary'),
            ('SafModelSource', 'twr', '(primary as java.lang.Throwable).addSuppressed(closeFailure)', 'primary.addSuppressed(closeFailure)', 'SAF Java self suppression cause'),
            ('OperationContext', 'asset', 'unchanged<String>(name)', 'name!!', 'Asset null must reach receiver'),
            ('ImeBackend', 'ime-request-order', 'val targetRequest = request\n', 'val targetRequest = request!!\n', 'IME null request must evaluate filesDir first'),
            ('ImeBackend', 'ime-inference-order', 'val targetInference = inference\n', 'val targetInference = inference!!\n', 'IME null inference must evaluate arguments first'),
        ]
        for owner, mode, before, after, diagnostic in mutations:
            if mutation_names is not None and mode not in mutation_names:
                continue
            targets = [path for path in sources if path.stem == owner and path.suffix == '.kt']
            require(len(targets) == 1, 'mutant requires unique real Kotlin owner ' + owner)
            target = targets[0]
            original = target.read_bytes()
            text = original.decode()
            require(text.count(before) == 1, 'mutant anchor not unique ' + mode)
            bad = text.replace(before, after, 1)
            if mode == 'backend':
                require(bad.count('target.update(seconds)') == 1, 'bound progress callback anchor')
                bad = bad.replace('target.update(seconds)', 'target!!.update(seconds)', 1)
            try:
                target.write_text(bad)
                compile_run(mode + '-red', mode, diagnostic)
            finally:
                records = [directory / (mode + '-red-' + phase + '.json') for phase in ('compile', 'run')]
                states = [json.loads(path.read_text()) for path in records if path.exists()]
                if states and all(state.get('owned_exited') and state.get('root_reaped') for state in states):
                    target.write_bytes(original)
            require(target.read_bytes() == original, 'restoration refused or source mismatch ' + mode)
            compile_run(mode + '-restored', mode)
            print('PASS runtime production mutant compile0/external target1/restored0 ' + mode, flush=True)
    owned.write_json(directory / 'result.json', {'pass': True, 'green_only': green_only,
        'driver': identity(Path(__file__)), 'caller': identity(ROOT / 'tests/fixtures/kotlin-runtime/RuntimeContractTest.java'),
        'mixed_compile_routes': 1 if green_only else 1 + 2 * (7 if mutation_names is None else len(mutation_names)),
        'expected_compiler_commands': 2 if green_only else 2 + 4 * (7 if mutation_names is None else len(mutation_names)),
        'java_runs': 1 if green_only else 1 + 2 * (7 if mutation_names is None else len(mutation_names)),
        'mutation_names': mutation_names, 'compile_timeout': 270, 'java_timeout': 45,
        'budget_worst_seconds': 1 * 325 if green_only else 15 * 325, 'not_android_runtime': True})
    print('PASS runtime4 migration evidence ' + str(directory), flush=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--green-only', action='store_true')
    parser.add_argument('--evidence-dir', type=Path)
    args = parser.parse_args()
    if args.evidence_dir is not None:
        directory = args.evidence_dir
    else:
        parent = ROOT / '.work/ap5rest-order-fix/contracts'
        parent.mkdir(parents=True, exist_ok=True)
        directory = Path(tempfile.mkdtemp(prefix='run-', dir=parent)) / 'evidence'
    suite(directory, args.green_only)


if __name__ == '__main__':
    signal.signal(signal.SIGTERM, owned.interrupted_by_signal)
    main()
