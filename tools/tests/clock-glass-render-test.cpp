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
alignas(16) unsigned char ref[0x80]{}, paint[kPaintSize]{};
std::array<unsigned char, kPaintSize> before;
const void *originalPainter = nullptr, *originalVariant = nullptr, *originalBlobRef = nullptr;
Parameters expected;
void layer(void *builder, const OptionalRect *bounds, const void *layerPaint, const void *backdrop,
        Optional64 id, Optional64 token) {
    calls.push_back(1);
    assert(builder && bounds->present == 1 && !layerPaint && !backdrop);
    assert(bounds->value.left == 7 && bounds->value.top == 9 && bounds->value.right == 121 && bounds->value.bottom == 101);
    assert(!id.value && !id.present && !token.value && !token.present);
}
void paragraph(void *painter, const void *blob, float x, float y, const void *variant) {
    calls.push_back(2);
    assert(painter != originalPainter && variant != originalVariant && blob == originalBlobRef && x == 20 && y == 80);
    assert(read<void *>(painter, 8) == read<void *>(originalPainter, 8) && read<uint8_t>(painter, 0x18) == 1);
    assert(read<int32_t>(variant) == 0 && read<uint32_t>(variant, 0x50) == 1);
    const void *vector = read<void *>(painter, 0x10), *mask = read<void *>(vector);
    assert(read<uintptr_t>(vector, 8) - reinterpret_cast<uintptr_t>(mask) == kPaintSize);
    assert(read<uintptr_t>(vector, 8) == read<uintptr_t>(vector, 16));
    assert(read<uint8_t>(mask) == 3 && !read<uint8_t>(mask, 0x29));
    for (unsigned offset : {4u,8u,12u,16u}) assert(read<float>(mask, offset) == 1);
    assert(!std::memcmp(static_cast<const unsigned char *>(mask) + 0xf8, paint + 0xf8, 0x40));
}
void setPaint(void *, const void *value, uint64_t flags) {
    if (flags == kRectFlags) {
        calls.push_back(3);
        assert(read<uint8_t>(value) == 5); // SrcIn, not SrcOver; preserve fractional glyph coverage.
        assert(read<uint8_t>(value, 0x29) == 1 && read<uint8_t>(value, 0x2c) == 3);
        for (unsigned offset : {0x2du,0x2eu}) assert(read<uint8_t>(value, offset) == 0xff);
        assert(read<uint32_t>(value, 0x30) == (((expected.tint * 255 + 50) / 100) << 24 | 0x4080bf));
        assert(read<float>(value, 0x3c) == expected.radius && !read<uint8_t>(value, 0x48));
        assert(read<uint16_t>(value) & 0x4000);
        assert(read<int32_t>(value, 0x40) == kBlurMarker && read<float>(value, 4) == 1.f);
    } else {
        calls.push_back(6);
        assert(flags == (UINT64_C(0x20c203f824) | kRestoreFlags));
        assert(!std::memcmp(value, before.data(), kPaintSize));
    }
}
void draw(void *, const Rect *rect) {
    calls.push_back(4);
    assert(rect->left == 7 && rect->top == 9 && rect->right == 121 && rect->bottom == 101);

}
void restore(void *) { calls.push_back(5); }
}
int main() {
    assert(tintComponent(0.f) == 0 && tintComponent(1.f) == 255 && tintComponent(NAN) == 0);
    assert(tintComponent(-1.f) == 0 && tintComponent(2.f) == 255 && tintComponent(.5f) == 128);
    assert(normalize(0) == 0 && normalize(1) == 1 && normalize(2) == 2 && normalize(3) == 0 && normalize(-1u) == 0);
    for (unsigned radius : {2u,12u,40u}) for (unsigned tint : {0u,36u,80u}) {
        uint32_t packed = radius | (tint << 6);
        auto result = Parameters::decode(packed);
        assert(Parameters::normalize(packed) == packed && result.radius == radius && result.tint == tint);
    }
    for (uint32_t invalid : {0u,1u,41u,12u|(81u<<6),12u|(128u<<6),~0u})
        assert(Parameters::normalize(invalid) == Parameters::defaults);
    assert(Scope::current() == 0);
    { Scope selected(SoftGlass); assert(Scope::current() == SoftGlass);
      { Scope other(Default); assert(Scope::current() == Default); }
      std::thread thread([] { assert(!Scope::current()); Scope local(Blur); assert(Scope::current() == Blur); }); thread.join();
      assert(Scope::current() == SoftGlass); }
    assert(!Scope::current());
    Renderer renderer{paragraph, setPaint, draw, layer, restore};
    alignas(16) unsigned char painter[32]{}, builder[0x200]{}, blob[0x30]{}, variant[0x58]{};
    const uintptr_t paints[] = {reinterpret_cast<uintptr_t>(paint), reinterpret_cast<uintptr_t>(paint) + sizeof(paint)};
    put<void *>(painter, 8, builder); put<const void *>(painter, 0x10, paints); put<uint8_t>(painter, 0x18, 1);
    put<float>(paint, 4, 1.f); put<float>(blob, 4, -12.f); put<float>(blob, 8, -70.f);
    put<float>(paint, 8, .25f); put<float>(paint, 12, .5f); put<float>(paint, 16, .75f);
    put<float>(blob, 12, 100.f); put<float>(blob, 16, 20.f);
    put<float>(builder, 0x104, 7.f);
    std::memcpy(before.data(), paint, sizeof(paint)); put<float>(before.data(), 0x3c, 7.f);
    put<uint32_t>(variant, 0x50, 1); void *blobRef = blob;
    originalPainter = painter; originalVariant = variant; originalBlobRef = &blobRef;
    auto original = std::array<unsigned char, kPaintSize>{}; std::memcpy(original.data(), paint, sizeof(paint));
    auto painterCopy = std::array<unsigned char, sizeof(painter)>{}; std::memcpy(painterCopy.data(), painter, sizeof(painter));
    auto variantCopy = std::array<unsigned char, sizeof(variant)>{}; std::memcpy(variantCopy.data(), variant, sizeof(variant));
    for (uint16_t aa : {uint16_t{0},uint16_t{0x4000}}) for (Parameters params : {Parameters{},Parameters{2,0},Parameters{40,80}}) {
        expected = params; put<uint16_t>(builder, 0xc8, aa); put<uint16_t>(before.data(), 0, aa); calls.clear();
        assert(renderer.draw(painter, &blobRef, 20.f, 80.f, variant, Blur, params));
        assert((calls == std::vector<unsigned>{1,2,3,4,5,6}));
        assert(!std::memcmp(paint, original.data(), sizeof(paint)));
        assert(!std::memcmp(painter, painterCopy.data(), sizeof(painter)) && !std::memcmp(variant, variantCopy.data(), sizeof(variant)));
    }
    auto rejected = [&] { calls.clear(); assert(!renderer.draw(painter, &blobRef, 0, 0, variant, Blur)); assert(calls.empty()); };
    put<int32_t>(variant, 0, -1); rejected(); put<int32_t>(variant, 0, 1); rejected(); put<int32_t>(variant, 0, 0);
    put<uint32_t>(variant, 0x50, 0); rejected(); put<uint32_t>(variant, 0x50, 1);
    painter[0x18] = 0; rejected(); painter[0x18] = 1;
    for (unsigned offset : {0x29u,0x48u}) { paint[offset] = 1; rejected(); paint[offset] = 0; }
    paint[1] = 1; rejected(); paint[1] = 0;
    for (unsigned offset : {0xf1u,0x110u}) { builder[offset] = 1; rejected(); builder[offset] = 0; }
    for (unsigned offset : {0xf8u,0x108u,0x118u}) { put<void *>(paint, offset, ref); rejected(); put<void *>(paint, offset, nullptr); }
    for (float alpha : {0.f,.6f,-1.f,2.f,NAN}) { put<float>(paint, 4, alpha); rejected(); } put<float>(paint, 4, 1.f);
    put<float>(blob, 4, NAN); rejected(); put<float>(blob, 4, 100.f); rejected(); put<float>(blob, 4, -12.f);
    calls.clear(); assert(!renderer.draw(painter, &blobRef, 0, 0, variant, Default) && calls.empty());
    assert(!renderer.draw(painter, &blobRef, 0, 0, variant, SoftGlass) && calls.empty());
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
    std::cout << "Native text alpha mask: bounded layer, SrcIn blend contract, immutable borrowed views, opacity/fallback, exact state restoration, tuning boundaries, TLS scope and backdrop fallback passed\n";
}
