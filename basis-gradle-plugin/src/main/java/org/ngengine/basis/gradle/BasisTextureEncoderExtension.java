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
        getExcludeOriginalsWhenEncoded().convention(false);
        getBasisuArguments().convention(Arrays.asList("-basis", "-quiet"));
        getBasisuExecutable().convention("");
    }

    public abstract ListProperty<String> getImageExtensions();

    public abstract ListProperty<String> getResourceDirectories();

    public abstract Property<Boolean> getExcludeOriginalsWhenEncoded();

    public abstract ListProperty<String> getBasisuArguments();

    public abstract Property<String> getBasisuExecutable();
}
