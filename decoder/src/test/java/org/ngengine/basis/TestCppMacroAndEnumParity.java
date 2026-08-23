package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

public class TestCppMacroAndEnumParity {

    private static final Pattern MACRO_DEFINE_PATTERN =
            Pattern.compile("^\\s*#\\s*define\\s+([A-Z_][A-Z0-9_]*)\\s+(.*?)\\s*$");
    private static final Pattern CONST_EXPR_PATTERN =
            Pattern.compile(
                    "^\\s*(?:constexpr\\s+)?(?:const\\s+|static\\s+constexpr\\s+)[^\\n]*\\b"
                            + "([A-Z_][A-Z0-9_]*)\\s*=\\s*(.*?)\\s*;\\s*$");
    private static final Pattern CHAR_LITERAL = Pattern.compile("'((?:\\\\.|[^'])+)'", Pattern.DOTALL);
    private static final Pattern INTERESTING_NAME = Pattern.compile("[A-Z0-9_]+$", Pattern.CASE_INSENSITIVE);

    private static final String TRANSCODER_HEADER = "transcoder/basisu_transcoder.h";
    private static final String FILE_HEADERS_HEADER = "transcoder/basisu_file_headers.h";

    @Test
    public void testKtx2ConstantsMatchCppValues() throws IOException {
        Map<String, String> cppConstantExpressions = readCppConstantExpressions();
        Map<String, Integer> javaConstants = collectJavaIntConstants();

        int checked = 0;
        int missingFromJava = 0;
        for (Map.Entry<String, String> entry : cppConstantExpressions.entrySet()) {
            String name = entry.getKey();
            String cppExpr = entry.getValue();

            Integer javaValue = javaConstants.get(name);
            if (javaValue == null) {
                missingFromJava++;
                continue;
            }

            long cppValue = parseCppIntExpression(cppExpr);
            assertEquals(
                    javaValue.intValue(),
                    (int) cppValue,
                    "C++/Java constant mismatch for " + name + " (cpp expr: " + cppExpr + ")");
            checked++;
        }

        assertTrue(
                checked >= 10,
                "Expected to validate at least 10 C++ constants, validated only " + checked);
        assertTrue(
                missingFromJava < 5,
                "Too many C++ constants not exposed in Ktx2Constants: " + missingFromJava);
    }

    @Test
    public void testNoNativeBindingDeclarationsInCoreSources() throws IOException {
        Path moduleRoot = Paths.get(System.getProperty("user.dir"));
        Path srcMain = moduleRoot.resolve("src/main/java");

        assertTrue(Files.exists(srcMain));
        List<String> nativeDecls = new ArrayList<>();
        List<String> libLoads = new ArrayList<>();

        Files.walk(srcMain)
                .filter(Files::isRegularFile)
                .filter(p -> p.toString().endsWith(".java"))
                .forEach(
                        path -> {
                            try {
                                List<String> lines = Files.readAllLines(path, StandardCharsets.UTF_8);
                                for (String rawLine : lines) {
                                    String line = rawLine.trim();
                                    if (line.startsWith("//") || line.isEmpty()) {
                                        continue;
                                    }

                                    if (line.contains("native")
                                            && line.contains("(")
                                            && line.matches(".*\\bnative\\b.*\\(.*")) {
                                        nativeDecls.add(path.toString());
                                    }
                                    if (line.contains("System.loadLibrary")) {
                                        libLoads.add(path.toString());
                                    }
                                }
                            } catch (IOException e) {
                                throw new RuntimeException(e);
                            }
                        });

        assertEquals(
                0,
                nativeDecls.size(),
                "No Java `native` declarations are expected in decoder-transcoder core path: " + nativeDecls);
        assertEquals(
                0,
                libLoads.size(),
                "No JNI/System.loadLibrary calls should exist in decoder-transcoder core path: " + libLoads);
    }

