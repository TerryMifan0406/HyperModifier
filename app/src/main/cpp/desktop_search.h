#pragma once
#include <cstdint>

void loadDesktopSearchConfig();
bool configureDesktopSearch(bool enabled);
void installDesktopSearch(const char *path, uintptr_t base,
        int (*hook)(void *, void *, void **), int (*unhook)(void *));
