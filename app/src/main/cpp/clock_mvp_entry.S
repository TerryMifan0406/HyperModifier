.text
.p2align 2
// These are Dart ABI leaf detours, never C calls or new frames across a Dart
// call. x15 scratch saves are fully removed before tailcalling the trampoline.
// Every GPR and NZCV is restored; SIMD, x18 and Dart roots stay untouched.
.macro enabled_or target
    adrp x16, mhm_clock_mvp_enabled
    add x16, x16, :lo12:mhm_clock_mvp_enabled
    ldarb w16, [x16]
    cbz w16, \target
.endm
.macro save
    stp x9, x10, [x15, #-64]!
    stp x11, x12, [x15, #16]
    stp x13, x14, [x15, #32]
    mrs x14, nzcv
    str x14, [x15, #48]
.endm
.macro restore
    ldr x14, [x15, #48]
    msr nzcv, x14
    ldp x11, x12, [x15, #16]
    ldp x13, x14, [x15, #32]
    ldp x9, x10, [x15], #64
.endm
.macro tail name
    adrp x16, \name
    ldr x16, [x16, :lo12:\name]
    br x16
.endm
// Valid only after observing the concrete Workspace getter receiver. Never
// guess the class or dereference an uninitialized singleton as a Workspace.
.macro page invalid
    adrp x11, mhm_clock_workspace_cid
    ldr w11, [x11, :lo12:mhm_clock_workspace_cid]
    cbz w11, \invalid
    ldr x9, [x26, #120]
    ldr x9, [x9, #10720]
    tbz x9, #0, \invalid
    cmp w9, w22
    b.eq \invalid
    ldur w10, [x9, #-1]
    ubfx w10, w10, #12, #20
    cmp w10, w11
    b.ne \invalid
    ldur w9, [x9, #103]
    tbz w9, #0, \invalid
    cmp w9, w22
    b.eq \invalid
    add x9, x9, x28, lsl #32
    ldur w9, [x9, #19]
    tbnz w9, #0, \invalid
    asr w9, w9, #1
    cmp w9, #31
    b.hi \invalid
.endm
.global mhm_clock_workspace_entry
.hidden mhm_clock_workspace_entry
.type mhm_clock_workspace_entry, %function
mhm_clock_workspace_entry:
    enabled_or 1f
    ldur w16, [x1, #-1]
    ubfx w16, w16, #12, #20
    adrp x17, mhm_clock_workspace_cid
    add x17, x17, :lo12:mhm_clock_workspace_cid
    stlr w16, [x17]
1:  tail mhm_clock_original_workspace
.size mhm_clock_workspace_entry, .-mhm_clock_workspace_entry

.global mhm_clock_unlock_entry
.hidden mhm_clock_unlock_entry
.type mhm_clock_unlock_entry, %function
mhm_clock_unlock_entry:
    enabled_or 3f
    save
    page 1f
    b 2f
1:  mov w9, #-1
2:  adrp x10, mhm_clock_page_epoch
    add x10, x10, :lo12:mhm_clock_page_epoch
    ldr x11, [x10]
    lsr x11, x11, #32
    add w11, w11, #1
    orr x11, x9, x11, lsl #32
    stlr x11, [x10]
    restore
3:  tail mhm_clock_original_unlock
.size mhm_clock_unlock_entry, .-mhm_clock_unlock_entry

.global mhm_clock_predicate_entry
.hidden mhm_clock_predicate_entry
.type mhm_clock_predicate_entry, %function
mhm_clock_predicate_entry:
    enabled_or 9f
    save
    // Per-unlock validity latch: no class discovery partway through preparation.
    adrp x10, mhm_clock_page_epoch
    ldr x10, [x10, :lo12:mhm_clock_page_epoch]
    cbnz w10, 8f
    page 8f
    cbnz w9, 8f
    ldur w9, [x1, #11]       // State.widget
    cmp w9, w22
    b.eq 8f
    add x9, x9, x28, lsl #32
    ldur x10, [x9, #31]     // UnlockWidget.cellScreenId (unboxed)
    cmp x10, #1
    b.ne 8f
    ldur w9, [x9, #19]      // ItemInfoModel
    cmp w9, w22
    b.eq 8f
    tbz w9, #0, 8f
    add x9, x9, x28, lsl #32
    ldur x10, [x9, #7]      // calibrated item id 493
    cmp x10, #493
    b.ne 8f
    ldur x10, [x9, #47]     // screenId
    cmp x10, #1
    b.ne 8f
    ldur x10, [x9, #31]     // spanX
    cmp x10, #4
    b.ne 8f
    ldur x10, [x9, #39]     // spanY
    cmp x10, #2
    b.ne 8f
    ldur x10, [x9, #55]     // cellX
    cbnz x10, 8f
    ldur x10, [x9, #63]     // cellY
    cbnz x10, 8f
    ldur x10, [x9, #71]     // desktop container
    cmn x10, #100
    b.ne 8f
    adrp x10, mhm_clock_suppressed
    add x10, x10, :lo12:mhm_clock_suppressed
    ldr x11, [x10]
    add x11, x11, #1
    str x11, [x10]
    restore
    add x0, x22, #0x30     // Dart false; caller skips only this item's animation
    ret
8:  restore
9:  tail mhm_clock_original_predicate
.size mhm_clock_predicate_entry, .-mhm_clock_predicate_entry
.section .note.GNU-stack,"",%progbits
