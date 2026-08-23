# Bundled BasisU Binaries

This directory contains `basisu` encoder executables built from the tracked
`basis_universal` submodule:

- `darwin-aarch64/basisu`
- `darwin-x86_64/basisu`
- `linux-aarch64/basisu`
- `linux-x86_64/basisu`
- `windows-aarch64/basisu.exe`
- `windows-x86_64/basisu.exe`

The Gradle release gate verifies that all six files are present:

```bash
./gradlew :basis-gradle-plugin:verifyBundledBasisuBinaries
```
