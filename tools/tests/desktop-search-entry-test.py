"""Run reviewed 7722/7751 Dart shims on macOS ARM64, including real epilogues."""
import pathlib
import platform
import subprocess
import tempfile

if platform.system() != 'Darwin' or platform.machine() != 'arm64':
    raise SystemExit('Requires a macOS ARM64 host')
root = pathlib.Path(__file__).resolve().parents[2]
source = (root / 'app/src/main/cpp/desktop_search_entry.S').read_text()
source = '\n'.join(line for line in source.splitlines()
                   if not line.startswith(('.hidden', '.type', '.size', '.section')))
for name in ('mhm_search_rows_entry', 'mhm_search_space_entry',
             'mhm_desktop_search_enabled', 'mhm_original_search_rows'):
    source = source.replace(name, '_' + name)
for reg, name in (('x16', '_mhm_desktop_search_enabled'), ('x17', '_mhm_original_search_rows')):
    source = source.replace(f'adrp {reg}, {name}', f'adrp {reg}, {name}@PAGE')
    source = source.replace(f':lo12:{name}', f'{name}@PAGEOFF')
source += '''
.global _rows_fallback
_rows_fallback:
    add x0, x22, #0x30
    ret
.global _exercise
_exercise:
    sub sp, sp, #160
    stp x29, x30, [sp]
    stp x22, x20, [sp, #16]
    str x19, [sp, #32]
    mov x19, x0
    mov x20, x1
    ldp x2, x3, [x19]
    ldr x5, [x19, #16]
    mov x22, #1
    movk x22, #2, lsl #16
    dup v0.2d, x22
    ldr x6, [x19, #24]
    msr nzcv, x6
    ldr x6, [x19, #32]
    cbnz x6, 1f
    mov x15, #0x3456
    bl _mhm_search_rows_entry
    b 3f
1:
    // Fake Dart frame: saved fp/lr, row count and tagged context fields.
    add x29, sp, #112
    adr x7, 2f
    stp x29, x7, [x29]
    stur x5, [x29, #-16]
    add x7, sp, #64
    stur x7, [x29, #-24]
    lsl w2, w2, #1
    lsl w3, w3, #1
    // Real space-check context: search at +15, layout at +19. This differs
    // from the rows-check argument order. Verified by the caller's x2/x3 swap
    // and the logged field reads in both actual Dart images.
    stur w3, [x7, #15]
    stur w2, [x7, #19]
    add x16, x22, #0x20
    add x17, x22, #0x30
    b _mhm_search_space_entry
2:
    // The production epilogue must pop precisely one Dart frame.
    add x7, sp, #128
    sub x15, x15, x7
3:
    str x0, [x20]
    mrs x7, nzcv
    str x7, [x20, #8]
    str q0, [x20, #16]
    stp x15, x22, [x20, #32]
    ldp x29, x30, [sp]
    ldp x22, x20, [sp, #16]
    ldr x19, [sp, #32]
    add sp, sp, #160
    ret
'''
checks = '''
#include <stdint.h>
#include <assert.h>
void rows_fallback(void);
void *mhm_original_search_rows = rows_fallback;
uint8_t mhm_desktop_search_enabled;
struct Input { uint64_t layout, search, rows, flags, space; };
struct Output { uint64_t result, flags, vector[2], shadow, nullbase; };
void exercise(struct Input *, struct Output *);
int main(void) {
    unsigned count = 0;
    for (unsigned enabled = 0; enabled < 2; ++enabled)
    for (unsigned layout = 0; layout < 3; ++layout)
    for (unsigned search = 0; search < 6; ++search)
    for (unsigned rows = 5; rows < 9; ++rows)
    for (unsigned bits = 0; bits < 16; ++bits)
    for (unsigned space = 0; space < 2; ++space) {
        mhm_desktop_search_enabled = enabled;
        struct Input in = {layout, search, rows, (uint64_t)bits << 28, space};
        struct Output out = {0};
        exercise(&in, &out);
        int override = enabled && layout == 0 && rows == 7 && (search == 2 || search == 3);
        int native = space && ((bits >> 3) == (bits & 1)); // CSEL GE: N == V
        assert(out.result == 0x20001 + ((override || native) ? 0x20 : 0x30));
        assert(out.flags == in.flags);
        assert(out.vector[0] == 0x20001 && out.vector[1] == 0x20001);
        assert(out.shadow == (space ? 0 : 0x3456) && out.nullbase == 0x20001);
        ++count;
    }
    return count == 4608 ? 0 : 1;
}
'''
with tempfile.TemporaryDirectory(prefix='mhm-search-abi-') as tmp:
    path = pathlib.Path(tmp)
    (path / 'entry.S').write_text(source)
    (path / 'test.c').write_text(checks)
    subprocess.run(['clang', '-arch', 'arm64', str(path / 'entry.S'), str(path / 'test.c'),
                    '-o', str(path / 'test')], check=True)
    subprocess.run([str(path / 'test')], check=True)
print('Desktop search shims: 4608 native ABI cases passed')
