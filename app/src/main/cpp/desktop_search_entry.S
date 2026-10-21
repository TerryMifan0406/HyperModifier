.text
.p2align 2
.global mhm_search_rows_entry
.hidden mhm_search_rows_entry
.type mhm_search_rows_entry, %function
// Reviewed 7722/7751 isLayoutAndSearchCompatibleWithCurrentRows:
// x2 = layout (0: four columns/max rows), x3 = search type, x5 = current rows.
// Types 2/3 use this search sizing path; icon/compact types 1/4 stay native.
// No C ABI call, allocation, flag changes or Dart shadow-stack changes.
mhm_search_rows_entry:
    adrp x16, mhm_desktop_search_enabled
    add x16, x16, :lo12:mhm_desktop_search_enabled
    ldarb w16, [x16]
    cbz w16, 2f
    cbnz x2, 2f
    sub x17, x5, #7
    cbnz x17, 2f
    sub x17, x3, #2
    cbz x17, 1f
    sub x17, x3, #3
    cbnz x17, 2f
1:
    add x0, x22, #0x20
    ret
2:
    adrp x17, mhm_original_search_rows
    ldr x17, [x17, :lo12:mhm_original_search_rows]
    br x17
.size mhm_search_rows_entry, .-mhm_search_rows_entry

.global mhm_search_space_entry
.hidden mhm_search_space_entry
.type mhm_search_space_entry, %function
// Reviewed 7722/7751 isLayoutAndSearchTypeSupported, after its normal result.
// Unlike the rows function, this function takes x2=search, x3=layout. Its
// captured context stores tagged search at +15 and tagged layout at +19.
// The frame retains the calculated row count and the layout/search context.
// Only permit the reviewed four-column/seven-row combination; other layouts
// keep their result. Scratch x16/x17 are dead at the original epilogue.
mhm_search_space_entry:
    csel x0, x16, x17, ge
    adrp x16, mhm_desktop_search_enabled
    add x16, x16, :lo12:mhm_desktop_search_enabled
    ldarb w16, [x16]
    cbz w16, 2f
    ldur x17, [x29, #-16]
    sub x17, x17, #7
    cbnz x17, 2f
    ldur x17, [x29, #-24]
    ldur w16, [x17, #19]
    cbnz w16, 2f
    ldur w17, [x17, #15]
    sub w17, w17, #4
    cbz w17, 1f
    sub w17, w17, #2
    cbnz w17, 2f
1:
    add x0, x22, #0x20
2:
    mov x15, x29
    ldp x29, x30, [x15], #16
    ret
.size mhm_search_space_entry, .-mhm_search_space_entry
.section .note.GNU-stack,"",%progbits
