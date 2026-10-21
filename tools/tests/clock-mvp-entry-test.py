"""Run the production Dart leaf shims on a macOS ARM64 host.
Only ELF/Mach-O symbol/relocation spelling changes; no VM or Hook backend test.
Synthetic tagged objects exercise register preservation and item/page isolation.
"""
import pathlib
import platform
import re
import subprocess
import tempfile

if platform.system() != 'Darwin' or platform.machine() != 'arm64':
    raise SystemExit('Requires a macOS ARM64 host')
root = pathlib.Path(__file__).resolve().parents[2]
source = (root / 'app/src/main/cpp/clock_mvp_entry.S').read_text()
source = '\n'.join(line for line in source.splitlines()
                   if not line.startswith(('.hidden', '.type', '.size', '.section')))
for name in sorted(set(re.findall(r'mhm_clock_\w+', source)), key=len, reverse=True):
    source = source.replace(name, '_' + name)
source = re.sub(r'(adrp x\d+, [^\s]+)', r'\1@PAGE', source)
source = re.sub(r':lo12:([^\s\]]+)', r'\1@PAGEOFF', source)
source += '''
.global _clock_fallback
_clock_fallback:
    ret
.global _exercise
_exercise:
    sub sp, sp, #256
    stp x19, x20, [sp]
    stp x21, x22, [sp, #16]
    stp x23, x24, [sp, #32]
    stp x25, x26, [sp, #48]
    stp x27, x28, [sp, #64]
    stp x29, x30, [sp, #80]
    stp q8, q9, [sp, #96]
    stp q10, q11, [sp, #128]
    stp q12, q13, [sp, #160]
    stp q14, q15, [sp, #192]
    str x2, [sp, #224]
    str x1, [sp, #232]
    str x0, [sp, #240]
    mov x16, x1
'''
for i in list(range(0, 16, 2)) + list(range(18, 30, 2)):
    if i == 18:  # Platform register x18 must remain owned by the OS.
        source += '    ldr x19, [x16, #152]\n'
    else:
        source += f'    ldp x{i}, x{i+1}, [x16, #{i*8}]\n'
for i in range(32):
    source += f'    dup v{i}.2d, x22\n'
source += '''
    ldr x17, [x16, #248]
    msr nzcv, x17
    ldr x16, [sp, #240]
    adr x30, 1f
    br x16
1:
    ldr x16, [sp, #224]
'''
for i in list(range(0, 16, 2)) + list(range(18, 30, 2)):
    source += f'    stp x{i}, x{i+1}, [x16, #{i*8}]\n'
source += '''
    mrs x17, nzcv
    str x17, [x16, #248]
'''
for i in range(32):
    # AArch64 scaled immediate permits the full 256..752 range.
    source += f'    str q{i}, [x16, #{256+i*16}]\n'
