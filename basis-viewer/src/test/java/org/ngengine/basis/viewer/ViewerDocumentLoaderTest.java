package org.ngengine.basis.viewer;

import static org.junit.jupiter.api.Assertions.assertFalse;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class ViewerDocumentLoaderTest {

    @Test
    void loadsBasisFixtureIntoDisplayableImages() throws IOException {
        ViewerDocument document = ViewerDocumentLoader.load(fixture("fixtures/basis/kodim20.basis"));

        assertFalse(document.getImages().isEmpty());
    }

    @Test
    void loadsKtx2FixtureIntoDisplayableImages() throws IOException {
        ViewerDocument document = ViewerDocumentLoader.load(fixture("fixtures/ktx2/kodim23.ktx2"));

        assertFalse(document.getImages().isEmpty());
    }

    private static Path fixture(String resourcePath) {
        Path current = Path.of(System.getProperty("user.dir")).toAbsolutePath();
        for (Path candidate = current; candidate != null; candidate = candidate.getParent()) {
            Path fixture = candidate.resolve("decoder/src/test/resources").resolve(resourcePath);
            if (Files.isRegularFile(fixture)) {
                return fixture;
            }
        }
        throw new AssertionError("Fixture not found: " + resourcePath);
    }
}
