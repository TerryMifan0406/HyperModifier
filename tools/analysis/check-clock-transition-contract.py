"""Check the SystemUI-side observer contract against local APK smali.

Offline only; does not install anything or claim runtime Hook coverage.
Usage: python3 -B tools/analysis/check-clock-transition-contract.py reference/systemui-4.0.2.0
"""
import json
import sys
from pathlib import Path

root = Path(sys.argv[1])
contracts = {
    'com/miui/clock/allInOne/AllInOneBase': {
        'methods': ['onFinishInflate()V', 'onDetachedFromWindow()V'],
        'fields': ['mDoubleRow:Z', 'mEditMode:Z', 'mTimeGroup:Landroid/view/View;',
                   'mHourView:Lcom/miui/clock/allInOne/TimeView;',
                   'mMinuteView:Lcom/miui/clock/allInOne/TimeView;',
                   'mColonView:Lcom/miui/clock/allInOne/TimeView;'],
    },
    'com/miui/clock/allInOne/TimeView': {
        'fields': ['mTextPath:Landroid/graphics/Path;', 'mColonPath:Landroid/graphics/Path;',
                   'mMinuteOnlyPath:Landroid/graphics/Path;', 'mDrawTextX:F', 'mDrawTextY:F',
                   'mColonSplitIndex:I', 'mColonAlpha:F', 'fontAlpha:F', 'mColonAdvanceWidth:F',
                   'mText:Ljava/lang/String;', 'isMiGlassEffectEnable:Z'],
    },
    'com/android/systemui/keyguard/KeyguardViewMediator': {
        'methods': [
            'startKeyguardExitAnimation(Lcom/android/wm/shell/shared/compat/SurfaceTransition$Params;)V',
            'handleStartKeyguardExitAnimationInner(Lcom/android/wm/shell/shared/compat/SurfaceTransition$Params;)V',
            'onKeyguardExitFinished(Ljava/lang/String;)V',
            'cancelKeyguardExitAnimation()V', 'handleCancelKeyguardExitAnimation()V',
        ],
    },
    'com/android/systemui/keyguard/KeyguardService$1': {
        'methods': ['startAnimation(Landroid/os/IBinder;Landroid/window/TransitionInfo;Landroid/view/SurfaceControl$Transaction;Landroid/window/IRemoteTransitionFinishedCallback;)V'],
    },
    'com/android/keyguard/injector/KeyguardViewMediatorInjector': {
        'methods': ['keyguardUnlockHide(JJLjava/lang/String;)V', 'keyguardGoingAway()V'],
    },
}
result = []
for owner, contract in contracts.items():
    matches = list(root.glob('smali*/' + owner + '.smali'))
    if len(matches) != 1:
        raise SystemExit(f'{owner}: expected exactly one class, found {len(matches)}')
    path = matches[0]
    lines = path.read_text().splitlines()
    for kind, entries in contract.items():
        prefix = '.method ' if kind == 'methods' else '.field '
        for entry in entries:
            found = [i for i, line in enumerate(lines, 1)
                     if line.startswith(prefix) and line.split()[-1] == entry]
            if len(found) != 1:
                raise SystemExit(f'{owner}: missing or ambiguous {entry}')
            result.append({'owner': owner, 'member': entry,
                           'file': str(path), 'line': found[0]})
print(json.dumps({'verified_members': len(result), 'members': result,
                  'runtime_required': ['ClassLoader resolution of SurfaceTransition.Params',
                                       'Hook delivery/order after compiler inlining',
                                       'live horizontal clock bounds and window lifetime'],
                  'desktop_hooks_added': 0}, ensure_ascii=False, indent=2))
