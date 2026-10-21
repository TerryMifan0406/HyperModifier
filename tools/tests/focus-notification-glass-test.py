#!/usr/bin/env python3
"""Validate production recipes and Hook contracts against local, uncommitted SystemUI APKs."""
import argparse
import re
import subprocess
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--aapt2', required=True)
parser.add_argument('--apk', required=True)
parser.add_argument('--previous', required=True)
parser.add_argument('--decoded', required=True)
args = parser.parse_args()
root = Path(__file__).resolve().parents[2]
source = (root / 'app/src/main/java/com/aritxonly/myhypermodifier/settings/FocusNotificationGlassPolicy.java').read_text()

def recipes(apk):
    dump = subprocess.check_output([args.aapt2, 'dump', 'resources', apk], text=True)
    result = {}
    for kind, name in [('NORMAL', 'normal'), ('KEYGUARD', 'on_keyguard'), ('AOD', 'full_aod')]:
        match = re.search(r'array/focus_notification_glass_params_' + name +
                          r'\s+\(\) \(array\) size=42\s+\[(.*?)\]', dump, re.S)
        assert match, (apk, name)
        result[kind] = [float(v) for v in re.findall(r'"([^"]+)"', match[1])]
        assert len(result[kind]) == 42
    return result

current, previous = recipes(args.apk), recipes(args.previous)
assert current == previous, 'Native recipes changed; reassess compatibility before copying'
for kind, values in current.items():
    body = re.search(r'DARK_' + kind + r' = \{(.*?)\};', source, re.S)[1]
    implemented = [float(v.strip().removesuffix('f')) for v in body.split(',')]
    assert implemented == values, kind

base = Path(args.decoded)
effects = ['FocusNotificationGlassEffect', 'FocusNotificationGlassOnKeyguardEffect',
           'FocusNotificationGlassOnKeyguardLightWallPaperEffect', 'FocusNotificationGlassFullAodEffect']
for name in effects + ['FocusNotificationGlassCustomBgEffect']:
    files = list(base.glob('smali*/com/android/systemui/statusbar/notification/style/vieweffect/' + name + '.smali'))
    assert len(files) == 1, name
    code = files[0].read_text()
    assert '.method public final apply(Ljava/lang/Object;Landroid/content/Context;)V' in code, name
    if name in effects:
        assert '.method public final clear(Ljava/lang/Object;)V' in code, name
        assert 'MiGlassCompat;->setMiGlassCompat(Landroid/view/View;[F)V' in code, name
        assert 'getBackgroundNormal()Lcom/android/systemui/statusbar/notification/row/NotificationBackgroundView;' in code
for owner, signature in [
    ('ExpandableNotificationRow', 'getInjector()Lcom/android/systemui/statusbar/notification/row/ExpandableNotificationRowInjector;'),
    ('ExpandableNotificationRowInjector', 'getBackgroundNormal()Lcom/android/systemui/statusbar/notification/row/NotificationBackgroundView;'),
]:
    path = next(base.glob('smali*/com/android/systemui/statusbar/notification/row/' + owner + '.smali'))
    assert signature in path.read_text(), owner
print('PASS: 126 recipe channels match both APKs; four apply/clear pairs, custom-image exit and row access contracts verified')
