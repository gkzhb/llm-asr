"""Source wiring/package-input checks, never claimed compiled APK/device verification."""
from pathlib import Path
import ast
import re
import xml.etree.ElementTree as ET
root = Path(__file__).resolve().parents[1]
android = root / 'android/app'
SRC_ROOT = android / 'src/org/llmasr/minimal'
ns = '{http://schemas.android.com/apk/res/android}'
manifest = ET.parse(android / 'AndroidManifest.xml').getroot()
assert manifest.get(ns+'versionName') == '0.6-debug'
assert manifest.get(ns+'versionCode') == '6'
assert [p.get(ns+'name') for p in manifest.findall('uses-permission')] == ['android.permission.RECORD_AUDIO']
app = manifest.find('application')
assert len(app.findall('activity')) == 3
page, = [a for a in app.findall('activity') if a.get(ns+'name') == '.ModelManagementActivity']
assert page.get(ns+'exported') == 'false' and not page.findall('intent-filter')
logs, = [a for a in app.findall('activity') if a.get(ns+'name') == '.LogsActivity']
assert logs.get(ns+'exported') == 'false' and not logs.findall('intent-filter')
assert all(e.get(ns+'process') is None for e in manifest.iter())
service, = app.findall('service')
assert service.get(ns+'name') == '.AsrImeService'
assert service.get(ns+'permission') == 'android.permission.BIND_INPUT_METHOD'
assert service.get(ns+'exported') == 'true'
# R6 package map: simple class name -> subpackage under org.llmasr.minimal.
# Keep this map in lockstep with the migration in scripts/compile-android-java.sh.
PACKAGE_MAP = {
    'AppGraph': '', 'MainActivity': '', 'AsrImeService': '',
    'ModelManagementActivity': '', 'LogsActivity': '',
    'TaskKind': 'task', 'RequestContext': 'task', 'TaskCoordinator': 'task', 'RequestRunner': 'task',
    'WaveInput': 'audio', 'PcmWave': 'audio', 'AsrText': 'audio',
    'RecordingControl': 'audio', 'ForegroundRecorder': 'audio',
    'ModelEntry': 'model', 'ModelManifest': 'model', 'ModelReports': 'model',
    'ModelSource': 'model', 'ModelRepository': 'model', 'ModelReadiness': 'model',
    'ModelAccess': 'model', 'ModelProviderBoundary': 'model', 'SafModelSource': 'model',
    'FileSafety': 'model',
    'NativeResponse': 'asr', 'NativeTranscription': 'asr', 'JniNativeTranscription': 'asr',
    'InferenceAdapter': 'asr', 'InferencePhase': 'asr', 'InferencePhaseListener': 'asr',
    'LoggingInferencePhaseListener': 'asr',
    'AppState': 'transcription', 'ResultState': 'transcription',
    'TextExportController': 'transcription', 'TextExportPage': 'transcription',
    'AsrOperation': 'transcription', 'AppRequestPolicy': 'transcription',
    'AppReportWriter': 'transcription', 'ResultFiles': 'transcription',
    'ImeSession': 'ime', 'ImeFieldPolicy': 'ime', 'ImeController': 'ime', 'ImeBackend': 'ime',
    'ModelOperationControl': 'modelmanagement', 'ModelManagementState': 'modelmanagement',
    'ModelManagementController': 'modelmanagement', 'ModelPageSession': 'modelmanagement',
    'ModelPickerTickets': 'modelmanagement', 'ModelUiText': 'modelmanagement',
    'RuntimeLogEventKind': 'diagnostics', 'RuntimeLogSource': 'diagnostics',
    'RuntimeLogEvent': 'diagnostics', 'RuntimeLogClock': 'diagnostics',
    'RuntimeLogSink': 'diagnostics', 'RuntimeLogStore': 'diagnostics',
    'RuntimeLogPersistence': 'diagnostics', 'RuntimeLogCodec': 'diagnostics',
    'RuntimeLogWorker': 'diagnostics', 'LogExportController': 'diagnostics',
    'LogExportPage': 'diagnostics', 'RuntimeLogText': 'diagnostics',
    'OperationContext': 'platform',
}
def java(name):
    if name not in PACKAGE_MAP:
        raise AssertionError('R6 unknown class ' + name)
    sub = PACKAGE_MAP[name]
    if sub:
        return (SRC_ROOT / sub / f'{name}.java').read_text()
    return (SRC_ROOT / f'{name}.java').read_text()
