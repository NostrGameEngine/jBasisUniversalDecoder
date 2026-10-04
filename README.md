# jBasisUniversalDecoder

Standalone Java decoder/transcoder port for Basis Universal, and Gradle plugin for build-time texture conversion.

> [!NOTE]  
> The original code was ported primarily by GPT-5.5, with minimal human intervention limited to testing and patching.

## Modules

- `decoder`: pure Java runtime API and implementation. This is the artifact to
  embed in applications and engine integrations.
- `parity-tests`: end-to-end parity harness. It builds the reference `basisu`
  tool from the tracked `basis_universal` submodule plus the local encoder
  patches in `vendor/basis-universal` in `build/`, encodes test
  PNGs, unpacks them with the reference tool, decodes the same payload with Java,
  and compares RGBA pixels plus packed RGB565/BGR565/RGBA4444 output derived
  from the reference unpack.
- `basis-gradle-plugin`: Gradle plugin for build-time texture
  conversion. It scans resources, writes generated `.basis` files under
  `build/generated`, and wires them into the runtime classpath without modifying
  developer source resources.
- `basis_universal`: Git submodule pinned to the official Basis Universal
  reference repository.

## Build

```bash
./gradlew :decoder:test
./gradlew :parity-tests:test
./gradlew parityTest
./gradlew check
```
 
> [!TIP]
> Running `parityTest` requires a native C++ toolchain with CMake.

## Publishing

The `decoder` library and the `basis-gradle-plugin` are published with the
`org.ngengine.basis` Maven group. Local development defaults to
`1.0.0-SNAPSHOT`; release automation supplies the version from the GitHub
release tag.

GitHub Actions publishes `1.0.0-SNAPSHOT` to the Central Portal snapshot
repository after every successful commit on `master`. A Maven Central release
is published when a GitHub Release is published, using a tag such as `v1.0.0`.

The Central Portal namespace `org.ngengine.basis` must be verified and have
snapshot publishing enabled before the workflows can deploy. Configure these
repository secrets:

- `GPG_PRIVATE_KEY`
- `GPG_PASSPHRASE`
- `SONATYPE_USERNAME`
- `SONATYPE_PASSWORD`

For local snapshot publication, run:

```bash
./gradlew publishSnapshots -PprojectVersion=1.0.0-SNAPSHOT
```

## Supported formats

The runtime decoder is platform and rendering-engine agnostic. Callers must
choose a `BasisTranscodeTarget` explicitly; the decoder never inspects
GPU capabilities, identifies a runtime platform, or selects a fallback format.
Platform-specific target ordering belongs in the consuming engine or
application.

- `.basis` ETC1S to RGBA8, RGB565/BGR565/RGBA4444, ETC1/ETC2,
  ETC2 EAC R11/RG11, BC1/BC3/BC4/BC5/BC7
- `.basis` UASTC LDR 4x4 to RGBA8, RGB565/BGR565/RGBA4444,
  ETC1/ETC2, ETC2 EAC R11/RG11, BC1/BC3/BC4/BC5/BC7, and ASTC 4x4
- KTX2 ETC1S to RGBA8, RGB565/BGR565/RGBA4444, ETC1/ETC2,
  ETC2 EAC R11/RG11, BC1/BC3/BC4/BC5/BC7
- KTX2 UASTC LDR 4x4 to RGBA8, RGB565/BGR565/RGBA4444,
  ETC1/ETC2, ETC2 EAC R11/RG11, BC1/BC3/BC4/BC5/BC7, and ASTC 4x4, including
  selected array images
- KTX2 XUASTC LDR to RGBA8, RGB565/BGR565/RGBA4444,
  ETC1/ETC2, ETC2 EAC R11/RG11, BC1/BC3/BC4/BC5, matching ASTC LDR block
  sizes covered by the native-gold fixture suite, default BC7 for validated
  4x4, 5x4, 5x5, 6x5, 6x6, 8x5, and 8x6 streams, including the dedicated
  4x4, 6x6, and 8x6 fast paths, plus the same validated block sizes on the
  `cDecodeFlagXUASTCLDRDisableFastBC7Transcoding` generic path, BC7
  no-deblock parity for 8x8, 10x5, 10x6, 10x8, 10x10, 12x10, and 12x12
  streams when `cDecodeFlagsNoDeblockFiltering` is requested, and reference
  parity for BC7 high-quality and whole-image deblocked transcoding paths
  covered by the parity fixture suite
- `.basis` UASTC HDR 4x4, ASTC HDR 6x6, and UASTC HDR 6x6 intermediate to
  matching ASTC HDR GPU payloads (`ASTC_HDR_4X4`/`ASTC_HDR_6X6`) and CPU HDR
  payloads (`RGB_HALF`/`RGBA_HALF`/`RGB_9E5`), plus HDR to `BC6H`, with parity
  against the official Basis Universal transcoder for default and high-quality
  6x6 BC6H paths
- KTX2 UASTC HDR 4x4, ASTC HDR 6x6, and UASTC HDR 6x6 intermediate
  (`KTX2_SS_UASTC_HDR_6x6I`, plus legacy v1.6/v2.0 `BASISLZ`-marked 6x6I
  containers) to matching ASTC HDR GPU payloads and CPU HDR payloads
  (`RGB_HALF`/`RGBA_HALF`/`RGB_9E5`), plus HDR to `BC6H`, including the
  standard KTX2 Zstandard-compressed level payloads emitted by `basisu`, with
  parity against the official Basis Universal transcoder for default and
  high-quality 6x6 BC6H paths
- plain uncompressed KTX2 RGBA payload pass-through, plus Java materialization
  of KTX2 NONE, Zstandard, and Deflate/Zlib level payloads for direct
  Java-supported formats
- KTX2 Basis Universal supercompression schemes used by supported texture
  payloads: `BASISLZ`, `XUASTC_LDR`, and `UASTC_HDR_6x6I`

### Unsupported formats

| Format family | Basis Universal targets | Java status | Reason |
| --- | --- | --- | --- |
| PVRTC1 | `cTFPVRTC1_4_RGB`, `cTFPVRTC1_4_RGBA` | Not implemented | Legacy PowerVR/mobile path; not a target for the standalone runtime. |
| PVRTC2 | `cTFPVRTC2_4_RGB`, `cTFPVRTC2_4_RGBA` | Not implemented | Narrow hardware support and low integration value for the planned loader. |
| ATC | `cTFATC_RGB`, `cTFATC_RGBA` | Not implemented | Obsolete vendor-specific mobile format. |
| FXT1 | `cTFFXT1_RGB` | Not implemented | Obsolete desktop/vendor-specific format. |


## Gradle Plugin

The repository includes a plugin for converting textures at build time using the native `basisu` tool (for macOS, Linux, and Windows on `aarch64` and `x86_64`).

Apply the `org.ngengine.basis.texture-encoder` plugin to your Java project. By default, the plugin scans `src/main/resources` for common image formats and generates corresponding `.basis` files in the build directory.

The plugin can be configured in your `build.gradle` file as follows:

```groovy
basisTextures {
    imageExtensions.addAll('png', 'jpg', 'jpeg')
    resourceDirectories.add('src/main/resources')
    excludeOriginalsWhenEncoded = false
    encoderTimeoutSeconds = 300
    encoderLogBytes = 1048576L
}
```

Each image encoding has a wall timeout (1 to 86400 seconds) and a limit on
captured diagnostic output. Exceeding either limit fails the task and removes
the partial texture. Failed encodes retain a bounded diagnostic log under the
task's temporary directory; exception messages include at most 16 KiB. Cleanup
stops the encoder and observed child processes within an additional five-second
budget.
