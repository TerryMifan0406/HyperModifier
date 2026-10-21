#include "clock_glass.h"
#include "clock_glass_profile.h"
#include "clock_glass_render.h"
#include <android/log.h>
#include <atomic>
#include <cstdio>
#include <elf.h>
#include <fcntl.h>
#include <unistd.h>

namespace {
using namespace clock_glass;
constexpr char TAG[] = "MHMClockMaterial";
std::atomic<unsigned> configured{Default};
std::atomic<bool> ready{false};
std::atomic<uint64_t> targets{0}, draws{0};
bool attempted = false; // Only the existing native installation worker writes this.
void *originalText = nullptr, *originalParagraph = nullptr;
struct Text { const char *data; size_t size; };
using Name = Text (*)(const void *);
using DrawText = void (*)(void *, void *, float);
using Paragraph = void (*)(void *, const void *, float, float, const void *);
Name name = nullptr;
Renderer renderer;
void configPath(char (&path)[256]) {
    std::snprintf(path, sizeof(path), "/data/user_de/%u/com.miui.home/cache/mhm_clock_material", getuid() / 100000);
}
bool target(void *self) {
    if (!self || !(read<uint8_t>(self, 0x4d4) & 1)) return false;
    Text value = name(static_cast<unsigned char *>(self) + 0x430);
    constexpr char id[] = "center_classic_plus";
    return value.data && value.size == sizeof(id) - 1 && !std::memcmp(value.data, id, sizeof(id) - 1);
}
void textHook(void *self, void *canvas, float offset) {
    unsigned mode = configured.load(std::memory_order_relaxed);
    if (!mode || !ready.load(std::memory_order_acquire)) {
        reinterpret_cast<DrawText>(originalText)(self, canvas, offset); return;
    }
    bool selected = target(self);
    Scope scope(selected ? mode : Default);
    if (selected) targets.fetch_add(1, std::memory_order_relaxed);
    reinterpret_cast<DrawText>(originalText)(self, canvas, offset);
}
void paragraphHook(void *self, const void *blob, float x, float y, const void *paint) {
    unsigned mode = Scope::current();
    if (mode && configured.load(std::memory_order_relaxed) == mode
            && ready.load(std::memory_order_acquire) && renderer.draw(self, blob, x, y, paint, mode)) {
        draws.fetch_add(1, std::memory_order_relaxed); return;
    }
    reinterpret_cast<Paragraph>(originalParagraph)(self, blob, x, y, paint);
}
template<size_t B, size_t C>
bool matches(ClockGlassImage image, const unsigned char (&buildId)[B], const Contract (&contracts)[C]) {
    if (!image.base || !image.headers) return false;
    const auto *headers = static_cast<const Elf64_Phdr *>(image.headers);
    bool found = false;
    for (size_t i = 0; i < image.count; ++i) {
        const auto &ph = headers[i];
        if (ph.p_type != PT_NOTE) continue;
        const auto *cursor = reinterpret_cast<const unsigned char *>(image.base + ph.p_vaddr);
        size_t remaining = ph.p_memsz;
        while (remaining >= sizeof(Elf64_Nhdr)) {
            Elf64_Nhdr note = read<Elf64_Nhdr>(cursor);
            size_t names = (size_t(note.n_namesz) + 3) & ~size_t{3};
            size_t descs = (size_t(note.n_descsz) + 3) & ~size_t{3};
            if (names > remaining - sizeof(note) || descs > remaining - sizeof(note) - names) break;
            if (note.n_type == NT_GNU_BUILD_ID && note.n_namesz == 4 && note.n_descsz == B
                    && !std::memcmp(cursor + sizeof(note), "GNU", 4)
                    && !std::memcmp(cursor + sizeof(note) + names, buildId, B)) found = true;
            size_t bytes = sizeof(note) + names + descs; cursor += bytes; remaining -= bytes;
        }
    }
    if (!found) return false;
    for (const auto &contract : contracts) {
        bool executable = false;
        for (size_t i = 0; i < image.count; ++i) {
            const auto &ph = headers[i];
            if (ph.p_type == PT_LOAD && (ph.p_flags & (PF_R | PF_X)) == (PF_R | PF_X)
                    && contract.rva >= ph.p_vaddr && contract.rva - ph.p_vaddr <= ph.p_memsz
                    && contract.size <= ph.p_memsz - (contract.rva - ph.p_vaddr)) executable = true;
        }
        if (!executable) return false;
        const auto *bytes = reinterpret_cast<const unsigned char *>(image.base + contract.rva);
        uint64_t hash = UINT64_C(0xcbf29ce484222325);
        for (size_t i = 0; i < contract.size; ++i) hash = (hash ^ bytes[i]) * UINT64_C(0x100000001b3);
        if (hash != contract.hash) return false;
    }
    return true;
}
}
void loadClockGlassConfig() {
    char path[256], value = '0'; configPath(path);
    int fd = open(path, O_RDONLY | O_CLOEXEC);
    if (fd < 0) return;
    if (read(fd, &value, 1) == 1) configured.store(clock_glass::normalize(unsigned(value - '0')));
    close(fd);
}
bool configureClockGlass(unsigned value) {
    unsigned mode = clock_glass::normalize(value);
    unsigned previous = configured.exchange(mode);
    if (previous == mode) return false;
    char path[256]; configPath(path);
    int fd = open(path, O_WRONLY | O_CREAT | O_TRUNC | O_CLOEXEC, 0600);
    if (fd >= 0) { char byte = char('0' + mode); (void)write(fd, &byte, 1); close(fd); }
    __android_log_print(ANDROID_LOG_INFO, TAG, "mode=%u ready=%d target=%llu path=%llu", mode,
        ready.load(), static_cast<unsigned long long>(targets.load()), static_cast<unsigned long long>(draws.load()));
    return mode != clock_glass::Default;
}
void installClockGlass(ClockGlassImage maml, ClockGlassImage flutter,
        int (*hook)(void *, void *, void **), int (*unhook)(void *)) {
    using namespace clock_glass;
    if (attempted || !configured.load() || !maml.base || !flutter.base) return;
    attempted = true; // Publish before any backend call; no failed-install retry loop.
    if (!hook || !unhook || !matches(maml, kMamlBuildId, kMamlContracts)
            || !matches(flutter, kFlutterBuildId, kFlutterContracts)) {
        __android_log_write(ANDROID_LOG_WARN, TAG, "rejected: native ABI/code mismatch or missing rollback API"); return;
    }
    name = reinterpret_cast<Name>(maml.base + kName);
    renderer = {
        reinterpret_cast<decltype(renderer.getPath)>(flutter.base + kGetPath),
        reinterpret_cast<decltype(renderer.makeDlPath)>(flutter.base + kDlPath),
        reinterpret_cast<decltype(renderer.dropDlPath)>(flutter.base + kDropDlPath),
        reinterpret_cast<decltype(renderer.dropPathRef)>(flutter.base + kDropPathRef),
        reinterpret_cast<decltype(renderer.setPaint)>(flutter.base + kSetPaint),
        reinterpret_cast<decltype(renderer.drawPath)>(flutter.base + kDrawPath),
        reinterpret_cast<decltype(renderer.save)>(flutter.base + kSave),
        reinterpret_cast<decltype(renderer.restore)>(flutter.base + kRestore),
        reinterpret_cast<decltype(renderer.translate)>(flutter.base + kTranslate),
    };
    void *paragraph = reinterpret_cast<void *>(flutter.base + kParagraph);
    int result = hook(paragraph, reinterpret_cast<void *>(paragraphHook), &originalParagraph);
    if (result != 0 || !originalParagraph) {
        if (!result) (void)unhook(paragraph);
        __android_log_print(ANDROID_LOG_WARN, TAG, "paragraph rejected result=%d", result); return;
    }
    void *text = reinterpret_cast<void *>(maml.base + kTextScope);
    result = hook(text, reinterpret_cast<void *>(textHook), &originalText);
    if (result != 0 || !originalText) {
        if (!result) (void)unhook(text);
        int rollback = unhook(paragraph);
        __android_log_print(ANDROID_LOG_WARN, TAG, "scope rejected result=%d rollback=%d", result, rollback); return;
    }
    ready.store(true, std::memory_order_release);
    __android_log_write(ANDROID_LOG_INFO, TAG, "ready: classic glyph material prototype (blur/soft glass)");
}
