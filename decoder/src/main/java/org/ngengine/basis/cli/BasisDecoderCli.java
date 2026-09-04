package org.ngengine.basis.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.ngengine.basis.BasisDecodeException;
import org.ngengine.basis.BasisDecodeRequest;
import org.ngengine.basis.BasisDecodeResult;
import org.ngengine.basis.BasisDecoder;
import org.ngengine.basis.BasisDecoderFactory;
import org.ngengine.basis.BasisImageFormat;
import org.ngengine.basis.BasisTranscodeTarget;

/**
 * Small command line entrypoint for decoder/transcoder smoke checks and deterministic CI checks.
 */
public final class BasisDecoderCli {

    private static final Set<String> SUPPORTED_OPTIONS = Set.of(
            "help",
            "input",
            "output",
            "format",
            "linear-color-space",
            "quality",
            "threads",
            "image-index",
            "strict",
            "preset"
    );

    private static final String USAGE =
            "Usage: java -cp <classpath> org.ngengine.basis.cli.BasisDecoderCli \\n"
            + "  --input <file> --output <file> "
            + "--format <RGBA8|BC1|BC3|BC4|BC5|BC6H|BC7|ASTC_LDR_*|ASTC_HDR_*|ETC2|ETC2_NO_ALPHA|ETC1> \\n"
            + "  [--linear-color-space <true|false>] [--quality <0-10>] [--threads <1-64>] \\n"
            + "  [--image-index <0+>] [--strict <true|false>] [--preset <string>] [--help]";


    private BasisDecoderCli() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    public static int run(String[] args, PrintStream out, PrintStream err) {
        Map<String, String> options;
        try {
            options = parseArgs(args);
        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            return 2;
        }

        if (options.containsKey("help") || options.isEmpty()) {
            out.println(USAGE);
            if (!options.containsKey("help") && options.isEmpty()) {
                err.println("Error: --input, --output, and --format are required.");
                return 2;
            }
            return 0;
        }

        try {
            String input = options.get("input");
            String output = options.get("output");
            if (input == null || output == null || options.get("format") == null) {
                err.println("Error: --input, --output, and --format are required.");
                out.println(USAGE);
                return 2;
            }

            BasisTranscodeTarget format = parseTarget(options.get("format"));
            boolean linearColorSpace = parseBooleanOption(options.get("linear-color-space"), true,
                    "linear-color-space");
            int quality = parseIntOption(options.get("quality"), 7);
            int threadCount = parseThreadOption(options.get("threads"), 1);
            int imageIndex = parseImageIndexOption(options.get("image-index"), 0);
            boolean strictMode = parseBooleanOption(options.get("strict"), false, "strict");
            String preset = options.get("preset");

            BasisDecoder decoder = BasisDecoderFactory.createDefault();
            if (decoder == null) {
                err.println("Error: No decoder backend available.");
                return 1;
            }

            byte[] payload = Files.readAllBytes(Paths.get(input));
            BasisDecodeRequest.Builder builder = BasisDecodeRequest.builder(payload)
                    .target(format)
                    .linearColorSpace(linearColorSpace)
                    .qualityLevel(quality)
                    .threadCount(threadCount)
                    .imageIndex(imageIndex)
                    .strictMode(strictMode)
                    .transcodePreset(preset);
            BasisDecodeRequest request = builder
                    .allocator(ByteBuffer::allocate)
                    .build();
            BasisDecodeResult decoded = decoder.decode(request);

            byte[] decodedBytes = toByteArray(decoded.getPixelData());
            Files.write(Paths.get(output), decodedBytes);

            BasisImageFormat formatHint = format.getImageFormat();
            out.printf("OK format=%s width=%d height=%d bytes=%d colorSpace=%s\n",
                    formatHint,
                    decoded.getWidth(),
                    decoded.getHeight(),
                    decodedBytes.length,
                    decoded.getColorSpace());
            return 0;
        } catch (IllegalArgumentException e) {
            err.println("Error: " + e.getMessage());
            return 2;
        } catch (IOException | BasisDecodeException e) {
            err.println("Error: " + e.getMessage());
            return 1;
        }
    }

    private static BasisTranscodeTarget parseTarget(String value) {
        if (value == null) {
            throw new IllegalArgumentException("--format is required");
        }
        try {
            return BasisTranscodeTarget.valueOf(value);
        } catch (IllegalArgumentException ex) {
            throw new IllegalArgumentException("Unsupported --format value: " + value);
        }
    }

    private static boolean parseBooleanOption(String value, boolean defaultValue, String optionName) {
        if (value == null) {
            return defaultValue;
        }
        if ("true".equalsIgnoreCase(value) || "false".equalsIgnoreCase(value)) {
            return Boolean.parseBoolean(value);
        }
        throw new IllegalArgumentException("Expected boolean value for --"
                + optionName
                + " but got: "
                + value);
    }

    private static int parseIntOption(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < BasisDecodeRequest.MIN_QUALITY || parsed > BasisDecodeRequest.MAX_QUALITY) {
                throw new IllegalArgumentException("--quality must be in range ["
                        + BasisDecodeRequest.MIN_QUALITY
                        + ","
                        + BasisDecodeRequest.MAX_QUALITY
                        + "]");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Expected integer value for --quality but got: " + value);
        }
    }

    private static int parseThreadOption(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < BasisDecodeRequest.MIN_THREADS || parsed > BasisDecodeRequest.MAX_THREADS) {
                throw new IllegalArgumentException("--threads must be in range ["
                        + BasisDecodeRequest.MIN_THREADS
                        + ","
                        + BasisDecodeRequest.MAX_THREADS
                        + "]");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Expected integer value for --threads but got: " + value);
        }
    }

    private static int parseImageIndexOption(String value, int defaultValue) {
        if (value == null) {
            return defaultValue;
        }
        try {
            int parsed = Integer.parseInt(value);
            if (parsed < 0) {
                throw new IllegalArgumentException("--image-index must be non-negative");
            }
            return parsed;
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("Expected integer value for --image-index but got: "
                    + value);
        }
    }

    private static byte[] toByteArray(java.nio.ByteBuffer source) {
        java.nio.ByteBuffer duplicate = source.asReadOnlyBuffer();
        duplicate.rewind();
        byte[] out = new byte[duplicate.remaining()];
        duplicate.get(out);
        return out;
    }

    private static Map<String, String> parseArgs(String[] args) {
        if (args.length == 0) {
            return Collections.emptyMap();
        }

        Map<String, String> options = new HashMap<>();
        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if ("--help".equals(arg)) {
                options.put("help", "true");
                continue;
            }

            if (!arg.startsWith("--")) {
                throw new IllegalArgumentException("Unexpected token: " + arg);
            }

            String key = arg.substring(2);
            if (!SUPPORTED_OPTIONS.contains(key)) {
                throw new IllegalArgumentException("Unsupported option: " + arg);
            }

            if ((i + 1) >= args.length) {
                throw new IllegalArgumentException("Missing value for " + arg);
            }

            String value = args[i + 1];
            if (value.startsWith("--")) {
                throw new IllegalArgumentException("Missing value for " + arg);
            }
            options.put(key, value);
            i++;
        }
        return options;
    }
}
