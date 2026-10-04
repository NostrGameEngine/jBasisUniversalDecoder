#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <set>
#include <vector>

#define BASISU_NOTE_UNUSED(value) (void)value
#define TINYDDS_IMPLEMENTATION
#include "encoder/3rdparty/tinydds.h"

// Bounded parser regression: authored RGBA mip payloads remain complete and in order.
namespace {
struct input_stream {
    std::vector<uint8_t> bytes;
    size_t position = 0;
    bool error = false;
    std::set<void*> allocations;
    size_t allocated = 0, freed = 0, invalid_frees = 0;
    size_t reads = 0, seeks = 0;
    size_t fail_seek_at = 0;
};

void append_u32(std::vector<uint8_t>& bytes, uint32_t value) {
    for (int shift = 0; shift < 32; shift += 8) bytes.push_back(static_cast<uint8_t>(value >> shift));
}

uint32_t mip_count(uint32_t width, uint32_t height) {
    uint32_t count = 1;
    while (width > 1 || height > 1) {
        width = std::max(1U, width / 2); height = std::max(1U, height / 2); ++count;
    }
    return count;
}

input_stream fixture(uint32_t width, uint32_t height, uint32_t levels, bool cube = false) {
    input_stream stream;
    std::vector<uint8_t>& bytes = stream.bytes;
    append_u32(bytes, 0x20534444); append_u32(bytes, 124); append_u32(bytes, 0x2100f);
    append_u32(bytes, height); append_u32(bytes, width); append_u32(bytes, width * 4);
    append_u32(bytes, 0); append_u32(bytes, levels);
    for (int index = 0; index < 11; ++index) append_u32(bytes, 0);
    append_u32(bytes, 32); append_u32(bytes, 0x41); append_u32(bytes, 0); append_u32(bytes, 32);
    append_u32(bytes, 0xff); append_u32(bytes, 0xff00); append_u32(bytes, 0xff0000); append_u32(bytes, 0xff000000);
    append_u32(bytes, levels > 1 ? 0x401008 : 0x1000);
    append_u32(bytes, cube ? 0xfe00 : 0);
    for (int index = 0; index < 3; ++index) append_u32(bytes, 0);
    for (uint32_t face = 0; face < (cube ? 6U : 1U); ++face) {
        uint32_t w = width, h = height;
        for (uint32_t level = 0; level < levels; ++level) {
            for (uint32_t index = 0; index < w * h * 4; ++index)
                bytes.push_back(static_cast<uint8_t>((face * 23 + level * 43 + index * 7) & 255));
            w = std::max(1U, w / 2); h = std::max(1U, h / 2);
        }
    }
    return stream;
}

TinyDDS_Callbacks allocator_callbacks() {
    TinyDDS_Callbacks callbacks;
    callbacks.errorFn = [](void* user, const char* message) {
        static_cast<input_stream*>(user)->error = true; std::fprintf(stderr, "tinydds: %s\n", message);
    };
    callbacks.allocFn = [](void* user, size_t size) -> void* {
        input_stream* input = static_cast<input_stream*>(user);
        void* memory = std::malloc(size);
        if (memory) { input->allocations.insert(memory); ++input->allocated; }
        return memory;
    };
    callbacks.freeFn = [](void* user, void* memory) {
        input_stream* input = static_cast<input_stream*>(user);
        // Old-provider negative controls observe bad arguments without executing invalid free.
        // Every valid allocated pointer is actually passed to the ordinary allocator's free.
        if (input->allocations.erase(memory) != 1) { ++input->invalid_frees; return; }
        ++input->freed; std::free(memory);
    };
    callbacks.readFn = [](void* user, void* destination, size_t size) -> size_t {
        input_stream* input = static_cast<input_stream*>(user);
        ++input->reads;
        size = std::min(size, input->bytes.size() - input->position);
        std::memcpy(destination, input->bytes.data() + input->position, size); input->position += size; return size;
    };
    callbacks.seekFn = [](void* user, int64_t offset) -> bool {
        input_stream* input = static_cast<input_stream*>(user);
        ++input->seeks;
        if (input->fail_seek_at && input->seeks >= input->fail_seek_at) return false;
        if (offset < 0 || static_cast<uint64_t>(offset) > input->bytes.size()) return false;
        input->position = static_cast<size_t>(offset); return true;
    };
    callbacks.tellFn = [](void* user) -> int64_t { return static_cast<int64_t>(static_cast<input_stream*>(user)->position); };
    return callbacks;
}

bool check(uint32_t width, uint32_t height, uint32_t extra_levels) {
    uint32_t expected = mip_count(width, height);
    input_stream stream = fixture(width, height, expected + extra_levels);
    TinyDDS_Callbacks callbacks = allocator_callbacks();
    TinyDDS_ContextHandle context = TinyDDS_CreateContext(&callbacks, &stream);
    if (context == nullptr) return false;
    bool success = TinyDDS_ReadHeader(context) && TinyDDS_NumberOfMipmaps(context) == expected;
    uint32_t actual = TinyDDS_NumberOfMipmaps(context);
    size_t offset = 128;
    uint32_t mip_width = width, mip_height = height;
    if (success) for (uint32_t level = 0; level < expected; ++level) {
        uint32_t size = mip_width * mip_height * 4;
        const void* pixels = TinyDDS_ImageRawData(context, level);
        success = success && TinyDDS_ImageSize(context, level) == size && pixels != nullptr;
        if (success) success = std::memcmp(pixels, stream.bytes.data() + offset, size) == 0;
        offset += size; mip_width = std::max(1U, mip_width / 2); mip_height = std::max(1U, mip_height / 2);
    }
    TinyDDS_DestroyContext(context);
    success = success && !stream.error && stream.invalid_frees == 0 && stream.allocations.empty() && stream.allocated == stream.freed;
    std::printf("%s %ux%u expected=%u actual=%u extra=%u payloads=%s\n", success ? "PASS" : "FAIL",
            width, height, expected, actual, extra_levels, success ? "exact" : "unverified");
    return success;
}

bool failure_lifecycle(const char* label, bool cube, bool seek_failure, bool base_truncated) {
    input_stream stream = cube ? fixture(2, 2, 2, true) : fixture(17, 9, 5);
    std::vector<uint8_t> complete = stream.bytes;
    if (!seek_failure) stream.bytes.resize(cube ? 243 : base_truncated ? 739 : 908);
    TinyDDS_Callbacks callbacks = allocator_callbacks();
    TinyDDS_ContextHandle context = TinyDDS_CreateContext(&callbacks, &stream);
    if (!context) return false;
    bool success = TinyDDS_ReadHeader(context);
    uint32_t level = cube || base_truncated || seek_failure ? 0 : 4;
    stream.seeks = 0;
    if (seek_failure) stream.fail_seek_at = cube ? 3 : 1;
    size_t reads_before = stream.reads, allocations_before = stream.allocated;
    const void* first = success ? TinyDDS_ImageRawData(context, level) : nullptr;
    success = success && first == nullptr && stream.allocations.size() == 1 && stream.invalid_frees == 0;
    if (seek_failure && !cube)
        success = success && stream.reads == reads_before && stream.allocated == allocations_before;
    size_t reads_after_first = stream.reads;
    const void* retry = TinyDDS_ImageRawData(context, level);
    success = success && retry == nullptr && stream.allocations.size() == 1 && stream.invalid_frees == 0;
    if (!seek_failure) success = success && stream.reads > reads_after_first;
    // A failed read must not cache partial bytes. Restore the same source and retry this context.
    // Never inspect the old provider's uninitialized cached partial data in its negative control.
    if (success) {
        stream.bytes = complete; stream.fail_seek_at = 0;
        const void* restored = TinyDDS_ImageRawData(context, level);
        success = restored != nullptr;
        if (success && cube) {
            for (uint32_t face = 0; face < 6; ++face)
                success = success && std::memcmp(static_cast<const uint8_t*>(restored) + face * 16,
                        complete.data() + 128 + face * 20, 16) == 0;
        } else if (success) {
            size_t offset = level == 4 ? 908 : 128;
            success = std::memcmp(restored, complete.data() + offset, TinyDDS_ImageSize(context, level)) == 0;
        }
        size_t reads = stream.reads, seeks = stream.seeks;
        success = success && TinyDDS_ImageRawData(context, level) == restored && stream.reads == reads && stream.seeks == seeks;
    }
    TinyDDS_DestroyContext(context);
    success = success && stream.allocations.empty() && stream.invalid_frees == 0 && stream.allocated == stream.freed;
    std::printf("%s lifecycle=%s actual_malloc=%zu actual_free=%zu invalid_free_arguments=%zu live=%zu\n",
            success ? "PASS" : "FAIL", label, stream.allocated, stream.freed, stream.invalid_frees, stream.allocations.size());
    return success;
}

bool complete_cube() {
    input_stream stream = fixture(2, 2, 2, true);
    TinyDDS_Callbacks callbacks = allocator_callbacks();
    TinyDDS_ContextHandle context = TinyDDS_CreateContext(&callbacks, &stream);
    if (!context) return false;
    bool success = TinyDDS_ReadHeader(context) && TinyDDS_NumberOfMipmaps(context) == 2;
    for (uint32_t level = 0; level < 2 && success; ++level) {
        const void* pixels = TinyDDS_ImageRawData(context, level);
        uint32_t face_size = level == 0 ? 16 : 4;
        success = pixels && TinyDDS_ImageSize(context, level) == face_size * 6;
        if (success) for (uint32_t face = 0; face < 6; ++face)
            success = success && std::memcmp(static_cast<const uint8_t*>(pixels) + face * face_size,
                    stream.bytes.data() + 128 + face * 20 + (level == 0 ? 0 : 16), face_size) == 0;
    }
    TinyDDS_DestroyContext(context);
    success = success && !stream.error && stream.allocations.empty() && stream.invalid_frees == 0 && stream.allocated == stream.freed;
    std::printf("%s complete-cube=2x2 all2mips all6faces malloc=%zu free=%zu live=%zu\n",
            success ? "PASS" : "FAIL", stream.allocated, stream.freed, stream.allocations.size());
    return success;
}
}

int main() {
    const uint32_t dimensions[][2] = {{17, 9}, {20, 33}, {33, 20}, {64, 64}, {1, 8}, {8, 1}, {1, 1}, {3, 2}};
    int failures = 0;
    for (const auto& dimensions_pair : dimensions)
        if (!check(dimensions_pair[0], dimensions_pair[1], 0)) ++failures;
    if (!check(17, 9, 2)) ++failures;
    if (!check(64, 64, 2)) ++failures;
    if (!complete_cube()) ++failures;
    if (!failure_lifecycle("missing-rectangular-final1x1", false, false, false)) ++failures;
    if (!failure_lifecycle("truncated-base", false, false, true)) ++failures;
    if (!failure_lifecycle("noncube-seek-failure", false, true, false)) ++failures;
    if (!failure_lifecycle("cubemap-final-face-short-read", true, false, false)) ++failures;
    if (!failure_lifecycle("cubemap-mid-face-seek-failure", true, true, false)) ++failures;
    std::printf("bounded-cases=16 failures=%d\n", failures);
    return failures == 0 ? 0 : 1;
}
