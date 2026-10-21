#pragma once
#include "clock_glass_render.h"
namespace clock_glass {
enum class Backdrop : unsigned { MissingState, MissingTexture, TextureAvailable };
// Mirrors the state/texture chain used by DlDispatcherBase::drawRect. Never
// retain any of these borrowed objects beyond the current raster callback.
inline Backdrop backdrop(void *canvas, void (*consumePending)(void *)) {
    if (!canvas) return Backdrop::MissingState;
    void *contextRef = read<void *>(canvas);
    if (!contextRef) return Backdrop::MissingState;
    void *context = read<void *>(contextRef);
    if (!context) return Backdrop::MissingState;
    void *stateHolder = read<void *>(context, 8);
    if (!stateHolder) return Backdrop::MissingState;
    void *state = read<void *>(stateHolder, 0x10);
    if (!state) return Backdrop::MissingState;
    if (read<void *>(state, 0x60)) consumePending(state);
    return read<void *>(state) ? Backdrop::TextureAvailable : Backdrop::MissingTexture;
}
template<class Original, class Observe>
void replayBlur(void *dispatcher, const Rect *rect, void *canvas,
        void (*consumePending)(void *), Original original, Observe observe) {
    Backdrop source = backdrop(canvas, consumePending);
    observe(source);
    // Keep the stable material id. The engine's cache is keyed by id/namespace,
    // and a radius mismatch replaces that entry. Using stock's default id 100
    // would make this radius and unrelated stock radii evict each other.
    // One fixed id reuses one native entry across frames/clock instances.
    if (source == Backdrop::TextureAvailable) { original(dispatcher, rect); return; }
    // Stock PassBlur silently drops the draw if no window texture exists. Keep
    // a visible AA-clipped fallback and report the missing prerequisite.
    auto *passBlur = static_cast<unsigned char *>(dispatcher) + 0x51;
    unsigned char previous = *passBlur;
    struct Restore { unsigned char *field; unsigned char value; ~Restore() { *field = value; } } restore{passBlur, previous};
    *passBlur = 0;
    original(dispatcher, rect);
}
}
