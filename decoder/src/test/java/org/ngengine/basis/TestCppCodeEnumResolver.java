package org.ngengine.basis;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

class TestCppCodeEnumResolver {

    @Test
    void testDuplicateCodesResolveToFirstAliasEntry() {
        assertSame(Ktx2DfdColorPrimaries.KTX2_DF_PRIMARIES_BT709, Ktx2DfdColorPrimaries.fromCode(1));
        assertSame(Ktx2DfdChannelId.KTX2_DF_CHANNEL_ETC1S_RGB, Ktx2DfdChannelId.fromCode(0));
    }

    @Test
    void testResolverEnforcesExplicitBoundedErrorForUnknownCodes() {
        assertThrows(IllegalArgumentException.class, () -> Ktx2TextureFormat.fromCode(999));
        assertThrows(IllegalArgumentException.class, () -> Ktx2BasisTextureType.fromCode(-1));
        assertThrows(
                IllegalArgumentException.class,
                () -> Ktx2SupercompressionScheme.fromCode(Integer.MAX_VALUE));
    }

    @Test
    void testResolverDescribesPresenceAndRepr() {
        assertTrue(CppEnumCodeResolver.describe(0, Ktx2DfdChannelId.class).contains("mapped=true"));
        assertTrue(CppEnumCodeResolver.describe(1234, Ktx2TextureFormat.class).contains("mapped=false"));
        assertTrue(CppEnumCodeResolver.hasCode(Ktx2TextureFormat.class, Ktx2TextureFormat.cRGBA32.getCode()));
        assertEquals(
                "code=" + Ktx2DfdColorPrimaries.KTX2_DF_PRIMARIES_UNSPECIFIED.getCode()
                        + " enum=ktx2dfdcolorprimaries mapped=true",
                CppEnumCodeResolver.describe(Ktx2DfdColorPrimaries.KTX2_DF_PRIMARIES_UNSPECIFIED.getCode(),
                        Ktx2DfdColorPrimaries.class));
    }

    @Test
    void testResolverSupportsConcurrentReadOnlyUse() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(8);
        try {
            List<Callable<Ktx2DfdColorPrimaries>> tasks = new ArrayList<>();
            for (int i = 0; i < 200; i++) {
                final int code = (i % 2 == 0) ? 1 : 0;
                tasks.add(() -> Ktx2DfdColorPrimaries.fromCode(code));
            }

            List<Future<Ktx2DfdColorPrimaries>> results = executor.invokeAll(tasks);
            for (Future<Ktx2DfdColorPrimaries> result : results) {
                assertNotNull(result.get());
            }
        } finally {
            executor.shutdown();
            assertTrue(executor.awaitTermination(2, TimeUnit.SECONDS));
        }
    }
}