    private static Map<String, Integer> collectJavaIntConstants() {
        Map<String, Integer> javaConstants = new HashMap<>();
        for (Field field : Ktx2Constants.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers())
                    && Modifier.isFinal(field.getModifiers())
                    && field.getType() == int.class) {
                try {
                    javaConstants.put(field.getName(), field.getInt(null));
                } catch (IllegalAccessException e) {
                    throw new RuntimeException(e);
                }
            }
        }
        return javaConstants;
    }

    private static Map<String, String> readCppConstantExpressions() throws IOException {
        Map<String, String> result = new LinkedHashMap<>();
        for (String path : new String[] {TRANSCODER_HEADER, FILE_HEADERS_HEADER}) {
            Path source = resolveCppHeader(path);

            List<String> lines = Files.readAllLines(source, StandardCharsets.UTF_8);
            for (String line : lines) {
                String trimmed = stripComments(line).trim();
                if (trimmed.isEmpty()) {
                    continue;
                }

                Matcher defineMatch = MACRO_DEFINE_PATTERN.matcher(trimmed);
                if (defineMatch.matches()) {
                    String name = defineMatch.group(1);
                    String value = defineMatch.group(2);
                    if (INTERESTING_NAME.matcher(name).matches()) {
                        result.putIfAbsent(name, value);
                    }
                    continue;
                }

                Matcher constMatch = CONST_EXPR_PATTERN.matcher(trimmed);
                if (constMatch.matches()) {
                    String name = constMatch.group(1);
                    String value = constMatch.group(2);
                    if (INTERESTING_NAME.matcher(name).matches() && isLikelyNumericExpression(value)) {
                        result.putIfAbsent(name, value);
                    }
                }
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

    private static boolean isLikelyNumericExpression(String value) {
        if (value.trim().isEmpty()) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isWhitespace(c)
                    || Character.isDigit(c)
                    || (c >= 'a' && c <= 'f')
                    || (c >= 'A' && c <= 'F')
                    || "xX+-|<>~&^()'\\".indexOf(c) >= 0
                    || c == '('
                    || c == ')'
                    || c == '\\') {
                continue;
            }
            return false;
        }
        return true;
    }

    private static String stripComments(String line) {
        int slashSlash = line.indexOf("//");
        if (slashSlash >= 0) {
            return line.substring(0, slashSlash);
        }
        int blockStart = line.indexOf("/*");
        int blockEnd = line.indexOf("*/", blockStart + 1);
        if (blockStart >= 0 && blockEnd >= 0) {
            return line.substring(0, blockStart) + line.substring(blockEnd + 2);
        }
        return line;
    }

    private static long parseCppIntExpression(String sourceExpression) {
        String normalized = normalizeExpression(sourceExpression).replaceAll("\\s+", "");
        return new CppIntExpressionParser(normalized).parseExpression();
    }

    private static String normalizeExpression(String sourceExpression) {
        String expression = sourceExpression.replaceAll("[uULl]{1,2}", "");
        Matcher matcher = CHAR_LITERAL.matcher(expression);
        StringBuffer resolved = new StringBuffer();
        while (matcher.find()) {
            String raw = matcher.group(1);
            int value = decodeCppCharLiteral(raw);
            matcher.appendReplacement(resolved, String.valueOf(value));
        }
        matcher.appendTail(resolved);
        return resolved.toString();
    }

    private static int decodeCppCharLiteral(String raw) {
        if (raw.length() == 1) {
            return raw.charAt(0);
        }
        if (raw.startsWith("\\\\")) {
            char escaped = raw.charAt(1);
            if (escaped == 'n') {
                return '\n';
            } else if (escaped == 'r') {
                return '\r';
            } else if (escaped == 't') {
                return '\t';
            } else if (escaped == '\\') {
                return '\\';
            } else if (escaped == '\'') {
                return '\'';
            } else if (escaped == '"') {
                return '"';
            } else if (escaped == 'x') {
                return Integer.parseInt(raw.substring(2), 16);
            }
            return escaped;
        }
        return 0;
    }

    private static final class CppIntExpressionParser {
        private final String text;
        private int position;

        private CppIntExpressionParser(String text) {
            this.text = text;
        }

        private long parseExpression() {
            long value = parseOr();
            if (position != text.length()) {
                throw new IllegalArgumentException(
                        "Unconsumed characters at position " + position + " in " + text);
            }
            return value;
        }

        private long parseOr() {
            long value = parseXor();
            while (consume('|')) {
                value |= parseXor();
            }
            return value;
        }

        private long parseXor() {
            long value = parseAnd();
            while (consume('^')) {
                value ^= parseAnd();
            }
            return value;
        }

        private long parseAnd() {
            long value = parseShift();
            while (consume('&')) {
                value &= parseShift();
            }
            return value;
        }

        private long parseShift() {
            long value = parseAdd();
            while (true) {
                if (consumeString("<<")) {
                    value <<= parseAdd();
                } else if (consumeString(">>")) {
                    value >>= parseAdd();
                } else {
                    return value;
                }
            }
        }

        private long parseAdd() {
            long value = parseUnary();
            while (true) {
                if (consume('+')) {
                    value += parseUnary();
                } else if (consume('-')) {
                    value -= parseUnary();
                } else {
                    return value;
                }
            }
        }

        private long parseUnary() {
            if (consume('~')) {
                return ~parseUnary();
            }
            if (consume('+')) {
                return parseUnary();
            }
            if (consume('-')) {
                return -parseUnary();
            }
            return parsePrimary();
        }

        private long parsePrimary() {
            if (consume('(')) {
                long value = parseOr();
                if (!consume(')')) {
                    throw new IllegalArgumentException("Missing ')' in " + text + " at position " + position);
                }
                return value;
            }

            int start = position;
            while (position < text.length()) {
                char current = text.charAt(position);
                boolean isHexDigit = Character.isDigit(current)
                        || current >= 'a' && current <= 'f'
                        || current >= 'A' && current <= 'F';
                if (isHexDigit || current == 'x' || current == 'X') {
                    position++;
                } else {
                    break;
                }
            }

            if (start == position) {
                throw new IllegalArgumentException("Expected numeric literal at " + position + " in " + text);
            }
            String token = text.substring(start, position);
            if (token.startsWith("0x") || token.startsWith("0X")) {
                return Long.parseUnsignedLong(token.substring(2), 16);
            }
            return Long.parseLong(token);
        }

        private boolean consume(char expected) {
            if (position < text.length() && text.charAt(position) == expected) {
                position++;
                return true;
            }
            return false;
        }

        private boolean consumeString(String expected) {
            if (text.startsWith(expected, position)) {
                position += expected.length();
                return true;
            }
            return false;
        }
    }
}
