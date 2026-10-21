#!/usr/bin/env python3
"""Exercise the production material installer against mapped real ELF images.

Only logging/ELF platform declarations and the detour backend are host stubs.
No foreign Android function is executed. This does not establish visual output.
"""
from pathlib import Path
import struct
import subprocess
import sys
import tempfile

root = Path(__file__).resolve().parents[2]
if len(sys.argv) != 3:
    raise SystemExit('usage: clock-glass-install-test.py maml.so libhyper_os_flutter.so')

platform = r'''
#include <cstddef>
#include <cstdint>
struct Elf64_Phdr { uint32_t p_type,p_flags; uint64_t p_offset,p_vaddr,p_paddr,p_filesz,p_memsz,p_align; };
struct Elf64_Nhdr { uint32_t n_namesz,n_descsz,n_type; };
constexpr int PT_LOAD=1,PT_NOTE=4,PF_R=4,PF_X=1,NT_GNU_BUILD_ID=3;
'''
driver = r'''
#include <cassert>
#include <fstream>
#include <iostream>
#include <iterator>
#include <vector>
unsigned hooks=0, rollbacks=0, failAt=0, nullAt=0;
int backend(void *, void *, void **out) {
    ++hooks;
    if (hooks == failAt) return 1; // Occupied by another module, never unhook it.
    *out = hooks == nullAt ? nullptr : reinterpret_cast<void *>(0x1234);
    return 0;
}
int undo(void *) { ++rollbacks; return 0; }
void reset(unsigned mode=Blur) {
    configured.store(mode); ready.store(false); attempted=false;
    hooks=rollbacks=failAt=nullAt=0; originalText=originalParagraph=originalRect=nullptr;
}
int main(int, char **argv) {
    std::ifstream m(argv[1],std::ios::binary), f(argv[2],std::ios::binary);
    std::vector<unsigned char> maml((std::istreambuf_iterator<char>(m)), {});
    std::vector<unsigned char> flutter((std::istreambuf_iterator<char>(f)), {});
    ClockGlassImage mi{reinterpret_cast<uintptr_t>(maml.data()), mamlHeaders, sizeof(mamlHeaders)/sizeof(*mamlHeaders)};
    ClockGlassImage fi{reinterpret_cast<uintptr_t>(flutter.data()), flutterHeaders, sizeof(flutterHeaders)/sizeof(*flutterHeaders)};
    assert(matches(mi,kMamlBuildId,kMamlContracts) && matches(fi,kFlutterBuildId,kFlutterContracts));
    // Every consulted ABI function must fail closed if another module patched it.
    for (const auto &entry : kMamlContracts) {
        maml[entry.rva] ^= 1; assert(!matches(mi,kMamlBuildId,kMamlContracts)); maml[entry.rva] ^= 1;
    }
    for (const auto &entry : kFlutterContracts) {
        flutter[entry.rva] ^= 1; assert(!matches(fi,kFlutterBuildId,kFlutterContracts)); flutter[entry.rva] ^= 1;
    }
    auto &header=mamlHeaders[0]; auto flags=header.p_flags; header.p_flags=0;
    assert(!matches(mi,kMamlBuildId,kMamlContracts)); header.p_flags=flags;
    reset(Default); installClockGlass(mi,fi,backend,undo); assert(!hooks && !attempted);
    reset(); installClockGlass({},fi,backend,undo); assert(!attempted);
    installClockGlass(mi,fi,backend,nullptr); assert(attempted && !hooks && !ready);
    installClockGlass(mi,fi,backend,undo); assert(!hooks);
    reset(); maml[kTextScope]^=1; installClockGlass(mi,fi,backend,undo);
    assert(attempted && !hooks); maml[kTextScope]^=1;
    for (unsigned failed : {1u,2u,3u}) {
        reset(); failAt=failed; installClockGlass(mi,fi,backend,undo);
        assert(!ready && hooks==failed && rollbacks==failed-1);
        for(unsigned i=0;i<10000;i++) installClockGlass(mi,fi,backend,undo);
        assert(hooks==failed);
        reset(); nullAt=failed; installClockGlass(mi,fi,backend,undo);
        assert(!ready && hooks==failed && rollbacks==failed);
    }
    reset(SoftGlass); installClockGlass(mi,fi,backend,undo); assert(!hooks && !attempted);
    reset(Blur); installClockGlass(mi,fi,backend,undo);
    assert(ready && hooks==3 && !rollbacks);
    configured.store(Default); installClockGlass(mi,fi,backend,undo); assert(hooks==3);
    configured.store(Blur); installClockGlass(mi,fi,backend,undo); assert(hooks==3);
    std::cout << "Material installer: both real ELF contracts, 13 code mutations, lazy/late load, transactional rollback, occupied targets and terminal failures passed\n";
}
'''
with tempfile.TemporaryDirectory(prefix='mhm-clock-material-') as tmp:
    tmp = Path(tmp)
    (tmp / 'android').mkdir()
    (tmp / 'elf.h').write_text(platform)
    (tmp / 'android/log.h').write_text('''
constexpr int ANDROID_LOG_INFO=4,ANDROID_LOG_WARN=5;
inline int __android_log_print(int,const char *,const char *,...) { return 0; }
inline int __android_log_write(int,const char *,const char *) { return 0; }
''')
    arrays = []
    paths = []
    for label, source in zip(['maml','flutter'], sys.argv[1:]):
        data = Path(source).read_bytes()
        if data[:6] != b'\x7fELF\x02\x01': raise SystemExit('ELF64 little-endian required')
        off = struct.unpack_from('<Q', data, 32)[0]
        size, count = struct.unpack_from('<HH', data, 54)
        ph = [struct.unpack_from('<IIQQQQQQ', data, off+i*size) for i in range(count)]
        mapped = bytearray(max(p[3]+p[6] for p in ph if p[0] == 1))
        for p in ph:
            if p[0] == 1: mapped[p[3]:p[3]+p[5]] = data[p[2]:p[2]+p[5]]
        path = tmp / (label + '.mapped'); path.write_bytes(mapped); paths.append(str(path))
        # Put the executable segment first for the permission-mutation test.
        ph.sort(key=lambda p: not(p[0] == 1 and p[1] & 1))
        arrays.append('Elf64_Phdr '+label+'Headers[] = {'+','.join('{'+','.join(map(str,p))+'}' for p in ph)+'};')
    cpp = tmp / 'test.cpp'
    cpp.write_text('#include "clock_glass.cpp"\n'+'\n'.join(arrays)+'\n'+driver)
    subprocess.run(['clang++','-std=c++17','-O1','-Wall','-Wextra','-Werror',
                    '-fsanitize=address,undefined','-I',str(tmp),'-I',str(root/'app/src/main/cpp'),
                    str(cpp),'-o',str(tmp/'test')],check=True)
    subprocess.run([str(tmp/'test'),*paths],check=True,timeout=30)
