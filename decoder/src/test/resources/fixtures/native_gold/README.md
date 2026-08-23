# Native parity goldens

This directory contains reference output blobs produced by the original C++ Basis Universal transcoder.

Current files:

- `base_xuastc_zstd.dat`
- `base_xuastc_zstd_astc5x4.dat`
- `base_xuastc_arith.dat`
- `base_xuastc_arith_astc5x4.dat`
- `tough_xuastc*_astc*.dat`
- `kodim23_xuastc6x6_mip_arith_astc6x6.dat`
- `kodim_xuastc6x6_array_mip_arith_image1_astc6x6.dat`
- `kodim_xuastc6x6_cubemap_mip_arith_face5_astc6x6.dat`
- `kodim_uastc4x4_astc4x4.dat`
- `kodim23.dat`
- `kodim23_mip.dat`
- `kodim23_mip_etc2.dat`
- `kodim23_mip_bc1.dat`
- `kodim23_alpha.dat`
- `kodim23_alpha_bc3.dat`
- `kodim23_alpha_bc4.dat`
- `kodim23_alpha_bc5.dat`
- `kodim23_bc3.dat`
- `kodim23_bc4.dat`
- `kodim23_bc5.dat`
- `kodim23_bc7.dat`
- `kodim23_alpha_bc7.dat`
- `kodim20_alpha.dat`
- `kodim20_alpha_etc1.dat`
- `kodim20_alpha_etc2.dat`
- `kodim20_alpha_bc1.dat`
- `kodim20_alpha_bc3.dat`
- `kodim20_alpha_bc4.dat`
- `kodim20_alpha_bc5.dat`
- `kodim20_alpha_bc7.dat`
- `kodim20_bc3.dat`
- `kodim20_bc4.dat`
- `kodim20_bc5.dat`
- `kodim20_bc7.dat`
- `kodim20_mip.dat`
- `kodim20_mip_etc2.dat`
- `kodim20_mip_bc1.dat`
- `kodim_video_iframe0_etc2.dat`
- `kodim_video_iframe0_bc1.dat`
- `kodim_video_pframe1_etc2.dat`
- `kodim_video_pframe1_bc1.dat`
- `kodim20_21_array_image1.dat`
- `kodim_array_image1.dat`
- `kodim_array_image1_bc1.dat`
- `kodim_array_mip_image1.dat`
- `kodim_array_mip_image1_etc2.dat`
- `kodim_array_mip_image1_bc1.dat`
- `kodim_cubemap_face5.dat`
- `kodim_cubemap_face5_bc1.dat`
- `kodim_cubemap_array_image11_bc1.dat`
- `kodim_cubemap_mip_face5_etc2.dat`
- `kodim_cubemap_mip_face5_bc1.dat`
- `kodim_cubemap_basis_image5.dat`
- `kodim_cubemap_basis_image5_etc2.dat`
- `kodim_cubemap_basis_image5_bc1.dat`
- `kodim23_etc1.dat`
- `kodim23_etc2.dat`
- `kodim23_alpha_etc2.dat`
- `kodim23_bc1.dat`

