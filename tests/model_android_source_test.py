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
assert manifest.get(ns+'versionName') == '0.9-debug'
assert manifest.get(ns+'versionCode') == '9'
def check_version_declarations(source, xml):
    # AGP owns final package version after staging removes the source attributes.
    code = re.findall(r'^\s*versionCode\s*=\s*(\d+)\s*$', source, re.M)
    name = re.findall(r'^\s*versionName\s*=\s*"([^"]+)"\s*$', source, re.M)
    if code != ['9'] or name != ['0.9-debug'] or code != [xml.get(ns+'versionCode')] or name != [xml.get(ns+'versionName')]:
        raise AssertionError('App version declarations must agree at 9 / 0.9-debug')
dsl = (android / 'build.gradle.kts').read_text()
check_version_declarations(dsl, manifest)
for before, after in [('versionCode = 9', 'versionCode = 8'), ('versionName = "0.9-debug"', 'versionName = "0.8-debug"')]:
    assert dsl.count(before) == 1, 'unique version declaration fixture'
    try:
        check_version_declarations(dsl.replace(before, after), manifest)
    except AssertionError as failure:
        assert str(failure) == 'App version declarations must agree at 9 / 0.9-debug'
    else:
        raise AssertionError('version declaration guard accepted independent stale field')
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
    'ModelAccess': 'model', 'ModelResidency': 'model', 'ModelVerification': 'model', 'AndroidModelVerificationIo': 'model',
    'ModelProviderBoundary': 'model', 'SafModelSource': 'model',
    'ModelPackagePolicy': 'model', 'ModelDeployment': 'model', 'ModelCatalog': 'model',
    'FixedModelPackage': 'model', 'ModelZipArchive': 'model', 'ModelZipSource': 'model', 'SafModelZipSource': 'model',
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
    'OperationContext': 'platform', 'ActivityWindow': 'platform',
}
def java(name):
    if name not in PACKAGE_MAP:
        raise AssertionError('R6 unknown class ' + name)
    sub = PACKAGE_MAP[name]
    base = SRC_ROOT / sub / name
    matches = [p for p in (base.with_suffix('.java'), base.with_suffix('.kt')) if p.is_file()]
    assert len(matches) == 1, 'missing/duplicate JVM source ' + name
    return matches[0].read_text()
