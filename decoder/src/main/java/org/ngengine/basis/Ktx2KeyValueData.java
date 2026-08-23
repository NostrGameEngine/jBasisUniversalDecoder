package org.ngengine.basis;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

/**
 * Parsed KTX2 key/value metadata.
 */
public final class Ktx2KeyValueData {
    private static final Ktx2KeyValueData EMPTY = new Ktx2KeyValueData(new Ktx2KeyValue[0]);

    private final Ktx2KeyValue[] entries;

    private Ktx2KeyValueData(Ktx2KeyValue[] entries) {
        this.entries = Arrays.copyOf(entries, entries.length);
    }

    public static Ktx2KeyValueData parse(byte[] data, Ktx2Header header) {
        if (header.getKvdByteLength() == 0) {
            if (header.getKvdByteOffset() != 0) {
                throw new BasisDecodeException("KTX2 KVD offset must be zero when KVD length is zero");
            }
            return EMPTY;
        }

        int offset = UnsignedFields.sizeToInt(header.getKvdByteOffset(), "KVD byte offset");
        int length = UnsignedFields.sizeToInt(header.getKvdByteLength(), "KVD byte length");
        validateRange(data.length, offset, length, "KVD");

        ByteBuffer buffer = ByteBuffer.wrap(data, offset, length)
                .slice()
                .order(ByteOrder.LITTLE_ENDIAN);
        ByteBuffer countBuffer = buffer.asReadOnlyBuffer().order(ByteOrder.LITTLE_ENDIAN);
        Ktx2KeyValue[] parsed = new Ktx2KeyValue[countEntries(countBuffer)];
        for (int i = 0; i < parsed.length; i++) {
            parsed[i] = readEntry(buffer);
        }
        return new Ktx2KeyValueData(parsed);
    }

    public Ktx2KeyValue[] getEntries() {
        return Arrays.copyOf(entries, entries.length);
    }

    public Ktx2KeyValue find(String key) {
        for (Ktx2KeyValue entry : entries) {
            if (entry.getKey().equals(key)) {
                return entry;
            }
        }
        return null;
    }

    private static int countEntries(ByteBuffer buffer) {
        int count = 0;
        while (buffer.hasRemaining()) {
            if (buffer.remaining() < Integer.BYTES) {
                throw new BasisDecodeException("KTX2 KVD entry missing byte length");
            }
            int entryLength = buffer.getInt();
            if (entryLength < 0 || entryLength > buffer.remaining()) {
                throw new BasisDecodeException("KTX2 KVD entry extends beyond metadata range");
            }
            buffer.position(buffer.position() + entryLength);
            int padding = (4 - (entryLength & 3)) & 3;
            if (padding > buffer.remaining()) {
                throw new BasisDecodeException("KTX2 KVD entry padding extends beyond metadata range");
            }
            buffer.position(buffer.position() + padding);
            count++;
        }
        return count;
    }

    private static Ktx2KeyValue readEntry(ByteBuffer buffer) {
        int entryLength = buffer.getInt();
        byte[] entryBytes = new byte[entryLength];
        buffer.get(entryBytes);
        int padding = (4 - (entryLength & 3)) & 3;
        buffer.position(buffer.position() + padding);

        int separator = findSeparator(entryBytes);
        String key = new String(entryBytes, 0, separator, StandardCharsets.UTF_8);
        byte[] value = Arrays.copyOfRange(entryBytes, separator + 1, entryBytes.length);
        return new Ktx2KeyValue(key, value);
    }

    private static int findSeparator(byte[] entryBytes) {
        for (int i = 0; i < entryBytes.length; i++) {
            if (entryBytes[i] == 0) {
                if (i == 0) {
                    throw new BasisDecodeException("KTX2 KVD entry has an empty key");
                }
                return i;
            }
        }
        throw new BasisDecodeException("KTX2 KVD entry has no NUL key separator");
    }

    private static void validateRange(int dataLength, int offset, int length, String fieldName) {
        if (offset < Ktx2Constants.KTX2_HEADER_SIZE || offset > dataLength || length > dataLength - offset) {
            throw new BasisDecodeException("KTX2 " + fieldName + " range extends beyond input buffer");
        }
    }
}
