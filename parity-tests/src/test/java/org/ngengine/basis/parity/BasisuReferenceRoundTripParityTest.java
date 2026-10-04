package org.ngengine.basis.parity;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ngengine.basis.BasisDecodeRequest;
import org.ngengine.basis.BasisDecodeResult;
import org.ngengine.basis.BasisDecoderFactory;
import org.ngengine.basis.BasisImageFormat;
import org.ngengine.basis.BasisTranscodeTarget;
import org.ngengine.basis.Ktx2Container;
import org.ngengine.basis.Ktx2DecodeFlag;
import org.ngengine.basis.Ktx2SupercompressionScheme;

class BasisuReferenceRoundTripParityTest {

    @TempDir
    Path tempDir;

    @Test
    void nativeUastcBasisPreservesRequestedTransferFunction() throws Exception {
        Path png = tempDir.resolve("transfer.png");
        writeCheckerPng(png);
        for (boolean linear : new boolean[] {false, true}) {
            Path encoded = tempDir.resolve(linear ? "linear.basis" : "srgb.basis");
            List<String> command = new ArrayList<>(List.of(
                    basisuExecutable().toString(), "-basis", "-uastc", "-quiet",
                    "-output_file", encoded.toString(), png.toString()));
            if (linear) {
                command.add("-linear");
            }
            run(command, tempDir);
            try (var input = Files.newInputStream(encoded)) {
                byte[] header = input.readNBytes(77);
                assertEquals(77, header.length);
                assertEquals(linear ? 0 : 16,
                        Short.toUnsignedInt(ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getShort(21)) & 16);
            }
        }
    }

    @Test
    void javaDecoderMatchesOfficialBasisuRgbaUnpackForKtx2AndBasis() throws Exception {
        Path inputPng = tempDir.resolve("checker.png");
        writeCheckerPng(inputPng);

        assertReferenceParity(inputPng, tempDir.resolve("checker-etc1s.ktx2"), List.of("-ktx2"));
        assertReferenceParity(inputPng, tempDir.resolve("checker-uastc.ktx2"), List.of("-ktx2", "-uastc"));
        assertReferenceParity(inputPng, tempDir.resolve("checker-etc1s.basis"), List.of("-basis"));
        assertReferenceParity(inputPng, tempDir.resolve("checker-uastc.basis"), List.of("-basis", "-uastc"));
    }

    @Test
    void javaDecoderMatchesOfficialBasisuRgbaUnpackForBroaderLdrCorpus() throws Exception {
        for (int i = 0; i < 4; i++) {
            Path inputPng = tempDir.resolve("corpus-" + i + ".png");
            writeCheckerPng(inputPng, 41 + i * 37, false);
            assertReferenceParity(inputPng, tempDir.resolve("corpus-" + i + "-etc1s.ktx2"), List.of("-ktx2"));
            assertReferenceParity(inputPng, tempDir.resolve("corpus-" + i + "-uastc.ktx2"), List.of("-ktx2", "-uastc"));
            assertReferenceParity(inputPng, tempDir.resolve("corpus-" + i + "-etc1s.basis"), List.of("-basis"));
            assertReferenceParity(inputPng, tempDir.resolve("corpus-" + i + "-uastc.basis"), List.of("-basis", "-uastc"));
        }
    }

    @Test
    void javaHdrAstcTargetsMatchOfficialBasisuKtxPayloadsForKtx2AndBasis() throws Exception {
        Path inputPng = tempDir.resolve("checker-hdr.png");
        writeCheckerPng(inputPng, 89);

        assertReferenceKtxPayloadParity(
                inputPng,
                tempDir.resolve("checker-hdr4.ktx2"),
                List.of("-ktx2", "-hdr"),
                List.of(
                        BasisTranscodeTarget.ASTC_HDR_4X4,
                        BasisTranscodeTarget.BC6H,
                        BasisTranscodeTarget.RGB_HALF,
                        BasisTranscodeTarget.RGBA_HALF,
                        BasisTranscodeTarget.RGB_9E5));
        assertReferenceKtxPayloadParity(
                inputPng,
                tempDir.resolve("checker-hdr4.basis"),
                List.of("-basis", "-hdr"),
                List.of(
                        BasisTranscodeTarget.ASTC_HDR_4X4,
                        BasisTranscodeTarget.BC6H,
                        BasisTranscodeTarget.RGB_HALF,
                        BasisTranscodeTarget.RGBA_HALF,
                        BasisTranscodeTarget.RGB_9E5));
        assertReferenceKtxPayloadParity(
                inputPng,
                tempDir.resolve("checker-hdr6.ktx2"),
                List.of("-ktx2", "-hdr_6x6"),
                List.of(
                        BasisTranscodeTarget.ASTC_HDR_6X6,
                        BasisTranscodeTarget.BC6H,
                        BasisTranscodeTarget.RGB_HALF,
                        BasisTranscodeTarget.RGBA_HALF,
                        BasisTranscodeTarget.RGB_9E5));
        assertReferenceKtxPayloadParity(
                inputPng,
                tempDir.resolve("checker-hdr6.basis"),
                List.of("-basis", "-hdr_6x6"),
                List.of(
                        BasisTranscodeTarget.ASTC_HDR_6X6,
                        BasisTranscodeTarget.BC6H,
                        BasisTranscodeTarget.RGB_HALF,
                        BasisTranscodeTarget.RGBA_HALF,
                        BasisTranscodeTarget.RGB_9E5));
        assertReferenceKtxPayloadParity(
                inputPng,
                tempDir.resolve("checker-hdr6i.ktx2"),
                List.of("-ktx2", "-hdr_6x6i"),
                List.of(
                        BasisTranscodeTarget.ASTC_HDR_6X6,
                        BasisTranscodeTarget.BC6H,
                        BasisTranscodeTarget.RGB_HALF,
                        BasisTranscodeTarget.RGBA_HALF,
                        BasisTranscodeTarget.RGB_9E5));
        assertReferenceKtxPayloadParity(
                inputPng,
                tempDir.resolve("checker-hdr6i.basis"),
                List.of("-basis", "-hdr_6x6i"),
                List.of(
                        BasisTranscodeTarget.ASTC_HDR_6X6,
                        BasisTranscodeTarget.BC6H,
                        BasisTranscodeTarget.RGB_HALF,
                        BasisTranscodeTarget.RGBA_HALF,
                        BasisTranscodeTarget.RGB_9E5));
    }

