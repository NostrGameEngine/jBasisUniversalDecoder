package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.ByteBuffer;
import java.util.function.IntFunction;
import org.junit.jupiter.api.Test;

public class TestBasisDecoderDefaults {

    @Test
    public void testDefaultAllocatorIsHeapAllocate() {
        BasisDecodeRequest request = BasisDecodeRequest.from(
                new byte[0], BasisTranscodeTarget.RGBA8);
        ByteBuffer buffer = request.getAllocator().apply(16);
        assertEquals(16, buffer.capacity());
        assertFalse(buffer.isDirect());
    }

    @Test
    public void testAllocatorCanBeOverridden() {
        IntFunction<ByteBuffer> customAllocator = ByteBuffer::allocate;
        BasisDecodeRequest request = BasisDecodeRequest.builder(new byte[0])
                .target(BasisTranscodeTarget.RGBA8)
                .allocator(customAllocator)
                .build();

        assertTrue(request.getAllocator() == customAllocator);
    }
}
