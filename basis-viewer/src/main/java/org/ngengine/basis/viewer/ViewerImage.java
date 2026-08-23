package org.ngengine.basis.viewer;

import java.awt.image.BufferedImage;

final class ViewerImage {
    private final String name;
    private final BufferedImage image;
    private final String description;

    ViewerImage(String name, BufferedImage image, String description) {
        this.name = name;
        this.image = image;
        this.description = description;
    }

    BufferedImage getImage() {
        return image;
    }

    String describe() {
        return name + "\n" + description;
    }

    @Override
    public String toString() {
        return name;
    }
}
