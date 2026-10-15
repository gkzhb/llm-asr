#!/usr/bin/env python3
"""Lexical Activity integration guards with actual weakening fixtures, NOT device tests."""
from pathlib import Path
import re
import subprocess
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
BASE = ROOT / 'android/app/src/org/llmasr/minimal'
NS = '{http://schemas.android.com/apk/res/android}'


def check_pages(pages, manifest):
    activities = ET.fromstring(manifest).find('application').findall('activity')
    for name, text in pages.items():
        if 'ActivityWindow.configure(this, scroll);' not in text:
            raise ValueError(name + ' normal viewport missing')
        failure_name = 'text' if name == 'LogsActivity' else 'failure'
        if 'ActivityWindow.configure(this, ' + failure_name + ');' not in text:
            raise ValueError(name + ' failure viewport missing')
        if 'new ActivityWindow.BackRegistration(this, this::onBackPressed)' not in text:
            raise ValueError(name + ' callback must use existing back policy')
        if 'windowBack.resumed();' not in text or 'windowBack.paused();' not in text:
            raise ValueError(name + ' back lifecycle missing')
        if 'setOnApplyWindowInsetsListener' in text:
            raise ValueError(name + ' duplicate inset strategy')
        matching = [a for a in activities if a.get(NS + 'name') == '.' + name]
        if len(matching) != 1 or matching[0].get(NS + 'enableOnBackInvokedCallback') != 'true':
            raise ValueError(name + ' manifest callback opt-in missing')
        if matching[0].get(NS + 'windowSoftInputMode') != 'adjustResize':
            raise ValueError(name + ' resize policy missing')
    main = pages['MainActivity']
    back = main[main.index('    @Override public void onBackPressed()'):main.index('    @Override protected void onDestroy()')]
    if not (back.index('r.cancel()') < back.index('coordinator().isBusy()') < back.rindex('super.onBackPressed()')):
        raise ValueError('recording cancellation/busy/default order changed')
    if 'page.cancelOwn(); finish();' not in pages['ModelManagementActivity']:
        raise ValueError('model cancel confirmation must retain real owner cleanup')


def check_api_contract(source):
    # Lexical contract only; actual boundary dispatch runs in activity_window_test.py.
    for level in (30, 33):
        if not re.search(r'@(?:android\.annotation\.)?TargetApi\(' + str(level) +
                         r'\)\s+private static final class Api' + str(level), source):
            raise ValueError('helper API contract missing: ' + str(level))
    for guarded in (
        'if (Build.VERSION.SDK_INT >= 30) Api30.edgeToEdge(window);',
        'if (Build.VERSION.SDK_INT >= 30) return Api30.safeInsets(insets);',
        'if (Build.VERSION.SDK_INT >= 33 && callback == null)',
        'if (Build.VERSION.SDK_INT >= 33 && callback != null)',
    ):
        if source.count(guarded) != 1:
            raise ValueError('helper outer SDK guard missing or changed')
    if 'SuppressLint' in source:
        raise ValueError('API diagnostic suppression is not an API contract')


class WiringTests(unittest.TestCase):
    def setUp(self):
        self.pages = {name: (BASE / (name + '.java')).read_text()
                      for name in ['MainActivity', 'ModelManagementActivity', 'LogsActivity']}
        self.manifest = (ROOT / 'android/app/AndroidManifest.xml').read_text()

    def test_api_contract_and_guard_negatives(self):
        source = (BASE / 'platform/ActivityWindow.java').read_text()
        check_api_contract(source)
        for level in (30, 33):
            before = '@android.annotation.TargetApi(' + str(level) + ')'
            self.assertEqual(source.count(before), 1)
            for replacement in ('', '@android.annotation.TargetApi(29)'):
                with self.assertRaisesRegex(ValueError, 'helper API contract missing'):
                    check_api_contract(source.replace(before, replacement))
        for before in ('SDK_INT >= 30', 'SDK_INT >= 33'):
            with self.assertRaisesRegex(ValueError, 'outer SDK guard'):
                check_api_contract(source.replace(before, 'SDK_INT >= 29'))

    def test_current_wiring(self):
        check_pages(self.pages, self.manifest)
        self.assertNotIn('ActivityWindow', (BASE / 'AsrImeService.java').read_text())

    def test_pre_m6_production_is_red(self):
        # Frozen public source baseline only; no prior test/classes/artifact reuse.
        old = dict(self.pages)
        old['MainActivity'] = subprocess.check_output([
            'git', 'show', '82a5563:android/app/src/org/llmasr/minimal/MainActivity.java'],
            cwd=ROOT, text=True)
        with self.assertRaisesRegex(ValueError, 'normal viewport missing'):
            check_pages(old, self.manifest)

    def test_real_guard_negative_fixtures(self):
        for name in self.pages:
            for before in ['ActivityWindow.configure(this, scroll);',
                           'windowBack.resumed();', 'windowBack.paused();',
                           'new ActivityWindow.BackRegistration(this, this::onBackPressed)']:
                pages = dict(self.pages)
                self.assertEqual(pages[name].count(before), 1)
                pages[name] = pages[name].replace(before, '')
                with self.assertRaises(ValueError):
                    check_pages(pages, self.manifest)
        with self.assertRaises(ValueError):
            check_pages(self.pages, self.manifest.replace('enableOnBackInvokedCallback="true"',
                                                         'enableOnBackInvokedCallback="false"'))


if __name__ == '__main__':
    unittest.main()
