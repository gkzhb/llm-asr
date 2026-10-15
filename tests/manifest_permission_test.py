#!/usr/bin/env python3
"""Exact source/merged XML recording contract, not signed APK or runtime permission acceptance."""
import copy
from pathlib import Path
import unittest
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[1]
ANDROID = '{http://schemas.android.com/apk/res/android}'
PERMISSION = {ANDROID + 'name': 'android.permission.RECORD_AUDIO'}
FEATURE = {ANDROID + 'name': 'android.hardware.microphone', ANDROID + 'required': 'false'}


def check_manifest(manifest, kind):
    """Shared narrow assertion used by source, real stage and actual merge checks."""
    permissions = [node for node in manifest if node.tag.startswith('uses-permission')]
    if (len(permissions) != 1 or permissions[0].tag != 'uses-permission'
            or permissions[0].attrib != PERMISSION or len(permissions[0])):
        raise ValueError('Expected exactly RECORD_AUDIO-only, without permission attributes/variants')
    features = manifest.findall('uses-feature')
    application = manifest.find('application')
    if kind == 'runtime-source':
        if manifest.attrib or len(manifest) != 1:
            raise ValueError('Runtime source must contribute only the recording permission')
    elif kind == 'runtime-merged':
        if features or application is not None:
            raise ValueError('Runtime must not contribute microphone/debug policy')
    elif kind in ('app-source', 'app-staged', 'app-merged'):
        if len(features) != 1 or features[0].attrib != FEATURE:
            raise ValueError('Expected optional microphone feature exactly')
        expected = None if kind == 'app-staged' else 'true'
        if application is None or application.get(ANDROID + 'debuggable') != expected:
            raise ValueError('Unexpected App debug policy')
    else:
        raise ValueError('Unknown manifest check kind')


class PermissionTests(unittest.TestCase):
    def test_runtime_source_contract(self):
        check_manifest(ET.parse(ROOT / 'android/asr-runtime/src/main/AndroidManifest.xml').getroot(),
                       'runtime-source')

    def test_app_source_contract(self):
        check_manifest(ET.parse(ROOT / 'android/app/AndroidManifest.xml').getroot(), 'app-source')

    def test_permission_weakenings_rejected(self):
        good = ET.Element('manifest')
        ET.SubElement(good, 'uses-permission', PERMISSION)
        check_manifest(good, 'runtime-source')
        variants = [ET.Element('manifest')]
        extra = copy.deepcopy(good)
        ET.SubElement(extra, 'uses-permission', {ANDROID + 'name': 'android.permission.INTERNET'})
        variants.append(extra)
        duplicate = copy.deepcopy(good)
        duplicate.append(copy.deepcopy(duplicate[0]))
        variants.append(duplicate)
        for attribute, value in [('maxSdkVersion', '32'), ('usesPermissionFlags', 'neverForLocation')]:
            changed = copy.deepcopy(good)
            changed[0].set(ANDROID + attribute, value)
            variants.append(changed)
        for tag in ('uses-permission-sdk-23', 'uses-permission-sdk-m'):
            changed = copy.deepcopy(good)
            changed[0].tag = tag
            variants.append(changed)
            extra_variant = copy.deepcopy(good)
            ET.SubElement(extra_variant, tag, PERMISSION)
            variants.append(extra_variant)
        wrong = copy.deepcopy(good)
        wrong[0].set(ANDROID + 'name', 'android.permission.CAMERA')
        variants.append(wrong)
        for number, variant in enumerate(variants):
            with self.subTest(variant=number):
                for kind in ('runtime-source', 'runtime-merged', 'app-source', 'app-staged', 'app-merged'):
                    with self.assertRaisesRegex(ValueError, 'RECORD_AUDIO-only'):
                        check_manifest(variant, kind)

    def test_feature_and_debug_weakenings_rejected(self):
        app = ET.parse(ROOT / 'android/app/AndroidManifest.xml').getroot()
        for mode in ('app-source', 'app-merged'):
            for change in ('required', 'missing-feature', 'debug'):
                bad = copy.deepcopy(app)
                if change == 'required':
                    bad.find('uses-feature').set(ANDROID + 'required', 'true')
                elif change == 'missing-feature':
                    bad.remove(bad.find('uses-feature'))
                else:
                    bad.find('application').set(ANDROID + 'debuggable', 'false')
                with self.subTest(mode=mode, change=change), self.assertRaises(ValueError):
                    check_manifest(bad, mode)
        with self.assertRaisesRegex(ValueError, 'debug policy'):
            check_manifest(app, 'app-staged')


if __name__ == '__main__':
    unittest.main()