    @Test
    void javaHdrAstcTargetsMatchOfficialBasisuKtxPayloadsForBroaderHdrCorpus() throws Exception {
        int[][] corpus = {
            {7, 5, 173},
            {19, 11, 211}
        };
        for (int[] entry : corpus) {
            int width = entry[0];
            int height = entry[1];
            int variant = entry[2];
            Path inputPng = tempDir.resolve("checker-hdr-" + width + "x" + height + ".png");
            writeCheckerPng(inputPng, variant, false, width, height);

            assertReferenceKtxPayloadParity(
                    inputPng,
                    tempDir.resolve("checker-hdr4-" + width + "x" + height + ".ktx2"),
                    List.of("-ktx2", "-hdr"),
                    List.of(
                            BasisTranscodeTarget.ASTC_HDR_4X4,
                            BasisTranscodeTarget.BC6H,
                            BasisTranscodeTarget.RGB_HALF,
                            BasisTranscodeTarget.RGBA_HALF,
                            BasisTranscodeTarget.RGB_9E5));
            assertReferenceKtxPayloadParity(
                    inputPng,
                    tempDir.resolve("checker-hdr6-" + width + "x" + height + ".basis"),
                    List.of("-basis", "-hdr_6x6"),
                    List.of(
                            BasisTranscodeTarget.ASTC_HDR_6X6,
                            BasisTranscodeTarget.BC6H));
            assertReferenceKtxPayloadParity(
                    inputPng,
                    tempDir.resolve("checker-hdr6i-" + width + "x" + height + ".ktx2"),
                    List.of("-ktx2", "-hdr_6x6i"),
                    List.of(
                            BasisTranscodeTarget.ASTC_HDR_6X6,
                            BasisTranscodeTarget.BC6H));
            assertReferenceKtxPayloadParity(
                    inputPng,
                    tempDir.resolve("checker-hdr6i-" + width + "x" + height + ".basis"),
                    List.of("-basis", "-hdr_6x6i"),
                    List.of(
                            BasisTranscodeTarget.ASTC_HDR_6X6,
                            BasisTranscodeTarget.BC6H,
                            BasisTranscodeTarget.RGB_HALF,
                            BasisTranscodeTarget.RGBA_HALF,
                            BasisTranscodeTarget.RGB_9E5));
        }
    }

    @Test
    void javaHdrSixBySixBc6hHighQualityMatchesOfficialBasisuForKtx2AndBasis() throws Exception {
        Path inputPng = tempDir.resolve("checker-hdr6-hq.png");
        writeCheckerPng(inputPng, 251, true, 19, 11);

        assertReferenceKtxPayloadParity(
                inputPng,
                tempDir.resolve("checker-hdr6-hq.ktx2"),
                List.of("-ktx2", "-hdr_6x6"),
                BasisTranscodeTarget.BC6H,
                List.of(Ktx2DecodeFlag.cDecodeFlagsHighQuality),
                List.of("-higher_quality_transcoding"));
        assertReferenceKtxPayloadParity(
                inputPng,
                tempDir.resolve("checker-hdr6-hq.basis"),
                List.of("-basis", "-hdr_6x6"),
                BasisTranscodeTarget.BC6H,
                List.of(Ktx2DecodeFlag.cDecodeFlagsHighQuality),
                List.of("-higher_quality_transcoding"));
        assertReferenceKtxPayloadParity(
                inputPng,
                tempDir.resolve("checker-hdr6i-hq.ktx2"),
                List.of("-ktx2", "-hdr_6x6i"),
                BasisTranscodeTarget.BC6H,
                List.of(Ktx2DecodeFlag.cDecodeFlagsHighQuality),
                List.of("-higher_quality_transcoding"));
        assertReferenceKtxPayloadParity(
                inputPng,
                tempDir.resolve("checker-hdr6i-hq.basis"),
                List.of("-basis", "-hdr_6x6i"),
                BasisTranscodeTarget.BC6H,
                List.of(Ktx2DecodeFlag.cDecodeFlagsHighQuality),
                List.of("-higher_quality_transcoding"));
    }

    @Test
    void javaHdrSixBySixIntermediateLegacyBasislzKtx2MatchesOfficialBasisu() throws Exception {
        Path inputPng = tempDir.resolve("checker-hdr6i-legacy.png");
        Path encodedOutput = tempDir.resolve("checker-hdr6i-legacy.ktx2");
        writeCheckerPng(inputPng, 29, true, 19, 11);

        List<String> encodeCommand = new ArrayList<>();
        encodeCommand.add(basisuExecutable().toString());
        encodeCommand.add("-quiet");
        encodeCommand.add("-ktx2");
        encodeCommand.add("-hdr_6x6i");
        encodeCommand.add("-hdr_6x6i_16_compatibility");
        encodeCommand.add("-output_file");
        encodeCommand.add(encodedOutput.toString());
        encodeCommand.add(inputPng.toString());
        run(encodeCommand);
        assertTrue(Files.isRegularFile(encodedOutput), "basisu did not create " + encodedOutput);

        byte[] encodedBytes = rewriteHdr6x6iKtx2ToLegacyBasislz(Files.readAllBytes(encodedOutput));
        Files.write(encodedOutput, encodedBytes);
        assertEquals(
                Ktx2SupercompressionScheme.BASISLZ,
                Ktx2Container.parse(encodedBytes).getHeader().getSupercompressionScheme());
        assertReferenceKtxPayloadParity(
                encodedOutput,
                encodedBytes,
                List.of(
                        BasisTranscodeTarget.ASTC_HDR_6X6,
                        BasisTranscodeTarget.BC6H,
                        BasisTranscodeTarget.RGB_HALF,
                        BasisTranscodeTarget.RGBA_HALF,
                        BasisTranscodeTarget.RGB_9E5));
    }

    @Test
    void javaPackedPixelTargetsMatchOfficialBasisuRgbaUnpackForKtx2AndBasis() throws Exception {
        Path inputPng = tempDir.resolve("checker-packed.png");
        writeCheckerPng(inputPng, 91);

        assertReferencePackedParity(inputPng, tempDir.resolve("checker-packed-etc1s.ktx2"), List.of("-ktx2"));
        assertReferencePackedParity(inputPng, tempDir.resolve("checker-packed-uastc.ktx2"), List.of("-ktx2", "-uastc"));
        assertReferencePackedParity(inputPng, tempDir.resolve("checker-packed-etc1s.basis"), List.of("-basis"));
        assertReferencePackedParity(inputPng, tempDir.resolve("checker-packed-uastc.basis"), List.of("-basis", "-uastc"));
    }

    @Test
    void javaEtc1sEacTargetsMatchOfficialBasisuKtxPayloadsForKtx2AndBasis() throws Exception {
        Path inputPng = tempDir.resolve("checker-eac-alpha.png");
        writeCheckerPng(inputPng, 123, true);

        assertReferenceKtxPayloadParity(inputPng, tempDir.resolve("checker-eac.ktx2"), List.of("-ktx2"));
        assertReferenceKtxPayloadParity(inputPng, tempDir.resolve("checker-eac.basis"), List.of("-basis"));
        assertReferenceKtxPayloadParity(inputPng, tempDir.resolve("checker-eac-uastc.ktx2"), List.of("-ktx2", "-uastc"));
        assertReferenceKtxPayloadParity(inputPng, tempDir.resolve("checker-eac-uastc.basis"), List.of("-basis", "-uastc"));
    }

