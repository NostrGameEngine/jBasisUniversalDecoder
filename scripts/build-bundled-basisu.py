#!/usr/bin/env python3
"""Build one bundled encoder from the prepared, patched upstream source."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import platform
import shutil
import subprocess


def digest(path):
    value = hashlib.sha256()
    with path.open('rb') as stream:
        for block in iter(lambda: stream.read(65536), b''):
            value.update(block)
    return value.hexdigest()


def main():
    root = Path(__file__).resolve().parents[1]
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--platform', required=True, choices=[
        'darwin-aarch64', 'darwin-x86_64', 'linux-aarch64', 'linux-x86_64',
        'windows-aarch64', 'windows-x86_64'])
    parser.add_argument('--jobs', type=int, default=2)
    parser.add_argument('--llvm-mingw', type=Path, help='LLVM MinGW installation for Windows builds')
    args = parser.parse_args()
    if args.jobs < 1:
        parser.error('--jobs must be positive')
    recipe = json.loads((root / 'vendor/basis-universal/manifest.json').read_text())
    prepared = root / 'build/basisu-patched/src'
    for path, row in recipe['sourceFiles'].items():
        if not (prepared / path).is_file() or digest(prepared / path) != row['patchedSha256']:
            parser.error('Run ./gradlew prepareBasisUniversalSource before building encoders')
    os_name, arch = args.platform.split('-')
    build_root = root / 'build/bundled-basisu' / args.platform
    source = build_root / 'src'
    shutil.copytree(prepared, source, dirs_exist_ok=True)
    definitions = [
        '-DCMAKE_BUILD_TYPE=Release', '-DBASISU_EXAMPLES=OFF', '-DBASISU_OPENCL=OFF',
        '-DBASISU_SSE=OFF', '-DBASISU_ZSTD=ON', '-DBASISU_BUILD_PYTHON=OFF',
        '-DCMAKE_C_FLAGS_RELEASE=-O2 -DNDEBUG -ffp-contract=off',
        '-DCMAKE_CXX_FLAGS_RELEASE=-O2 -DNDEBUG -ffp-contract=off']
    environment = os.environ.copy()
    if os_name == 'darwin':
        if platform.system() != 'Darwin':
            parser.error('Darwin builds require Xcode on macOS')
        definitions += ['-DCMAKE_OSX_ARCHITECTURES=' + ('arm64' if arch == 'aarch64' else arch),
                        '-DCMAKE_OSX_DEPLOYMENT_TARGET=' + ('11.0' if arch == 'aarch64' else '10.15')]
    elif os_name == 'linux':
        if platform.system() != 'Linux':
            parser.error('Linux builds require a Linux host')
        definitions += ['-DBASISU_STATIC=ON']
        if arch == 'aarch64' and platform.machine() not in ('aarch64', 'arm64'):
            definitions += ['-DCMAKE_SYSTEM_NAME=Linux', '-DCMAKE_SYSTEM_PROCESSOR=aarch64',
                            '-DCMAKE_C_COMPILER=aarch64-linux-gnu-gcc',
                            '-DCMAKE_CXX_COMPILER=aarch64-linux-gnu-g++']
            strip = shutil.which('aarch64-linux-gnu-strip')
            if strip is None:
                parser.error('Install the AArch64 GNU compiler and binutils')
            toolbin = build_root / 'toolbin'
            toolbin.mkdir(parents=True, exist_ok=True)
            shim = toolbin / 'strip'
            if not shim.exists():
                shim.symlink_to(strip)
            environment['PATH'] = str(toolbin) + os.pathsep + environment['PATH']
        elif arch != platform.machine():
            parser.error('The requested Linux architecture requires a matching compiler')
    else:
        if args.llvm_mingw is None:
            parser.error('Windows builds require --llvm-mingw')
        prefix = args.llvm_mingw.resolve() / 'bin' / (arch + '-w64-mingw32-')
        definitions += ['-DCMAKE_SYSTEM_NAME=Windows', '-DCMAKE_SYSTEM_PROCESSOR=' + arch,
                        '-DCMAKE_C_COMPILER=' + str(prefix) + 'clang',
                        '-DCMAKE_CXX_COMPILER=' + str(prefix) + 'clang++', '-DBASISU_STATIC=ON']
    cmake_build = build_root / 'cmake-build'
    subprocess.run(['cmake', '-S', str(source), '-B', str(cmake_build)] + definitions,
                   env=environment, check=True)
    subprocess.run(['cmake', '--build', str(cmake_build), '--target', 'basisu',
                    '--parallel', str(args.jobs)], env=environment, check=True)
    name = 'basisu.exe' if os_name == 'windows' else 'basisu'
    resources = root / 'basis-gradle-plugin/src/main/resources'
    destination = resources / 'basisu-bin' / args.platform / name
    destination.parent.mkdir(parents=True, exist_ok=True)
    shutil.copy2(source / 'bin' / name, destination)
    metadata = resources / 'META-INF/native-basis/binaries.json'
    metadata.parent.mkdir(parents=True, exist_ok=True)
    record = json.loads(metadata.read_text()) if metadata.exists() else {}
    if record.get('upstreamBase') != recipe['upstreamBase'] or record.get('patches') != recipe['patches']:
        # A changed source recipe requires rebuilding every platform.
        record = {}
    record['upstreamBase'] = recipe['upstreamBase']
    record['patches'] = recipe['patches']
    record.setdefault('binaries', {})[args.platform] = {
        'path': args.platform + '/' + name, 'sha256': digest(destination),
        'bytes': destination.stat().st_size}
    metadata.write_text(json.dumps(record, indent=2, sort_keys=True) + '\n')
    print('Built', destination, flush=True)


if __name__ == '__main__':
    main()
