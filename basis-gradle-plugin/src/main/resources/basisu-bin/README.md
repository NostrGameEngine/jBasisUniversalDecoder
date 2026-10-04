# Bundled BasisU Binaries

This directory contains `basisu` encoder executables built from the tracked
`basis_universal` submodule with the patches in `vendor/basis-universal`:

- `darwin-aarch64/basisu`
- `darwin-x86_64/basisu`
- `linux-aarch64/basisu`
- `linux-x86_64/basisu`
- `windows-aarch64/basisu.exe`
- `windows-x86_64/basisu.exe`

The Gradle release gate verifies all six binaries against their source recipe
and hashes in `META-INF/native-basis/binaries.json`:

```bash
./gradlew :basis-gradle-plugin:verifyBundledBasisuBinaries
```

Run `./gradlew prepareBasisUniversalSource` and
`python3 scripts/build-bundled-basisu.py --platform <platform>` to rebuild an
encoder. See `vendor/basis-universal/README.md` for cross compiler requirements.
