package org.ngengine.basis.viewer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ViewerDocument {
    private final Path source;
    private final String sourceType;
    private final List<ViewerImage> images;
    private final List<String> skippedTargets;

    ViewerDocument(Path source, String sourceType, List<ViewerImage> images, List<String> skippedTargets) {
        this.source = source;
        this.sourceType = sourceType;
        this.images = Collections.unmodifiableList(new ArrayList<>(images));
        this.skippedTargets = Collections.unmodifiableList(new ArrayList<>(skippedTargets));
    }

    List<ViewerImage> getImages() {
        return images;
    }

    String describe() {
        StringBuilder builder = new StringBuilder();
        builder.append("File: ").append(source).append('\n');
        builder.append("Type: ").append(sourceType).append('\n');
        builder.append("Displayable views: ").append(images.size());
        if (!skippedTargets.isEmpty()) {
            builder.append("\n\nUnsupported for this payload:");
            for (String skipped : skippedTargets) {
                builder.append('\n').append(" - ").append(skipped);
            }
        }
        return builder.toString();
    }
}
