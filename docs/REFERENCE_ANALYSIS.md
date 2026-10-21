# Reference analysis

What the original 3D Pipes screensaver does, how each fact was established, and what this app does with it.

## Sources

**Footage.** Five public recordings of the original, each by a different uploader, downloaded only for analysis and
measured frame by frame with the scripts in `tools/reference/`. Nothing downloaded is part of this repository.

| Recording | Uploader's title | Frames | Scenes measured | Mode |
|---|---|---|---|---|
| [72zBfGRQlVA](https://youtu.be/72zBfGRQlVA) | 3D Pipes - Windows XP screensaver | 1080p, 60 fps, 6:41 | 33 | Multiple pipes |
| [MKqrLGFoK9E](https://youtu.be/MKqrLGFoK9E) | 3D pipes screensaver (1080p) | 1080p, 60 fps, 7:36 | 31 | Multiple pipes |
| [pBSq6RCJRCA](https://youtu.be/pBSq6RCJRCA) | Windows 98 Pipes Screensaver (1 Hour) | 1080p, 60 fps, first 10:00 | 16 | Single pipe |
| [tCpnDTzRAjE](https://youtu.be/tCpnDTzRAjE) | 3d Pipes Screensaver 10 Hours | 1080p, 30 fps, first 10:00 | 23 | Single pipe |
| [I0hypYunmwI](https://youtu.be/I0hypYunmwI) | Windows 95 Screensaver - 3D PIPES | 1080p, 30 fps, 9:53 | 19 | Single pipe |

That is 122 complete scenes; scenes cut off by the start or end of a recording are not counted. The per-scene
tables are in [`docs/reference/`](reference/).

**The original program.** Microsoft published the source of 3D Pipes as an OpenGL sample in the Windows NT 4.0
SDK (`MSTOOLS\SAMPLES\OPENGL\SCRSAVE`). That SDK is not freely available, but
[SspipesSharp](https://github.com/RussDev7/SspipesSharp) is a port of it that documents the original's constants
and rules. It is GPL-3.0, so this project took facts from it (numbers, rules, behavior) and copied no code. No
leaked Windows source and no disassembly was used.

**How to read the Basis column.** *Source* means the value comes from the original program's published rules.
*Measured* means it was measured in the footage, with the number of scenes or intervals. *Observed* means it was
seen in frames but not counted. Where the two disagree, this says so.

## Findings

| Area | Finding | Basis | This app |
|---|---|---|---|
| Lattice | 15 nodes across the screen's long side, the short side scaled by the aspect ratio (15 x 8 x 15 on a 16:9 screen), 15 deep | Source | Same |
| Pipe size | Pipe radius is 1/7 of the node spacing | Source; observed in close-ups | Same |
| Ball joints and end caps | A sphere of radius sqrt(2) x pipe radius / cos(180 deg / sides), about 1.53 x the pipe | Source; observed | Same |
| Elbows | A quarter torus whose bend radius equals the pipe radius, so the inside of the bend is a sharp crease | Source; observed (the pointed inner corner is visible in every recording) | Same |
| Tessellation | 8 sides on pipes, elbows and balls, with smooth (Gouraud) shading, so balls show a faceted outline | Source; observed in close-ups of balls | Same |
| Joints | Mixed: each turn is a ball joint 1 time in 3, an elbow otherwise. 1 turn in 1000 is the Utah teapot instead | Source; ball and elbow mix observed | Same |
| Turning | Each pipe gets a straight-ahead weight when it starts: 1 to 4 (19 pipes in 20) or 25 to 100 (1 in 20). At each node the pipe goes straight with weight w against 1 for each free turn | Source | Same |
| Speed | One whole lattice segment per pipe per timer tick, appearing at once (no sliding growth). 30 ticks per second | Source (one segment per tick); measured: in both clean 60 fps recordings 93 to 94 % of the intervals between frames that show new pipe are exactly 2 frames (10,365 and 14,738 intervals). Cross-check: scene lengths divided by the modeled ticks per scene give 25 to 32 ticks per second across all five recordings | 30 ticks per second |
| Pipes per scene | Multiple-pipes mode: 2 to 4 pipes grow at once, 7 pipes in all per scene. Single-pipe mode: one at a time, 5 per scene | Source; measured: 2 to 4 at once in both multi recordings, 1 in the other three | Multiple pipes, the mode of the Windows XP recording |
| Chase | In multiple-pipes mode, 1 scene in 5 switches the other pipes to following a lead pipe | Source | Same |
| Scene end | A scene ends when its last pipe is trapped, not after a fixed time or fill | Source; consistent with the measured spread of scene lengths | Same |
| Scene length | Multiple pipes: median 11.6 s (72z, n = 33) and 12.1 s (MKqr, n = 31). Single pipe: median 27.5, 24.6 and 29.6 s | Measured | Follows from the rules at 30 ticks per second |
| Coverage | 26 to 34 % of the screen covered when a scene ends (medians per recording) | Measured | Follows from the rules |
| Materials | 16 materials from the classic OpenGL "teapots" set: emerald, jade, pearl, ruby, turquoise, brass, bronze, copper, gold, silver, cyan plastic, white plastic, yellow plastic, cyan rubber, green rubber, white rubber. Each pipe picks one at random | Source; measured: in 122 scenes every pipe color matches one of the 24 teapot materials, the colors that matched outside the 16 are look-alikes (chrome looks like silver, red plastic like ruby), and all 16 occur | Same, with each material's own ambient, diffuse, specular and shininess |
| Light | One white directional light fixed to the camera, from the upper right and in front: direction (90, 90, 150) in eye space; global ambient 1.0, light ambient 0.1, viewer at infinity | Source; observed: highlights on balls sit up and to the right of center | Same, computed per vertex as the original's fixed-function pipeline did |
| Camera | 90 degree vertical field of view, 75/7 node spacings from the lattice's center, level (no tilt or roll). Each new scene turns the view 9.73156 degrees further around the vertical axis | Source; observed: vertical pipes are vertical on screen in every scene | Same |
| Hold | The finished picture stays briefly before the clear: medians 0.18 to 0.22 s in the 60 fps recordings | Measured | 0.2 s |
| Clear | Small black squares in random order eat the frozen picture. Duration varies by machine: 1.67 s median in tCpn (n = 23, range 1.60 to 1.73), 0.17 to 0.48 s in the others. The square size is calibrated by timing the graphics hardware, so it is not a constant either; in tCpn the squares are about 1/180 of the screen height | Source (calibrated by hardware); measured | 1.67 s with squares 1/180 of the screen height, the one recording where the clear can be followed frame by frame. The 72z recording fades instead of dissolving, which looks like editing and was not used |
| Teapot | 1 in 1000 turns; 2.5 x pipe radius in GLAUX units, so its body is about 2.5 x the pipe radius, upright | Source; none seen in the measured scenes, which is expected at that rate | Same |
| Anti-aliasing | None: the original draws with plain OpenGL 1.1 | Source | None |

## Corrections to the previous version of this file

The first version of this analysis sampled one recording at 854 x 480 by seeking. Several of its findings were
wrong and are corrected above:

- **Colors** were described as random RGB. They are 16 fixed materials, each with its own highlight color.
- **Speed** was estimated at 10 to 12 lattice units per second, growing smoothly. It is 30 whole segments per
  second.
- **Light** was said to come from the upper left. It comes from the upper right.
- **Camera** was said to take a random viewpoint and distance per scene. It is always level and at the same
  distance, turning 9.73 degrees per scene.
- **Elbows** were drawn with a wide bend. The original's bend is as tight as the pipe.
- **Scene length** was a random timer. It is when the last pipe gets trapped.

## Skeptic review

An independent reviewer was given only the per-scene tables, the cadence counts and the claims above, and asked
to refute each. Its verdicts are in [`docs/reference/SKEPTIC.md`](reference/SKEPTIC.md).