    @Test
    void javaXuastcEacTargetsMatchOfficialBasisuKtxPayloadsForFixture() throws Exception {
        assertReferenceKtxPayloadParity(encodedFixture("fixtures/ktx2/base_xuastc_zstd.ktx2"));
    }

    @Test
    void javaXuastcEtcColorTargetsMatchOfficialBasisuKtxPayloadsForFixture() throws Exception {
        assertReferenceKtxPayloadParity(
                encodedFixture("fixtures/ktx2/base_xuastc_zstd.ktx2"),
                List.of(BasisTranscodeTarget.ETC1, BasisTranscodeTarget.ETC2));
    }

    @Test
    void javaUastcEtcColorTargetsMatchOfficialBasisuKtxPayloads() throws Exception {
        Path inputPng = tempDir.resolve("checker-etc-color-alpha.png");
        writeCheckerPng(inputPng, 151, true);

        List<BasisTranscodeTarget> etcTargets = List.of(
                BasisTranscodeTarget.ETC1,
                BasisTranscodeTarget.ETC2);
        assertReferenceKtxPayloadParity(
                inputPng,
                tempDir.resolve("checker-etc-color-uastc.ktx2"),
                List.of("-ktx2", "-uastc"),
                etcTargets);
        assertReferenceKtxPayloadParity(
                inputPng,
                tempDir.resolve("checker-etc-color-uastc.basis"),
                List.of("-basis", "-uastc"),
                etcTargets);
    }

    @Test
    void javaUastcAndXuastcBc1Bc3Bc4Bc5TargetsMatchOfficialBasisuKtxPayloads() throws Exception {
        Path inputPng = tempDir.resolve("checker-bc45-alpha.png");
        writeCheckerPng(inputPng, 207, true);

        List<BasisTranscodeTarget> bcTargets = List.of(
                BasisTranscodeTarget.BC1,
                BasisTranscodeTarget.BC3,
                BasisTranscodeTarget.BC4,
                BasisTranscodeTarget.BC5);
        assertReferenceKtxPayloadParity(
                inputPng,
                tempDir.resolve("checker-bc45-uastc.ktx2"),
                List.of("-ktx2", "-uastc"),
                bcTargets);
        assertReferenceKtxPayloadParity(
                inputPng,
                tempDir.resolve("checker-bc45-uastc.basis"),
                List.of("-basis", "-uastc"),
                bcTargets);
        assertReferenceKtxPayloadParity(
                encodedFixture("fixtures/ktx2/base_xuastc_zstd.ktx2"),
                bcTargets);
    }

    @Test
    void javaUastcBc7TargetMatchesOfficialBasisuKtxPayloads() throws Exception {
        Path inputPng = tempDir.resolve("checker-bc7-alpha.png");
        writeCheckerPng(inputPng, 219, true);

        List<BasisTranscodeTarget> bc7Target = List.of(BasisTranscodeTarget.BC7);
        assertReferenceKtxPayloadParity(
                inputPng,
                tempDir.resolve("checker-bc7-uastc.ktx2"),
                List.of("-ktx2", "-uastc"),
                bc7Target);
        assertReferenceKtxPayloadParity(
                inputPng,
                tempDir.resolve("checker-bc7-uastc.basis"),
                List.of("-basis", "-uastc"),
                bc7Target);
    }

    @Test
    void javaXuastcBc7Generic4x4TargetMatchesOfficialBasisuNoFastKtxPayloads() throws Exception {
        Path inputPng = tempDir.resolve("checker-xuastc-bc7-no-fast.png");
        writeCheckerPng(inputPng, 231);
        assertXuastcBc7NoFastGeneratedParity(inputPng, tempDir.resolve("checker-xuastc-bc7-no-fast.ktx2"));
    }

    @Test
    void javaXuastcBc7Generic4x4AlphaTargetMatchesOfficialBasisuNoFastKtxPayloads() throws Exception {
        Path inputPng = tempDir.resolve("checker-alpha-xuastc-bc7-no-fast.png");
        writeCheckerPng(inputPng, 231, true);
        assertXuastcBc7NoFastGeneratedParity(inputPng, tempDir.resolve("checker-alpha-xuastc-bc7-no-fast.ktx2"));
    }

    @Test
    void javaXuastcBc7GenericTargetMatchesOfficialBasisuNoFastNon4x4Fixture() throws Exception {
        assertXuastcBc7FixtureParity(
                encodedFixture("fixtures/ktx2/base_xuastc_zstd.ktx2"),
                List.of(Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding),
                List.of("-no_fast_xuastc_ldr_bc7_transcoding"));
    }

    @Test
    void javaXuastcBc7Generic5x5TargetMatchesOfficialBasisuNoFastFixture() throws Exception {
        assertXuastcBc7FixtureParity(
                encodedFixture("fixtures/ktx2/tough_xuastc5x5_zstd.ktx2"),
                List.of(Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding),
                List.of("-no_fast_xuastc_ldr_bc7_transcoding"));
    }

    @Test
    void javaXuastcBc7Generic6x5TargetMatchesOfficialBasisuNoFastFixture() throws Exception {
        assertXuastcBc7FixtureParity(
                encodedFixture("fixtures/ktx2/tough_xuastc6x5_zstd.ktx2"),
                List.of(Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding),
                List.of("-no_fast_xuastc_ldr_bc7_transcoding"));
    }

    @Test
    void javaXuastcBc7Generic6x6TargetMatchesOfficialBasisuNoFastFixture() throws Exception {
        assertXuastcBc7FixtureParity(
                encodedFixture("fixtures/ktx2/tough_xuastc6x6_zstd.ktx2"),
                List.of(Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding),
                List.of("-no_fast_xuastc_ldr_bc7_transcoding"));
    }

    @Test
    void javaXuastcBc7Generic8x5TargetMatchesOfficialBasisuNoFastFixture() throws Exception {
        assertXuastcBc7FixtureParity(
                encodedFixture("fixtures/ktx2/tough_xuastc8x5_zstd.ktx2"),
                List.of(Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding),
                List.of("-no_fast_xuastc_ldr_bc7_transcoding"));
    }

    @Test
    void javaXuastcBc7Generic8x6TargetMatchesOfficialBasisuNoFastFixture() throws Exception {
        assertXuastcBc7FixtureParity(
                encodedFixture("fixtures/ktx2/tough_xuastc8x6_zstd.ktx2"),
                List.of(Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding),
                List.of("-no_fast_xuastc_ldr_bc7_transcoding"));
    }

    @Test
    void javaXuastcBc7Default5x4TargetMatchesOfficialBasisuFixture() throws Exception {
        assertXuastcBc7FixtureParity(
                encodedFixture("fixtures/ktx2/base_xuastc_zstd.ktx2"),
                List.of(),
                List.of());
    }

