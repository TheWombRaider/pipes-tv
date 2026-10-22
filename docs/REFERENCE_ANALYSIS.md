# Reference analysis

What the original 3D Pipes screensaver does, how each fact was established, and what this app does with it.

## Sources

**Footage.** Five public recordings of the original, each by a different uploader, downloaded only for analysis and
measured frame by frame with the scripts in `tools/reference/`. Nothing downloaded is part of this repository.

| Recording | Uploader's title | Video | Scenes | Pipes | Joints |
|---|---|---|---|---|---|
| [72zBfGRQlVA](https://youtu.be/72zBfGRQlVA) | 3D Pipes - Windows XP screensaver | 1080p, 30 fps doubled to 60, 6:41 | 33 | Multiple | Elbows |
| [MKqrLGFoK9E](https://youtu.be/MKqrLGFoK9E) | 3D pipes screensaver (1080p) | 1080p, 60 fps, 7:36 | 31 | Multiple | Mixed |
| [pBSq6RCJRCA](https://youtu.be/pBSq6RCJRCA) | Windows 98 Pipes Screensaver (1 Hour) | 1080p, 30 fps doubled to 60, first 10:00 | 21 | Single | Elbows |
| [tCpnDTzRAjE](https://youtu.be/tCpnDTzRAjE) | 3d Pipes Screensaver 10 Hours | 1080p, 30 fps, first 10:00 | 23 | Single | Mixed |
| [I0hypYunmwI](https://youtu.be/I0hypYunmwI) | Windows 95 Screensaver - 3D PIPES | 1080p, 30 fps, 9:53 | 19 | Single | Elbows |

That is 127 complete scenes; scenes cut off by the start or end of a recording are not counted. The per-scene tables
are in [`docs/reference/`](reference/).

**The original program.** Microsoft published the source of 3D Pipes as an OpenGL sample in the Windows NT 4.0
SDK (`MSTOOLS\SAMPLES\OPENGL\SCRSAVE`). That SDK is not freely available, but
[SspipesSharp](https://github.com/RussDev7/SspipesSharp) is a port of it that documents the original's constants
and rules. It is GPL-3.0, so this project took facts from it (numbers, rules, behavior) and copied no code. No
leaked Windows source and no disassembly was used.

**Two versions.** On two points the footage splits. tCpn matches the NT sample: light from the upper right, and a
clear in visible squares lasting 1.7 s. The other four, including the Windows XP recording, are lit from above with
no sideways component and clear in about half a second with squares too small to resolve. This app follows the
majority and the XP recording on both points.

**Settings.** The original had settings for single or multiple pipes and for the joint style. The recordings use
different combinations. This app defaults to multiple pipes and elbow turns, matching all 33 measured scenes of
the XP recording (72z). This establishes that recording's setting, not that XP lacked ball or mixed settings.
Ball and mixed turns remain available through the preview's `joints` launch option; spherical end caps remain in
the default mode.

**How to read the Basis column.** *Source* means the value comes from the original program's published rules.
*Measured* means it was measured in the footage, with the number of scenes. *Observed* means it was seen in frames
but not counted. *Estimated* means it was inferred where the evidence is indirect.

## Findings

| Area | Finding | Basis | This app |
|---|---|---|---|
| Lattice | 15 nodes across the screen's long side, the short side scaled by the aspect ratio (15 x 8 x 15 on a 16:9 screen), 15 deep | Source | Same |
| Pipe size | Pipe radius is 1/7 of the node spacing | Source | Same |
| Ball joints and end caps | A sphere of radius sqrt(2) x pipe radius / cos(180 deg / sides), about 1.53 x the pipe | Source; observed (8 facets on a close-up ball in MKqr) | Same |
| Elbows | A quarter torus whose bend radius equals the pipe radius, so the inside of the bend is a sharp crease | Source; observed in every recording | Same |
| Tessellation | 8 sides on pipes, elbows and balls, with smooth (Gouraud) shading | Source; observed | Same |
| Joints | A setting: elbows only, balls only, or mixed. In mixed, a turn is a ball 1 time in 3 and an elbow otherwise | Source; observed: mixed in MKqr and tCpn, elbows only in the other three | Elbow turns by default, matching the XP recording; ball and mixed turns remain preview options |
| Teapot | The NT sample makes 1 turn in 1000 a Utah teapot in mixed mode. None appear in MKqr's 31 mixed scenes, where that rate would give about 14, so the shipped versions use a lower rate or a different rule. Ten-hour uploads advertise teapots as an occasional event | Source; measured (none in 31 mixed scenes) | No teapot turns in the default elbow mode. Mixed preview: estimated 1 turn in 20,000, about one scene in 40. Upright, classic GLAUX model at caller scale 2.5 pipe radii (body diameter about 5 radii); boxed-in starts also produce a teapot |
| Turning | Each pipe gets a straight-ahead weight when it starts: 1 to 4 (19 pipes in 20) or 25 to 100 (1 in 20). At each node the pipe goes straight with weight w against 1 for each free turn | Source | Same |
| Speed | One whole lattice segment per pipe per timer tick, appearing at once (no sliding growth). The NT sample asks for a 16 ms timer, so the rate depends on the machine. Measured on distinct frames: at least 28.7 ticks/s in the XP recording (its 30 fps capture caps it), about 27/s in pBSq, 25/s in tCpn and 23/s in I0hyp. Scene lengths give 25 to 29/s in the multiple-pipe recordings, depending on the chase model | Source; measured | 30 ticks per second: consistent with the XP recording, and an even two refreshes per step on a 60 Hz TV |
| Pipes per scene | Multiple-pipes mode: 2 to 4 pipes grow at once, 7 in all per scene. Single-pipe mode: one at a time, 5 per scene | Source; measured: 2 to 4 at once in both multiple-pipe recordings, 1 in the others | Multiple pipes |
| Chase | In multiple-pipes mode each scene has a 1 in 5 chance of switching on chase, where the other pipes follow a lead pipe; once on, it stays on for the rest of the session | Source; observed (tight, off-center clusters in MKqr's later scenes) | Same |
| Scene end | A scene ends when its last pipe is trapped, not after a fixed time or fill | Source | Same |
| Scene length | Multiple pipes: median 11.5 s (72z, n = 33) and 12.0 s (MKqr, n = 31). Single pipe: 26.8, 24.6 and 29.0 s (n = 21, 23, 19) | Measured | Follows from the rules at 30 ticks per second |
| Coverage | 31 to 40 % of the screen covered when a scene ends (medians per recording) | Measured | Follows from the rules |
| Materials | 16 materials from the classic OpenGL "teapots" set: emerald, jade, pearl, ruby, turquoise, brass, bronze, copper, gold, silver, cyan plastic, white plastic, yellow plastic, cyan rubber, green rubber, white rubber. Each pipe picks one at random | Source; measured: 83 to 91 % of colored pixels per scene fit one of the teapot materials, against 33 % for random colors. About 10 of the 16 can be told apart by color and all of those occur; silver, white plastic, white rubber and pearl look alike, as do jade and green rubber, and turquoise and cyan rubber | Same, with each material's own ambient, diffuse, specular and shininess |
| Light | One white directional light fixed to the camera. The NT sample puts it at (90, 90, 150) in eye space, upper right and in front. In four recordings, including the XP one, highlights sit straight above center: no sideways component. Only tCpn is lit from the right | Source; measured across all final frames (horizontal offset of the brightest line on straight pipes, corrected for perspective: +0.01, -0.01, +0.01, +0.03 against +0.21 in tCpn) | (0, 90, 150), computed per vertex as OpenGL 1.1 did, with global ambient 1.0, light ambient 0.1 and the viewer at infinity. Checked with the same measurement on this app's frames: -0.02 |
| Camera | 90 degree vertical field of view, 75/7 node spacings from the lattice's center, level (no tilt or roll). Each new scene turns the view 9.73156 degrees further around the vertical axis | Source; observed: vertical pipes stay vertical on screen in every recording | Same |
| Hold | The finished picture stays for about one tick before the clear | Source; measured: median 0.03 to 0.10 s | One tick |
| Clear | Small black squares in random order eat the frozen picture. The NT sample sizes them by timing the graphics hardware, so both size and duration vary by machine. tCpn: 1.70 s (range 1.60 to 1.77, n = 23) in squares 4 pixels across at 1080 lines. The other four: 0.45, 0.65, 0.55 and 0.60 s, with squares too small to survive video compression, so the clear looks like a fade | Source; measured | 0.57 s in squares 1/270 of the screen height (4 pixels at 1080 lines), the only square size that could be measured |
| Anti-aliasing | None: the original draws with plain OpenGL 1.1 | Source; observed (stair-stepped edges in close-ups) | None |

## Corrections

The first version of this analysis sampled one recording at 854 x 480 by seeking. Several of its findings were
wrong:

- **Colors** were described as random RGB. They are 16 fixed materials, each with its own highlight color.
- **Speed** was estimated at 10 to 12 lattice units per second, growing smoothly. It is about 30 whole segments a
  second.
- **Camera** was said to take a random viewpoint and distance per scene. It is always level and at the same
  distance, turning 9.73 degrees per scene.
- **Elbows** were drawn with a wide bend. The original's bend is as tight as the pipe.
- **Scene length** was a random timer. It is when the last pipe gets trapped.

The skeptic review then corrected the second version: the 2-frame cadence in two recordings came from doubled
frames, chase stays on once it starts, the light in the XP recording has no sideways component, the hold is one
tick, four of the five clears are short, the square size is 1/270 of the height, the teapot rate in the source does
not match the footage, and the scene count is 127.

## Skeptic review

An independent reviewer was given only the per-scene tables, the cadence counts and the claims, and asked to refute
each. Its verdicts are in [`docs/reference/SKEPTIC.md`](reference/SKEPTIC.md); every correction it asked for is made
above.

## Teapot geometry audit (2026-10-21)

The teapot now uses pinned classic SGI patch/control-point tables, cross-checked numerically with a public
GLAUX archive. Both have 10 base patches and 127 points, reflected to 32 patches. The GLAUX solid path specifies
14 intervals per patch edge, a half-scale, a raw Z translation of -1.5, and a 270-degree X rotation. The previous
three.js-derived asset used eight intervals, centered height at 1.575, and enlarged the lid by 7.7%. Those
adjustments have been removed. The model retains the historical lid gap and component intersections.

The app supplies the existing caller scale of 2.5 pipe radii after the GLAUX transform. Earlier wording that
called this the body diameter was wrong: the body spans about five pipe radii. Teapots share one GPU mesh,
rather than inserting a complete mesh copy into the pipe batch at each node. Normals and winding are converted
to the renderer's outward/CCW convention; back-face culling remains enabled.

This verifies the selected published classic model, not exact XP binary provenance. The public GLAUX archive
corroborates the implementation; it does not authenticate Microsoft's shipped XP data or driver rasterization.
Source revisions, hashes, generation instructions, independent checks, and limits are recorded in
[tools/teapot/README.md](../tools/teapot/README.md). See [teapot QA](teapot-qa.md) for execution evidence.
