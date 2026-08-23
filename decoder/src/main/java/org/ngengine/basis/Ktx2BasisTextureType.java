package org.ngengine.basis;

import java.util.EnumSet;
import java.util.Objects;

/**
 * Java parity representation of {@code basist::basis_texture_type}.
 */
public enum Ktx2BasisTextureType implements CodedEnum {
    cBASISTexType2D(0),
    cBASISTexType2DArray(1),
    cBASISTexTypeCubemapArray(2),
    cBASISTexTypeVideoFrames(3),
    cBASISTexTypeVolume(4),
    cBASISTexTypeTotal(5),
    ;

    private final int code;

    Ktx2BasisTextureType(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    public String getNativeName() {
        return name();
    }

    public static Ktx2BasisTextureType fromCode(int code) {
        return EnumCodeResolver.fromCode(Ktx2BasisTextureType.class, code,
                "Unsupported basis_texture_type code: ");
    }

    public static EnumSet<Ktx2BasisTextureType> fromBits(int bits) {
        EnumSet<Ktx2BasisTextureType> result = EnumSet.noneOf(Ktx2BasisTextureType.class);
        for (Ktx2BasisTextureType value : values()) {
            if (value != cBASISTexTypeTotal && (bits & (1 << value.code)) != 0) {
                result.add(value);
            }
        }
        return result;
    }

    public static int toBits(Ktx2BasisTextureType... values) {
        int bits = 0;
        for (Ktx2BasisTextureType value : values) {
            Objects.requireNonNull(value, "value");
            if (value != cBASISTexTypeTotal) {
                bits |= (1 << value.code);
            }
        }
        return bits;
    }
}