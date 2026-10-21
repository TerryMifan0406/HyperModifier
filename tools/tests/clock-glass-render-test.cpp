#include "clock_glass_render.h"
#include "clock_glass_playback.h"
#include <array>
#include <cassert>
#include <iostream>
#include <thread>
#include <vector>
using namespace clock_glass;
namespace {
std::vector<unsigned> calls;
bool empty = false;
alignas(16) unsigned char ref[0x80]{};
alignas(16) unsigned char paint[0x138]{};
float tx = 0, ty = 0;
std::array<unsigned char, kPaintSize> before;
void getPath(void *path, void *) { calls.push_back(1); put<void *>(path, 0, ref); put<uint32_t>(ref, 0x48, empty ? 0 : 90); }
void makePath(void *path, const void *sk) { calls.push_back(2); assert(read<void *>(sk) == ref); put<void *>(path, 8, ref); }
void dropDl(void *path) { calls.push_back(8); assert(read<void *>(path, 8) == ref); }
void dropRef(void *path) { calls.push_back(9); assert(path == ref); }
void save(void *) { calls.push_back(3); }
void translate(void *, float x, float y) { calls.push_back(4); tx = x; ty = y; }
void setPaint(void *, const void *value, uint64_t flags) {
    if (flags == kRectFlags) {
        calls.push_back(5);
        assert(read<uint8_t>(value, 0x29) == 1);
        assert(read<uint8_t>(value, 0x2c) == 3);
        for (unsigned offset : {0x2d,0x2e}) assert(read<uint8_t>(value, offset) == 0xff);
        assert(read<uint32_t>(value, 0x30) == 0x334080bf);
        assert(read<float>(value, 0x3c) == 12.f);
        assert(read<uint8_t>(value, 0x48) == 0);
        assert(read<uint16_t>(value) & 0x4000);
        assert(read<int32_t>(value, 0x40) == kBlurMarker);
        assert(read<float>(value, 4) == 1.f); // Preserve opacity through unlock.
        assert(!std::memcmp(static_cast<const unsigned char *>(value) + 0xf8, paint + 0xf8, 0x40));
    } else {
        calls.push_back(7); assert(flags == kRestoreFlags);
        assert(!read<uint8_t>(value, 0x29) && !read<uint8_t>(value, 0x48));
        assert(read<float>(value, 0x3c) == 7.f); // Restore builder, not source defaults.
        assert((read<uint16_t>(value) & 0x4000) == (read<uint16_t>(before.data()) & 0x4000));
        assert(!std::memcmp(value, before.data(), kPaintSize));
    }
}
void clip(void *, const void *path, uint32_t op, bool aa) {
    calls.push_back(11); assert(read<void *>(path, 8) == ref && op == 1 && aa);
}
void draw(void *, const Rect *rect) {
    calls.push_back(6); assert(rect->left == -1 && rect->top == -1 && rect->right == 113.f && rect->bottom == 91.f);
}
void restore(void *) { calls.push_back(10); }
}
int main() {
    assert(tintComponent(0.f) == 0 && tintComponent(1.f) == 255 && tintComponent(NAN) == 0);
    assert(tintComponent(-1.f) == 0 && tintComponent(2.f) == 255 && tintComponent(.5f) == 128);
    assert(normalize(0) == 0 && normalize(1) == 1 && normalize(2) == 2 && normalize(3) == 0 && normalize(-1u) == 0);
    assert(Scope::current() == 0);
    { Scope selected(SoftGlass); assert(Scope::current() == SoftGlass);
      { Scope other(Default); assert(Scope::current() == Default); }
      assert(Scope::current() == SoftGlass);
      std::thread thread([] { assert(!Scope::current()); Scope local(Blur); assert(Scope::current() == Blur); }); thread.join();
      assert(Scope::current() == SoftGlass); }
    assert(!Scope::current());
    Renderer renderer{getPath, makePath, dropDl, dropRef, setPaint, clip, draw, save, restore, translate};
    alignas(16) unsigned char painter[32]{}, builder[0x200]{}, blob[0x30]{}, variant[0x58]{};
    const uintptr_t paints[] = {reinterpret_cast<uintptr_t>(paint), reinterpret_cast<uintptr_t>(paint) + sizeof(paint)};
    put<void *>(painter, 8, builder); put<const void *>(painter, 0x10, paints); put<uint8_t>(painter, 0x18, 1);
    put<float>(paint, 4, 1.f); put<float>(blob, 4, -12.f); put<float>(blob, 8, -70.f);
    put<float>(paint, 8, .25f); put<float>(paint, 12, .5f); put<float>(paint, 16, .75f);
    put<float>(blob, 12, 100.f); put<float>(blob, 16, 20.f);
    put<float>(builder, 0x104, 7.f); put<float>(builder, 0x118, 2.f);
    std::memcpy(before.data(), paint, sizeof(paint));
    put<float>(before.data(), 0x3c, 7.f);
    put<uint32_t>(variant, 0x50, 1); void *blobRef = blob;
    auto original = std::array<unsigned char, kPaintSize>{}; std::memcpy(original.data(), paint, sizeof(paint));
    for (uint16_t aa : {uint16_t{0}, uint16_t{0x4000}}) {
        put<uint16_t>(builder, 0xc8, aa); put<uint16_t>(before.data(), 0, aa);
        calls.clear();
        assert(renderer.draw(painter, &blobRef, 20.f, 80.f, variant, Blur));
        assert((calls == std::vector<unsigned>{1,2,3,4,11,5,6,7,10,8,9}));
        assert(tx == 8.f && ty == 10.f && !std::memcmp(paint, original.data(), sizeof(paint)));
    }
    auto rejected = [&] { calls.clear(); assert(!renderer.draw(painter, &blobRef, 0, 0, variant, Blur)); assert(calls.empty()); };
    put<int32_t>(variant, 0, -1); rejected(); put<int32_t>(variant, 0, 1); rejected(); put<int32_t>(variant, 0, 0);
    put<uint32_t>(variant, 0x50, 0); rejected(); put<uint32_t>(variant, 0x50, 1);
    painter[0x18] = 0; rejected(); painter[0x18] = 1;
    for (unsigned offset : {0x29,0x48}) { paint[offset] = 1; rejected(); paint[offset] = 0; }
    paint[1] = 1; rejected(); paint[1] = 0;
    for (unsigned offset : {0xf1,0x110}) { builder[offset] = 1; rejected(); builder[offset] = 0; }
    for (unsigned offset : {0xf8,0x108,0x118}) { put<void *>(paint, offset, ref); rejected(); put<void *>(paint, offset, nullptr); }
    for (float alpha : {0.f,.6f,-1.f,2.f,NAN}) { put<float>(paint, 4, alpha); rejected(); } put<float>(paint, 4, 1.f);
    put<float>(blob, 4, NAN); rejected(); put<float>(blob, 4, -12.f);
    calls.clear(); assert(!renderer.draw(painter, &blobRef, 0, 0, variant, Default) && calls.empty());
    calls.clear(); assert(!renderer.draw(painter, &blobRef, 0, 0, variant, SoftGlass) && calls.empty());
    empty = true; calls.clear(); assert(!renderer.draw(painter, &blobRef, 0, 0, variant, Blur));
    assert((calls == std::vector<unsigned>{1,9}));
    alignas(16) unsigned char canvas[8]{}, contextRef[16]{}, context[16]{}, holder[32]{}, state[0x70]{}, dispatcher[0x70]{};
    bool sampled = false; Backdrop observed{};
    auto consume = +[](void *state) { put<void *>(state, 0x60, nullptr); put<void *>(state, 0, ref); };
    auto originalRect = [&](void *d, const Rect *) {
        sampled = read<uint8_t>(d, 0x51) != 0;
        assert(read<int32_t>(d, 0x68) == kBlurMarker); // Stable cache id; leave stock entries alone.
    };
    auto observe = [&](Backdrop value) { observed = value; };
    dispatcher[0x51] = 1;
    put<int32_t>(dispatcher, 0x68, kBlurMarker);
    replayBlur(dispatcher, nullptr, canvas, consume, originalRect, observe);
    assert(observed == Backdrop::MissingState && !sampled && dispatcher[0x51] == 1);
    put<void *>(canvas, 0, contextRef); put<void *>(contextRef, 0, context); put<void *>(context, 8, holder);
    put<void *>(holder, 0x10, state);
    replayBlur(dispatcher, nullptr, canvas, consume, originalRect, observe);
    assert(observed == Backdrop::MissingTexture && !sampled && dispatcher[0x51] == 1);
    put<void *>(state, 0x60, ref);
    replayBlur(dispatcher, nullptr, canvas, consume, originalRect, observe);
    assert(observed == Backdrop::TextureAvailable && sampled && !read<void *>(state, 0x60) && dispatcher[0x51] == 1);
    put<void *>(state, 0, nullptr); put<void *>(state, 0x60, ref);
    auto failedImport = +[](void *state) { put<void *>(state, 0x60, nullptr); };
    replayBlur(dispatcher, nullptr, canvas, failedImport, originalRect, observe);
    assert(observed == Backdrop::MissingTexture && !sampled && dispatcher[0x51] == 1);
    assert(read<int32_t>(dispatcher, 0x68) == kBlurMarker);
    std::cout << "Glyph clip/backdrop: missing state/texture, pending import success/failure, fallback restoration; material: mode, nested/thread scope, translation, alpha, immutable source, native lifetimes, fallback and coexistence passed\n";
}
