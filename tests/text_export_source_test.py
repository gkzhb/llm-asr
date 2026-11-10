"""R5 Android wiring guards. Lexical checks with negative fixtures, NOT device tests."""
from pathlib import Path
ROOT = Path(__file__).resolve().parents[1] / 'android/app/src/org/llmasr/minimal'

def check_main(s):
    assert 'exportPage=new TextExportPage<>(graph.textExport())' in s
    resume=s.split('void onResume()',1)[1].split('void onPause()',1)[0]
    pause=s.split('void onPause()',1)[1].split('void onDestroy()',1)[0]
    destroy=s.split('void onDestroy()',1)[1]
    assert 'exportPage.foreground(page==Page.TRANSCRIBE)' in resume
    assert 'exportPage.foreground(false)' in pause
    assert 'textExport().addListener(appStateListener)' in resume
    assert 'textExport().removeListener(appStateListener)' in pause
    assert 'exportPage.destroy()' in destroy
    assert 'TextExportController.Listener' in s
    assert 'exportStatus.setText(TextExportController.statusText(exportState))' in s
    assert 'final ResultState.Snapshot original' in s
    assert 'currentRevision()' not in s
    assert 'appStateListener.whileExportBusy(foreground && exportState.busy())' in s
    poll=s.split('synchronized void whileExportBusy',1)[1].split('synchronized void detach',1)[0]
    assert 'main.removeCallbacks(this)' in poll and 'if(busy)main.postDelayed(this,250L)' in poll
    assert 'appStateListener.detach()' in pause and 'appStateListener.detach()' in destroy
    assert 'buttons.add(exportBtn)' not in s
    assert '已开始的外部写入不能撤回' in s
    leave=s.split('private void changePage(',1)[1].split('private void displayPage',1)[0]
    assert 'revokeWave(); exportPage.destroy(); exportPage=new TextExportPage' in leave
    export=s.split('private void beginTextExport()',1)[1].split('private void chooseWave()',1)[0]
    assert 'setLastStatus' not in export

source=(ROOT/'MainActivity.java').read_text()
check_main(source)
for old,new in [('exportPage.foreground(page==Page.TRANSCRIBE)',''),('exportPage.foreground(false)',''),
                ('textExport().addListener(appStateListener)','textExport().state()'),
                ('textExport().removeListener(appStateListener)','textExport().state()'),
                ('exportPage.destroy()',''),('final ResultState.Snapshot original','final String original'),
                ('if(busy)main.postDelayed(this,250L)',''),
                ('appStateListener.whileExportBusy(foreground && exportState.busy())','')]:
    assert old in source
    try: check_main(source.replace(old,new))
    except AssertionError: pass
    else: raise AssertionError('guard accepted missing lifecycle wiring: '+old)
print('PASS R5 Activity lifecycle/observer/atomic edit/export status wiring and 8 negative fixtures (not Android runtime)')