The `base_xuastc_zstd.dat`, `base_xuastc_arith.dat`, and `kodim23.dat` binaries are
RGBA32 pixel payloads for level 0.
The `base_xuastc_*_astc5x4.dat` binaries are native ASTC LDR 5x4 transcode outputs
for the XUASTC fixtures.
`tough_xuastc*_astc*.dat` binaries are native ASTC LDR transcode outputs for
the XUASTC fixtures covering the standard ASTC LDR block sizes.
`kodim23_xuastc6x6_mip_arith_astc6x6.dat` is native all-level ASTC LDR 6x6
output for the mipped XUASTC LDR fixture
`fixtures/ktx2/kodim23_xuastc6x6_mip_arith.ktx2`.
`kodim_xuastc6x6_array_mip_arith_image1_astc6x6.dat` is native all-level ASTC
LDR 6x6 output for image/layer index 1 of the mipped XUASTC LDR 2D array
fixture `fixtures/ktx2/kodim_xuastc6x6_array_mip_arith.ktx2`.
`kodim_xuastc6x6_cubemap_mip_arith_face5_astc6x6.dat` is native all-level
ASTC LDR 6x6 output for face/image index 5 of the mipped XUASTC LDR cubemap
fixture `fixtures/ktx2/kodim_xuastc6x6_cubemap_mip_arith.ktx2`.
`kodim_uastc4x4_astc4x4.dat` is native ASTC LDR 4x4 output for the KTX2 UASTC
LDR 4x4 fixtures `fixtures/ktx2/kodim_uastc4x4.ktx2` and
`fixtures/ktx2/kodim_uastc4x4_zstd.ktx2`.
`kodim_uastc4x4.rgba8.dat` is native RGBA32 output for the same UASTC fixtures,
decoded with the C++ tool's UASTC LDR linear ASTC decode profile.
`kodim23_alpha.dat` is native RGBA32 output for a KTX2 ETC1S fixture with a
real alpha slice.
`kodim20_alpha.dat` is native RGBA32 output for a `.basis` ETC1S fixture with a
real alpha slice.
`kodim20_alpha_etc2.dat` is native ETC2 RGBA output for the same `.basis`
alpha-slice fixture.
`kodim20_alpha_etc1.dat` and `kodim20_alpha_bc1.dat` are native opaque target
outputs for the same `.basis` alpha-slice fixture; they use the RGB slice, not
the optional alpha-to-opaque decode flag.
`kodim20_alpha_bc3.dat` is native BC3 output for the same `.basis` alpha-slice
fixture, preserving alpha through the DXT5 alpha block. `kodim20_bc3.dat` is
native opaque-alpha BC3 output for `fixtures/basis/kodim20.basis`.
`kodim20_alpha_bc4.dat`, `kodim20_alpha_bc5.dat`, `kodim20_bc4.dat`, and
`kodim20_bc5.dat` are native RGTC outputs for the same `.basis` ETC1S fixtures.
BC4 uses the ETC1S red-channel conversion table; BC5 stores red in the first BC4
block and the alpha slice, or opaque 255 when absent, in the second BC4 block.
`kodim23_alpha_bc7.dat`, `kodim23_bc7.dat`, `kodim20_alpha_bc7.dat`, and
`kodim20_bc7.dat` are native BC7 mode 5 outputs for the same ETC1S fixtures,
generated with ETC1S chroma filtering disabled.
`kodim20_mip.dat`, `kodim20_mip_etc2.dat`, and `kodim20_mip_bc1.dat` are
native all-level RGBA32, ETC2 RGBA, and BC1 outputs for the mipped `.basis`
ETC1S fixture `fixtures/basis/kodim20_mip.basis`.
`kodim_video_iframe0_etc2.dat`, `kodim_video_iframe0_bc1.dat`,
`kodim_video_pframe1_etc2.dat`, and `kodim_video_pframe1_bc1.dat` are native
ETC2 RGBA and BC1 outputs for image/frame indices 0 and 1 of the `.basis`
video fixture `fixtures/basis/kodim_video.basis`; frame 1 is reconstructed from
the previous frame's ETC1S endpoint/selector state.
`kodim20_21_array_image1.dat` is native RGBA32 output for image index 1 of the
`.basis` 2D array fixture `fixtures/basis/kodim20_21_array.basis`.
`kodim_array_image1.dat` and `kodim_array_image1_bc1.dat` are native RGBA32
and BC1 outputs for image index 1 of the KTX2 ETC1S 2D array fixture
`fixtures/ktx2/kodim_array.ktx2`.
`kodim_array_mip_image1.dat`, `kodim_array_mip_image1_etc2.dat`, and
`kodim_array_mip_image1_bc1.dat` are native all-level RGBA32, ETC2 RGBA, and
BC1 outputs for image index 1 of the mipped KTX2 ETC1S 2D array fixture
`fixtures/ktx2/kodim_array_mip.ktx2`.
`kodim_cubemap_face5.dat` and `kodim_cubemap_face5_bc1.dat` are native RGBA32
and BC1 outputs for face/image index 5 of `fixtures/ktx2/kodim_cubemap.ktx2`.
`kodim_cubemap_array_image11_bc1.dat` is native BC1 output for image index 11,
which maps to layer 1 / face 5, of the mixed KTX2 cubemap-array fixture
`fixtures/ktx2/kodim_cubemap_array.ktx2`.
`kodim_cubemap_mip_face5_etc2.dat` and `kodim_cubemap_mip_face5_bc1.dat` are
native all-level ETC2 RGBA and BC1 outputs for face/image index 5 of the
mipped KTX2 ETC1S cubemap fixture `fixtures/ktx2/kodim_cubemap_mip.ktx2`.
`kodim_cubemap_basis_image5.dat` is native RGBA32 output for image index 5 of
`fixtures/basis/kodim_cubemap.basis`.
`kodim_cubemap_basis_image5_etc2.dat` and
`kodim_cubemap_basis_image5_bc1.dat` are native ETC2 RGBA and BC1 outputs for
image index 5 of `fixtures/basis/kodim_cubemap.basis`.
`kodim23_mip.dat`, `kodim23_mip_etc2.dat`, and `kodim23_mip_bc1.dat` are
native transcode outputs for all levels of a mipped KTX2 ETC1S fixture,
concatenated in mip level order.
`kodim23_etc1.dat`, `kodim23_etc2.dat`, `kodim23_bc1.dat`,
`kodim23_bc3.dat`, `kodim23_bc4.dat`, `kodim23_bc5.dat`, and
`kodim23_bc7.dat` are native
compressed transcode outputs for the same KTX2 ETC1S fixture.
`kodim23_alpha_etc2.dat`, `kodim23_alpha_bc3.dat`, `kodim23_alpha_bc4.dat`,
`kodim23_alpha_bc5.dat`, and `kodim23_alpha_bc7.dat` are native compressed
outputs for the alpha-slice KTX2 ETC1S fixture.

The dynamic end-to-end reference harness lives in the `parity-tests` module and
builds `basisu` from the tracked submodule before running.

To run Java-side parity for these checked-in goldens:

```bash
./gradlew :decoder:testClasses
java -cp decoder/build/classes/java/main:decoder/build/classes/java/test:decoder/build/resources/test org.ngengine.basis.ParityVerifier
```

To enforce strict match (exit non-zero on any mismatch/error):

```bash
java -cp decoder/build/classes/java/main:decoder/build/classes/java/test:decoder/build/resources/test org.ngengine.basis.ParityVerifier --strict
```