graph = java('AppGraph')
graph_kotlin = (SRC_ROOT / 'AppGraph.kt').is_file()
assert ('AppState()' if graph_kotlin else 'new AppState()') in graph
assert 'state::notifyChange' not in graph
assert 'ModelAccess.forCatalog(catalog, resident)' in graph
assert ('ModelManagementController(coordinator, modelRepository, readiness, modelControl, modelState, runtimeLogStore)' if graph_kotlin else 'new ModelManagementController(coordinator, modelRepository, readiness, modelControl, modelState, runtimeLogStore)') in graph
assert 'RuntimeLogStore' in graph
assert 'runtimeLogStore' in graph
assert 'RuntimeLogPersistence' in graph
assert 'RuntimeLogWorker' in graph
assert 'ModelCatalog(FixedModelPackage.mnn(manifest)' in graph
assert 'AndroidModelVerificationIo.Catalog { app.noBackupFilesDir }' in graph
assert 'ModelRepository.forAppFiles(policy.manifest, files, space, verification)' in java('ModelCatalog')
assert 'ModelVerification(policy.manifest, ready, io.proof(key), resident, domain)' in java('ModelCatalog')
assert 'modelAccess.pin()' in java('AsrOperation') and 'modelAccess.pin()' in java('ImeBackend')
assert 'modelAccess.configPath(' in java('AsrOperation') and 'modelAccess.configPath(' in java('ImeBackend')
assert 'startRestore()' in java('ModelManagementController')
assert '恢复证明不重新计算 SHA' in java('ModelManagementActivity')
assert java('AsrOperation').count('modelAccess.requireResidentReady()') == 3
assert 'modelAccess.requireResidentReady()' in java('ImeBackend')
# Only the explicit user-intent preparation helper may hash; ordinary adapter gates remain light.
access = java('ModelAccess')
prepare = access.index('fun prepareForCapture(')
release = access.index('    fun requestRelease()', prepare)
assert 'repo.verifyAll' in access[prepare:release]
assert 'repo.verifyAll' not in access[:prepare] + access[release:]
assert 'modelAccess.qualifyCapture' in java('ImeBackend') and 'modelAccess.loadForCapture' in java('ImeBackend')
assert 'modelAccess.startEarlyCapture' in java('ImeBackend')
assert 'backend!!.qualify(control)' in java('ImeController')
assert java('ImeController').index('capture.start()') < java('ImeController').index('backend!!.prepare(control)')
assert 'backend!!.prepare(control)' in java('ImeController')
assert 'access::requireResidentReady' in graph
assert 'object : InferenceAdapter.Session' in graph
assert 'registerComponentCallbacks' in graph and 'level == 20' not in graph
assert 'startPrepare()' in java('ModelManagementActivity') and 'startRelease()' in java('ModelManagementActivity')
assert ('JniNativeTranscription()' if graph_kotlin else 'new JniNativeTranscription()') in graph
assert 'TaskKind.MODEL_OPERATION' not in java('AsrOperation')
assert 'MODEL_TREE' not in java('MainActivity')
assert 'setVerified(true)' not in '\n'.join(p.read_text() for p in (android/'src').rglob('*') if p.suffix in {'.java', '.kt'})
ime = java('AsrImeService')
ime_kotlin = (SRC_ROOT / 'AsrImeService.kt').is_file()
assert 'ModelManagementActivity' not in ime, 'IME App entry must not bypass HOME'
assert 'MainActivity::class.java' in ime and 'Intent.FLAG_ACTIVITY_CLEAR_TOP' in ime
assert 'leaveForExternalUi' in ime
activity = java('ModelManagementActivity')
assert 'takePersistableUriPermission' not in activity
assert 'Intent.EXTRA_ALLOW_MULTIPLE,false' in activity and 'Intent.ACTION_OPEN_DOCUMENT)' in activity
assert 'graph.modelZipSource(selection.value)' in activity and 'page.consumeTyped' in activity
assert 'FixedModelPackage.mnn(modelRepository!!.manifest())' in graph
assert 'page.stopped(isChangingConfigurations())' in activity
assert 'ActivityWindow.configure(this, scroll);' in activity
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
log_kotlin = (SRC_ROOT / 'LogsActivity.kt').is_file()
page_receiver = 'page!!.' if log_kotlin else 'page.'
assert page_receiver + 'foreground(true)' in log_page and page_receiver + 'foreground(false)' in log_page
assert page_receiver + 'destroy()' in log_page and page_receiver + 'result(request,' in log_page
log_kotlin = (SRC_ROOT / 'LogsActivity.kt').is_file()
assert ('LogExportPage(graph!!.logExports())' if log_kotlin else 'new LogExportPage<>(graph.logExports())') in log_page
assert ('LogExportController { target ->' if graph_kotlin else 'new LogExportController<>(target ->') in graph
assert ('context!!.contentResolver()!!.openOutputStream(unchanged<android.net.Uri>(target), "wt")' if graph_kotlin else 'context.contentResolver().openOutputStream(target, "wt")') in graph
def check_log_export_lane(source, kotlin):
    # Ignore comments: a removed call cannot survive as an explanatory comment.
    code = re.sub(r'/\*.*?\*/|//[^\n]*', '', source, flags=re.S)
    required = ('RuntimeLogCodec.writeSnapshot(ticket.snapshot, out)',
                ('ArrayBlockingQueue<Runnable>(1)' if kotlin else 'new ArrayBlockingQueue<Runnable>(1)'))
    if any(value not in code for value in required):
        raise AssertionError('log export codec/bounded queue source contract')

log_export_source = java('LogExportController')
log_export_kotlin = (SRC_ROOT / 'diagnostics/LogExportController.kt').is_file()
check_log_export_lane(log_export_source, log_export_kotlin)
queue = 'ArrayBlockingQueue<Runnable>(1)' if log_export_kotlin else 'new ArrayBlockingQueue<Runnable>(1)'
codec = 'RuntimeLogCodec.writeSnapshot(ticket.snapshot, out)'
for old, replacement in [(queue, queue.replace('(1)', '(2)')), (codec, 'bytesWithoutCodec()'),
                         (codec, '/* ' + codec + ' */')]:
    if log_export_source.count(old) != 1:
        raise AssertionError('log export negative anchor ambiguous')
    try:
        check_log_export_lane(log_export_source.replace(old, replacement), log_export_kotlin)
    except AssertionError:
        pass
    else:
        raise AssertionError('log export guard accepted target mutation')
assert ('runtimeLogStore()!!.snapshot()' if log_kotlin else 'runtimeLogStore().snapshot()') in log_page and 'RuntimeLogText.export(state)' in log_page
assert 'refresh.stop()' in log_page and ('WeakReference(a)' if log_kotlin else 'WeakReference<LogsActivity>') in log_page
assert 'if(page==Page.HOME)guardCapture(() -> startActivity(new Intent(this,LogsActivity.class)))' in java('MainActivity')
assert 'reportCancellation(r.cancel()); action.run()' in java('MainActivity')
assert 'LogsActivity' not in activity and 'page.cancelOwn(); returnHome();' in activity
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