    @Test
    void javaXuastcBc7Default5x5TargetMatchesOfficialBasisuFixture() throws Exception {
        assertXuastcBc7FixtureParity(
                encodedFixture("fixtures/ktx2/tough_xuastc5x5_zstd.ktx2"),
                List.of(),
                List.of());
    }

    @Test
    void javaXuastcBc7Default6x5TargetMatchesOfficialBasisuFixture() throws Exception {
        assertXuastcBc7FixtureParity(
                encodedFixture("fixtures/ktx2/tough_xuastc6x5_zstd.ktx2"),
                List.of(),
                List.of());
    }

    @Test
    void javaXuastcBc7Default8x5TargetMatchesOfficialBasisuFixture() throws Exception {
        assertXuastcBc7FixtureParity(
                encodedFixture("fixtures/ktx2/tough_xuastc8x5_zstd.ktx2"),
                List.of(),
                List.of());
    }

    @Test
    void javaXuastcBc7NoDeblock8x8TargetMatchesOfficialBasisuFixture() throws Exception {
        assertXuastcBc7NoDeblockFixtureParity("fixtures/ktx2/tough_xuastc8x8_zstd.ktx2");
    }

    @Test
    void javaXuastcBc7NoDeblock10x5TargetMatchesOfficialBasisuFixture() throws Exception {
        assertXuastcBc7NoDeblockFixtureParity("fixtures/ktx2/tough_xuastc10x5_zstd.ktx2");
    }

    @Test
    void javaXuastcBc7NoDeblock10x6TargetMatchesOfficialBasisuFixture() throws Exception {
        assertXuastcBc7NoDeblockFixtureParity("fixtures/ktx2/tough_xuastc10x6_zstd.ktx2");
    }

    @Test
    void javaXuastcBc7NoDeblock10x8TargetMatchesOfficialBasisuFixture() throws Exception {
        assertXuastcBc7NoDeblockFixtureParity("fixtures/ktx2/tough_xuastc10x8_zstd.ktx2");
    }

    @Test
    void javaXuastcBc7NoDeblock10x10TargetMatchesOfficialBasisuFixture() throws Exception {
        assertXuastcBc7NoDeblockFixtureParity("fixtures/ktx2/tough_xuastc10x10_zstd.ktx2");
    }

    @Test
    void javaXuastcBc7NoDeblock12x10TargetMatchesOfficialBasisuFixture() throws Exception {
        assertXuastcBc7NoDeblockFixtureParity("fixtures/ktx2/tough_xuastc12x10_zstd.ktx2");
    }

    @Test
    void javaXuastcBc7NoDeblock12x12TargetMatchesOfficialBasisuFixture() throws Exception {
        assertXuastcBc7NoDeblockFixtureParity("fixtures/ktx2/tough_xuastc12x12_zstd.ktx2");
    }

    @Test
    void javaXuastcBc7DefaultDeblocked10x6TargetMatchesOfficialBasisuFixture() throws Exception {
        assertXuastcBc7FixtureParity(
                encodedFixture("fixtures/ktx2/tough_xuastc10x6_zstd.ktx2"),
                List.of(),
                List.of());
    }

    @Test
    void javaXuastcBc7HighQuality4x4TargetMatchesOfficialBasisuFixture() throws Exception {
        assertXuastcBc7FixtureParity(
                encodedFixture("fixtures/ktx2/tough_xuastc4x4_zstd.ktx2"),
                List.of(Ktx2DecodeFlag.cDecodeFlagsHighQuality),
                List.of("-higher_quality_transcoding"));
    }

    @Test
    void javaXuastcBc7HighQualityDeblocked10x6TargetMatchesOfficialBasisuFixture() throws Exception {
        assertXuastcBc7FixtureParity(
                encodedFixture("fixtures/ktx2/tough_xuastc10x6_zstd.ktx2"),
                List.of(Ktx2DecodeFlag.cDecodeFlagsHighQuality),
                List.of("-higher_quality_transcoding"));
    }

    @Test
    void javaXuastcBc7ForceDeblocked6x6TargetMatchesOfficialBasisuFixture() throws Exception {
        assertXuastcBc7FixtureParity(
                encodedFixture("fixtures/ktx2/tough_xuastc6x6_zstd.ktx2"),
                List.of(Ktx2DecodeFlag.cDecodeFlagsForceDeblockFiltering),
                List.of("-force_deblocking"));
    }

    @Test
    void javaXuastcBc7StrongerDeblocked10x6TargetMatchesOfficialBasisuFixture() throws Exception {
        assertXuastcBc7FixtureParity(
                encodedFixture("fixtures/ktx2/tough_xuastc10x6_zstd.ktx2"),
                List.of(Ktx2DecodeFlag.cDecodeFlagsStrongerDeblockFiltering),
                List.of("-stronger_deblocking"));
    }

    private void assertXuastcBc7NoDeblockFixtureParity(String fixtureName) throws Exception {
        assertXuastcBc7FixtureParity(
                encodedFixture(fixtureName),
                List.of(Ktx2DecodeFlag.cDecodeFlagsNoDeblockFiltering),
                List.of("-no_deblocking"));
    }

    private void assertXuastcBc7FixtureParity(
            Path encodedOutput,
            List<Ktx2DecodeFlag> javaDecodeFlags,
            List<String> officialTranscodeFlags)
            throws Exception {
        Path officialDir = Files.createDirectories(tempDir.resolve(
                encodedOutput.getFileName() + "-BC7-xuastc-fixture-official-ktx"));
        List<String> command = new ArrayList<>();
        command.add(basisuExecutable().toString());
        command.add("-quiet");
        command.addAll(officialTranscodeFlags);
        command.add("-unpack");
        command.add("-ktx_only");
        command.add("-format_only");
        command.add(Integer.toString(formatCode(BasisTranscodeTarget.BC7)));
        command.add(encodedOutput.toString());
        run(command, officialDir);

        BasisDecodeResult javaResult = BasisDecoderFactory.createDefault().decode(
                BasisDecodeRequest.builder(Files.readAllBytes(encodedOutput))
                        .target(BasisTranscodeTarget.BC7)
                        .decodeFlags(javaDecodeFlags.toArray(Ktx2DecodeFlag[]::new))
                        .build());

        assertEquals(BasisImageFormat.BC7, javaResult.getImageFormat());
        assertArrayEqualsWithFirstDiff(
                readFirstKtxLevelPayload(newestKtx(officialDir)),
                readBuffer(javaResult.getPixelData()));
    }

