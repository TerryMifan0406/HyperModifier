#pragma once
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <initializer_list>

namespace clock_glass {
enum Mode : unsigned { Default = 0, Blur = 1, SoftGlass = 2 };
inline unsigned normalize(unsigned value) { return value <= SoftGlass ? value : Default; }
template<class T> T read(const void *p, size_t offset = 0) {
    T value; std::memcpy(&value, static_cast<const unsigned char *>(p) + offset, sizeof(value)); return value;
}
template<class T> void put(void *p, size_t offset, T value) {
    std::memcpy(static_cast<unsigned char *>(p) + offset, &value, sizeof(value));
}
// Nested non-target text must not inherit the outer target's material.
class Scope {
    inline static thread_local unsigned mode = Default;
    unsigned previous;
public:
    explicit Scope(unsigned value) : previous(mode) { mode = value; }
    ~Scope() { mode = previous; }
    Scope(const Scope &) = delete;
    Scope &operator=(const Scope &) = delete;
    static unsigned current() { return mode; }
};
inline constexpr size_t kPaintSize = 0x138;
inline constexpr uint64_t kMaterialFlags = (UINT64_C(1) << 19) | (UINT64_C(1) << 20);
inline constexpr uint64_t kRestoreFlags = kMaterialFlags | (UINT64_C(1) << 10);
inline constexpr uint64_t kRectFlags = UINT64_C(0x2087fbfc24) | kRestoreFlags;
inline constexpr int32_t kBlurMarker = 0x4d484d43;
struct Rect { float left, top, right, bottom; };
inline uint32_t tintComponent(float value) {
    if (!std::isfinite(value) || value <= 0.f) return 0;
    if (value >= 1.f) return 255;
    return static_cast<uint32_t>(value * 255.f + .5f);
}
// Foreign object layouts are verified by the matching engine code contracts.
struct OptionalRect { Rect value; uint8_t present; uint8_t padding[3]{}; };
struct Optional64 { uint64_t value = 0; uint64_t present = 0; };
static_assert(sizeof(OptionalRect) == 20 && sizeof(Optional64) == 16);
struct Parameters {
    unsigned radius = 12, tint = 36;
    static constexpr uint32_t defaults = 12 | (36 << 6);
    static uint32_t normalize(uint32_t value) {
        unsigned radius = value & 63, tint = (value >> 6) & 127;
        return !(value >> 13) && radius >= 2 && radius <= 40 && tint <= 80 ? value : defaults;
    }
    static Parameters decode(uint32_t value) {
        value = normalize(value); return {value & 63, (value >> 6) & 127};
    }
};
struct Renderer {
    using Paragraph = void (*)(void *, const void *, float, float, const void *);
    Paragraph paragraph = nullptr;
    void (*setPaint)(void *, const void *, uint64_t) = nullptr;
    void (*drawRect)(void *, const Rect *) = nullptr;
    void (*saveLayer)(void *, const OptionalRect *, const void *, const void *, Optional64, Optional64) = nullptr;
    void (*restore)(void *) = nullptr;

