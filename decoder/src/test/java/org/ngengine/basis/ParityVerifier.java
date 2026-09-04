package org.ngengine.basis;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Utility runner for end-to-end parity checks against native-ground-truth .dat files.
 *
 * Strict mode is the default so native-gold mismatches cannot be hidden by smoke runs.
 * Pass --non-strict only for local inventory work.
 */
public final class ParityVerifier {

    private static final class ParityCase {
        private final String inputFixture;
        private final String goldFixture;
        private final BasisTranscodeTarget target;
        private final int imageIndex;

        private ParityCase(String inputFixture, String goldFixture) {
            this(inputFixture, goldFixture, BasisTranscodeTarget.RGBA8, 0);
        }

        private ParityCase(String inputFixture, String goldFixture, BasisTranscodeTarget target) {
            this(inputFixture, goldFixture, target, 0);
        }

        private ParityCase(
                String inputFixture,
                String goldFixture,
                BasisTranscodeTarget target,
                int imageIndex) {
            this.inputFixture = inputFixture;
            this.goldFixture = goldFixture;
            this.target = target;
            this.imageIndex = imageIndex;
        }
    }

    public static void main(String[] args) {
        boolean strict = !hasFlag(args, "--non-strict");
        List<ParityCase> cases = new ArrayList<>();
        cases.add(new ParityCase("fixtures/ktx2/base_xuastc_zstd.ktx2",
                "fixtures/native_gold/base_xuastc_zstd.dat"));
        cases.add(new ParityCase("fixtures/ktx2/base_xuastc_zstd.ktx2",
                "fixtures/native_gold/base_xuastc_zstd_astc5x4.dat",
                BasisTranscodeTarget.ASTC_LDR_5X4));
        cases.add(new ParityCase("fixtures/ktx2/base_xuastc_arith.ktx2",
                "fixtures/native_gold/base_xuastc_arith.dat"));
        cases.add(new ParityCase("fixtures/ktx2/base_xuastc_arith.ktx2",
                "fixtures/native_gold/base_xuastc_arith_astc5x4.dat",
                BasisTranscodeTarget.ASTC_LDR_5X4));
        cases.add(new ParityCase("fixtures/ktx2/tough_xuastc6x6_arith.ktx2",
                "fixtures/native_gold/tough_xuastc6x6_arith_astc6x6.dat",
                BasisTranscodeTarget.ASTC_LDR_6X6));
        cases.add(new ParityCase("fixtures/ktx2/tough_xuastc6x6_zstd.ktx2",
                "fixtures/native_gold/tough_xuastc6x6_zstd_astc6x6.dat",
                BasisTranscodeTarget.ASTC_LDR_6X6));
        cases.add(new ParityCase("fixtures/ktx2/kodim23_xuastc6x6_mip_arith.ktx2",
                "fixtures/native_gold/kodim23_xuastc6x6_mip_arith_astc6x6.dat",
                BasisTranscodeTarget.ASTC_LDR_6X6));
        cases.add(new ParityCase("fixtures/ktx2/kodim_xuastc6x6_array_mip_arith.ktx2",
                "fixtures/native_gold/kodim_xuastc6x6_array_mip_arith_image1_astc6x6.dat",
                BasisTranscodeTarget.ASTC_LDR_6X6,
                1));
        cases.add(new ParityCase("fixtures/ktx2/kodim_xuastc6x6_cubemap_mip_arith.ktx2",
                "fixtures/native_gold/kodim_xuastc6x6_cubemap_mip_arith_face5_astc6x6.dat",
                BasisTranscodeTarget.ASTC_LDR_6X6,
                5));
        cases.add(new ParityCase("fixtures/ktx2/kodim_uastc4x4.ktx2",
                "fixtures/native_gold/kodim_uastc4x4_astc4x4.dat",
                BasisTranscodeTarget.ASTC_LDR_4X4));
        cases.add(new ParityCase("fixtures/ktx2/kodim_uastc4x4_zstd.ktx2",
                "fixtures/native_gold/kodim_uastc4x4_astc4x4.dat",
                BasisTranscodeTarget.ASTC_LDR_4X4));
        cases.add(new ParityCase("fixtures/ktx2/kodim_uastc4x4.ktx2",
                "fixtures/native_gold/kodim_uastc4x4.rgba8.dat",
                BasisTranscodeTarget.RGBA8));
        cases.add(new ParityCase("fixtures/ktx2/kodim_uastc4x4_zstd.ktx2",
                "fixtures/native_gold/kodim_uastc4x4.rgba8.dat",
                BasisTranscodeTarget.RGBA8));
        addXuastcAstcCases(cases, "4x4", BasisTranscodeTarget.ASTC_LDR_4X4);
        addXuastcAstcCases(cases, "5x5", BasisTranscodeTarget.ASTC_LDR_5X5);
        addXuastcAstcCases(cases, "6x5", BasisTranscodeTarget.ASTC_LDR_6X5);
        addXuastcAstcCases(cases, "8x5", BasisTranscodeTarget.ASTC_LDR_8X5);
        addXuastcAstcCases(cases, "8x6", BasisTranscodeTarget.ASTC_LDR_8X6);
        addXuastcAstcCases(cases, "8x8", BasisTranscodeTarget.ASTC_LDR_8X8);
        addXuastcAstcCases(cases, "10x5", BasisTranscodeTarget.ASTC_LDR_10X5);
        addXuastcAstcCases(cases, "10x6", BasisTranscodeTarget.ASTC_LDR_10X6);
        addXuastcAstcCases(cases, "10x8", BasisTranscodeTarget.ASTC_LDR_10X8);
        addXuastcAstcCases(cases, "10x10", BasisTranscodeTarget.ASTC_LDR_10X10);
        addXuastcAstcCases(cases, "12x10", BasisTranscodeTarget.ASTC_LDR_12X10);
        addXuastcAstcCases(cases, "12x12", BasisTranscodeTarget.ASTC_LDR_12X12);
        cases.add(new ParityCase("fixtures/ktx2/kodim23.ktx2",
                "fixtures/native_gold/kodim23.dat"));
        cases.add(new ParityCase("fixtures/ktx2/kodim23_mip.ktx2",
                "fixtures/native_gold/kodim23_mip.dat",
                BasisTranscodeTarget.RGBA8));
        cases.add(new ParityCase("fixtures/ktx2/kodim23_mip.ktx2",
                "fixtures/native_gold/kodim23_mip_etc2.dat",
                BasisTranscodeTarget.ETC2));
        cases.add(new ParityCase("fixtures/ktx2/kodim23_mip.ktx2",
                "fixtures/native_gold/kodim23_mip_bc1.dat",
                BasisTranscodeTarget.BC1));
        cases.add(new ParityCase("fixtures/ktx2/kodim_array.ktx2",
                "fixtures/native_gold/kodim_array_image1.dat",
                BasisTranscodeTarget.RGBA8,
                1));
        cases.add(new ParityCase("fixtures/ktx2/kodim_array.ktx2",
                "fixtures/native_gold/kodim_array_image1_bc1.dat",
                BasisTranscodeTarget.BC1,
                1));
        cases.add(new ParityCase("fixtures/ktx2/kodim_array_mip.ktx2",
                "fixtures/native_gold/kodim_array_mip_image1.dat",
                BasisTranscodeTarget.RGBA8,
                1));
        cases.add(new ParityCase("fixtures/ktx2/kodim_array_mip.ktx2",
                "fixtures/native_gold/kodim_array_mip_image1_etc2.dat",
                BasisTranscodeTarget.ETC2,
                1));
        cases.add(new ParityCase("fixtures/ktx2/kodim_array_mip.ktx2",
                "fixtures/native_gold/kodim_array_mip_image1_bc1.dat",
                BasisTranscodeTarget.BC1,
                1));
        cases.add(new ParityCase("fixtures/ktx2/kodim_cubemap.ktx2",
                "fixtures/native_gold/kodim_cubemap_face5.dat",
                BasisTranscodeTarget.RGBA8,
                5));
        cases.add(new ParityCase("fixtures/ktx2/kodim_cubemap.ktx2",
                "fixtures/native_gold/kodim_cubemap_face5_bc1.dat",
                BasisTranscodeTarget.BC1,
                5));
        cases.add(new ParityCase("fixtures/ktx2/kodim_cubemap_array.ktx2",
                "fixtures/native_gold/kodim_cubemap_array_image11_bc1.dat",
                BasisTranscodeTarget.BC1,
                11));
        cases.add(new ParityCase("fixtures/ktx2/kodim_cubemap_mip.ktx2",
                "fixtures/native_gold/kodim_cubemap_mip_face5_etc2.dat",
                BasisTranscodeTarget.ETC2,
                5));
        cases.add(new ParityCase("fixtures/ktx2/kodim_cubemap_mip.ktx2",
                "fixtures/native_gold/kodim_cubemap_mip_face5_bc1.dat",
                BasisTranscodeTarget.BC1,
                5));
        cases.add(new ParityCase("fixtures/ktx2/kodim23_alpha.ktx2",
                "fixtures/native_gold/kodim23_alpha.dat",
                BasisTranscodeTarget.RGBA8));
        cases.add(new ParityCase("fixtures/ktx2/kodim23_alpha.ktx2",
                "fixtures/native_gold/kodim23_alpha_etc2.dat",
                BasisTranscodeTarget.ETC2));
        cases.add(new ParityCase("fixtures/ktx2/kodim23_alpha.ktx2",
                "fixtures/native_gold/kodim23_alpha_bc3.dat",
                BasisTranscodeTarget.BC3));
        cases.add(new ParityCase("fixtures/ktx2/kodim23_alpha.ktx2",
                "fixtures/native_gold/kodim23_alpha_bc4.dat",
                BasisTranscodeTarget.BC4));
        cases.add(new ParityCase("fixtures/ktx2/kodim23_alpha.ktx2",
                "fixtures/native_gold/kodim23_alpha_bc5.dat",
                BasisTranscodeTarget.BC5));
        cases.add(new ParityCase("fixtures/ktx2/kodim23_alpha.ktx2",
                "fixtures/native_gold/kodim23_alpha_bc7.dat",
                BasisTranscodeTarget.BC7));
        cases.add(new ParityCase("fixtures/ktx2/kodim23.ktx2",
                "fixtures/native_gold/kodim23_etc1.dat",
                BasisTranscodeTarget.ETC1));
        cases.add(new ParityCase("fixtures/ktx2/kodim23.ktx2",
                "fixtures/native_gold/kodim23_etc1.dat",
                BasisTranscodeTarget.ETC2_NO_ALPHA));
        cases.add(new ParityCase("fixtures/ktx2/kodim23.ktx2",
                "fixtures/native_gold/kodim23_etc2.dat",
                BasisTranscodeTarget.ETC2));
        cases.add(new ParityCase("fixtures/ktx2/kodim23.ktx2",
                "fixtures/native_gold/kodim23_bc1.dat",
                BasisTranscodeTarget.BC1));
        cases.add(new ParityCase("fixtures/ktx2/kodim23.ktx2",
                "fixtures/native_gold/kodim23_bc3.dat",
                BasisTranscodeTarget.BC3));
        cases.add(new ParityCase("fixtures/ktx2/kodim23.ktx2",
                "fixtures/native_gold/kodim23_bc4.dat",
                BasisTranscodeTarget.BC4));
        cases.add(new ParityCase("fixtures/ktx2/kodim23.ktx2",
                "fixtures/native_gold/kodim23_bc5.dat",
                BasisTranscodeTarget.BC5));
        cases.add(new ParityCase("fixtures/ktx2/kodim23.ktx2",
                "fixtures/native_gold/kodim23_bc7.dat",
                BasisTranscodeTarget.BC7));
        cases.add(new ParityCase("fixtures/basis/kodim20_alpha.basis",
                "fixtures/native_gold/kodim20_alpha.dat",
                BasisTranscodeTarget.RGBA8));
        cases.add(new ParityCase("fixtures/basis/kodim20_alpha.basis",
                "fixtures/native_gold/kodim20_alpha_etc2.dat",
                BasisTranscodeTarget.ETC2));
        cases.add(new ParityCase("fixtures/basis/kodim20_alpha.basis",
                "fixtures/native_gold/kodim20_alpha_etc1.dat",
                BasisTranscodeTarget.ETC1));
        cases.add(new ParityCase("fixtures/basis/kodim20_alpha.basis",
                "fixtures/native_gold/kodim20_alpha_etc1.dat",
                BasisTranscodeTarget.ETC2_NO_ALPHA));
        cases.add(new ParityCase("fixtures/basis/kodim20_alpha.basis",
                "fixtures/native_gold/kodim20_alpha_bc1.dat",
                BasisTranscodeTarget.BC1));
        cases.add(new ParityCase("fixtures/basis/kodim20_alpha.basis",
                "fixtures/native_gold/kodim20_alpha_bc3.dat",
                BasisTranscodeTarget.BC3));
        cases.add(new ParityCase("fixtures/basis/kodim20_alpha.basis",
                "fixtures/native_gold/kodim20_alpha_bc4.dat",
                BasisTranscodeTarget.BC4));
        cases.add(new ParityCase("fixtures/basis/kodim20_alpha.basis",
                "fixtures/native_gold/kodim20_alpha_bc5.dat",
                BasisTranscodeTarget.BC5));
        cases.add(new ParityCase("fixtures/basis/kodim20_alpha.basis",
                "fixtures/native_gold/kodim20_alpha_bc7.dat",
                BasisTranscodeTarget.BC7));
        cases.add(new ParityCase("fixtures/basis/kodim20.basis",
                "fixtures/native_gold/kodim20_bc3.dat",
                BasisTranscodeTarget.BC3));
        cases.add(new ParityCase("fixtures/basis/kodim20.basis",
                "fixtures/native_gold/kodim20_bc4.dat",
                BasisTranscodeTarget.BC4));
        cases.add(new ParityCase("fixtures/basis/kodim20.basis",
                "fixtures/native_gold/kodim20_bc5.dat",
                BasisTranscodeTarget.BC5));
        cases.add(new ParityCase("fixtures/basis/kodim20.basis",
                "fixtures/native_gold/kodim20_bc7.dat",
                BasisTranscodeTarget.BC7));
        cases.add(new ParityCase("fixtures/basis/kodim20_21_array.basis",
                "fixtures/native_gold/kodim20_21_array_image1.dat",
                BasisTranscodeTarget.RGBA8,
                1));
        cases.add(new ParityCase("fixtures/basis/kodim_cubemap.basis",
                "fixtures/native_gold/kodim_cubemap_basis_image5.dat",
                BasisTranscodeTarget.RGBA8,
                5));
        cases.add(new ParityCase("fixtures/basis/kodim_cubemap.basis",
                "fixtures/native_gold/kodim_cubemap_basis_image5_etc2.dat",
                BasisTranscodeTarget.ETC2,
                5));
        cases.add(new ParityCase("fixtures/basis/kodim_cubemap.basis",
                "fixtures/native_gold/kodim_cubemap_basis_image5_bc1.dat",
                BasisTranscodeTarget.BC1,
                5));
        cases.add(new ParityCase("fixtures/basis/kodim20_mip.basis",
                "fixtures/native_gold/kodim20_mip.dat",
                BasisTranscodeTarget.RGBA8));
        cases.add(new ParityCase("fixtures/basis/kodim20_mip.basis",
                "fixtures/native_gold/kodim20_mip_etc2.dat",
                BasisTranscodeTarget.ETC2));
        cases.add(new ParityCase("fixtures/basis/kodim20_mip.basis",
                "fixtures/native_gold/kodim20_mip_bc1.dat",
                BasisTranscodeTarget.BC1));
        cases.add(new ParityCase("fixtures/basis/kodim_video.basis",
                "fixtures/native_gold/kodim_video_iframe0_etc2.dat",
                BasisTranscodeTarget.ETC2));
        cases.add(new ParityCase("fixtures/basis/kodim_video.basis",
                "fixtures/native_gold/kodim_video_iframe0_bc1.dat",
                BasisTranscodeTarget.BC1));
        cases.add(new ParityCase("fixtures/basis/kodim_video.basis",
                "fixtures/native_gold/kodim_video_pframe1_etc2.dat",
                BasisTranscodeTarget.ETC2,
                1));
        cases.add(new ParityCase("fixtures/basis/kodim_video.basis",
                "fixtures/native_gold/kodim_video_pframe1_bc1.dat",
                BasisTranscodeTarget.BC1,
                1));

        BasisDecoder decoder = BasisDecoderFactory.createDefault();
        int failures = 0;
        int skipped = 0;

        for (ParityCase parityCase : cases) {
            byte[] input = readFixture(parityCase.inputFixture);
            byte[] expected = readFixture(parityCase.goldFixture);

            try {
                BasisDecodeRequest.Builder builder = BasisDecodeRequest.builder(input)
                        .target(parityCase.target)
                        .imageIndex(parityCase.imageIndex);
                BasisDecodeRequest request = builder.build();
                BasisDecodeResult result = decoder.decode(request);
                byte[] decoded = byteBufferToArray(result.getPixelData());

                if (!Arrays.equals(decoded, expected)) {
                    failures++;
                    System.out.printf("Parity FAIL: %s -> decodedBytes=%d expected=%d "
                                    + "format=%s colorspace=%s width=%d height=%d\n",
                            parityCase.inputFixture,
                            decoded.length,
                            expected.length,
                            result.getImageFormat(),
                            result.getColorSpace(),
                            result.getWidth(),
                            result.getHeight());
                } else {
                    System.out.printf("Parity OK: %s%s\n",
                            parityCase.inputFixture,
                            parityTargetLabel(parityCase));
                }
            } catch (Exception exception) {
                skipped++;
                System.out.printf("Parity SKIP/ERROR: %s -> %s\n", parityCase.inputFixture,
                        exception.getClass().getSimpleName() + ": " + exception.getMessage());
                if (strict) {
                    failures++;
                }
            }
        }

        if (failures > 0) {
            throw new IllegalStateException(
                    "Parity verification failed for " + failures
                    + " cases (" + skipped + " skipped/errors). ");
        }

        String modeLabel = strict
                ? " (strict mode)"
                : " (non-strict mode)";
        System.out.println("Parity verification completed successfully." + modeLabel);
    }