    private void assertXuastcBc7NoFastGeneratedParity(Path inputPng, Path encodedOutput) throws Exception {
        List<String> encodeCommand = new ArrayList<>();
        encodeCommand.add(basisuExecutable().toString());
        encodeCommand.add("-quiet");
        encodeCommand.add("-ktx2");
        encodeCommand.add("-xuastc_ldr_4x4");
        encodeCommand.add("-xuastc_zstd");
        encodeCommand.add("-output_file");
        encodeCommand.add(encodedOutput.toString());
        encodeCommand.add(inputPng.toString());
        run(encodeCommand);
        assertTrue(Files.isRegularFile(encodedOutput), "basisu did not create " + encodedOutput);

        assertXuastcBc7FixtureParity(
                encodedOutput,
                List.of(Ktx2DecodeFlag.cDecodeFlagXUASTCLDRDisableFastBC7Transcoding),
                List.of("-no_fast_xuastc_ldr_bc7_transcoding"));
    }

    @Test
    void javaXuastcBc7Fast4x4TargetMatchesOfficialBasisuKtxPayloads() throws Exception {
        Path inputPng = tempDir.resolve("checker-xuastc-bc7-fast.png");
        writeCheckerPng(inputPng, 73);

        Path encodedOutput = tempDir.resolve("checker-xuastc-bc7-fast.ktx2");
        List<String> encodeCommand = new ArrayList<>();
        encodeCommand.add(basisuExecutable().toString());
        encodeCommand.add("-quiet");
        encodeCommand.add("-ktx2");
        encodeCommand.add("-xuastc_ldr_4x4");
        encodeCommand.add("-xuastc_zstd");
        encodeCommand.add("-output_file");
        encodeCommand.add(encodedOutput.toString());
        encodeCommand.add(inputPng.toString());
        run(encodeCommand);
        assertTrue(Files.isRegularFile(encodedOutput), "basisu did not create " + encodedOutput);

        Path officialDir = Files.createDirectories(tempDir.resolve(
                encodedOutput.getFileName() + "-BC7-fast-official-ktx"));
        run(List.of(
                basisuExecutable().toString(),
                "-quiet",
                "-unpack",
                "-ktx_only",
                "-format_only",
                Integer.toString(formatCode(BasisTranscodeTarget.BC7)),
                encodedOutput.toString()),
                officialDir);

        BasisDecodeResult javaResult = BasisDecoderFactory.createDefault().decode(
                BasisDecodeRequest.builder(Files.readAllBytes(encodedOutput))
                        .target(BasisTranscodeTarget.BC7)
                        .build());

        assertEquals(BasisImageFormat.BC7, javaResult.getImageFormat());
        assertArrayEqualsWithFirstDiff(
                readFirstKtxLevelPayload(newestKtx(officialDir)),
                readBuffer(javaResult.getPixelData()));
    }

    @Test
    void javaXuastcBc7Fast6x6TargetMatchesOfficialBasisuFixture() throws Exception {
        assertXuastcBc7FixtureParity(
                encodedFixture("fixtures/ktx2/tough_xuastc6x6_zstd.ktx2"),
                List.of(),
                List.of());
    }

    @Test
    void javaXuastcBc7Fast8x6TargetMatchesOfficialBasisuFixture() throws Exception {
        assertXuastcBc7FixtureParity(
                encodedFixture("fixtures/ktx2/tough_xuastc8x6_zstd.ktx2"),
                List.of(),
                List.of());
    }

    @Test
    void javaDecoderMatchesOfficialBasisuForSelectedKtx2UastcArrayImage() throws Exception {
        Path first = tempDir.resolve("checker-array-0.png");
        Path second = tempDir.resolve("checker-array-1.png");
        writeCheckerPng(first, 0);
        writeCheckerPng(second, 37);

        assertReferenceParity(
                List.of(first, second),
                1,
                tempDir.resolve("checker-array-uastc.ktx2"),
                List.of("-ktx2", "-uastc", "-tex_array"));
    }

    private void assertReferenceParity(Path inputPng, Path encodedOutput, List<String> encodeMode) throws Exception {
        assertReferenceParity(List.of(inputPng), 0, encodedOutput, encodeMode);
    }

    private void assertReferenceParity(
            List<Path> inputPngs,
            int imageIndex,
            Path encodedOutput,
            List<String> encodeMode) throws Exception {
        Path officialDir = Files.createDirectories(tempDir.resolve(encodedOutput.getFileName() + "-official"));
        List<String> encodeCommand = new ArrayList<>();
        encodeCommand.add(basisuExecutable().toString());
        encodeCommand.add("-quiet");
        encodeCommand.addAll(encodeMode);
        encodeCommand.add("-output_file");
        encodeCommand.add(encodedOutput.toString());
        for (Path inputPng : inputPngs) {
            encodeCommand.add(inputPng.toString());
        }
        run(encodeCommand);
        assertTrue(Files.isRegularFile(encodedOutput), "basisu did not create " + encodedOutput);

        run(List.of(
                basisuExecutable().toString(),
                "-quiet",
                "-unpack",
                "-no_ktx",
                encodedOutput.toString()),
                officialDir);

        Path officialPng = newestPng(officialDir, imageIndex);
        byte[] officialRgba = readRgba(officialPng);
        byte[] encodedBytes = Files.readAllBytes(encodedOutput);

        BasisDecodeResult javaResult = BasisDecoderFactory.createDefault().decode(
                BasisDecodeRequest.builder(encodedBytes)
                        .target(BasisTranscodeTarget.RGBA8)
                        .imageIndex(imageIndex)
                        .build());

        assertEquals(BasisImageFormat.RGBA8, javaResult.getImageFormat());
        assertEquals(16, javaResult.getWidth());
        assertEquals(16, javaResult.getHeight());
        assertArrayEquals(officialRgba, readBuffer(javaResult.getPixelData()));
    }

    private void assertReferencePackedParity(Path inputPng, Path encodedOutput, List<String> encodeMode)
            throws Exception {
        Path officialDir = Files.createDirectories(tempDir.resolve(encodedOutput.getFileName() + "-official-packed"));
        List<String> encodeCommand = new ArrayList<>();
        encodeCommand.add(basisuExecutable().toString());
        encodeCommand.add("-quiet");
        encodeCommand.addAll(encodeMode);
        encodeCommand.add("-output_file");
        encodeCommand.add(encodedOutput.toString());
        encodeCommand.add(inputPng.toString());
        run(encodeCommand);
        assertTrue(Files.isRegularFile(encodedOutput), "basisu did not create " + encodedOutput);

        run(List.of(
                basisuExecutable().toString(),
                "-quiet",
                "-unpack",
                "-no_ktx",
                encodedOutput.toString()),
                officialDir);

        byte[] officialRgba = readRgba(newestPng(officialDir, 0));
        byte[] encodedBytes = Files.readAllBytes(encodedOutput);
        for (BasisTranscodeTarget target : List.of(
                BasisTranscodeTarget.RGB565,
                BasisTranscodeTarget.BGR565,
                BasisTranscodeTarget.RGBA4444)) {
            BasisDecodeResult javaResult = BasisDecoderFactory.createDefault().decode(
                    BasisDecodeRequest.builder(encodedBytes)
                            .target(target)
                            .build());

            assertEquals(target.getImageFormat(), javaResult.getImageFormat(), target.name());
            assertEquals(16, javaResult.getWidth(), target.name());
            assertEquals(16, javaResult.getHeight(), target.name());
            assertArrayEquals(packRgba(officialRgba, target), readBuffer(javaResult.getPixelData()), target.name());
        }
    }

