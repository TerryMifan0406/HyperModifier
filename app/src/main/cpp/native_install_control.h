#pragma once
#include <atomic>
#include <cstring>

// The four launcher Runtime libraries stay resident for the process lifetime.
// Their first external load can wake the worker; repeated NOLOAD notifications
// must not form an installation -> dlopen -> notification -> installation loop.
class NativeLibraryNotifications {
    std::atomic<unsigned> seen{0};
public:
    bool accept(const char *name) {
        if (!name) return false;
        const char *file = std::strrchr(name, '/'); file = file ? file + 1 : name;
        unsigned bit;
        if (!std::strcmp(file, "libapp_launcher.so")) bit = 1;
        else if (!std::strcmp(file, "libapp.so")) bit = 2;
        else if (!std::strcmp(file, "librust_maml_sdk.so")) bit = 4;
        else if (!std::strcmp(file, "libhyper_os_broadcast_private.dylib.so")) bit = 8;
        else return false;
        if (seen.load(std::memory_order_relaxed) & bit) return false;
        return !(seen.fetch_or(bit, std::memory_order_relaxed) & bit);
    }
};

// LSPosed may deliver an onLoaded callback synchronously for RTLD_NOLOAD.
// Never queue work from our own install pass, including its dlclose calls.
class NativeInstallScope {
    inline static thread_local unsigned depth = 0;
public:
    NativeInstallScope() { ++depth; }
    ~NativeInstallScope() { --depth; }
    NativeInstallScope(const NativeInstallScope &) = delete;
    NativeInstallScope &operator=(const NativeInstallScope &) = delete;
    static bool active() { return depth != 0; }
};
