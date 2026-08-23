package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

public class TestCppEnumParityParity {

    private static final Pattern ENUM_START_PATTERN =
            Pattern.compile("^[ \t]*enum(?:[ \t]+class)?[ \t]+([A-Za-z_][A-Za-z0-9_]*)");
    private static final Pattern ENUM_ENTRY_PATTERN =
            Pattern.compile("^[ \t]*([A-Za-z_][A-Za-z0-9_]*)[ \t]*(?:=[ \t]*([^,]+))?,?[ \t]*$");
    private static final Pattern DIGIT_ONLY = Pattern.compile("^[+-]?\\d+$");

    private static final String TRANSCODER_HEADER = "transcoder/basisu_transcoder.h";
    private static final String FILE_HEADERS_HEADER = "transcoder/basisu_file_headers.h";

    private static final Map<String, Class<?>> TARGET_ENUMS = new LinkedHashMap<>();

    static {
        TARGET_ENUMS.put("basis_slice_desc_flags", Ktx2BasisSliceDescFlag.class);
        TARGET_ENUMS.put("basis_header_flags", Ktx2BasisHeaderFlag.class);
        TARGET_ENUMS.put("basis_texture_type", Ktx2BasisTextureType.class);
        TARGET_ENUMS.put("basis_tex_format", Ktx2BasisTextureFormat.class);
        TARGET_ENUMS.put("basisu_decode_flags", Ktx2DecodeFlag.class);
        TARGET_ENUMS.put("debug_flags_t", Ktx2DebugFlag.class);
        TARGET_ENUMS.put("ktx2_supercompression", Ktx2SupercompressionScheme.class);
        TARGET_ENUMS.put("ktx2_df_channel_id", Ktx2DfdChannelId.class);
        TARGET_ENUMS.put("ktx2_df_color_primaries", Ktx2DfdColorPrimaries.class);
        TARGET_ENUMS.put("transcoder_texture_format", Ktx2TranscoderTextureFormat.class);
    }

    @Test
    public void testCppEnumValuesMatchJavaParityEnums() throws IOException {
        Map<String, Map<String, Integer>> cppEnumValues = readCppEnumValues();

        for (Map.Entry<String, Class<?>> entry : TARGET_ENUMS.entrySet()) {
            String cppEnum = entry.getKey();
            @SuppressWarnings("unchecked")
            Class<? extends Enum<?>> javaEnum = (Class<? extends Enum<?>>) entry.getValue();
            Map<String, Integer> constants = cppEnumValues.get(cppEnum);
            assertTrue(constants != null && !constants.isEmpty(), "Missing enum parse: " + cppEnum);

            for (Enum<?> javaValue : javaEnum.getEnumConstants()) {
                int javaCode = extractEnumCode(javaValue);
                String cppName = toCppName(cppEnum, javaValue.name());
                Integer cppCode = constants.get(cppName);
                assertTrue(
                        cppCode != null,
                        "Missing C++ constant: " + cppEnum + "." + cppName
                                + " (java " + javaValue.name() + ")");
                assertEquals(cppCode.intValue(), javaCode, cppEnum + "." + cppName);
                constants.remove(cppName);
            }

            Set<String> javaAliasNames = collectStaticAliasNames(javaEnum);
            constants.keySet().removeIf(
                    value -> isAllowedCppSentinel(value) || javaAliasNames.contains(value));
            assertTrue(
                    constants.isEmpty(),
                    "Unmapped C++ constants for " + cppEnum + ": " + constants.keySet());
        }
    }

    private static boolean isAllowedCppSentinel(String name) {
        return name.contains("Total")
                || name.equals("KTX2_DF_CHANNEL_UNKNOWN")
                || name.equals("KTX2_DF_CHROMA_INCOHERENT")
                || name.equals("KTX2_DF_PRI_BT_709");
    }

    private static String toCppName(String cppEnum, String javaName) {
        if ("ktx2_supercompression".equals(cppEnum)) {
            if (javaName.equals("NONE")) {
                return "KTX2_SS_NONE";
            }
            if (javaName.equals("UASTC_HDR_6X6I")) {
                return "KTX2_SS_UASTC_HDR_6x6I";
            }
            return "KTX2_SS_" + javaName;
        }
        return javaName;
    }

    private static Set<String> collectStaticAliasNames(Class<? extends Enum<?>> javaEnum) {
        Set<String> aliases = new java.util.LinkedHashSet<>();
        for (java.lang.reflect.Field field : javaEnum.getDeclaredFields()) {
            if (!Modifier.isStatic(field.getModifiers()) || !Modifier.isFinal(field.getModifiers())) {
                continue;
            }
            if (field.getType() != javaEnum) {
                continue;
            }
            aliases.add(field.getName());
        }
        return aliases;
    }