    private void assertReferenceKtxPayloadParity(Path inputPng, Path encodedOutput, List<String> encodeMode)
            throws Exception {
        assertReferenceKtxPayloadParity(
                inputPng,
                encodedOutput,
                encodeMode,
                List.of(BasisTranscodeTarget.ETC2_EAC_R11, BasisTranscodeTarget.ETC2_EAC_RG11));
    }

    private void assertReferenceKtxPayloadParity(
            Path inputPng,
            Path encodedOutput,
            List<String> encodeMode,
            List<BasisTranscodeTarget> targets)
            throws Exception {
        List<String> encodeCommand = new ArrayList<>();
        encodeCommand.add(basisuExecutable().toString());
        encodeCommand.add("-quiet");
        encodeCommand.addAll(encodeMode);
        encodeCommand.add("-output_file");
        encodeCommand.add(encodedOutput.toString());
        encodeCommand.add(inputPng.toString());
        run(encodeCommand);
        assertTrue(Files.isRegularFile(encodedOutput), "basisu did not create " + encodedOutput);

        byte[] encodedBytes = Files.readAllBytes(encodedOutput);
        assertReferenceKtxPayloadParity(encodedOutput, encodedBytes, targets);
    }

    private void assertReferenceKtxPayloadParity(
            Path inputPng,
            Path encodedOutput,
            List<String> encodeMode,
            BasisTranscodeTarget target,
            List<Ktx2DecodeFlag> javaDecodeFlags,
            List<String> officialTranscodeFlags)
            throws Exception {
        List<String> encodeCommand = new ArrayList<>();
        encodeCommand.add(basisuExecutable().toString());
        encodeCommand.add("-quiet");
        encodeCommand.addAll(encodeMode);
        encodeCommand.add("-output_file");
        encodeCommand.add(encodedOutput.toString());
        encodeCommand.add(inputPng.toString());
        run(encodeCommand);
        assertTrue(Files.isRegularFile(encodedOutput), "basisu did not create " + encodedOutput);

        byte[] encodedBytes = Files.readAllBytes(encodedOutput);
        BasisDecodeResult javaResult = BasisDecoderFactory.createDefault().decode(
                BasisDecodeRequest.builder(encodedBytes)
                        .target(target)
                        .decodeFlags(javaDecodeFlags.toArray(Ktx2DecodeFlag[]::new))
                        .build());

        Path officialDir = Files.createDirectories(tempDir.resolve(
                encodedOutput.getFileName() + "-" + target.name() + "-official-ktx-flags"));
        List<String> officialCommand = new ArrayList<>();
        officialCommand.add(basisuExecutable().toString());
        officialCommand.add("-quiet");
        officialCommand.addAll(officialTranscodeFlags);
        officialCommand.add("-unpack");
        officialCommand.add("-ktx_only");
        officialCommand.add("-format_only");
        officialCommand.add(Integer.toString(formatCode(target)));
        officialCommand.add(encodedOutput.toString());
        run(officialCommand, officialDir);

        assertEquals(target.getImageFormat(), javaResult.getImageFormat(), encodedOutput.getFileName().toString());
        assertArrayEqualsWithFirstDiff(
                readFirstKtxLevelPayload(newestKtx(officialDir)),
                readBuffer(javaResult.getPixelData()));
    }

    private void assertReferenceKtxPayloadParity(Path encodedOutput) throws Exception {
        assertReferenceKtxPayloadParity(
                encodedOutput,
                Files.readAllBytes(encodedOutput),
                List.of(BasisTranscodeTarget.ETC2_EAC_R11, BasisTranscodeTarget.ETC2_EAC_RG11));
    }

    private void assertReferenceKtxPayloadParity(Path encodedOutput, List<BasisTranscodeTarget> targets)
            throws Exception {
        assertReferenceKtxPayloadParity(encodedOutput, Files.readAllBytes(encodedOutput), targets);
    }

    private void assertReferenceKtxPayloadParity(
            Path encodedOutput,
            byte[] encodedBytes,
            List<BasisTranscodeTarget> targets)
            throws Exception {
        for (BasisTranscodeTarget target : targets) {
            BasisDecodeResult javaResult = BasisDecoderFactory.createDefault().decode(
                    BasisDecodeRequest.builder(encodedBytes)
                            .target(target)
                            .build());

            String parityLabel = encodedOutput.getFileName() + " " + target.name();
            assertEquals(target.getImageFormat(), javaResult.getImageFormat(), parityLabel);
            byte[] officialPayload;
            if (isHdrCpuTarget(target)) {
                officialPayload = runRawTranscodeHarness(encodedOutput, target);
            } else {
                Path officialDir = Files.createDirectories(tempDir.resolve(
                        encodedOutput.getFileName() + "-" + target.name() + "-official-ktx"));
                run(List.of(
                        basisuExecutable().toString(),
                        "-quiet",
                        "-unpack",
                        "-ktx_only",
                        "-format_only",
                        Integer.toString(formatCode(target)),
                        encodedOutput.toString()),
                        officialDir);
                officialPayload = readFirstKtxLevelPayload(newestKtx(officialDir));
            }
            assertArrayEqualsWithFirstDiff(
                    parityLabel,
                    officialPayload,
                    readBuffer(javaResult.getPixelData()));
        }
    }

