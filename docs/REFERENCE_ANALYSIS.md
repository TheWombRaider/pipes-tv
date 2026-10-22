# Reference analysis

What the original 3D Pipes screensaver does, how each fact was established, and what this app does with it.

## Sources

**Footage.** Public recordings of the original (five, listed below, then two more uploads of the 10-hour recording), downloaded only for analysis and
measured frame by frame with the scripts in `tools/reference/`. Nothing downloaded is part of this repository.

| Recording | Uploader's title | Video | Scenes | Pipes | Joints |
|---|---|---|---|---|---|
| [72zBfGRQlVA](https://youtu.be/72zBfGRQlVA) | 3D Pipes - Windows XP screensaver | 1080p, 30 fps doubled to 60, 6:41 | 33 | Multiple | Elbows |
| [MKqrLGFoK9E](https://youtu.be/MKqrLGFoK9E) | 3D pipes screensaver (1080p) | 1080p, 60 fps, 7:36 | 31 | Multiple | Mixed |
| [pBSq6RCJRCA](https://youtu.be/pBSq6RCJRCA) | Windows 98 Pipes Screensaver (1 Hour) | 1080p, 30 fps doubled to 60, first 10:00 | 21 | Single | Elbows |
| [tCpnDTzRAjE](https://youtu.be/tCpnDTzRAjE) | 3d Pipes Screensaver 10 Hours | 1080p, 30 fps, first 10:00 | 23 | Single | Mixed |
| [I0hypYunmwI](https://youtu.be/I0hypYunmwI) | Windows 95 Screensaver - 3D PIPES | 1080p, 30 fps, 9:53 | 19 | Single | Elbows |

Four more segments were measured later (tCpn at minutes 60 and 120, and the 3:2 and 4:3 versions of the same upload: 20, 18, 14 and 5 scenes). That is 184 complete scenes in all. Three further tCpn segments at minutes 180, 240 and 300 (23, 18 and 20 scenes) extend tCpn to 60 sampled minutes: seven added segments and 245 complete scenes in all. Scenes cut off by the start or end of a recording are not counted. The per-scene tables
are in [`docs/reference/`](reference/).

**The original program.** Microsoft published the source of 3D Pipes as an OpenGL sample in the Windows NT 4.0
SDK (`MSTOOLS\SAMPLES\OPENGL\SCRSAVE`). That SDK is not freely available, but
[SspipesSharp](https://github.com/RussDev7/SspipesSharp) is a port of it that documents the original's constants
and rules. It is GPL-3.0, so this project took facts from it (numbers, rules, behavior) and copied no code. No
leaked Windows source and no disassembly was used.

**Two versions.** The recordings split into two groups. tCpn, and the other uploads by the same person (16:9, 3:2 and
4:3 versions), agree with the NT sample on the measured mode, mixed joints, upper-right light and visible-square
clear. Clear durations vary: about 1.7 s in four tCpn segments, 0.9 s in two, and 1.0 to 1.2 s in sibling uploads.
The teapot rate remains unverified. The other four, including the one titled "Windows XP screensaver", are lit from
above and fade out in about half a second. MKqr has mixed joints; 72z, pBSq and I0hyp have elbows only. The two
multiple-pipe recordings are 72z and MKqr. This app follows the tCpn family's mode, joints, light and clear style,
and it agrees with the maintainer's own memory of the screensaver. An earlier version followed the XP-titled recording
(several pipes, elbows only), which does not match the screensaver as it is remembered.

**Settings.** The original had settings for single or multiple pipes and for the joint style. The app uses one pipe at
a time and mixed joints. Several pipes at once, and elbow-only or ball-only joints, remain available through the
preview's launch options.

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
| Joints | A setting: elbows only, balls only, or mixed. In mixed, a turn is a ball 1 time in 3 and an elbow otherwise | Source; observed: mixed in MKqr and tCpn (and tCpn's sibling uploads), elbows only in 72z, pBSq and I0hyp. Two blind counters labeled 4,933 and 4,796 turns in the same 136 footage frames. Calibration reads 0.289 to 0.302 (bootstrap bounds 0.266 to 0.329); an automatic detector reads 0.26 (0.17 to 0.35). Intervals omit calibration transfer bias ([JOINTS.md](reference/JOINTS.md)) | Mixed, 1 turn in 3: the code's value; the detector interval includes it. Compression's effect on the estimated share was not measured |
| Teapot | 1 turn in 1000 in mixed mode, in the NT sample | Source. Counters reported 6 to 7 teapots in 136 footage frames and 3 to 4 against 38 true teapots in 54 render frames. Selected calibration, unknown false positives and uncertain transfer prevent a reliable population-rate estimate. The former 0.1 to 2 interval and exclusion of 1 in 20,000 are withdrawn. Candidate viewing also failed calibration; MKqr's zero count does not resolve its rate ([JOINTS.md](reference/JOINTS.md)) | Retain the code's 1 in 1000: expected count 0.48 for 480 eligible turns, not a measured footage rate. Upright, 2.5 pipe radii in GLAUX units |
| Turning | Each pipe gets a straight-ahead weight when it starts: 1 to 4 (19 pipes in 20) or 25 to 100 (1 in 20). At each node the pipe goes straight with weight w against 1 for each free turn | Source | Same |
| Speed | One whole lattice segment per pipe per timer tick, appearing at once (no sliding growth). The NT sample asks for a 16 ms timer, so the rate depends on the machine. Scene lengths of 162 single-pipe scenes fit 29.75 steps a second (bootstrap interval 29.0 to 31.0), by recording 26.0 to 33.5. Growth shows on 77 to 82 % of the frames of the 30 fps recordings, a visibility-dependent lower bound. Only 0.4 % of 6,600 near-field growth blobs were classified as holding two segments; foreshortening and selection prevent this from proving an upper tick-rate bound. Direct segment lengths do not provide an independent rate estimate. Pipes per scene is not reliably measurable from frame differences ([VERIFICATION.md](reference/VERIFICATION.md)) | Source; model-dependent fit and visibility checks | 30 steps a second |
| Pipes per scene | Multiple-pipes mode: 2 to 4 pipes grow at once, 7 in all per scene. Single-pipe mode: one at a time, 5 per scene | Source; measured: 2 to 4 at once in both multiple-pipe recordings, 1 in the others | One pipe at a time, 5 per scene |
| Chase | In multiple-pipes mode each scene has a 1 in 5 chance of switching on chase, where the other pipes follow a lead pipe; once on, it stays on for the rest of the session | Source; observed (tight, off-center clusters in MKqr's later scenes) | Not used: it needs several pipes (kept in the code for the multiple-pipes option) |
| Scene end | A scene ends when its last pipe is trapped, not after a fixed time or fill | Source | Same |
| Scene length | Multiple pipes: median 11.5 s (72z, n = 33) and 12.0 s (MKqr, n = 31). Single pipe: 26.8, 24.6 and 29.0 s (n = 21, 23, 19) | Measured | Follows from the rules at 30 ticks per second |
| Coverage | 31 to 40 % of the screen covered when a scene ends in the five initial recordings; 31 to 45 % across all 12 per-segment/upload tables. The six tCpn segments together have median 36 % | Measured; table values are rounded | Follows from the rules |
| Materials | 16 materials from the classic OpenGL "teapots" set: emerald, jade, pearl, ruby, turquoise, brass, bronze, copper, gold, silver, cyan plastic, white plastic, yellow plastic, cyan rubber, green rubber, white rubber. Each pipe picks one at random | Source; measured: 83 to 91 % of colored pixels per scene fit one of the teapot materials, against 33 % for random colors. About 10 of the 16 can be told apart by color and all of those occur; silver, white plastic, white rubber and pearl look alike, as do jade and green rubber, and turquoise and cyan rubber | Same, with each material's own ambient, diffuse, specular and shininess |
| Light | One white directional light fixed to the camera, at (90, 90, 150) in eye space: upper right and in front. Four recordings, including the XP one, are lit from straight above instead | Source; measured across all final frames (horizontal offset of the brightest line on straight pipes, corrected for perspective: +0.01, -0.01, +0.01, +0.03 against +0.21 in tCpn) | (90, 90, 150), as in the NT sample and tCpn, computed per vertex as OpenGL 1.1 did, with global ambient 1.0, light ambient 0.1 and the viewer at infinity. Checked on this app's frames with the same measurement |
| Camera | 90 degree vertical field of view, 75/7 node spacings from the lattice's center, level (no tilt or roll). Each new scene turns the view 9.73156 degrees further around the vertical axis | Source; observed: vertical pipes stay vertical on screen in every recording | Same |
| Hold | The finished picture stays for about one tick before the clear | Source; measured: median 0.03 to 0.10 s | One tick |
| Clear | Small black squares in random order eat the frozen picture. The NT sample sizes them by timing the graphics hardware, so both size and duration vary by machine, and the recording shows two values: 1.67 s in four segments of tCpn (84 scenes, range 1.57 to 1.77) and 0.9 s in two (38 scenes), 1.0 to 1.2 s in sibling uploads; squares 4 pixels across at 1080 lines. The other four recordings fade in 0.45 to 0.65 s | Source; measured | 1.7 s in squares 1/270 of the screen height (4 pixels at 1080 lines): the more common of the two values |
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

## Later corrections

The default of several pipes with elbows only, and the light from above, followed the XP-titled recording. Taking the recordings as groups, and checking them against the program's code and the maintainer's memory, moved the app to one pipe at a time, mixed joints, light from the upper right and the 1.7 s clear. The joint estimates and model-dependent speed fit support retaining the code-derived defaults. The second review corrected ties in the speed fit's ECDF calculation: the pooled fit is 29.75 steps a second over 162 single-pipe scenes, with a reproducible 200-resample interval of 29.0 to 31.0. The teapot population rate could not be established; see [reference/JOINTS.md](reference/JOINTS.md).

## Skeptic review

The first reviewer was given the per-scene tables, cadence counts and claims, and asked to refute each. That review
and its response remain historical records. A second review checks the current code, raw counts and reported
conclusions, and records corrections and unresolved limits in [`docs/reference/SKEPTIC.md`](reference/SKEPTIC.md).

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
