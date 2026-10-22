# Canonical teapot inputs

The selected model is the classic 32-patch SGI/GLAUX Utah teapot. Its 10 base patches and 127 control points
are preserved in `control-points.json`, extracted without coordinate changes from the vendored
[`glut_teapot.c`](glut_teapot.c). The manifest pins that file's upstream commit and SHA-256.
The C source is an audit reference, not compiled or linked into the app.

The same base tables were compared numerically with `TEAPOT.H` in the public
[GLAUX archive at revision 78dfb19](https://github.com/tdechaize/Glaux/tree/78dfb19b946cebb2e3a7ff124c9daea6f5b78d5a/src).
All patch indices and all coordinate values match. Its `TEAPOT.C` calls `solidTeapot(14, scale)` and applies:

```text
rotate X by 270 degrees
scale by 0.5 * callerScale
translate raw Z by -1.5
```

The independent generator applies these transforms analytically: `(x/2, (z-1.5)/2, -y/2)`.
The renderer supplies `callerScale = 2.5 * pipeRadius`, based on the existing pipes reference analysis.
There is no lid enlargement or extra normalization. Decimal source values are evaluated in double precision
and rounded to float32 for storage. This preserves the published values; it does not simulate every rounding
step of a particular legacy driver's evaluator.

Each rim/body/lid/bottom base patch produces four reflected patches. Handle/spout patches produce two.
Single-axis reflections reverse the column parameter. Normals and triangle indices are converted to the app's
outward/CCW convention, avoiding per-object front-face state changes. Back-face culling stays enabled.
The generator removes collapsed pole triangles and retains the historic lid gap and intersecting components.
It does not add interiors or make the surface watertight.

The 14-by-14 grid produces 7,200 indexed vertices and 12,432 triangles. A quad is split along its first-to-third
vertex diagonal. Exact quad rasterization and singular-point normal behavior of the XP driver remain unverified.
GLAUX source evidence supports this reconstruction, but an authenticated XP binary/source comparison has not
been obtained. Do not describe it as proven bit-for-bit XP geometry or pixels.

## Generate and verify

No network access or third-party Python packages are required:

```text
python -I tools/gen_teapot.py
python -I tools/gen_teapot.py --check
python -I tools/test_teapot.py
gradlew testDebugUnitTest lintRelease assembleDebug assembleRelease
```

The generator verifies canonical source/data hashes. Every Android build verifies those hashes and the release
asset hash. When intentionally changing source, review provenance, run the independent tests, and update the
manifest explicitly. Git attributes retain LF bytes for these hashed inputs across Windows/Linux checkouts.

The Python tests parse the original C tables independently and use Bernstein polynomials instead of the
generator's de Casteljau evaluator. They check every stored vertex and normal, all patches, reflected topology,
matching source edges, random interior samples, outward body normals, packed-normal precision, and deterministic
asset output. Java tests cover the runtime parser, camera envelope, spawn paths, and scene capacity.

## Inspect on Android

Install a debug build. These diagnostic extras are ignored by release builds:

```text
adb shell am start -n io.github.thewombraider.pipestv/.PipesActivity --es fixture teapot --ei material 8 --ef turn 25 --ef pitch 15
```

`--es meshview wire|normals|solid` selects an inspection view. `--ez ortho false` enables perspective.
`--ei material 0` through `15` select materials. `--ef turn` and `--ef pitch` control orientation.
Force-stop before each launch so new extras create a new renderer. Normal preview runs accept `--el seed 1975`
in debug builds for repeatable pipe scenes. Normal user settings and teapot frequency are unchanged.

For captures and a 30-minute mixed plus 10-minute forced-teapot soak:

```text
python -I tools/teapot_qa.py --serial emulator-5570 --out shots/teapot-qa
```

The QA capture tool requires Pillow (`python -m pip install Pillow`); generation and numerical tests use only
the Python standard library. The command operates on the explicitly named device. It starts preview activities and writes screenshots,
process/GL/fallback checks, sampled presentation intervals, memory samples, and logs. CPU logging measures
submission time, not GPU completion. Sampled SurfaceFlinger intervals do not establish continuous 60 fps.
Keep that distinction in QA reports. `--mixed-seconds` and `--forced-seconds` override test durations.

See [the implementation spec](../../docs/design/2026-10-21-teapot-fidelity-design.md)
and [the QA report](../../docs/teapot-qa.md) for evidence and remaining limits.

## Notices

This is Martin Newell's Utah teapot, developed at the University of Utah, in its later classic SGI/GLAUX form.
See the [official model archive](https://graphics.cs.utah.edu/teapot/) for historical variants.
`LICENSE-SGI.txt` preserves the source's copyright and permission notice. `LICENSE-threejs.txt` preserves the
MIT notice for the previous tessellator, which informed this project's earlier implementation. Both are also
included with the APK in `teapot-notices.txt`.
