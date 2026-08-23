package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.ByteBuffer;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestBasisDecoderFactory {

    @Test
    public void testDefaultFactoryReturnsJavaDecoder() {
        BasisDecoder decoder = BasisDecoderFactory.createDefault();
        assertNotNull(decoder);
        assertInstanceOf(BasisuJavaDecoder.class, decoder);
    }

    @Test
    public void testFactoryAcceptsExplicitDecoderInstanceWithoutReflection() {
        FakeDecoder fake = new FakeDecoder();
        BasisDecoder decoder = BasisDecoderFactory.create(fake);
        assertInstanceOf(FakeDecoder.class, decoder);
        assertEquals(0, fake.decodeCalls);

        byte[] payload = new byte[] {0x00};
        BasisDecodeRequest request = BasisDecodeRequest.builder(payload).build();
        byte[] expected = new byte[] {1, 2, 3, 4};
        fake.nextResult = new BasisDecodeResult(
                2,
                2,
                ByteBuffer.wrap(expected),
                BasisImageFormat.RGBA8,
                new int[] {expected.length},
                BasisColorSpace.Linear);

        BasisDecodeResult result = decoder.decode(request);
        assertEquals(4, result.getPixelData().remaining());
        assertEquals(expected.length, result.getPixelData().remaining());
        assertEquals(1, fake.decodeCalls);
    }

    @Test
    public void testMissingBackendFactoryReturnsUnavailableDecoder() {
        BasisDecoder decoder = BasisDecoderFactory.missingBackend();
        Assertions.assertFalse(decoder.isAvailable());
        assertThrows(BasisDecodeException.class, () -> decoder.decode(
                BasisDecodeRequest.builder(new byte[] {1, 2, 3}).build()));
    }

    public static final class FakeDecoder implements BasisDecoder {

        int decodeCalls;
        BasisDecodeResult nextResult;

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public String getBackendName() {
            return "fake-decoder";
        }

        @Override
        public BasisDecodeResult decode(BasisDecodeRequest request) {
            decodeCalls++;
            return nextResult;
        }
    }
}
