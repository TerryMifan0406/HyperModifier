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
inline constexpr uint64_t kMaterialFlags = (UINT64_C(1) << 19) | (UINT64_C(1) << 20) | (UINT64_C(1) << 27);
inline constexpr uint64_t kPathFlags = UINT64_C(0x1b08203fdb4) | kMaterialFlags;
// These are engine-owned object ABIs, not definitions of foreign C++ classes.
// GetPath writes a 16-byte SkPath to x0. The LTO-specialized SkPath destructor
// at kDropPathRef takes the *contained SkPathRef*, not a SkPath address.
struct Renderer {
    void (*getPath)(void *, void *) = nullptr;
    void (*makeDlPath)(void *, const void *) = nullptr;
    void (*dropDlPath)(void *) = nullptr;
    void (*dropPathRef)(void *) = nullptr;
    void (*setPaint)(void *, const void *, uint64_t) = nullptr;
    void (*drawPath)(void *, const void *) = nullptr;
    void (*save)(void *) = nullptr;
    void (*restore)(void *) = nullptr;
    void (*translate)(void *, float, float) = nullptr;

    bool draw(void *painter, const void *blobRef, float x, float y,
            const void *variant, unsigned mode) const {
        if (!mode || !painter || !blobRef || !variant
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
        x += read<float>(blob, 4); y += read<float>(blob, 8);
        if (!std::isfinite(x) || !std::isfinite(y)) return false;
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
        std::memcpy(previous + 0x29, builderPaint + 0x29, 1);
        std::memcpy(previous + 0x2c, builderPaint + 0x2c, 0x18);
        std::memcpy(previous + 0x48, builderPaint + 0x48, 0x64);
        paint[0x29] = 1;
        put<float>(paint, 0x3c, 12.f);
        if (mode == SoftGlass) {
            paint[0x48] = 1;
            put<float>(paint, 0x4c, 0.f); put<float>(paint, 0x50, 1.2f);
            put<float>(paint, 0x54, 1.5f); put<float>(paint, 0x58, .7f);
            paint[0x5c] = 0; // Keep native fill visible if the material is unavailable.
            put<float>(paint, 0x60, .15f); put<float>(paint, 0x64, 1.f);
            put<float>(paint, 0x68, 1.f); put<float>(paint, 0x6c, 0.f);
            put<float>(paint, 0x70, .1f);
            put<float>(paint, 0x74, -.4f); put<float>(paint, 0x78, -.6f);
            put<float>(paint, 0x7c, 1.f); put<float>(paint, 0x80, .6f);
            put<float>(paint, 0x84, .2f); put<float>(paint, 0x88, .8f);
            put<float>(paint, 0x8c, .1f); put<float>(paint, 0x90, 1.f);
            put<float>(paint, 0x94, .2f); put<float>(paint, 0x98, .8f);
            put<float>(paint, 0x9c, 1.f); put<float>(paint, 0xa0, 1.f);
            put<float>(paint, 0xa4, 1.f); put<float>(paint, 0xa8, 0.f);
        }
        save(builder); translate(builder, x, y);
        setPaint(builder, paint, kPathFlags);
        drawPath(builder, dlPath);
        // DisplayList save/restore covers transforms/clips, not paint attrs.
        setPaint(builder, previous, kMaterialFlags);
        restore(builder);
        dropDlPath(dlPath); dropPathRef(ref);
        return true;
    }
};
}
