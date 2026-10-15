#!/usr/bin/env python3
"""Target fresh XML diagnostics; pure fixtures, not actual Lint execution."""
import tempfile
from pathlib import Path
from gradle_lint_verification_test import check_xml

with tempfile.TemporaryDirectory(prefix='lint-report-') as temporary:
    root = Path(temporary)
    source = root / 'LintProbe.java'
    source.write_text('class Probe {\n activity.getOnBackInvokedDispatcher();\n}\n')
    report = root / 'report.xml'
    positive = '<issues />'
    target = f'<issues><issue id="NewApi" severity="Error" message="Call requires API level 33 (current min is 29)"><location file="{source}" line="2" /></issue></issues>'
    report.write_text(positive)
    check_xml(report, source, False)
    report.write_text(target)
    check_xml(report, source, True)
    for bad in (positive, target.replace('NewApi', 'OtherError'), target.replace('severity="Error"', 'severity="Warning"'),
                target.replace('line="2"', 'line="9"'), target.replace('LintProbe.java', 'Wrong.java'),
                target.replace('33', '35'), target.replace('29', '28'),
                target.replace('</issues>', target.split('<issues>', 1)[1]),
                target.replace('</issues>', '<issue id="OtherError" severity="Fatal" /></issues>')):
        report.write_text(bad)
        try:
            check_xml(report, source, True)
        except ValueError as error:
            if 'missing unique NewApi' not in str(error):
                raise AssertionError('wrong diagnostic') from error
        else:
            raise AssertionError('non-target Lint report accepted')
    report.write_text(target)
    try:
        check_xml(report, source, False)
    except ValueError as error:
        if str(error) != 'Lint positive contains Error/Fatal':
            raise AssertionError('wrong positive failure diagnostic') from error
    else:
        raise AssertionError('error-containing green report accepted')
    report.write_text(target.replace('severity="Error"', 'severity="Fatal"'))
    check_xml(report, source, True)
    report.write_text(positive)
    check_xml(report, source, False)
print('PASS pure Lint target XML positive/negative/restore (not actual Lint)')