source += '''
    ldp q8, q9, [sp, #96]
    ldp q10, q11, [sp, #128]
    ldp q12, q13, [sp, #160]
    ldp q14, q15, [sp, #192]
    ldp x19, x20, [sp]
    ldp x21, x22, [sp, #16]
    ldp x23, x24, [sp, #32]
    ldp x25, x26, [sp, #48]
    ldp x27, x28, [sp, #64]
    ldp x29, x30, [sp, #80]
    add sp, sp, #256
    ret
'''
checks = r'''
#include <stdint.h>
#include <stdlib.h>
#include <string.h>
#include <assert.h>
#include <stdio.h>
void clock_fallback(void), mhm_clock_workspace_entry(void), mhm_clock_unlock_entry(void), mhm_clock_predicate_entry(void);
void *mhm_clock_original_workspace = clock_fallback;
void *mhm_clock_original_unlock = clock_fallback;
void *mhm_clock_original_predicate = clock_fallback;
uint8_t mhm_clock_mvp_enabled;
uint32_t mhm_clock_workspace_cid;
uint64_t mhm_clock_page_epoch, mhm_clock_suppressed;
struct Input { uint64_t r[31], flags; };
struct Output { uint64_t r[31], flags, v[64]; };
void exercise(void *, struct Input *, struct Output *);
static void put(uintptr_t p, uint64_t value, unsigned size) { memcpy((void *)p, &value, size); }
int main(void) {
    unsigned count = 0;
    char *arena = calloc(1, 0x20000);
    assert(arena);
    uintptr_t ws = (uintptr_t)arena + 0x1001, rx = (uintptr_t)arena + 0x2001;
    uintptr_t state = (uintptr_t)arena + 0x3001, widget = (uintptr_t)arena + 0x4001;
    uintptr_t item = (uintptr_t)arena + 0x5001, thread = (uintptr_t)arena + 0x6000;
    uintptr_t statics = (uintptr_t)arena + 0x7000;
    uintptr_t heap = ws & ~UINT64_C(0xffffffff);
    assert((item & ~UINT64_C(0xffffffff)) == heap);
    for (unsigned which = 0; which < 3; ++which)
    for (unsigned flags = 0; flags < 16; ++flags)
    for (unsigned variant = 0; variant < 16; ++variant) {
        memset(arena, 0, 0x20000);
        put(ws - 1, 1234 << 12, 4);
        put(ws + 103, (uint32_t)rx, 4);
        put(rx + 19, variant == 2 ? 2 : 0, 4);
        put(thread + 120, statics, 8); put(statics + 10720, ws, 8);
        put(state + 11, (uint32_t)widget, 4);
        put(widget + 19, (uint32_t)item, 4); put(widget + 31, 1, 8);
        unsigned offsets[] = {7,31,39,47,55,63,71};
        uint64_t values[] = {493,4,2,1,0,0,-100};
        for (unsigned i = 0; i < 7; ++i) put(item + offsets[i], values[i], 8);
        if (variant >= 5 && variant <= 11) put(item + offsets[variant-5], values[variant-5] + 1, 8);
        if (variant == 12) put(widget + 31, 2, 8);
        if (variant == 13) put(widget + 19, (uint32_t)(heap+1), 4);
        if (variant == 14) put(statics + 10720, heap + 1, 8);
        if (variant == 15) put(rx + 19, 1, 4); // Reject boxed/non-Smi page.
        mhm_clock_mvp_enabled = variant != 1;
        mhm_clock_workspace_cid = variant == 3 ? 0 : 1234;
        mhm_clock_page_epoch = UINT64_C(7) << 32 | (variant == 4 ? 0xffffffff : 0);
        mhm_clock_suppressed = 0;
        struct Input in = {0}; struct Output out = {0};
        for (unsigned i = 0; i < 31; ++i) in.r[i] = 0x123400 + i;
        in.r[1] = which == 0 ? ws : state;
        in.r[15] = (uintptr_t)arena + 0x1f000;
        in.r[22] = heap + 1; in.r[26] = thread; in.r[28] = heap >> 32;
        in.flags = (uint64_t)flags << 28;
        void *entries[] = {mhm_clock_workspace_entry, mhm_clock_unlock_entry, mhm_clock_predicate_entry};
        exercise(entries[which], &in, &out);
        int suppressed = which == 2 && variant == 0;
        for (unsigned i = 0; i < 30; ++i) {
            if (i == 16 || i == 17 || i == 18) continue;
            assert(out.r[i] == (i == 0 && suppressed ? heap + 0x31 : in.r[i]));
        }
        assert(out.flags == in.flags);
        for (unsigned i = 0; i < 64; ++i) assert(out.v[i] == heap + 1);
        assert(mhm_clock_suppressed == (unsigned)suppressed);
        if (which == 0 && variant != 1) assert(mhm_clock_workspace_cid == 1234);
        if (which == 1) {
            assert((mhm_clock_page_epoch >> 32) == (variant == 1 ? 7 : 8));
            uint32_t page = (uint32_t)mhm_clock_page_epoch;
            if (variant == 3 || variant == 14 || variant == 15) assert(page == 0xffffffff);
            else if (variant == 2) assert(page == 1);
            else if (variant != 1) assert(page == 0);
        }
        ++count;
    }
    free(arena);
    printf("Clock MVP Dart shims: %u native register/flags/SIMD and page/item isolation cases passed\n", count);
}
'''
with tempfile.TemporaryDirectory(prefix='mhm-clock-mvp-abi-') as tmp:
    path = pathlib.Path(tmp)
    (path / 'entry.S').write_text(source)
    (path / 'test.c').write_text(checks)
    subprocess.run(['clang', '-arch', 'arm64', str(path / 'entry.S'), str(path / 'test.c'), '-o', str(path / 'test')], check=True)
    subprocess.run([str(path / 'test')], check=True)