    private static Path encodedFixture(String resourcePath) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            Path fixture = candidate.resolve("decoder/src/test/resources").resolve(resourcePath);
            if (Files.isRegularFile(fixture)) {
                return fixture;
            }
        }
        throw new AssertionError("Fixture not found: " + resourcePath);
    }

    private static int formatCode(BasisTranscodeTarget target) {
        if (target == BasisTranscodeTarget.ETC1) {
            return 0;
        }
        if (target == BasisTranscodeTarget.ETC2) {
            return 1;
        }
        if (target == BasisTranscodeTarget.BC1) {
            return 2;
        }
        if (target == BasisTranscodeTarget.BC3) {
            return 3;
        }
        if (target == BasisTranscodeTarget.BC4) {
            return 4;
        }
        if (target == BasisTranscodeTarget.BC5) {
            return 5;
        }
        if (target == BasisTranscodeTarget.BC7) {
            return 6;
        }
        if (target == BasisTranscodeTarget.BC6H) {
            return 22;
        }
        if (target == BasisTranscodeTarget.ASTC_HDR_4X4) {
            return 23;
        }
        if (target == BasisTranscodeTarget.RGB_HALF) {
            return 24;
        }
        if (target == BasisTranscodeTarget.RGBA_HALF) {
            return 25;
        }
        if (target == BasisTranscodeTarget.RGB_9E5) {
            return 26;
        }
        if (target == BasisTranscodeTarget.ASTC_HDR_6X6) {
            return 27;
        }
        if (target == BasisTranscodeTarget.ETC2_EAC_R11) {
            return 20;
        }
        if (target == BasisTranscodeTarget.ETC2_EAC_RG11) {
            return 21;
        }
        throw new IllegalArgumentException("No reference format code for " + target);
    }

    private static Path basisuExecutable() {
        String configured = System.getProperty("basisu.executable");
        assertTrue(configured != null && !configured.isBlank(), "Missing basisu.executable system property");
        Path executable = Path.of(configured);
        assertTrue(Files.isExecutable(executable), "basisu executable is not available: " + executable);
        return executable;
    }

    private static Path basisuRawTranscodeExecutable() {
        String configured = System.getProperty("basisu.rawTranscode.executable");
        assertTrue(configured != null && !configured.isBlank(),
                "Missing basisu.rawTranscode.executable system property");
        Path executable = Path.of(configured);
        assertTrue(Files.isExecutable(executable), "basisu raw transcode executable is not available: " + executable);
        return executable;
    }

    private static byte[] runRawTranscodeHarness(Path encodedOutput, BasisTranscodeTarget target)
            throws IOException, InterruptedException {
        ProcessBuilder builder = new ProcessBuilder(
                basisuRawTranscodeExecutable().toString(),
                encodedOutput.toString(),
                Integer.toString(formatCode(target)));
        Path stderr = Files.createTempFile("basisu-raw-transcode", ".stderr");
        builder.redirectError(stderr.toFile());
        Process process = builder.start();
        byte[] stdout;
        try (var input = process.getInputStream()) {
            stdout = input.readAllBytes();
        }
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new AssertionError("basisu raw transcode failed with exit code " + exitCode
                    + ": " + Files.readString(stderr));
        }
        return stdout;
    }

    private static boolean isHdrCpuTarget(BasisTranscodeTarget target) {
        return target == BasisTranscodeTarget.RGB_HALF
                || target == BasisTranscodeTarget.RGBA_HALF
                || target == BasisTranscodeTarget.RGB_9E5;
    }

    private static byte[] rewriteHdr6x6iKtx2ToLegacyBasislz(byte[] source) {
        Ktx2Container container = Ktx2Container.parse(source);
        assertEquals(
                Ktx2SupercompressionScheme.UASTC_HDR_6X6I,
                container.getHeader().getSupercompressionScheme());

        long sgdOffsetLong = container.getHeader().getSgdByteOffset();
        long sgdLengthLong = container.getHeader().getSgdByteLength();
        int imageCount = Math.multiplyExact(
                Math.multiplyExact(Math.max((int) container.getHeader().getLayerCount(), 1),
                        container.getHeader().getFaceCount()),
                container.getHeader().getLevelCount());
        int stdSgdLength = Math.multiplyExact(imageCount, 12);
        int legacySgdLength = Math.multiplyExact(imageCount, 8);
        assertEquals(stdSgdLength, sgdLengthLong);

        int sgdOffset = Math.toIntExact(sgdOffsetLong);
        int sgdEnd = Math.toIntExact(sgdOffsetLong + sgdLengthLong);
        int removedBytes = Math.multiplyExact(imageCount, 4);
        byte[] rewritten = new byte[source.length - Math.multiplyExact(imageCount, 4)];
        System.arraycopy(source, 0, rewritten, 0, sgdOffset);
        ByteBuffer input = ByteBuffer.wrap(source, sgdOffset, stdSgdLength)
                .slice()
                .order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer output = ByteBuffer.wrap(rewritten, sgdOffset, legacySgdLength)
                .slice()
                .order(ByteOrder.LITTLE_ENDIAN);
        for (int i = 0; i < imageCount; i++) {
            output.putInt(input.getInt());
            output.putInt(input.getInt());
            input.getInt();
        }
        System.arraycopy(
                source,
                sgdEnd,
                rewritten,
                sgdOffset + legacySgdLength,
                source.length - sgdEnd);

        ByteBuffer header = ByteBuffer.wrap(rewritten).order(ByteOrder.LITTLE_ENDIAN)
                .putInt(44, Ktx2SupercompressionScheme.BASISLZ.getCode())
                .putLong(72, legacySgdLength);
        shiftUInt32Offset(header, 48, sgdEnd, removedBytes);
        shiftUInt32Offset(header, 56, sgdEnd, removedBytes);
        shiftUInt64Offset(header, 64, sgdEnd, removedBytes);
        for (int i = 0; i < container.getHeader().getLevelCount(); i++) {
            shiftUInt64Offset(header, 80 + i * 24, sgdEnd, removedBytes);
        }
        return rewritten;
    }

    private static void shiftUInt32Offset(ByteBuffer buffer, int position, int removedRangeEnd, int removedBytes) {
        long value = Integer.toUnsignedLong(buffer.getInt(position));
        if (value >= removedRangeEnd) {
            buffer.putInt(position, Math.toIntExact(value - removedBytes));
        }
    }

    private static void shiftUInt64Offset(ByteBuffer buffer, int position, int removedRangeEnd, int removedBytes) {
        long value = buffer.getLong(position);
        if (value >= removedRangeEnd) {
            buffer.putLong(position, value - removedBytes);
        }
    }

    private static void writeCheckerPng(Path output) throws IOException {
        writeCheckerPng(output, 0);
    }

    private static void writeCheckerPng(Path output, int variant) throws IOException {
        writeCheckerPng(output, variant, false);
    }

    private static void writeCheckerPng(Path output, int variant, boolean alphaPattern) throws IOException {
        writeCheckerPng(output, variant, alphaPattern, 16, 16);
    }

    private static void writeCheckerPng(
            Path output,
            int variant,
            boolean alphaPattern,
            int width,
            int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = alphaPattern ? 32 + ((x * 13 + y * 19 + variant) & 0xDF) : 255;
                int red = (x * 17 + variant) & 0xFF;
                int green = (y * 17 + variant * 3) & 0xFF;
                int blue = ((x ^ y) * 21 + variant * 5) & 0xFF;
                if (((x / 4) + (y / 4)) % 2 == 0) {
                    red = 255 - red;
                    green = 255 - green;
                }
                image.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        ImageIO.write(image, "PNG", output.toFile());
    }

    private static void writeLowVarianceAlphaPng(Path output, int variant) throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int delta = ((x * 3 + y * 5 + variant) & 3) - 1;
                int alpha = 132 + delta;
                int red = 96 + delta;
                int green = 127 - delta;
                int blue = 111 + ((x + y + variant) & 1);
                image.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        ImageIO.write(image, "PNG", output.toFile());
    }

    private static void writeAlphaBc7Png(Path output, int variant) throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int alpha = 32 + ((x * 47 + y * 67 + variant) & 0xDF);
                int red = (x * 53 + y * 11 + variant) & 0xFF;
                int green = (x * 17 + y * 61 + variant * 3) & 0xFF;
                int blue = ((x ^ y) * 71 + y * 19 + variant * 5) & 0xFF;
                image.setRGB(x, y, (alpha << 24) | (red << 16) | (green << 8) | blue);
            }
        }
        ImageIO.write(image, "PNG", output.toFile());
    }

    private static Path newestPng(Path directory, int imageIndex) throws IOException {
        String compactLayer = String.format(java.util.Locale.ROOT, "layer%04d", imageIndex);
        String separatedLayer = String.format(java.util.Locale.ROOT, "layer_%04d", imageIndex);
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".png"))
                    .filter(path -> path.getFileName().toString().contains("_rgb_RGBA32_"))
                    .filter(path -> {
                        if (imageIndex == 0) {
                            return true;
                        }
                        String name = path.getFileName().toString();
                        return name.contains(compactLayer) || name.contains(separatedLayer);
                    })
                    .max(Comparator.comparing(path -> path.toFile().lastModified()))
                    .orElseThrow(() -> new AssertionError("basisu did not unpack a PNG in " + directory));
        }
    }

    private static Path newestKtx(Path directory) throws IOException {
        try (var stream = Files.list(directory)) {
            return stream
                    .filter(path -> path.getFileName().toString().toLowerCase(java.util.Locale.ROOT).endsWith(".ktx"))
                    .max(Comparator.comparing(path -> path.toFile().lastModified()))
                    .orElseThrow(() -> new AssertionError("basisu did not unpack a KTX in " + directory));
        }
    }

    private static byte[] readRgba(Path png) throws IOException {
        BufferedImage image = ImageIO.read(png.toFile());
        ByteArrayOutputStream output = new ByteArrayOutputStream(image.getWidth() * image.getHeight() * 4);
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                int argb = image.getRGB(x, y);
                output.write((argb >>> 16) & 0xFF);
                output.write((argb >>> 8) & 0xFF);
                output.write(argb & 0xFF);
                output.write((argb >>> 24) & 0xFF);
            }
        }
        return output.toByteArray();
    }

    private static byte[] readBuffer(ByteBuffer buffer) {
        ByteBuffer copy = buffer.duplicate();
        byte[] data = new byte[copy.remaining()];
        copy.get(data);
        return data;
    }

    private static void assertArrayEqualsWithFirstDiff(byte[] expected, byte[] actual) {
        assertArrayEqualsWithFirstDiff(null, expected, actual);
    }

    private static void assertArrayEqualsWithFirstDiff(String label, byte[] expected, byte[] actual) {
        if (expected.length == actual.length) {
            for (int i = 0; i < expected.length; i++) {
                if (expected[i] != actual[i]) {
                    assertArrayEquals(
                            expected,
                            actual,
                            (label == null ? "" : label + ": ")
                                    + "first byte diff at " + i
                                    + " expected=" + Byte.toUnsignedInt(expected[i])
                                    + " actual=" + Byte.toUnsignedInt(actual[i])
                                    + " expectedBlock=" + blockHex(expected, i)
                                    + " actualBlock=" + blockHex(actual, i));
                }
            }
        }
        assertArrayEquals(expected, actual);
    }

    private static String blockHex(byte[] data, int offset) {
        int blockOffset = (offset / 16) * 16;
        int end = Math.min(data.length, blockOffset + 16);
        StringBuilder builder = new StringBuilder();
        builder.append(blockOffset).append(':');
        for (int i = blockOffset; i < end; i++) {
            builder.append(' ');
            int value = Byte.toUnsignedInt(data[i]);
            if (value < 16) {
                builder.append('0');
            }
            builder.append(Integer.toHexString(value));
        }
        return builder.toString();
    }

    private static byte[] readFirstKtxLevelPayload(Path ktx) throws IOException {
        byte[] data = Files.readAllBytes(ktx);
        byte[] identifier = {
            (byte) 0xAB, 0x4B, 0x54, 0x58, 0x20, 0x31, 0x31, (byte) 0xBB, 0x0D, 0x0A, 0x1A, 0x0A
        };
        for (int i = 0; i < identifier.length; i++) {
            assertEquals(identifier[i], data[i], "KTX identifier byte " + i);
        }
        ByteBuffer header = ByteBuffer.wrap(data).order(ByteOrder.LITTLE_ENDIAN);
        assertEquals(0x04030201, header.getInt(12), "KTX endianness");
        int keyValueBytes = header.getInt(60);
        int imageSizeOffset = 64 + keyValueBytes;
        int imageSize = header.getInt(imageSizeOffset);
        byte[] payload = new byte[imageSize];
        System.arraycopy(data, imageSizeOffset + 4, payload, 0, imageSize);
        return payload;
    }

    private static byte[] packRgba(byte[] rgba, BasisTranscodeTarget target) {
        byte[] packed = new byte[rgba.length / 2];
        for (int in = 0, out = 0; in < rgba.length; in += 4, out += 2) {
            int r = Byte.toUnsignedInt(rgba[in]);
            int g = Byte.toUnsignedInt(rgba[in + 1]);
            int b = Byte.toUnsignedInt(rgba[in + 2]);
            int a = Byte.toUnsignedInt(rgba[in + 3]);
            int value;
            if (target == BasisTranscodeTarget.RGB565) {
                value = (mul8(r, 31) << 11) | (mul8(g, 63) << 5) | mul8(b, 31);
            } else if (target == BasisTranscodeTarget.BGR565) {
                value = (mul8(b, 31) << 11) | (mul8(g, 63) << 5) | mul8(r, 31);
            } else {
                value = (mul8(r, 15) << 12)
                        | (mul8(g, 15) << 8)
                        | (mul8(b, 15) << 4)
                        | mul8(a, 15);
            }
            packed[out] = (byte) value;
            packed[out + 1] = (byte) (value >>> 8);
        }
        return packed;
    }

    private static int mul8(int value, int quantizedMax) {
        int v = value * quantizedMax + 128;
        return (v + (v >>> 8)) >>> 8;
    }

    private static void run(List<String> command) throws IOException, InterruptedException {
        run(command, null);
    }

    private static void run(List<String> command, Path directory) throws IOException, InterruptedException {
        ProcessBuilder processBuilder = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .directory(directory == null ? null : directory.toFile());
        Process process = processBuilder.start();
        byte[] output = process.getInputStream().readAllBytes();
        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new AssertionError("Command failed (" + exitCode + "): "
                    + String.join(" ", command) + "\n" + new String(output, java.nio.charset.StandardCharsets.UTF_8));
        }
    }
}
