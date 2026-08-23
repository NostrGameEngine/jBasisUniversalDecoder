package org.ngengine.basis;

/**
 * ETC1S endpoint palette entry.
 */
final class Etc1sEndpoint {
    private final int red5;
    private final int green5;
    private final int blue5;
    private final int intensity5;

    Etc1sEndpoint(int red5, int green5, int blue5, int intensity5) {
        this.red5 = red5 & 31;
        this.green5 = green5 & 31;
        this.blue5 = blue5 & 31;
        this.intensity5 = intensity5 & 7;
    }

    int getRed5() {
        return red5;
    }

    int getGreen5() {
        return green5;
    }

    int getBlue5() {
        return blue5;
    }

    int getIntensity5() {
        return intensity5;
    }
}
