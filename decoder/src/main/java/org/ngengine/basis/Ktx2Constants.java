package org.ngengine.basis;

/**
 * C++ parity constants extracted from basisu_transcoder.h for KTX2 metadata decoding.
 */
public final class Ktx2Constants {

    private Ktx2Constants() {
    }

    /** KTX2 12-byte file identifier magic. */
    public static final byte[] KTX2_FILE_IDENTIFIER = {
            (byte) 0xAB,
            (byte) 0x4B,
            (byte) 0x54,
            (byte) 0x58,
            0x20,
            0x32,
            0x30,
            (byte) 0xBB,
            0x0D,
            0x0A,
            0x1A,
            0x0A
    };

    /** KTX2 container fixed header size in bytes. */
    public static final int KTX2_HEADER_SIZE = 80;

    /** Basis file magic/version constants from basist::basis_file_header. */
    public static final int cBASISSigValue = ('B' << 8) | 's';
    public static final int cBASISFirstVersion = 0x10;
    public static final int cBASISMaxUSPerFrame = 0xFFFFFF;


    /** C++ constant mirrored from basisu_transcoder.h. */
    public static final int BASISU_MAX_SUPPORTED_TEXTURE_DIMENSION = 16384;

    /**
     * Compile-time support toggles mirrored from the C++ header for contract-complete constant mapping.
     */
    public static final int BASISD_SUPPORT_KTX2 = 1;
    public static final int BASISD_SUPPORT_KTX2_ZSTD = 1;

    /** Maximum number of supported mip levels in parity parser checks. */
    public static final int KTX2_MAX_SUPPORTED_LEVEL_COUNT = 16;

    /** Vulkan format IDs used by this Java decoder. */
    public static final int VK_FORMAT_R8G8B8A8_UNORM = 37;
    public static final int VK_FORMAT_R8G8B8A8_SRGB = 43;
    public static final int VK_FORMAT_B8G8R8A8_UNORM = 50;
    public static final int VK_FORMAT_B8G8R8A8_SRGB = 51;

    /** KTX2 supercompression values. */
    public static final int KTX2_SS_NONE = 0;
    public static final int KTX2_SS_BASISLZ = 1;
    public static final int KTX2_SS_ZSTANDARD = 2;
    public static final int KTX2_SS_DEFLATE = 3;
    public static final int KTX2_SS_UASTC_HDR_6x6I = 4;
    public static final int KTX2_SS_XUASTC_LDR = 5;

    /** Useful KTX2 semantic constants mirrored from the C++ header. */
    public static final int KTX2_KDF_DF_MODEL_ETC1S = 163;
    public static final int KTX2_KDF_DF_MODEL_UASTC_LDR_4X4 = 166;
    public static final int KTX2_KDF_DF_MODEL_UASTC_HDR_4X4 = 167;
    public static final int KTX2_KDF_DF_MODEL_UASTC_HDR_6X6_INTERMEDIATE = 168;
    public static final int KTX2_KDF_DF_MODEL_XUASTC_LDR_INTERMEDIATE = 169;
    public static final int KTX2_IMAGE_IS_P_FRAME = 2;
    public static final int KTX2_UASTC_BLOCK_SIZE = 16;
    public static final int KTX2_FORMAT_ASTC_4x4_SFLOAT_BLOCK = 1000066000;
    public static final int KTX2_FORMAT_ASTC_5x4_SFLOAT_BLOCK = 1000066001;
    public static final int KTX2_FORMAT_ASTC_5x5_SFLOAT_BLOCK = 1000066002;
    public static final int KTX2_FORMAT_ASTC_6x5_SFLOAT_BLOCK = 1000066003;
    public static final int KTX2_FORMAT_ASTC_6x6_SFLOAT_BLOCK = 1000066004;
    public static final int KTX2_FORMAT_ASTC_8x5_SFLOAT_BLOCK = 1000066005;
    public static final int KTX2_FORMAT_ASTC_8x6_SFLOAT_BLOCK = 1000066006;
    public static final int KTX2_FORMAT_ASTC_4x4_UNORM_BLOCK = 157;
    public static final int KTX2_FORMAT_ASTC_4x4_SRGB_BLOCK = 158;
    public static final int KTX2_FORMAT_ASTC_5x4_UNORM_BLOCK = 159;
    public static final int KTX2_FORMAT_ASTC_5x4_SRGB_BLOCK = 160;
    public static final int KTX2_FORMAT_ASTC_5x5_UNORM_BLOCK = 161;
    public static final int KTX2_FORMAT_ASTC_5x5_SRGB_BLOCK = 162;
    public static final int KTX2_FORMAT_ASTC_6x5_UNORM_BLOCK = 163;
    public static final int KTX2_FORMAT_ASTC_6x5_SRGB_BLOCK = 164;
    public static final int KTX2_FORMAT_ASTC_6x6_UNORM_BLOCK = 165;
    public static final int KTX2_FORMAT_ASTC_6x6_SRGB_BLOCK = 166;
    public static final int KTX2_FORMAT_ASTC_8x5_UNORM_BLOCK = 167;
    public static final int KTX2_FORMAT_ASTC_8x5_SRGB_BLOCK = 168;
    public static final int KTX2_FORMAT_ASTC_8x6_UNORM_BLOCK = 169;
    public static final int KTX2_FORMAT_ASTC_8x6_SRGB_BLOCK = 170;
    public static final int KTX2_FORMAT_ASTC_10x5_UNORM_BLOCK = 173;
    public static final int KTX2_FORMAT_ASTC_10x5_SRGB_BLOCK = 174;
    public static final int KTX2_FORMAT_ASTC_10x6_UNORM_BLOCK = 175;
    public static final int KTX2_FORMAT_ASTC_10x6_SRGB_BLOCK = 176;
    public static final int KTX2_FORMAT_ASTC_8x8_UNORM_BLOCK = 171;
    public static final int KTX2_FORMAT_ASTC_8x8_SRGB_BLOCK = 172;
    public static final int KTX2_FORMAT_ASTC_10x8_UNORM_BLOCK = 177;
    public static final int KTX2_FORMAT_ASTC_10x8_SRGB_BLOCK = 178;
    public static final int KTX2_FORMAT_ASTC_10x10_UNORM_BLOCK = 179;
    public static final int KTX2_FORMAT_ASTC_10x10_SRGB_BLOCK = 180;
    public static final int KTX2_FORMAT_ASTC_12x10_UNORM_BLOCK = 181;
    public static final int KTX2_FORMAT_ASTC_12x10_SRGB_BLOCK = 182;
    public static final int KTX2_FORMAT_ASTC_12x12_UNORM_BLOCK = 183;
    public static final int KTX2_FORMAT_ASTC_12x12_SRGB_BLOCK = 184;

    /**
     * Vulkan format constants mirrored for public contract parity.
     */
    public static final int KTX2_VK_FORMAT_UNDEFINED = 0;

    public static final int KTX2_KHR_DF_TRANSFER_LINEAR = 1;
    public static final int KTX2_KHR_DF_TRANSFER_SRGB = 2;
    public static final int KTX2_KDF_DF_MODEL_ASTC = 162;
}
