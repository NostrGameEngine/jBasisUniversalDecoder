package org.ngengine.basis;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

/**
 * Parsed XUASTC LDR payload header.
 */
final class XuastcLdrPayload {
    private static final int HYBRID_HEADER_BYTES = 45;
    private static final int FULL_ZSTD_HEADER_BYTES = 85;

    private final XuastcLdrSyntax syntax;
    private final int flags;
    private final long totalSectionBytes;

    private XuastcLdrPayload(XuastcLdrSyntax syntax, int flags, long totalSectionBytes) {
        this.syntax = syntax;
        this.flags = flags;
        this.totalSectionBytes = totalSectionBytes;
    }

    static XuastcLdrPayload parse(byte[] data, int offset, int length) {
        if (data == null) {
            throw new BasisDecodeException("XUASTC payload must not be null");
        }
        if (offset < 0 || length < 1 || offset > data.length || length > data.length - offset) {
            throw new BasisDecodeException("XUASTC payload range extends beyond input buffer");
        }
        int firstByte = Byte.toUnsignedInt(data[offset]);
        XuastcLdrSyntax syntax = XuastcLdrSyntax.fromCode(firstByte);
        if (syntax == XuastcLdrSyntax.FULL_ARITH) {
            return new XuastcLdrPayload(syntax, firstByte, length - 1L);
        }

        int headerBytes = syntax == XuastcLdrSyntax.HYBRID_ARITH_ZSTD
                ? HYBRID_HEADER_BYTES
                : FULL_ZSTD_HEADER_BYTES;
        if (length < headerBytes) {
            throw new BasisDecodeException("XUASTC payload is too small for " + syntax + " header");
        }

        ByteBuffer buffer = ByteBuffer.wrap(data, offset, headerBytes)
                .slice()
                .order(ByteOrder.LITTLE_ENDIAN);
        int flags = Byte.toUnsignedInt(buffer.get());
        long totalSectionBytes = 0;
        while (buffer.remaining() >= Integer.BYTES) {
            totalSectionBytes = checkedAdd(totalSectionBytes, Integer.toUnsignedLong(buffer.getInt()));
        }
        if (headerBytes + totalSectionBytes > length) {
            throw new BasisDecodeException("XUASTC payload sections exceed compressed slice length");
        }
        return new XuastcLdrPayload(syntax, flags, totalSectionBytes);
    }

    XuastcLdrSyntax getSyntax() {
        return syntax;
    }

    int getFlags() {
        return flags;
    }

    long getTotalSectionBytes() {
        return totalSectionBytes;
    }

    private static long checkedAdd(long left, long right) {
        if (left < 0 || right < 0 || left > Long.MAX_VALUE - right) {
            throw new BasisDecodeException("XUASTC section length arithmetic overflow");
        }
        return left + right;
    }
}
