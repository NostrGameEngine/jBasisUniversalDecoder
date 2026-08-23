#include <cstdint>
#include <fstream>
#include <iostream>
#include <iterator>
#include <vector>

#include "transcoder/basisu_transcoder.h"

namespace {
basist::transcoder_texture_format format_from_code(int code) {
    switch (code) {
        case 24:
            return basist::transcoder_texture_format::cTFRGB_HALF;
        case 25:
            return basist::transcoder_texture_format::cTFRGBA_HALF;
        case 26:
            return basist::transcoder_texture_format::cTFRGB_9E5;
        default:
            std::cerr << "unsupported format code\n";
            std::exit(2);
    }
}

uint32_t bytes_per_pixel(int code) {
    switch (code) {
        case 24:
            return 6;
        case 25:
            return 8;
        case 26:
            return 4;
        default:
            std::exit(2);
    }
}

std::vector<uint8_t> read_file(const char* path) {
    std::ifstream input(path, std::ios::binary);
    if (!input) {
        std::cerr << "failed to open input\n";
        std::exit(3);
    }
    return std::vector<uint8_t>(std::istreambuf_iterator<char>(input), {});
}

void write_stdout(const std::vector<uint8_t>& data) {
    std::cout.write(reinterpret_cast<const char*>(data.data()), static_cast<std::streamsize>(data.size()));
    if (!std::cout) {
        std::exit(4);
    }
}

bool has_ktx2_identifier(const std::vector<uint8_t>& data) {
    static const uint8_t ktx2_id[12] = {
        0xAB, 0x4B, 0x54, 0x58, 0x20, 0x32, 0x30, 0xBB, 0x0D, 0x0A, 0x1A, 0x0A
    };
    return data.size() >= sizeof(ktx2_id)
            && std::equal(std::begin(ktx2_id), std::end(ktx2_id), data.begin());
}
}

int main(int argc, char** argv) {
    if (argc != 3) {
        std::cerr << "usage: basisu_raw_transcode <file> <format-code>\n";
        return 1;
    }

    std::vector<uint8_t> data = read_file(argv[1]);
    int format_code = std::atoi(argv[2]);
    basist::transcoder_texture_format format = format_from_code(format_code);
    uint32_t bpp = bytes_per_pixel(format_code);

    basist::basisu_transcoder_init();

    if (has_ktx2_identifier(data)) {
        basist::ktx2_transcoder transcoder;
        if (!transcoder.init(data.data(), static_cast<uint32_t>(data.size())) || !transcoder.start_transcoding()) {
            std::cerr << "failed to init KTX2 transcoder\n";
            return 5;
        }
        basist::ktx2_image_level_info info;
        if (!transcoder.get_image_level_info(info, 0, 0, 0)) {
            std::cerr << "failed to query KTX2 level\n";
            return 6;
        }
        uint32_t pixels = info.m_orig_width * info.m_orig_height;
        std::vector<uint8_t> output(static_cast<size_t>(pixels) * bpp);
        if (!transcoder.transcode_image_level(0, 0, 0, output.data(), pixels, format, 0, info.m_orig_width, info.m_orig_height)) {
            std::cerr << "failed to transcode KTX2 level\n";
            return 7;
        }
        write_stdout(output);
        return 0;
    }

    basist::basisu_transcoder transcoder;
    if (!transcoder.validate_header(data.data(), static_cast<uint32_t>(data.size()))
            || !transcoder.start_transcoding(data.data(), static_cast<uint32_t>(data.size()))) {
        std::cerr << "failed to init Basis transcoder\n";
        return 8;
    }
    basist::basisu_image_level_info info;
    if (!transcoder.get_image_level_info(data.data(), static_cast<uint32_t>(data.size()), info, 0, 0)) {
        std::cerr << "failed to query Basis level\n";
        return 9;
    }
    uint32_t pixels = info.m_orig_width * info.m_orig_height;
    std::vector<uint8_t> output(static_cast<size_t>(pixels) * bpp);
    if (!transcoder.transcode_image_level(
            data.data(),
            static_cast<uint32_t>(data.size()),
            0,
            0,
            output.data(),
            pixels,
            format,
            0,
            info.m_orig_width,
            nullptr,
            info.m_orig_height)) {
        std::cerr << "failed to transcode Basis level\n";
        return 10;
    }
    write_stdout(output);
    return 0;
}
