"""Exercise production MVP installer against a local calibrated libapp.so.
Host Hook callbacks are fake; no Dart code or Android process is executed.
Usage: python3 -B tools/tests/clock-mvp-install-test.py /local/libapp.so
"""
from pathlib import Path
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[2]
image = Path(sys.argv[1]).resolve()
source = (root / 'app/src/main/cpp/clock_mvp.cpp').read_text().replace('#include <android/log.h>', '''
#include <cassert>
#include <fstream>
#include <vector>
constexpr int ANDROID_LOG_WARN=5, ANDROID_LOG_INFO=4;
void __android_log_write(int, const char *, const char *) {}
''')
source += r'''
extern "C" void mhm_clock_workspace_entry() {}
extern "C" void mhm_clock_unlock_entry() {}
extern "C" void mhm_clock_predicate_entry() {}
unsigned calls=0, rollbacks=0, failAt=0;
bool noTrampoline=false;
int fakeHook(void *, void *, void **out) {
    ++calls;
    *out = calls == failAt && noTrampoline ? nullptr : reinterpret_cast<void *>(0x1234);
    return calls == failAt && !noTrampoline ? 1 : 0;
}
int fakeUnhook(void *) { ++rollbacks; return 0; }
void reset() {
    attempted=false; ready.store(false); requested.store(false);
    mhm_clock_mvp_enabled=0; clockMvpPeerUid.store(0);
    calls=rollbacks=failAt=0; noTrampoline=false;
    mhm_clock_original_workspace=mhm_clock_original_unlock=mhm_clock_original_predicate=nullptr;
}
int main(int argc, char **argv) {
    assert(argc == 2);
    std::ifstream file(argv[1], std::ios::binary);
    std::vector<char> image((std::istreambuf_iterator<char>(file)), {});
    assert(image.size() == 29983600);
    auto base = reinterpret_cast<uintptr_t>(image.data());
    reset(); assert(configureClockMvp(true, 1000));
    assert(!configureClockMvp(true, 1000));
    assert(!mhm_clock_mvp_enabled); // no backend readiness yet
    installClockMvp(argv[1], base, fakeHook, fakeUnhook);
    assert(calls==3 && !rollbacks && mhm_clock_mvp_enabled);
    for (unsigned i=0; i<10000; ++i) installClockMvp(argv[1], base, fakeHook, fakeUnhook);
    assert(calls==3); assert(configureClockMvp(false, 1000)); assert(!mhm_clock_mvp_enabled);
    assert(configureClockMvp(true, 1000)); assert(mhm_clock_mvp_enabled);
    assert(configureClockMvp(true, 1000001)); assert(!mhm_clock_mvp_enabled);
    for (unsigned failure=1; failure<=3; ++failure) for (bool missing : {false,true}) {
        reset(); configureClockMvp(true,1000); failAt=failure; noTrampoline=missing;
        installClockMvp(argv[1], base, fakeHook, fakeUnhook);
        assert(calls==failure && rollbacks==failure-1+(missing?1:0) && !mhm_clock_mvp_enabled);
        for (unsigned i=0; i<10000; ++i) installClockMvp(argv[1], base, fakeHook, fakeUnhook);
        assert(calls==failure);
    }
    reset(); configureClockMvp(true,1000);
    installClockMvp("/missing/image",base,fakeHook,fakeUnhook);
    installClockMvp(argv[1],base,fakeHook,fakeUnhook); assert(!calls && !mhm_clock_mvp_enabled);
    reset(); configureClockMvp(true,1000); image[0x13c8b6c] ^= 1;
    installClockMvp(argv[1],base,fakeHook,fakeUnhook); assert(!calls);
    reset(); configureClockMvp(true,1000);
    installClockMvp(argv[1],base,fakeHook,nullptr); assert(!calls);
    std::puts("Clock MVP installer: exact image/entries, partial failures/rollback, real-time disable and no repeated backend calls passed");
}
'''
with tempfile.TemporaryDirectory(prefix='mhm-clock-install-') as tmp:
    path = Path(tmp)
    (path / 'test.cpp').write_text(source)
    subprocess.run(['clang++', '-std=c++17', '-O1', '-Wall', '-Wextra', '-Werror',
                    '-fsanitize=address,undefined', '-I', str(root / 'app/src/main/cpp'),
                    str(path / 'test.cpp'), '-o', str(path / 'test')], check=True)
    subprocess.run([str(path / 'test'), str(image)], check=True)