graph = java('AppGraph')
assert 'new AppState()' in graph
assert 'state::notifyChange' not in graph
assert 'new ModelAccess(repository, readiness)' in graph
assert 'new ModelManagementController(coordinator, modelRepository, readiness, modelControl, modelState, runtimeLogStore)' in graph
assert 'RuntimeLogStore' in graph
assert 'runtimeLogStore' in graph
assert 'RuntimeLogPersistence' in graph
assert 'RuntimeLogWorker' in graph
assert 'ModelRepository.forAppFiles(manifest, opCtx.filesDir(), space)' in graph
assert 'access::requireReady' in graph
assert 'new JniNativeTranscription()' in graph
assert 'TaskKind.MODEL_OPERATION' not in java('AsrOperation')
assert 'MODEL_TREE' not in java('MainActivity')
assert 'setVerified(true)' not in '\n'.join(p.read_text() for p in (android/'src').rglob('*.java'))
ime = java('AsrImeService')
assert re.search(r'"模型管理", \(\) -> leaveForExternalUi\(\(\) ->\s*startActivity\(new Intent\(this, ModelManagementActivity.class\).addFlags\(Intent.FLAG_ACTIVITY_NEW_TASK\)', ime)
activity = java('ModelManagementActivity')
assert 'takePersistableUriPermission' not in activity and 'putExtra' not in activity
assert 'page.stopped(isChangingConfigurations())' in activity
assert 'WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout()' in activity
assert 'getApplicationContext().getContentResolver()' in activity
for name in ['check-minimal-apk.py']:
    ast.parse((root/'scripts'/name).read_text())
# Exercise the real checker's new tree clause with a synthetic aapt-format fixture.
# This verifies checker logic only, NOT a compiled resource/manifest claim.
checker = (root/'scripts/check-minimal-apk.py').read_text()
clause = checker[checker.index('# Require exactly one internal model page'):checker.index("method = (report/")]
fixture = '''  E: application
    E: activity
      A: android:name="org.llmasr.minimal.MainActivity"
      A: android:exported=true
    E: activity
      A: android:name="org.llmasr.minimal.ModelManagementActivity"
      A: android:exported=false
    E: activity
      A: android:name="org.llmasr.minimal.LogsActivity"
      A: android:exported=false
'''
def check_tree(text):
    exec(clause, {'re': re, 'lines': text.splitlines()})
check_tree(fixture)
for bad in [fixture.replace('=false', '=true'), fixture.replace('=false', '=false\n      E: intent-filter'), fixture.replace('=false', '=false\n      A: android:process="other"'), fixture.replace('ModelManagementActivity', 'WrongActivity'), fixture.replace('LogsActivity', 'WrongActivity'), fixture.replace('LogsActivity', 'ModelManagementActivity')]:
    try:
        check_tree(bad)
    except AssertionError:
        pass
    else:
        raise AssertionError('checker accepted a weakened manifest')
print('PASS Android source wiring/manifest policy and checker clause mutation fixtures (not compiled APK)')

# Log page integration guards, not Android lifecycle/runtime simulation.
log_page = java('LogsActivity')
assert 'graph.appState()' not in log_page and 'new Thread' not in log_page
assert 'exportSnapshot' not in log_page and 'takePersistableUriPermission' not in log_page
assert 'page.foreground(true)' in log_page and 'page.foreground(false)' in log_page
assert 'page.destroy()' in log_page and 'page.result(request,' in log_page
assert 'new LogExportPage<>(graph.logExports())' in log_page
assert 'new LogExportController<>(target ->' in graph
assert 'context.contentResolver().openOutputStream(target, "wt")' in graph
assert 'RuntimeLogCodec.writeSnapshot(ticket.snapshot, out)' in java('LogExportController')
assert 'new ArrayBlockingQueue<Runnable>(1)' in java('LogExportController')
assert 'runtimeLogStore().snapshot()' in log_page and 'RuntimeLogText.export(state)' in log_page
assert 'refresh.stop()' in log_page and 'WeakReference<LogsActivity>' in log_page
assert 'recording.cancel(); startActivity(new Intent(this, LogsActivity.class))' in java('MainActivity')
assert 'if (page.ownsActive())' in activity
# Isolate weakening of just the LogsActivity node, leaving the model page intact.
log_node = fixture[fixture.index('    E: activity\n      A: android:name="org.llmasr.minimal.LogsActivity"'):]
for bad_node in [log_node.replace('=false', '=true'),
                 log_node+'      E: intent-filter\n',
                 log_node+'      A: android:process="other"\n', '', log_node+log_node]:
    try:
        check_tree(fixture.replace(log_node,bad_node))
    except AssertionError:
        pass
    else:
        raise AssertionError('checker accepted unsafe/missing/duplicate log page')
print('PASS log page source wiring and isolated log component negative fixtures (not Android runtime)')