    private static Map<String, Map<String, Integer>> readCppEnumValues() throws IOException {
        Map<String, Map<String, Integer>> result = new LinkedHashMap<>();
        for (String path : new String[] {TRANSCODER_HEADER, FILE_HEADERS_HEADER}) {
            Path source = resolveCppHeader(path);

            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            String activeEnum = null;
            Map<String, Integer> activeValues = null;
            long lastValue = -1;
            int braceDepth = 0;

            for (String rawLine : lines) {
                String line = stripComments(rawLine).trim();
                if (line.isEmpty()) {
                    continue;
                }

                if (activeEnum == null) {
                    Matcher start = ENUM_START_PATTERN.matcher(line);
                    if (start.find()) {
                        String enumName = start.group(1);
                        if (TARGET_ENUMS.containsKey(enumName)) {
                            activeEnum = enumName;
                            activeValues = new LinkedHashMap<>();
                            lastValue = -1;
                            braceDepth = count(line, '{') - count(line, '}');
                            result.put(activeEnum, activeValues);
                        }
                    }
                    continue;
                }

                braceDepth += count(line, '{');
                braceDepth -= count(line, '}');
                if (line.contains("}") && braceDepth <= 0) {
                    activeEnum = null;
                    activeValues = null;
                    continue;
                }

                Matcher valueMatcher = ENUM_ENTRY_PATTERN.matcher(line);
                if (!valueMatcher.matches()) {
                    continue;
                }

                String name = valueMatcher.group(1);
                String rawValue = valueMatcher.group(2);
                if (rawValue == null || rawValue.trim().isEmpty()) {
                    rawValue = String.valueOf(lastValue + 1);
                }

                int parsed;
                String normalized = rawValue.replaceAll("[uULl]", "").trim();
                Integer alias = activeValues.get(normalized);
                if (alias != null) {
                    parsed = alias;
                } else if (DIGIT_ONLY.matcher(normalized).matches()) {
                    parsed = Integer.parseInt(normalized);
                } else {
                    parsed = parseNumericOrThrow(normalized, activeValues);
                }

                activeValues.put(name, parsed);
                lastValue = parsed;
            }
        }
        return result;
    }

    private static Path resolveCppHeader(String relativePath) {
        Path projectDir = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        Path[] candidates = {
                projectDir.resolve("../basis_universal").normalize().resolve(relativePath),
                projectDir.resolve("basis_universal").resolve(relativePath)
        };
        for (Path candidate : candidates) {
            if (Files.exists(candidate)) {
                return candidate;
            }
        }
        assertTrue(false, "Expected C++ header in Basis Universal submodule: " + relativePath);
        return candidates[0];
    }

    private static int parseNumericOrThrow(String token, Map<String, Integer> aliases) {
        String normalized = token.trim();
        if ("cTFASTC_DR_4x4_RGBA".equals(normalized)) {
            Integer alias = aliases.get("cTFASTC_LDR_4x4_RGBA");
            if (alias == null) {
                throw new IllegalArgumentException("Unsupported C++ enum alias: " + token);
            }
            return alias;
        }

        if (normalized.startsWith("0x") || normalized.startsWith("0X")) {
            return Long.valueOf(normalized.substring(2), 16).intValue();
        }

        throw new IllegalArgumentException("Unsupported C++ enum expression: " + token);
    }

    private static int extractEnumCode(Object value) {
        try {
            try {
                Method getCode = value.getClass().getMethod("getCode");
                return ((Number) getCode.invoke(value)).intValue();
            } catch (NoSuchMethodException e) {
                Method getMask = value.getClass().getMethod("getMask");
                return ((Number) getMask.invoke(value)).intValue();
            }
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException e) {
            throw new RuntimeException("Could not extract enum code from " + value, e);
        }
    }

    private static String stripComments(String line) {
        int lineComment = line.indexOf("//");
        if (lineComment >= 0) {
            line = line.substring(0, lineComment);
        }
        int blockStart = line.indexOf("/*");
        int blockEnd = line.indexOf("*/", blockStart + 1);
        if (blockStart >= 0 && blockEnd >= 0) {
            return line.substring(0, blockStart) + line.substring(blockEnd + 2);
        }
        return line;
    }

    private static int count(String line, char ch) {
        int n = 0;
        for (int i = 0; i < line.length(); i++) {
            if (line.charAt(i) == ch) {
                n++;
            }
        }
        return n;
    }
}
