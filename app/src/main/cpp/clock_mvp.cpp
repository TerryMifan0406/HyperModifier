#include "clock_mvp.h"
#include "native_image_file.h"
#include <android/log.h>

extern "C" {
uint8_t mhm_clock_mvp_enabled = 0;
uint32_t mhm_clock_workspace_cid = 0;
alignas(8) uint64_t mhm_clock_page_epoch = 0xffffffffULL;
alignas(8) uint64_t mhm_clock_suppressed = 0;
void *mhm_clock_original_workspace = nullptr;
void *mhm_clock_original_unlock = nullptr;
void *mhm_clock_original_predicate = nullptr;
void mhm_clock_workspace_entry();
void mhm_clock_unlock_entry();
void mhm_clock_predicate_entry();
}
namespace {
std::atomic<bool> ready{false}, requested{false};
bool attempted = false;
void refresh() {
    __atomic_store_n(&mhm_clock_mvp_enabled, uint8_t(ready.load() && requested.load()), __ATOMIC_RELEASE);
}
}
bool configureClockMvp(bool enabled, unsigned peerUid) {
    // Called only after the existing settings receiver authenticates SystemUI.
    unsigned user = getuid() / 100000;
    clockMvpPeerUid.store(peerUid / 100000 == user && peerUid >= 1000 ? peerUid : 0);
    bool previous = requested.exchange(enabled && clockMvpPeerUid.load() != 0);
    refresh();
    return previous != requested.load();
}
bool clockMvpSettled() { return attempted || !requested.load(); }
void installClockMvp(const char *path, uintptr_t base, int (*hook)(void *, void *, void **), int (*unhook)(void *)) {
    if (attempted || !requested.load() || !path || !base) return;
    attempted = true;
    // Calibrated MVP: exact AOT image plus each entry contract. Unknown images
    // keep stock animations. This is a binary ABI guard, not a version check.
    if (!hook || !unhook || !matchingNativeImageFile(path, 29983600, 0x278dda665a7e3677ULL)) {
        __android_log_write(ANDROID_LOG_WARN, "MHMClockMvp", "calibrated AOT fingerprint or rollback API unavailable; stock animation retained");
        return;
    }
    struct Site { uintptr_t offset; uint32_t words[4]; void *entry; void **original; };
    Site sites[] = {
        {0xa071a8, {0xa9bf79fd,0xaa0f03fd,0xd100c1ef,0xf81f83a1}, reinterpret_cast<void *>(mhm_clock_workspace_entry), &mhm_clock_original_workspace},
        {0xd6742c, {0xa9bf79fd,0xaa0f03fd,0xd10061ef,0xf81f03a1}, reinterpret_cast<void *>(mhm_clock_unlock_entry), &mhm_clock_original_unlock},
        {0x13c8b6c,{0xa9bf79fd,0xaa0f03fd,0xd10041ef,0xf81f83a1}, reinterpret_cast<void *>(mhm_clock_predicate_entry), &mhm_clock_original_predicate},
    };
    for (const auto &site : sites)
        if (std::memcmp(reinterpret_cast<void *>(base + site.offset), site.words, sizeof(site.words))) return;
    unsigned installed = 0;
    for (const auto &site : sites) {
        void *target = reinterpret_cast<void *>(base + site.offset);
        int result = hook(target, site.entry, site.original);
        if (result != 0 || !*site.original) {
            if (result == 0) (void)unhook(target);
            while (installed) (void)unhook(reinterpret_cast<void *>(base + sites[--installed].offset));
            __android_log_write(ANDROID_LOG_WARN, "MHMClockMvp", "native contracts unavailable; stock animation retained");
            return;
        }
        ++installed;
    }
    ready.store(true); refresh();
    __android_log_write(ANDROID_LOG_INFO, "MHMClockMvp", "calibrated first-page clock contracts ready");
}
size_t clockMvpSnapshot(char *out, size_t capacity) {
    uint64_t value = __atomic_load_n(&mhm_clock_page_epoch, __ATOMIC_ACQUIRE);
    return static_cast<size_t>(std::snprintf(out, capacity,
        "MHM_CLOCK_MVP_V1\nready=%d\npage=%d\nepoch=%u\nsuppressed=%llu\n",
        __atomic_load_n(&mhm_clock_mvp_enabled, __ATOMIC_ACQUIRE) != 0,
        int32_t(value), uint32_t(value >> 32),
        static_cast<unsigned long long>(__atomic_load_n(&mhm_clock_suppressed, __ATOMIC_RELAXED))));
}
