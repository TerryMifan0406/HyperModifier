#pragma once
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <mutex>
#include <unordered_map>
#include <unordered_set>
#include <string>
#include <sstream>

inline bool widgetTextEquals(const char *data, size_t size, const char *text) {
    return data && size == std::strlen(text) && std::memcmp(data, text, size) == 0;
}
struct WidgetIdentity {
    std::string package, component;
    int appWidgetId = 0;
    bool valid() const { return !package.empty() && !component.empty(); }
};
inline WidgetIdentity widgetIdentity(std::string package, std::string name) {
    if (package.empty() || name.empty()) return {};
    if (name[0] == '.') name = package + name;
    else if (name.find('.') == std::string::npos) name = package + "." + name;
    return {package, package + "/" + name};
}
inline bool validWidgetRule(const std::string &rule) {
    if (rule == "*") return true;
    if (!rule.empty() && rule[0] == '-') return rule.size() > 1 && rule[1] != '-' && rule[1] != '*' && validWidgetRule(rule.substr(1));
    if (rule.empty() || rule.find('.') == std::string::npos) return false;
    unsigned slashes = 0;
    for (unsigned char c : rule) {
        if (c == '/') { if (++slashes > 1) return false; }
        else if (!((c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z')
                || (c >= '0' && c <= '9') || c == '.' || c == '_' || c == '$')) return false;
    }
    return rule.front() != '/' && rule.back() != '/';
}
class WidgetGlassRules {
    std::unordered_set<std::string> targets;
    std::unordered_set<std::string> excluded;
public:
    bool parse(const std::string &encoded) {
        if (encoded.size() > 16384) return false;
        std::unordered_set<std::string> next;
        std::unordered_set<std::string> nextExcluded;
        std::istringstream stream(encoded); std::string rule;
        while (std::getline(stream, rule)) {
            if (!validWidgetRule(rule)) return false;
            if (rule[0] == '-') nextExcluded.insert(rule.substr(1));
            else next.insert(rule);
        }
        targets = std::move(next); excluded = std::move(nextExcluded); return true;
    }
    bool matches(const WidgetIdentity &identity) const {
        return identity.valid() && !excluded.count(identity.package) && !excluded.count(identity.component)
            && (targets.count("*") || targets.count(identity.package) || targets.count(identity.component));
    }
};
class WidgetStyleRules {
    std::unordered_map<std::string, int> styles;
    std::unordered_map<std::string, std::unordered_map<int, int>> instances;
public:
    bool parse(const std::string &encoded) {
        if (encoded.size() > 16384) return false;
        std::unordered_map<std::string, int> next;
        std::unordered_map<std::string, std::unordered_map<int, int>> nextInstances;
        std::istringstream stream(encoded); std::string line;
        while (std::getline(stream, line)) {
            size_t tab = line.find('\t');
            if (tab == std::string::npos || tab + 2 != line.size() || line.back() < '0' || line.back() > '3') return false;
            std::string target = line.substr(0, tab), component = target;
            size_t hash = target.find('#');
            int instanceId = 0;
            if (hash != std::string::npos) {
                component = target.substr(0, hash); std::string id = target.substr(hash + 1);
                if (id.empty() || id.front() < '1' || id.front() > '9' || id.size() > 10) return false;
                uint64_t value = 0;
                for (char c : id) { if (c < '0' || c > '9') return false; value = value * 10 + c - '0'; }
                if (value > 2147483647 || component.find('/') == std::string::npos) return false;
                instanceId = static_cast<int>(value);
            }
            if (!validWidgetRule(component) || component == "*" || component.front() == '-'
                    || (line.back() == '3' && component.find('/') != std::string::npos)) return false;
            if (instanceId) nextInstances[component][instanceId] = line.back() - '0';
            else next[target] = line.back() - '0';
        }
        styles = std::move(next); instances = std::move(nextInstances); return true;
    }
    int resolve(const WidgetIdentity &identity, bool glass, bool clock) const {
        if (!identity.valid()) return 0;
        if (identity.appWidgetId > 0) {
            auto provider = instances.find(identity.component);
            if (provider != instances.end()) {
                auto entry = provider->second.find(identity.appWidgetId);
                if (entry != provider->second.end()) return entry->second;
            }
        }
        auto provider = styles.find(identity.component);
        if (provider != styles.end()) return provider->second;
        auto app = styles.find(identity.package);
        if (app != styles.end() && app->second != 3) return app->second;
        return glass ? (clock ? 2 : 1) : 0;
    }
};
// Owned provider / ActivityInfo clones carry IDs across asynchronous SDK calls.
class WidgetInstanceOwners {
    std::mutex mutex;
    std::unordered_map<void *, int> ids;
public:
    void set(void *owner, int id) {
        std::lock_guard<std::mutex> guard(mutex);
        if (!owner) return;
        if (id > 0) ids[owner] = id; else ids.erase(owner);
    }
    int get(void *owner) {
        std::lock_guard<std::mutex> guard(mutex);
        auto entry = ids.find(owner); return entry == ids.end() ? 0 : entry->second;
    }
    void clone(void *source, void *copy) {
        std::lock_guard<std::mutex> guard(mutex);
        if (!copy || source == copy) return;
        auto entry = ids.find(source);
        if (entry == ids.end()) ids.erase(copy); else ids[copy] = entry->second;
    }
};

// Desktop Flutter consumers also need this value for grid sizing and material.
// Keep value overrides separate from key-presence queries used for classification.
inline bool overrideWidgetBoolean(const char *key, size_t size, bool stack, bool glass) {
    return ((stack || glass) && widgetTextEquals(key, size, "miuiWidget"))
        || (glass && widgetTextEquals(key, size, "supportBackgroundBlur"));
}
inline bool overrideWidgetPresence(const char *key, size_t size, bool glass) {
    // Never invent a miuiWidget key: picker classification must see original metadata.
    return glass && widgetTextEquals(key, size, "supportBackgroundBlur");
}

inline bool overrideWidgetGlassStyle(const WidgetIdentity &identity, const char *key,
        size_t size, bool enabled, bool glass) {
    return enabled && glass && identity.valid()
        && widgetTextEquals(key, size, "usingFolderGlassStyle");
}

constexpr char LEGACY_WIDGET_GLASS_TARGET[] = "com.aritxonly.deadliner/com.aritxonly.deadliner.widgets.glance.ui.CompactTaskGlanceWidgetReceiver";
inline bool decodeWidgetCache(const std::string &cache, bool &stack, bool &glass, std::string &rules) {
    bool versioned = cache.size() >= 4 && (cache[0] == '2' || cache[0] == '3') && cache[3] == '\n';
    if ((!versioned && cache.size() != 2) || cache.size() > 16388) return false;
    size_t offset = versioned ? 1 : 0;
    if ((cache[offset] != '0' && cache[offset] != '1') || (cache[offset + 1] != '0' && cache[offset + 1] != '1')) return false;
    std::string nextRules = versioned ? cache.substr(4) : "";
    WidgetGlassRules parsed;
    if (!parsed.parse(nextRules)) return false;
    stack = cache[offset] == '1'; glass = cache[offset + 1] == '1';
    rules = versioned && cache[0] == '3' ? nextRules : "*"; return true;
}
inline std::string encodeWidgetCache(bool stack, bool glass, const std::string &rules) {
    return std::string("3") + (stack ? '1' : '0') + (glass ? '1' : '0') + '\n' + rules;
}

// Retain the one-byte experiment cache on upgrade, defaulting its new rules to all.
inline bool decodeWidgetStyleCache(const std::string &cache, bool &enabled, std::string &rules) {
    if (cache.empty() || (cache[0] != '0' && cache[0] != '1')) return false;
    if (cache.size() != 1 && (cache.size() < 2 || cache[1] != '\n')) return false;
    std::string next = cache.size() == 1 ? "*" : cache.substr(2);
    WidgetGlassRules parsed;
    if (!parsed.parse(next)) return false;
    enabled = cache[0] == '1'; rules = next; return true;
}
inline std::string encodeWidgetStyleCache(bool enabled, const std::string &rules) {
    return std::string(enabled ? "1\n" : "0\n") + rules;
}

// Only annotate borrowed metadata and its owned clones. Never change a Bundle.
// Owners retire borrowed pointers before free; Bundle_drop retires owned clones.
class WidgetMetadataRegistry {
    std::mutex mutex;
    std::unordered_map<void *, void *> owners;
    struct Borrowed { size_t count; WidgetIdentity identity; };
    std::unordered_map<void *, Borrowed> borrowed;
    std::unordered_map<void *, WidgetIdentity> clones;
    void forgetLocked(void *owner) {
        auto entry = owners.find(owner);
        if (entry == owners.end()) return;
        auto count = borrowed.find(entry->second);
        if (count != borrowed.end() && --count->second.count == 0) borrowed.erase(count);
        owners.erase(entry);
    }
public:
    void observe(void *owner, void *bundle, const WidgetIdentity &identity) {
        std::lock_guard<std::mutex> guard(mutex);
        forgetLocked(owner);
        if (identity.valid() && bundle) {
            owners[owner] = bundle; auto &entry = borrowed[bundle];
            if (entry.count == 0) entry.identity = identity;
            else if (entry.identity.component != identity.component) entry.identity = {};
            else if (entry.identity.appWidgetId != identity.appWidgetId) entry.identity.appWidgetId = 0;
            ++entry.count;
        }
    }
    void forgetOwner(void *owner) {
        std::lock_guard<std::mutex> guard(mutex); forgetLocked(owner);
    }
    void forgetBorrowed(void *bundle) {
        std::lock_guard<std::mutex> guard(mutex);
        // Most ActivityInfo/PackageItemInfo drops are unrelated to widgets.
        // Only tracked borrowed bundles can have owner aliases to retire.
        if (!borrowed.erase(bundle)) return;
        for (auto entry = owners.begin(); entry != owners.end();) {
            if (entry->second == bundle) entry = owners.erase(entry);
            else ++entry;
        }
    }
    void cloned(void *source, void *copy) {
        std::lock_guard<std::mutex> guard(mutex);
        if (!copy || copy == source) return;
        clones.erase(copy);
        auto entry = borrowed.find(source);
        if (entry != borrowed.end()) clones[copy] = entry->second.identity;
        else { auto clone = clones.find(source); if (clone != clones.end()) clones[copy] = clone->second; }
    }
    void dropped(void *bundle) {
        std::lock_guard<std::mutex> guard(mutex); clones.erase(bundle);
    }
    WidgetIdentity identity(void *bundle) {
        std::lock_guard<std::mutex> guard(mutex);
        auto entry = borrowed.find(bundle); if (entry != borrowed.end()) return entry->second.identity;
        auto clone = clones.find(bundle); return clone == clones.end() ? WidgetIdentity{} : clone->second;
    }
    bool hasIdentity(void *bundle) {
        std::lock_guard<std::mutex> guard(mutex);
        auto entry = borrowed.find(bundle);
        if (entry != borrowed.end()) return entry->second.identity.valid();
        auto clone = clones.find(bundle);
        return clone != clones.end() && clone->second.valid();
    }
    bool selected(void *bundle) {
        std::lock_guard<std::mutex> guard(mutex);
        return bundle && (borrowed.count(bundle) || clones.count(bundle));
    }
};
