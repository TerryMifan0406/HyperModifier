#pragma once
#include <cstddef>
#include <cstdint>
struct ClockGlassImage {
    uintptr_t base = 0;
    const void *headers = nullptr;
    size_t count = 0;
};
void loadClockGlassConfig();
bool configureClockGlass(unsigned mode);
void installClockGlass(ClockGlassImage maml, ClockGlassImage flutter,
        int (*hook)(void *, void *, void **), int (*unhook)(void *), void (*wakeWorker)() = nullptr);