    bool draw(void *painter, const void *blobRef, float x, float y,
            const void *variant, unsigned mode, Parameters parameters = {}) const {
        if (mode != Blur || !painter || !blobRef || !variant
                || read<uint32_t>(variant, 0x50) != 1 || read<uint8_t>(painter, 0x18) != 1) return false;
        void *blob = read<void *>(blobRef), *builder = read<void *>(painter, 8), *paints = read<void *>(painter, 0x10);
        if (!blob || !builder || !paints) return false;
        uintptr_t begin = read<uintptr_t>(paints), end = read<uintptr_t>(paints, 8);
        int32_t index = read<int32_t>(variant);
        if (!begin || end < begin || (end - begin) % kPaintSize || index < 0
                || static_cast<uint64_t>(index) >= (end - begin) / kPaintSize) return false;
        const auto *source = reinterpret_cast<const unsigned char *>(begin) + index * kPaintSize;
        // Native text handles animated opacity and unsupported paints unchanged.
        if (read<float>(source, 4) != 1.f || (source[1] & 3) || source[0x29] || source[0x48]
                || read<uint8_t>(builder, 0xf1) || read<uint8_t>(builder, 0x110)
                || read<void *>(source, 0xf8) || read<void *>(source, 0x108)
                || read<void *>(source, 0x118)) return false;
        OptionalRect bounds{{x + read<float>(blob, 4) - 1.f, y + read<float>(blob, 8) - 1.f,
            x + read<float>(blob, 12) + 1.f, y + read<float>(blob, 16) + 1.f}, 1, {}};
        const auto &r = bounds.value;
        if (!std::isfinite(r.left) || !std::isfinite(r.top) || !std::isfinite(r.right) || !std::isfinite(r.bottom)
                || r.right - r.left <= 2.f || r.bottom - r.top <= 2.f) return false;
        alignas(16) unsigned char paint[kPaintSize], previous[kPaintSize], mask[kPaintSize];
        std::memcpy(paint, source, sizeof(paint));
        std::memcpy(previous, source, sizeof(previous));
        std::memcpy(mask, source, sizeof(mask));
        const auto *builderPaint = static_cast<const unsigned char *>(builder) + 0xc8;
        put<uint16_t>(previous, 0, (read<uint16_t>(previous) & ~uint16_t{0x4000})
            | (read<uint16_t>(builderPaint) & 0x4000));
        std::memcpy(previous + 0x29, builderPaint + 0x29, 1);
        std::memcpy(previous + 0x2c, builderPaint + 0x2c, 0x18);
        mask[0] = 3; // SrcOver; white native glyph alpha, not a vector/stencil clip.
        for (unsigned offset : {4u,8u,12u,16u}) put<float>(mask, offset, 1.f);
        alignas(16) unsigned char maskPainter[32], maskVariant[0x58];
        std::memcpy(maskPainter, painter, sizeof(maskPainter));
        std::memcpy(maskVariant, variant, sizeof(maskVariant)); put<int32_t>(maskVariant, 0, 0);
        const uintptr_t maskVector[] = {reinterpret_cast<uintptr_t>(mask),
            reinterpret_cast<uintptr_t>(mask) + sizeof(mask), reinterpret_cast<uintptr_t>(mask) + sizeof(mask)};
        put<const void *>(maskPainter, 0x10, maskVector);
        // One clock-bounded transparent GPU layer. Original text is drawn into
        // it with its own glyph atlas AA; SrcIn multiplies texture by that alpha.
        // All borrowed painter/paint/vector views die after this synchronous call.
        saveLayer(builder, &bounds, nullptr, nullptr, {}, {});
        paragraph(maskPainter, blobRef, x, y, maskVariant);
        paint[0] = 5; // SrcIn: premultiplied output = sampled texture * glyph alpha.
        put<uint16_t>(paint, 0, read<uint16_t>(paint) | uint16_t{0x4000});
        paint[0x29] = 1;
        put<float>(paint, 0x3c, static_cast<float>(parameters.radius));
        paint[0x2c] = 3; paint[0x2d] = paint[0x2e] = 0xff;
        uint32_t alpha = (parameters.tint * 255 + 50) / 100;
        put<uint32_t>(paint, 0x30, (alpha << 24)
            | (tintComponent(read<float>(source, 8)) << 16)
            | (tintComponent(read<float>(source, 12)) << 8)
            | tintComponent(read<float>(source, 16)));
        put<int32_t>(paint, 0x40, kBlurMarker);
        setPaint(builder, paint, kRectFlags);
        drawRect(builder, &r);
        restore(builder);
        // Text's original attributes plus the exact preexisting AA/material
        // state. SaveLayer/restore does not restore DisplayList paint attrs.
        setPaint(builder, previous, UINT64_C(0x20c203f824) | kRestoreFlags);
        return true;
    }
};
}
