#pragma once
#include "desktop_search_contract.h"
#include <cstddef>
#include <cstring>
#include <vector>

namespace desktop_search {
struct Code { uintptr_t address; size_t size; };
struct Sites {
    uintptr_t rows = 0, space = 0;
    explicit operator bool() const { return rows && space; }
};
inline uint32_t wordAt(uintptr_t pc) {
    uint32_t value; std::memcpy(&value, reinterpret_cast<const void *>(pc), 4); return value;
}
inline bool contains(const std::vector<Code> &ranges, uintptr_t pc, size_t size) {
    if (pc & 3) return false;
    for (const auto &range : ranges)
        if (pc >= range.address && pc - range.address <= range.size
                && size <= range.size - (pc - range.address)) return true;
    return false;
}
inline uintptr_t branchTarget(uintptr_t pc, uint32_t value, unsigned bits, unsigned shift) {
    int64_t offset = (value >> shift) & ((uint32_t{1} << bits) - 1);
    if (offset & (int64_t{1} << (bits - 1))) offset -= int64_t{1} << bits;
    offset *= 4;
    if (offset >= 0) return static_cast<uint64_t>(offset) <= UINTPTR_MAX - pc ? pc + offset : 0;
    return static_cast<uint64_t>(-offset) <= pc ? pc - static_cast<uint64_t>(-offset) : 0;
}
inline uintptr_t callAt(uintptr_t function, unsigned index) {
    uintptr_t pc = function + index * 4;
    return branchTarget(pc, wordAt(pc), 26, 0);
}
inline uint32_t poolAt(uintptr_t function, unsigned index) {
    uint32_t add = wordAt(function + index * 4), load = wordAt(function + index * 4 + 4);
    return ((add >> 10) & 4095) * 4096 + ((load >> 10) & 4095) * 8;
}
inline uint32_t slotAt(uintptr_t function, unsigned index) {
    return (wordAt(function + index * 4) >> 10) & 4095;
}
template<size_t N>
inline bool matches(uintptr_t function, const Instruction (&code)[N],
        const std::vector<Code> &ranges) {
    if (!contains(ranges, function, N * 4)) return false;
    for (size_t i = 0; i < N; ++i) {
        uint32_t word = wordAt(function + i * 4);
        if ((word & code[i].mask) != code[i].value) return false;
        if (code[i].mask == 0xfc000000) {
            uintptr_t target = callAt(function, i);
            if (!contains(ranges, target, 4) || (target >= function && target < function + N * 4))
                return false;
        } else if ((word & 0xffc003e0) == 0x91400360) {
            uint32_t pool = poolAt(function, i);
            if (pool < 16 || pool >= 8 * 1024 * 1024) return false;
        }
    }
    return true;
}
inline bool related(uintptr_t rows, uintptr_t space) {
    for (size_t i = 0; i < sizeof(rowsCode) / sizeof(Instruction); ++i)
        if (rowsCode[i].mask == 0xfc000000 && i != rowsSpaceCall) {
            uintptr_t target = callAt(rows, i);
            if (target >= space && target - space < sizeof(spaceCode) / sizeof(Instruction) * 4) return false;
        }
    for (size_t i = 0; i < sizeof(spaceCode) / sizeof(Instruction); ++i)
        if (spaceCode[i].mask == 0xfc000000) {
            uintptr_t target = callAt(space, i);
            if (target >= rows && target - rows < sizeof(rowsCode) / sizeof(Instruction) * 4) return false;
        }
    // Shared allocation, late-init, grid calculation, write-barrier and logging
    // callees must still agree. Call addresses themselves need not be stable.
    for (unsigned i : {18u, 25u, 52u})
        if (callAt(rows, i) != callAt(space, 7)) return false;
    if (callAt(space, 7) != callAt(space, 20) || callAt(rows, 9) != callAt(space, 11)) return false;
    for (unsigned i : {35u, 69u})
        if (callAt(rows, i) != callAt(space, 42)) return false;
    for (unsigned i : {71u, 91u, 153u})
        if (callAt(space, i) != callAt(space, 42)) return false;
    if (callAt(rows, 47) != callAt(space, 59) || callAt(rows, 62) != callAt(space, 146)
            || callAt(rows, 74) != callAt(space, 158) || callAt(rows, 78) != callAt(space, 162)
            || callAt(space, 76) != callAt(space, 96) || callAt(space, 78) != callAt(space, 104)) return false;
    // Both paths resolve the same device configuration and logger. Geometry's
    // repeated controller/static reads and pool arguments must also agree.
    return poolAt(rows, 33) == poolAt(space, 40)
        && poolAt(space, 72) == poolAt(space, 92)
        && poolAt(rows, 71) != poolAt(space, 155)
        && slotAt(rows, 29) == slotAt(space, 36)
        && slotAt(rows, 64) == slotAt(space, 148)
        && slotAt(space, 62) == slotAt(space, 82)
        && slotAt(space, 66) == slotAt(space, 86)
        && slotAt(space, 62) != slotAt(rows, 29)
        && slotAt(space, 62) != slotAt(rows, 64);
}
inline Sites find(const std::vector<Code> &ranges) {
    size_t total = 0;
    if (ranges.empty() || ranges.size() > 16) return {};
    for (size_t i = 0; i < ranges.size(); ++i) {
        const auto &range = ranges[i];
        if (!range.address || (range.address & 3) || (range.size & 3)
                || range.size > 64 * 1024 * 1024 - total
                || range.address > UINTPTR_MAX - range.size) return {};
        total += range.size;
        for (size_t j = 0; j < i; ++j)
            if (range.address < ranges[j].address + ranges[j].size
                    && ranges[j].address < range.address + range.size) return {};
    }
    Sites found;
    uintptr_t spaceFunction = 0;
    for (const auto &range : ranges) {
        for (size_t offset = 0; offset + sizeof(rowsCode) / sizeof(Instruction) * 4 <= range.size; offset += 4) {
            uintptr_t start = range.address + offset;
            // Cheap, exact layout/search/rows capture prefix before full checks.
            if (wordAt(start) != 0xa9bf79fd || wordAt(start + 12) != 0xf81f03a1
                    || wordAt(start + 16) != 0xf81e83a3 || wordAt(start + 20) != 0xf81e03a5
                    || wordAt(start + 24) != 0xd37ff840) continue;
            if (!matches(start, rowsCode, ranges)) continue;
            uintptr_t space = callAt(start, rowsSpaceCall);
            if (!matches(space, spaceCode, ranges) || !related(start, space)) continue;
            if (found) return {}; // ambiguous pairs never select the first
            found = {start, space + spaceReturn * 4};
            spaceFunction = space;
        }
    }
    if (!found) return {};
    unsigned previewCalls = 0;
    // A second bounded pass validates the layout-preview search-off warning's
    // argument swap, and rejects branches into the displaced instruction tails.
    for (const auto &range : ranges) for (size_t offset = 0; offset + 4 <= range.size; offset += 4) {
        uintptr_t pc = range.address + offset;
        uint32_t word = wordAt(pc);
        if (word == 0xaa0203f0 && offset + 16 <= range.size
                && wordAt(pc + 4) == 0xaa0303e2 && wordAt(pc + 8) == 0xaa1003e3
                && (wordAt(pc + 12) & 0xfc000000) == 0x94000000
                && callAt(pc, 3) == spaceFunction) ++previewCalls;
        uintptr_t target;
        if ((word & 0x7c000000) == 0x14000000) target = branchTarget(pc, word, 26, 0);
        else if ((word & 0xff000010) == 0x54000000 || (word & 0x7e000000) == 0x34000000)
            target = branchTarget(pc, word, 19, 5);
        else if ((word & 0x7e000000) == 0x36000000) target = branchTarget(pc, word, 14, 5);
        else continue;
        if ((target > found.rows && target - found.rows < 16)
                || (target > found.space && target - found.space < 16)) return {};
    }
    return previewCalls == 1 ? found : Sites{};
}
}