    private static void addXuastcAstcCases(
            List<ParityCase> cases,
            String blockSize,
            BasisTranscodeTarget target) {
        String gold = "fixtures/native_gold/tough_xuastc" + blockSize
                + "_astc" + blockSize + ".dat";
        cases.add(new ParityCase("fixtures/ktx2/tough_xuastc" + blockSize + "_arith.ktx2",
                gold,
                target));
        cases.add(new ParityCase("fixtures/ktx2/tough_xuastc" + blockSize + "_zstd.ktx2",
                gold,
                target));
    }

    private static boolean hasFlag(String[] args, String flag) {
        if (args == null) {
            return false;
        }
        for (String arg : args) {
            if (flag.equalsIgnoreCase(arg)) {
                return true;
            }
        }
        return false;
    }

    private static String parityTargetLabel(ParityCase parityCase) {
        StringBuilder label = new StringBuilder();
        if (parityCase.target != null) {
            label.append(" -> ").append(parityCase.target);
        }
        if (parityCase.imageIndex != 0) {
            label.append(" image=").append(parityCase.imageIndex);
        }
        return label.toString();
    }

    private static byte[] byteBufferToArray(ByteBuffer buffer) {
        ByteBuffer duplicate = buffer.asReadOnlyBuffer();
        duplicate.rewind();
        byte[] data = new byte[duplicate.remaining()];
        duplicate.get(data);
        return data;
    }

    private static byte[] readFixture(String path) {
        try (InputStream stream =
                ParityVerifier.class.getClassLoader().getResourceAsStream(path)) {
            if (stream == null) {
                Path sourceTreePath = Paths.get("src/test/resources").resolve(path);
                if (Files.exists(sourceTreePath)) {
                    return Files.readAllBytes(sourceTreePath);
                }
                throw new RuntimeException("Fixture non trovata: " + path);
            }
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = stream.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        } catch (IOException exception) {
            throw new RuntimeException("Non riesco a leggere il fixture: " + path, exception);
        }
    }

    private ParityVerifier() {
    }
}
