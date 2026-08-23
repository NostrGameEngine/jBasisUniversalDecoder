package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

public class TestCppTypeMappings {

    @Test
    public void testUnsigned16ToIntPreservesZeroToMax() {
        assertEquals(0, CppTypeMappings.toIntFromUnsigned16(0, "u16"));
        assertEquals(255, CppTypeMappings.toIntFromUnsigned16(0x00FF, "u16"));
        assertEquals(65535, CppTypeMappings.toIntFromUnsigned16(0xFFFF, "u16"));
    }

    @Test
    public void testUnsigned8ToIntPreservesZeroToMax() {
        assertEquals(0, CppTypeMappings.toIntFromUnsigned8(0, "u8"));
        assertEquals(127, CppTypeMappings.toIntFromUnsigned8(0x7F, "u8"));
        assertEquals(255, CppTypeMappings.toIntFromUnsigned8(0xFF, "u8"));
    }

    @Test
    public void testUnsigned32ToIntThrowsOnOverflow() {
        assertEquals(0, CppTypeMappings.toIntFromUnsigned32(0, "u32"));
        assertEquals((int) 0x7FFFFFFF, CppTypeMappings.toIntFromUnsigned32((int) 0x7FFFFFFF, "u32"));

        assertThrows(BasisDecodeException.class,
                () -> CppTypeMappings.toIntFromUnsigned32(-1, "u32"));
    }

    @Test
    public void testUnsigned32ToIntMapsHighBitPatternsExplicitly() {
        assertThrows(BasisDecodeException.class,
                () -> CppTypeMappings.toIntFromUnsigned32(0x80000000, "u32-high-bit"));
    }

    @Test
    public void testUnsigned64MappingEnforcesPositiveDomain() {
        assertEquals(1L, CppTypeMappings.toLongFromUnsigned64(1L, "u64"));
        assertThrows(BasisDecodeException.class,
                () -> CppTypeMappings.toLongFromUnsigned64(-1L, "u64"));
    }

    @Test
    public void testSizeTPreservationAndOverflow() {
        assertEquals(1234, CppTypeMappings.toIntFromSizeT(1234L, "size_t"));
        assertThrows(BasisDecodeException.class,
                () -> CppTypeMappings.toIntFromSizeT(-1L, "size_t"));
        assertThrows(BasisDecodeException.class,
                () -> CppTypeMappings.toIntFromSizeT(((long) Integer.MAX_VALUE) + 1L, "size_t"));
    }
}
