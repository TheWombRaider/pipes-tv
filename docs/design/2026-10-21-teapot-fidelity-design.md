# Teapot fidelity: audit and proposed implementation

Date: 2026-10-21. Status: **approved and implemented**; evidence in [docs/teapot-qa.md](../teapot-qa.md).

Baseline: `211958862bbc2b58ebf28ef377bed7116d0afcd4`. The working tree was clean when this audit began.
This document records research, measured asset properties, and acceptance criteria. No app code or asset has
been changed. Implementation, device installation, and publication follow review.

## Outcome and fidelity target

Reproduce the Utah teapot used by the original Windows 3D Pipes screensaver, including its shape, placement,
surface orientation, and appearance at pipe scale. Preserve the historical model's imperfections where they
belong to that target. Do not replace it with a modern, repaired teapot because it looks smoother.

**Recommended decision:** target the Windows/GLAUX-era teapot, rather than Newell's earliest 1975 variant.
These are different models. The [University of Utah's official archive](https://graphics.cs.utah.edu/teapot/)
distinguishes Newell's taller, open-bottom model from later versions with changed proportions and a bottom.
Its current downloadable model also offers modern repairs. Selecting a download without recording its version
and options would leave the target ambiguous.

“1:1” means identical selected control-point values and patch topology, followed by documented original
transforms and subdivision behavior. Android and legacy OpenGL need not produce bit-identical pixels on
different GPUs. Screenshots alone cannot establish identical geometry.

## Research and evidence

| Source | What it establishes | Limit |
|---|---|---|
| [University of Utah model archive](https://graphics.cs.utah.edu/teapot/) | Authoritative model lineage and historical variants; legitimate model source. | Does not establish which data or rendering parameters shipped in XP. |
| [three.js r160 TeapotGeometry](https://raw.githubusercontent.com/mrdoob/three.js/r160/examples/jsm/geometries/TeapotGeometry.js) | The current asset's reproducible source; 32 patches and an optional lid enlargement. | A modern tessellator, not proof of Windows fidelity. |
| [Mark Kilgard's GLUT teapot source](https://github.com/markkilgard/glut/blob/master/lib/glut/glut_teapot.c) | Published classic patch tables, reflections, transforms, and source notices. | This revision uses a seven-step solid grid; GLUT is not interchangeable with Microsoft GLAUX. |
| [SspipesSharp](https://github.com/RussDev7/SspipesSharp), inspected local reference checkout | Secondary evidence: its GLAUX transcription uses a 14-step grid, a 270-degree X rotation, half-scale, and a raw Z offset of -1.5. Its pipes port supplies scale 2.5 times pipe radius. | GPL reference consulted for facts only. Its claims still need corroboration against legitimate original documentation/data. |

The audit has **not authenticated the exact model tables and evaluator settings shipped in XP**. Finding the
Utah model's origin is not the same as proving that final connection. This is a required implementation gate,
not a claim to hide behind a finished screenshot.

Before selecting replacement data:

1. Locate a legitimately published GLAUX/SDK source or other authoritative record for the teapot and its caller.
   Record edition, path, URL, file hashes, applicable notices, and the Windows version it supports.
2. Compare its expanded control-point patches numerically with the classic GLUT tables and current three.js data.
   Account explicitly for symmetry, patch ordering, parameter direction, and coordinate conventions.
3. Establish original scale, origin, subdivision, winding, culling, normal generation, and orientation at insertion.
4. Record any remaining gap between NT-era source evidence and XP behavior. Use authenticated XP reference
   imagery to corroborate visible behavior, without claiming imagery proves hidden control points.
5. If exact XP provenance remains unavailable, report that limitation before declaring “1:1.” Do not silently
   promote a plausible substitute into a verified original.

Existing project restrictions still apply: no leaked Windows source, no disassembly, and no GPL code copied into
the MIT app. Preserve legitimate Newell, Utah, and third-party source attribution.

## Measured baseline

The current generator was run against a fresh download of three.js r160, writing only to a temporary audit
directory. Its output matches the committed asset byte-for-byte. This proves reproducibility against that
source, not correctness against XP. A separate binary inspection measured the following:

| Property | Current value |
|---|---|
| Indexed vertices | 2,592 |
| Triangles | 4,032 |
| Indices | 12,096, unsigned 16-bit |
| Asset size | 86,408 bytes |
| Subdivision | Eight intervals on each patch edge |
| X bounds | -1.90476191 to 2.17978048 |
| Y bounds | -1 to 1 |
| Z bounds | -1.26984131 to 1.26984131 |
| Stored normal lengths | 0.999999963 to 1.000000041 |

SHA-256:

```text
three.js r160 TeapotGeometry.js
650c0b4224e4c4082b0877e6c4793d14f1f33e88c5151abeae7a1ba2cf518ad4

tools/gen_teapot.py
961a040cc6ecc641ca2df1732fb4d5baae7eb76e75201bd63f25a9cef7c472a0

app/src/main/assets/teapot.bin
a834446001d50742c536d911ab3e285fc550fe422614d449d8c67db8a85e34e2
```

## Findings in this repository

Paths below are relative to the repository. Severity refers to the requested fidelity and QA standard.

| Priority | Location | Finding and consequence |
|---|---|---|
| High | `tools/gen_teapot.py:68` | Lid patches are enlarged by 1.077 in both horizontal axes. This is an intentional three.js repair, not unmodified historical data. Remove unless original-target evidence requires it. |
| High | `tools/gen_teapot.py:33`, `:102`; `PipesRenderer.java:330` | Model normalization centers raw height at 1.575; the secondary GLAUX reference centers at 1.5. Scale and origin need an explicit, independently checked transform. |
| High | `tools/gen_teapot.py:16`; reference comparison | Eight subdivisions are fixed by the current asset, while the GLAUX port specifies 14. Resolve original tessellation before choosing triangle count. More triangles alone do not prove fidelity. |
| High | `PipesRenderer.java:122` | Any asset-loading exception silently replaces the teapot with a sphere. A broken release can appear operational while losing the required model. |
| High | `MeshesTest.java:36`, `:99` | Counts, broad bounds, signed volume, and winding/normal agreement do not prove the original shape. Winding and normals can both point inward and still agree. No independent control-point oracle exists. |
| Medium | `Meshes.java:128` | Loader trusts counts and indices, reads without a size bound, and accepts trailing data. Malformed bundled assets can cause unchecked failures or excessive allocations. |
| Medium | `PipesRenderer.java:47`, `:258` | Batch capacity is 640,000 vertices. Overflow stops later uploads; staging writes happen before the capacity check. Higher tessellation and forced teapots require explicit capacity and staging-buffer QA. |
| Medium | `PipesRenderer.java:116`; generator normals | Global back-face culling and quantized runtime normals need comparison with the target. Open historical surfaces can expose back faces. Do not globally disable culling as an unmeasured fix. |
| Medium | `CameraTest.java:11` | Camera clearance uses sphere padding, not the larger, asymmetric teapot envelope. Teapots at lattice boundaries are untested. |
| Medium | `tools/gen_teapot.py:20` | External JavaScript is parsed without a pinned input manifest or useful validation errors. Historical minus-sign regression demonstrates why independent data checks matter. |
| Medium | `NOTICE` | three.js is credited, but its full [MIT permission notice](https://raw.githubusercontent.com/mrdoob/three.js/r160/LICENSE) is absent. Preserve the required source notices with any redistributed substantial material. Review the chosen data source's notices before changing provenance. |

The renderer's scale comment is misleading, but the numerical scale is close to the secondary GLAUX reference.
Let `R` be pipe radius. Current normalization and render scaling combine to:

```text
current raw-coordinate scale = (2 / 3.15) * (2.5 R / 1.27) = 1.24984377 R
candidate GLAUX scale         = 0.5 * 2.5 R               = 1.25 R
ratio                        = 0.999875016
```

That is approximately 0.0125% smaller, not a factor-of-two size error. The origin difference is approximately
0.09375 pipe radii vertically, before the small scale difference. Confirm both against the chosen target.
Replace the `1.27` approximation with a derived transform if evidence supports the GLAUX convention.

The unused `blinn_scale = 1.3` variable does not by itself prove incorrect height: the selected three.js data
already follows its default squat proportions. Do not multiply height by 1.3 without changing the target.

## Proposed implementation

### Source data and generation

Track the selected canonical patch/control-point data, its provenance manifest, and required license text.
Pin upstream revisions and hashes. Generation must work offline from reviewed inputs; remote downloads must
never silently change a release asset.

Preserve exact decimal control-point values and reflected patch topology. Any reordering must retain a mapping
back to source patch and parameter coordinates. Keep lid, body, handle, spout, and bottom independently auditable.
Do not weld, repair gaps, add interiors, smooth intentional seams, or alter proportions without source evidence.

Use one documented conversion from source coordinates to app coordinates. Name scale and origin constants by
their physical meaning. Document how the conversion combines with `2.5 R` at insertion. Preserve original
orientation relative to pipe axes if verified; do not invent random teapot rotations.

Validate generator inputs and subdivision bounds with explicit errors. Check patch indices, finite values,
expected source hashes, output index range, and deterministic binary layout. Handle singular pole normals through
a documented limit matching the selected surface. Distinguish collapsed pole triangles from unintended zero-area
triangles. Do not require a watertight mesh where the historical surface is open or intersecting.

Choose subdivision after the source gate. If 14 intervals are confirmed and the current triangle scheme applies,
the expected count is 12,432 triangles, or 37,296 expanded vertices per teapot. Sixteen such teapots consume
596,736 vertices before any other geometry; 18 exceed the current batch capacity. Capacity work must precede
enabling a denser asset. Match the original cell diagonal and evaluator behavior where recoverable.

### Loading and rendering

Keep the binary format unless a concrete requirement demands a change. Validate header size, bounded counts,
overflow-safe expected length, triangle alignment, finite attributes, and index bounds before allocating expanded
arrays. Reject truncation and unexpected trailing bytes with descriptive checked errors.

Build/test validation must reject an invalid or missing release asset. Production may keep a documented fallback
to preserve screensaver availability, but it must emit a clear error. Debug fidelity fixtures must fail visibly
instead of substituting a sphere. Catch expected loading failures rather than swallowing unrelated exceptions.

Verify final transformed positions, normal transformation, winding, culling, depth behavior, and the packed
normal/material representation. Use source evidence to decide treatment of visible back faces. Preserve correct
state for pipes drawn before and after a teapot. Avoid a shader or global rendering rewrite unless tests show need.

Calculate upload/staging bounds before writing. A normal or diagnostic teapot-heavy scene must not silently lose
geometry or overrun a buffer. Prefer a bounded solution supported by the actual maximum scene size; document
memory costs and any batching change.

### Simulation and diagnostic fixture

Preserve default multiple pipes and elbow turns, existing preview options, and current spawn probability.
Teapots enter through both mixed-mode joints and boxed-in pipe starts. Exercise both paths independently.
The existing boosted probability extra does not provide an isolated geometry reference.

Add a debug-only deterministic fixture with one teapot, fixed camera, seed, light, material, and scale reference.
Support orthographic inspection, wireframe/normal views, and reproducible rotations. Keep diagnostics out of the
normal TV settings flow. Use a separate seeded pipe scene for attachment and occlusion checks.

## Acceptance tests and evidence

| Area | Required check | Pass condition |
|---|---|---|
| Provenance | Compare selected source tables and expanded patches; preserve source files, revision, hashes, and notices. | Every patch/control point maps to the target. Any unresolved XP-specific claim is explicitly documented. |
| Independent geometry | Implement a test oracle using direct Bernstein evaluation, independently from the production matrix evaluator. Sample every output vertex plus seeded interior UV points on all patches. | Position error at most `1e-6` source units before float32 encoding; at most `2e-6 * max(1, abs(expected coordinate))` after encoding. Tighten if baseline supports it; never loosen merely to pass. |
| Normals | Compare analytic derivatives with independent derivatives/finite differences away from singularities; test pole limits separately. | Finite unit normals within `1e-5`; dot product at least `0.99999` against the independent outward reference outside singular neighborhoods. Packed normals checked against quantization limits separately. |
| Topology | Verify patch identity, reflections, boundaries, crease policy, cell diagonals, winding, and pole degeneracies. | No unintended missing, folded, mirrored, or reversed patches. Preserve authentic gaps and intersections. |
| Placement | Test final world coordinates for known source landmarks and the complete transformed envelope. | Matches documented target affine transform within float32 tolerance; no approximate normalization constants. |
| Parser | Exercise short headers, bad/overflowing counts, partial floats/indices, out-of-range indices, nonfinite values, and trailing bytes. | Bounded rejection with useful errors; no silent sphere in debug tests. |
| Appearance | Capture front, back, both sides, top, bottom, and oblique closeups; inspect lid, spout mouth, handle, underside, and pipe attachment. | No introduced cracks, disappearing faces, inverted lighting, clipping, or unintended repairs. Review against the selected reference under matched camera/light conditions. |
| Materials | Sweep all 16 existing materials and representative scene rotations. | Highlights and silhouettes remain correct after packed-normal conversion; no material or GL-state bleed. |
| Spawn paths | Deterministic mixed joint, boxed-in start, and normal default scenes. | Correct teapot at the correct location; defaults, frequency, endcaps, and other joint types retain their behavior. |
| Camera | Check transformed teapot envelope at lattice extremes through a full camera turn, in landscape, 4:3, and portrait test layouts. | No near/far-plane or unintended view-edge clipping under the supported camera contract. |
| Capacity | Fill near batch/staging limits, add a teapot across the boundary, and run forced-teapot stress scenes. | No buffer exception, silent missing geometry, or unbounded growth. |
| Lifecycle | Recreate EGL context, resize, enter/leave preview, and start/stop the DreamService repeatedly. | Asset and GPU resources restore correctly; no stale state or accumulating allocations. |
| Device validation | Run emulator and physical TCL checks with the same deterministic captures, plus a 30-minute mixed-scene run and a separate 10-minute forced-teapot stress run. | No crashes, GL errors, fallback events, or unexplained geometry loss. Record frame times, memory, device/build, durations, and screenshots. |

Legacy evaluator precision may produce small differences. Investigate and record those differences before changing
the numerical contract. Do not use a screenshot similarity score as a substitute for the geometry tests.

Run the project's unit tests, release lint, and debug/release builds after implementation. Measure performance
against the current build on the same device and scene. These measurements protect integration quality; they
do not require a README comparison with previous versions. Physical budget sticks remain unverified unless
actually tested. If the selected mesh misses the existing performance target, report the conflict before reducing
fidelity or claiming success.

## Work sequence and review deliverables

1. Resolve and record the model/source gate. Present any material target ambiguity before replacing the asset.
2. Add pinned data, notices, independent numerical tests, and reproducible generation.
3. Integrate exact placement, loader diagnostics, safe buffer handling, and deterministic inspection fixtures.
4. Complete geometry, simulation, capacity, lifecycle, and device QA. Capture reproducible evidence.
5. Update `NOTICE` and the teapot section of `docs/REFERENCE_ANALYSIS.md` with verified facts and remaining limits.
   Remove inaccurate “closed mesh” and size comments. Keep product claims within demonstrated evidence.

Expected changes center on `tools/gen_teapot.py`, a canonical data/provenance directory, `teapot.bin`, `Meshes.java`,
`PipesRenderer.java`, mesh/camera tests, diagnostic fixture code, and source notices. Change `PipeSim.java` only
as needed for deterministic test access; behavioral changes require separate justification.

Completion requires a reviewable source manifest, deterministic asset hash, independent numerical report,
before/after inspection images, test/build results, and device QA results. Label anything not verified. Preserve
existing work throughout. Commit/push authorization for this implementation is separate from this draft review.

**Owner review:** approve the Windows/GLAUX-era fidelity target and this evidence-first implementation scope.
If “original” instead means the 1975 taller, open-bottom model, revise the target before coding.
