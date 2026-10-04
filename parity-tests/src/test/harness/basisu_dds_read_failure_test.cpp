#include "encoder/basisu_gpu_texture.h"
#include <algorithm>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
#include <vector>

// Actual native wrapper, linked to the freshly built encoder library; no image encoding.
namespace {
void u32(std::vector<uint8_t>& bytes, uint32_t value) {
    for (int shift = 0; shift < 32; shift += 8) bytes.push_back(static_cast<uint8_t>(value >> shift));
}
std::vector<uint8_t> fixture(uint32_t channels_bytes, uint32_t fourcc) {
    std::vector<uint8_t> bytes;
    for (uint32_t value : {0x20534444U, 124U, 0x2100fU, 9U, 17U, 17U * channels_bytes, 0U, 5U}) u32(bytes, value);
    for (int index = 0; index < 11; ++index) u32(bytes, 0);
    u32(bytes, 32); u32(bytes, fourcc ? 4 : 0x41); u32(bytes, fourcc); u32(bytes, fourcc ? 0 : 32);
    for (uint32_t value : {0xffU, 0xff00U, 0xff0000U, 0xff000000U}) u32(bytes, fourcc ? 0 : value);
    u32(bytes, 0x401008); for (int index = 0; index < 4; ++index) u32(bytes, 0);
    uint32_t width = 17, height = 9;
    for (uint32_t level = 0; level < 5; ++level) {
        for (uint32_t index = 0; index < width * height * channels_bytes; ++index)
            bytes.push_back(static_cast<uint8_t>((level * 43 + index * 7) & 255));
        width = std::max(1U, width / 2); height = std::max(1U, height / 2);
    }
    return bytes;
}
bool write(const std::string& path, const std::vector<uint8_t>& bytes) {
    FILE* file = std::fopen(path.c_str(), "wb");
    if (!file) return false;
    bool success = std::fwrite(bytes.data(), 1, bytes.size(), file) == bytes.size();
    return std::fclose(file) == 0 && success;
}
bool reject(const std::string& directory, const char* role, uint32_t channels_bytes, uint32_t fourcc, bool base) {
    std::vector<uint8_t> bytes = fixture(channels_bytes, fourcc);
    bytes.resize(base ? 128 + 17 * 9 * channels_bytes - 1 : bytes.size() - channels_bytes);
    std::string path = directory + "/" + role + (base ? "-truncated-base.dds" : "-missing-tail.dds");
    if (!write(path, bytes)) return false;
    basisu::vector<basisu::image> ldr; basisu::vector<basisu::imagef> hdr;
    bool success = !basisu::read_uncompressed_dds_file(path.c_str(), ldr, hdr);
    std::printf("%s actual-wrapper=%s failure=%s fixtureBytes=%zu\n", success ? "PASS" : "FAIL",
            role, base ? "base-short-read" : "last1x1-short-read", bytes.size());
    return success;
}
bool complete(const std::string& directory) {
    std::vector<uint8_t> bytes = fixture(4, 0); std::string path = directory + "/rgba8-complete17x9.dds";
    if (!write(path, bytes)) return false;
    basisu::vector<basisu::image> ldr; basisu::vector<basisu::imagef> hdr;
    bool success = basisu::read_uncompressed_dds_file(path.c_str(), ldr, hdr) && ldr.size() == 5 && hdr.empty();
    uint32_t width = 17, height = 9; size_t offset = 128;
    if (success) for (uint32_t level = 0; level < 5; ++level) {
        success = success && ldr[level].get_width() == width && ldr[level].get_height() == height
                && std::memcmp(ldr[level].get_ptr(), bytes.data() + offset, width * height * 4) == 0;
        offset += width * height * 4; width = std::max(1U, width / 2); height = std::max(1U, height / 2);
    }
    std::printf("%s actual-wrapper=RGBA8 complete17x9 all5mips pixelBytes=exact\n", success ? "PASS" : "FAIL");
    return success;
}
}
int main(int argc, char** argv) {
    if (argc != 2) return 2;
    int failed = complete(argv[1]) ? 0 : 1;
    for (bool base : {false, true}) {
        if (!reject(argv[1], "rgba8", 4, 0, base)) ++failed;
        if (!reject(argv[1], "rgba16f", 8, 113, base)) ++failed;
        if (!reject(argv[1], "rgba32f", 16, 116, base)) ++failed;
    }
    std::printf("actual-wrapper-cases=7 failures=%d\n", failed); return failed == 0 ? 0 : 1;
}
