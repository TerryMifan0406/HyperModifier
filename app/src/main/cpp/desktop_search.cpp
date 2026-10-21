#include "desktop_search.h"
#include "desktop_search_probe.h"
#include <android/log.h>
#include <atomic>
#include <cstdio>
#include <fcntl.h>
#include <link.h>
#include <mutex>
#include <unistd.h>

extern "C" {
__attribute__((visibility("hidden"))) std::atomic<uint8_t> mhm_desktop_search_enabled{0};
__attribute__((visibility("hidden"))) void *mhm_original_search_rows = nullptr;
__attribute__((visibility("hidden"))) void *mhm_original_search_space = nullptr;
void mhm_search_rows_entry();
void mhm_search_space_entry();
}

namespace {
std::atomic<bool> requested{false};
std::atomic<bool> ready{false};
static_assert(sizeof(mhm_desktop_search_enabled) == 1);
bool attempted = false; // accessed only by the existing launcher worker
std::mutex configMutex;

void cachePath(char (&path)[256]) {
    std::snprintf(path, sizeof(path),
            "/data/user_de/%u/com.miui.home/cache/mhm_desktop_search_4x7", getuid() / 100000);
}

}

void loadDesktopSearchConfig() {
    char path[256], value = '0'; cachePath(path);
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return;
    if (read(fd, &value, 1) == 1) requested.store(value == '1');
    close(fd);
}

bool configureDesktopSearch(bool value) {
    std::lock_guard<std::mutex> guard(configMutex);
    bool previous = requested.exchange(value);
    mhm_desktop_search_enabled.store(value && ready.load(), std::memory_order_release);
    if (previous == value) return false;
    char path[256]; cachePath(path);
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    if (fd < 0) return value;
    char byte = value ? '1' : '0';
    (void)write(fd, &byte, 1);
    close(fd);
    return value;
}

void installDesktopSearch(const char *path, uintptr_t base,
        int (*hook)(void *, void *, void **), int (*unhook)(void *)) {
    if (!requested.load() || attempted || !path || !base || !hook) return;
    attempted = true;
    if (!unhook) {
        __android_log_write(ANDROID_LOG_WARN, "MHMDesktopSearch", "Unavailable: backend has no rollback API");
        return;
    }
    struct Search { uintptr_t base; std::vector<desktop_search::Code> ranges; } search{base, {}};
    dl_iterate_phdr([](dl_phdr_info *info, size_t, void *opaque) {
        auto &value = *static_cast<Search *>(opaque);
        if (info->dlpi_addr != value.base) return 0;
        for (size_t i = 0; i < info->dlpi_phnum; ++i) {
            const auto &ph = info->dlpi_phdr[i];
            if (ph.p_type == PT_LOAD && (ph.p_flags & (PF_R | PF_X)) == (PF_R | PF_X))
                value.ranges.push_back({info->dlpi_addr + ph.p_vaddr, ph.p_memsz});
        }
        return 1;
    }, &search);
    // Worker only, once on first enable; inspect the already loaded executable
    // mappings. No APK read, whole-image fingerprint, build number or fixed RVA.
    desktop_search::Sites sites = desktop_search::find(search.ranges);
    if (!sites) {
        __android_log_print(ANDROID_LOG_WARN, "MHMDesktopSearch",
                "Unavailable: no unique compatible search-check structure path=%s", path);
        return;
    }
    void *rows = reinterpret_cast<void *>(sites.rows);
    void *space = reinterpret_cast<void *>(sites.space);
    int first = hook(rows, reinterpret_cast<void *>(mhm_search_rows_entry), &mhm_original_search_rows);
    int second = -1;
    if (first == 0 && mhm_original_search_rows)
        second = hook(space, reinterpret_cast<void *>(mhm_search_space_entry), &mhm_original_search_space);
    if (first == 0 && second == 0 && mhm_original_search_rows && mhm_original_search_space) {
        std::lock_guard<std::mutex> guard(configMutex);
        ready.store(true);
        mhm_desktop_search_enabled.store(requested.load(), std::memory_order_release);
    } else {
        // Only roll back addresses the backend reported as installed by us.
        if (second == 0) unhook(space);
        if (first == 0) unhook(rows);
    }
    __android_log_print(ANDROID_LOG_INFO, "MHMDesktopSearch", "structural hooks ready=%d enabled=%d rows=%d space=%d rva=0x%zx/0x%zx",
            ready.load(), mhm_desktop_search_enabled.load(), first, second,
            static_cast<size_t>(sites.rows - base), static_cast<size_t>(sites.space - base));
}
