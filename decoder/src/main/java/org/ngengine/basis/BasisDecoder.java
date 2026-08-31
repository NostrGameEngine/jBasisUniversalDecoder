package org.ngengine.basis;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Pluggable contract for Basis Universal decoding backends.
 */
public interface BasisDecoder {

    /**
     * Returns true when the backend is available and can decode now.
     */
    boolean isAvailable();

    /**
     * Human-readable backend name.
     */
    String getBackendName();

    /**
     * Decode the request to pixel data and metadata.
     */
    BasisDecodeResult decode(BasisDecodeRequest request);

    /**
     * Decodes every image in a Basis texture or KTX2 array. Implementations
     * may override this method to share container parsing, decompression, and
     * codebooks across the images.
     *
     * @param request decode settings; its image index is ignored
     * @return results ordered by zero-based image index
     */
    default List<BasisDecodeResult> decodeAllImages(BasisDecodeRequest request) {
        BasisDecodeResult first = decode(request.withImageIndex(0));
        int imageCount = first.getImageCount();
        if (imageCount == 1) {
            return Collections.singletonList(first);
        }
        List<BasisDecodeResult> results = new ArrayList<>(imageCount);
        results.add(first);
        for (int imageIndex = 1; imageIndex < imageCount; imageIndex++) {
            results.add(decode(request.withImageIndex(imageIndex)));
        }
        return results;
    }
}
