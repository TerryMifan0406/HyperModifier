#pragma once
#include <atomic>
#include <cstdint>
#include <cstdio>
#include <unistd.h>

// Only primitive values cross from the Dart mutator to the socket worker.
// No heap pointer, Dart call, allocation, log, lock or IPC in the detours.
extern "C" {
extern uint8_t mhm_clock_mvp_enabled;
extern uint32_t mhm_clock_workspace_cid;
extern uint64_t mhm_clock_page_epoch;
extern uint64_t mhm_clock_suppressed;
}
inline std::atomic<unsigned> clockMvpPeerUid{0};
bool configureClockMvp(bool enabled, unsigned peerUid);
void installClockMvp(const char *path, uintptr_t base, int (*hook)(void *, void *, void **), int (*unhook)(void *));
bool clockMvpSettled();
size_t clockMvpSnapshot(char *out, size_t capacity);
