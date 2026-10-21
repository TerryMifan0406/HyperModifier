"""Exercise the production structural search probe under ASan/UBSan.

Optional arguments are actual libapp.so paths; no versions, hashes or target
RVAs are supplied to the production finder. Reference files remain local.
"""
from pathlib import Path
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
root = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(root / 'tools/analysis'))
from schema_elf import Elf

source = r'''
#include "desktop_search_probe.h"
#include <cassert>
#include <chrono>
#include <fstream>
#include <iostream>
#include <iterator>
using namespace desktop_search;
int main(int argc, char **argv) {
    std::vector<uint32_t> words(8192, 0xd503201f);
    uintptr_t address = reinterpret_cast<uintptr_t>(words.data());
    std::vector<Code> ranges{{address, words.size() * 4}};
    size_t row = 0, space = 0, preview = 0, helper = 0;
    auto call = [&](size_t site, size_t target) {
        words[site] = 0x94000000 | ((target - site) & 0x3ffffff);
    };
    auto pool = [&](size_t site, uint32_t offset) {
        words[site] = (words[site] & 0xffc003ff) | ((offset / 4096) << 10);
        words[site + 1] = (words[site + 1] & 0xffc003ff) | (((offset % 4096) / 8) << 10);
    };
    auto slot = [&](size_t site, unsigned offset) {
        words[site] = (words[site] & 0xffc003ff) | (offset << 10);
    };
    auto fixture = [&](unsigned variant) {
        std::fill(words.begin(), words.end(), 0xd503201f);
        row = 256 + variant * 4; space = 1200 + variant * 8;
        helper = 4500 + variant * 8; preview = 7000 + variant * 4;
        auto fill = [&](size_t start, const auto &code) {
            for (size_t i = 0; i < std::size(code); ++i) {
                words[start + i] = code[i].value;
                if (code[i].mask == 0xfc000000) call(start + i, helper + 100 + i);
                else if (code[i].mask == 0xffc003ff) slot(start + i, 8 + variant);
            }
        };
        fill(row, rowsCode); fill(space, spaceCode);
        pool(row + 33, 0x10000 + variant * 4088);
        pool(space + 40, 0x10000 + variant * 4088);
        pool(row + 71, 0x300008 + variant * 8);
        pool(space + 155, 0x500000 + variant * 4088);
        pool(space + 72, 0x600ff8 + variant * 8);
        pool(space + 92, 0x600ff8 + variant * 8);
        slot(row + 29, 100 + variant); slot(space + 36, 100 + variant);
        slot(row + 64, 400 + variant); slot(space + 148, 400 + variant);
        slot(space + 62, 700 + variant); slot(space + 82, 700 + variant);
        slot(space + 66, 900 + variant); slot(space + 86, 900 + variant);
        call(row + 9, helper); call(space + 11, helper);
        for (unsigned i : {18u, 25u, 52u}) call(row + i, helper + 1);
        for (unsigned i : {7u, 20u}) call(space + i, helper + 1);
        for (unsigned i : {35u, 69u}) call(row + i, helper + 2);
        for (unsigned i : {42u, 71u, 91u, 153u}) call(space + i, helper + 2);
        for (auto pair : {std::pair<unsigned,unsigned>{47,59}, {62,146}, {74,158}, {78,162}}) {
            call(row + pair.first, helper + pair.first);
            call(space + pair.second, helper + pair.first);
        }
        call(space + 76, helper + 7); call(space + 96, helper + 7);
        call(space + 78, helper + 8); call(space + 104, helper + 8);
        call(row + rowsSpaceCall, space);
        words[preview] = 0xaa0203f0; words[preview + 1] = 0xaa0303e2;
        words[preview + 2] = 0xaa1003e3; call(preview + 3, space);
    };
    for (unsigned i = 0; i < 128; ++i) {
        fixture(i); Sites found = find(ranges);
        assert(found.rows == address + row * 4 && found.space == address + (space + spaceReturn) * 4);
    }
    unsigned rejected = 0;
    auto reject = [&] {
        if (find(ranges)) { std::cerr << "Unexpected acceptance, mutation " << rejected << "\n"; std::abort(); }
        ++rejected;
    };
    for (size_t i = 0; i < std::size(rowsCode); ++i) if (rowsCode[i].mask == 0xffffffff) {
        fixture(0); words[row + i] ^= 1; reject();
    }
    for (size_t i = 0; i < std::size(spaceCode); ++i) if (spaceCode[i].mask == 0xffffffff) {
        fixture(0); words[space + i] ^= 1; reject();
    }
    for (size_t i = 0; i < std::size(rowsCode); ++i) if (rowsCode[i].mask == 0xffc003ff) {
        fixture(0); words[row + i] ^= 1; reject(); // operand register must not move
    }
    for (size_t i = 0; i < std::size(spaceCode); ++i) if (spaceCode[i].mask == 0xffc003ff) {
        fixture(0); words[space + i] ^= 1; reject();
    }
    fixture(0); call(row + 101, 8191); reject(); // truncated target
    fixture(0); call(space + 33, row); reject(); // wrong max-row callee
    fixture(0); call(row + 35, helper + 3); reject(); // helper role disagreement
    fixture(0); call(row + 35, row); reject(); // loops into current function
    fixture(0); words[row + 33] ^= 1 << 10; reject(); // device pool disagreement
    fixture(0); words[space + 92] ^= 1 << 10; reject(); // argument pool disagreement
    fixture(0); words[space + 82] ^= 1 << 10; reject(); // static alias disagreement
    fixture(0); call(preview + 3, helper); reject(); // missing warning path
    fixture(0); call(7800, row + 1); reject(); // enters displaced prologue
    fixture(0); call(7800, space + spaceReturn + 1); reject(); // enters epilogue tail
    fixture(0); words[7800] = words[preview]; words[7801] = words[preview + 1];
    words[7802] = words[preview + 2]; call(7803, space); reject(); // duplicate warning witness
    fixture(0);
    auto duplicate = words; // PC-relative code relocates with the copied range
    assert(!find({ranges[0], {reinterpret_cast<uintptr_t>(duplicate.data()), duplicate.size() * 4}}));
    assert(!find({{address, (row + 5) * 4}}));
    assert(!find({{address, (space + spaceReturn + 2) * 4}}));
    assert(!find({{address, 64 * 1024 * 1024 + 4}}));
    assert(!find({{UINTPTR_MAX - 3, 8}}));
    assert(!find({{address + 1, 100}}));
    assert(!find({ranges[0], ranges[0]}));
    assert(branchTarget(4, 0x97fffffe, 26, 0) == 0); // signed branch underflow
    assert(branchTarget(UINTPTR_MAX - 3, 0x94000001, 26, 0) == 0);
    std::cout << "Synthetic: 128 independent relocations; " << rejected
              << " instruction/role mutations; ambiguity, truncation and bounds rejected\n";
    for (int i = 1; i < argc; ++i) {
        std::ifstream file(argv[i], std::ios::binary);
        std::vector<uint8_t> text((std::istreambuf_iterator<char>(file)), {});
        auto base = reinterpret_cast<uintptr_t>(text.data());
        auto begin = std::chrono::steady_clock::now();
        Sites found = find({{base, text.size()}});
        assert(found);
        auto us = std::chrono::duration_cast<std::chrono::microseconds>(
                std::chrono::steady_clock::now() - begin).count();
        std::cout << "Actual image: matched at text+0x" << std::hex << found.rows - base
                  << "/0x" << found.space - base << std::dec << " (host sanitizer " << us << " us)\n";
    }
}
'''
with tempfile.TemporaryDirectory(prefix='mhm-search-probe-') as tmp:
    path = Path(tmp)
    (path / 'test.cpp').write_text(source)
    images = []
    for i, filename in enumerate(sys.argv[1:]):
        target = path / f'image-{i}.text'
        target.write_bytes(Elf(Path(filename).read_bytes()).section('.text'))
        images.append(str(target))
    subprocess.run(['clang++', '-std=c++17', '-O1', '-Wall', '-Wextra', '-Werror',
                    '-fsanitize=address,undefined', '-I', str(root / 'app/src/main/cpp'),
                    str(path / 'test.cpp'), '-o', str(path / 'test')], check=True)
    subprocess.run([str(path / 'test'), *images], check=True)
