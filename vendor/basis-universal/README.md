# Local encoder patches

The `basis_universal` submodule stays pinned to the official upstream commit
recorded in `manifest.json`. These patches preserve the UASTC LDR 4x4 transfer
flag and rectangular DDS mip tails, and reject failed DDS reads without invalid
frees or cached partial data. Upstream copyright and license notices remain in
the source files.

Prepare a verified source copy without modifying the submodule:

```sh
./gradlew prepareBasisUniversalSource
```

The parity harness and bundled encoder builds use that patched copy. To rebuild
an encoder, run:

```sh
python3 scripts/build-bundled-basisu.py --platform darwin-aarch64
```

Darwin builds require macOS and Xcode; `darwin-x86_64` is also supported. Linux
builds use GCC, with `g++-aarch64-linux-gnu` and `binutils-aarch64-linux-gnu`
required for cross compiling `linux-aarch64` on x86_64. Windows builds use LLVM
MinGW, for example:

```sh
python3 scripts/build-bundled-basisu.py --platform windows-aarch64 --llvm-mingw /path/to/llvm-mingw
```

Build all six platforms before packaging. The plugin packaging check verifies
the binary hashes and their patch recipe. The source patches, base commit and
licenses are included with the plugin.
