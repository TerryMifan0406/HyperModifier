#pragma once
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <cstring>

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
// These are engine-owned object ABIs, not definitions of foreign C++ classes.
// GetPath writes a 16-byte SkPath to x0. The LTO-specialized SkPath destructor
// at kDropPathRef takes the *contained SkPathRef*, not a SkPath address.
struct Renderer {
    void (*getPath)(void *, void *) = nullptr;
    void (*makeDlPath)(void *, const void *) = nullptr;
    void (*dropDlPath)(void *) = nullptr;
    void (*dropPathRef)(void *) = nullptr;
    void (*setPaint)(void *, const void *, uint64_t) = nullptr;
    void (*clipPath)(void *, const void *, uint32_t, bool) = nullptr;
    void (*drawRect)(void *, const Rect *) = nullptr;
    void (*save)(void *) = nullptr;
    void (*restore)(void *) = nullptr;
    void (*translate)(void *, float, float) = nullptr;

    bool draw(void *painter, const void *blobRef, float x, float y,
            const void *variant, unsigned mode) const {
        // BloomStroke's native dispatch is not a glyph-edge glass renderer.
        // Until that capability exists, the glass selection keeps stock text.
        if (mode != Blur || !painter || !blobRef || !variant
                || read<uint32_t>(variant, 0x50) != 1 || read<uint8_t>(painter, 0x18) != 1) return false;
        void *blob = read<void *>(blobRef);
        void *builder = read<void *>(painter, 8);
        void *paints = read<void *>(painter, 0x10);
        if (!blob || !builder || !paints) return false;
        uintptr_t begin = read<uintptr_t>(paints), end = read<uintptr_t>(paints, 8);
        int32_t index = read<int32_t>(variant);
        if (!begin || end < begin || (end - begin) % kPaintSize || index < 0
                || static_cast<uint64_t>(index) >= (end - begin) / kPaintSize) return false;
        const auto *source = reinterpret_cast<const unsigned char *>(begin) + index * kPaintSize;
        // Coexist with existing material/filter owners. Skip transparent shadow
        // prepasses; DlColor stores alpha first at DlPaint+4 in this ABI.
        float alpha = read<float>(source, 4);
        if (!(alpha > 0.f && alpha <= 1.f) || (source[1] & 3) || source[0x29] || source[0x48]
                || read<uint8_t>(builder, 0xf1) || read<uint8_t>(builder, 0x110)
                || read<void *>(source, 0xf8) || read<void *>(source, 0x108)
                || read<void *>(source, 0x118)) return false;
        float left = read<float>(blob, 4), top = read<float>(blob, 8);
        Rect bounds{0.f, 0.f, read<float>(blob, 12) - left, read<float>(blob, 16) - top};
        x += left; y += top;
        // PassBlur's texture opacity does not consume this text's alpha. Keep
        // the original text path during opacity animations rather than pop.
        if (alpha != 1.f || !std::isfinite(x) || !std::isfinite(y)
                || !std::isfinite(bounds.right) || !std::isfinite(bounds.bottom)
                || bounds.right <= 0.f || bounds.bottom <= 0.f) return false;
        // Keep the rectangle's own AA edge outside the glyph clip. Otherwise
        // tight blob bounds can multiply edge coverage and thin outer strokes.
        bounds.left = bounds.top = -1.f;
        bounds.right += 1.f; bounds.bottom += 1.f;
        alignas(16) unsigned char path[16]{};
        getPath(path, blob);
        void *ref = read<void *>(path);
        if (!ref) return false;
        if (!read<uint32_t>(ref, 0x48)) { dropPathRef(ref); return false; }
        alignas(16) unsigned char dlPath[32]{};
        makeDlPath(dlPath, path);
        // A synchronous borrowed paint view: never mutate or destruct the
        // engine's shared_ptrs. SetAttributesFromPaint retains what it records.
        alignas(16) unsigned char paint[kPaintSize]; std::memcpy(paint, source, sizeof(paint));
        alignas(16) unsigned char previous[kPaintSize]; std::memcpy(previous, source, sizeof(previous));
        // Builder paint attributes persist independently of save/restore and
        // may have been set by another draw. Restore that exact material state,
        // including inactive parameters, rather than guessing from this text.
        const auto *builderPaint = static_cast<const unsigned char *>(builder) + 0xc8;
        put<uint16_t>(previous, 0, (read<uint16_t>(previous) & ~uint16_t{0x4000})
            | (read<uint16_t>(builderPaint) & 0x4000));
        std::memcpy(previous + 0x29, builderPaint + 0x29, 1);
        std::memcpy(previous + 0x2c, builderPaint + 0x2c, 0x18);
        put<uint16_t>(paint, 0, read<uint16_t>(paint) | uint16_t{0x4000});
        paint[0x29] = 1;
        put<float>(paint, 0x3c, 12.f);
        // One explicit 20% source-over tint, following the stock text color.
        // Pure backdrop blur is invisible over a uniform wallpaper. Do not
        // inherit opaque/default blend colors from an unrelated paint field.
        paint[0x2c] = 3; paint[0x2d] = paint[0x2e] = 0xff;
        put<uint32_t>(paint, 0x30, UINT32_C(0x33000000)
            | (tintComponent(read<float>(source, 8)) << 16)
            | (tintComponent(read<float>(source, 12)) << 8)
            | tintComponent(read<float>(source, 16)));
        put<int32_t>(paint, 0x40, kBlurMarker);
        save(builder); translate(builder, x, y);
        clipPath(builder, dlPath, 1, true); // intersect; AA glyph clip, including counters/holes
        setPaint(builder, paint, kRectFlags);
        // Only the engine's rectangle dispatcher consumes PassBlur and samples
        // its window texture. DrawPath/DrawText silently ignore this attribute.
        drawRect(builder, &bounds);
        // DisplayList save/restore covers transforms/clips, not paint attrs.
        setPaint(builder, previous, kRestoreFlags);
        restore(builder);
        dropDlPath(dlPath); dropPathRef(ref);
        return true;
    }
};
}
