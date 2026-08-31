package org.ngengine.basis.gradle;

import java.util.Arrays;
import javax.inject.Inject;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.Property;

public abstract class BasisTextureEncoderExtension {

    @Inject
    public BasisTextureEncoderExtension(ObjectFactory objects) {
        getImageExtensions().convention(Arrays.asList("png", "jpg", "jpeg", "tga", "qoi", "hdr", "exr"));
        getResourceDirectories().convention(Arrays.asList("src/main/resources"));
        getExcludedResourcePaths().convention(java.util.Collections.emptyList());
        getExcludeOriginalsWhenEncoded().convention(false);
        getDimensionAlignment().convention(1);
        getBasisuArguments().convention(Arrays.asList("-basis", "-quiet"));
        getBasisuExecutable().convention("");
    }

    public abstract ListProperty<String> getImageExtensions();

    public abstract ListProperty<String> getResourceDirectories();

    /**
     * Resource-relative image paths that another build step will encode.
     * Paths use forward slashes regardless of the host platform.
     *
     * @return paths excluded from ordinary one-image encoding
     */
    public abstract ListProperty<String> getExcludedResourcePaths();

    public abstract Property<Boolean> getExcludeOriginalsWhenEncoded();

    /**
     * Aligns encoded image dimensions by resampling each axis upward to a
     * multiple of this value. A value of {@code 1} disables alignment.
     *
     * @return encoded dimension alignment
     */
    public abstract Property<Integer> getDimensionAlignment();

    public abstract ListProperty<String> getBasisuArguments();

    public abstract Property<String> getBasisuExecutable();
}
