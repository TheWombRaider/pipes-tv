# Skeptic review of the Findings table

Reviewer brief: try to refute every row of the Findings table in `docs/REFERENCE_ANALYSIS.md`. Evidence used: the
per-scene tables, `cadence.txt`, `colors.txt`, the raw per-frame CSVs, frames pulled from the five videos, and the
SspipesSharp port. Checks that needed new code were run from scratch scripts outside the repository (`dup.py`,
`rate.py`, `dissolve.py`, `lightdir.py`, `lightscene.py`, `turns.py`, `colornull.py`, `colorshare.py`). Nothing under
`tools/` or `app/` was changed.

## Verdicts

| Claim | Verdict | What I checked | What would change |
|---|---|---|---|
| Lattice 15 x 8 x 15 on 16:9 | Holds | `PipeWorld.Resize`: x = NumDiv-1 = 15, y = (int)(15/aspect) = 8, z = 15 | Nothing |
| Pipe radius 1/7 of spacing | Holds | Radius 1, DivSize 7 | Nothing |
| Ball radius sqrt(2) r / cos(180/sides) = 1.53 r | Holds | `NormalPipe.AddSphere` (solid style); a close-up ball in MKqr at 276.8 s has 8 visible facets | Nothing. In Elbows mode these balls exist only as start and end caps |
| Elbow bend radius = pipe radius | Holds | `EmitClassicJointVertex`: the inner ring point (cos t = 1) maps to the same spot for every sweep angle, so the inner edge is a point | Nothing |
| 8 sides, smooth shading | Holds | `Settings.Slices` with Tessellation 0 gives 8; the port never calls glShadeModel, so GL_SMOOTH | Nothing |
| Joints: Mixed, 1 in 3 balls, teapot 1 in 1000 turns | Holds with caveat | The rule is right for the Mixed setting (`ChooseJointType`), but joint style is a user setting and the port's default is Elbows. Final-frame contact sheets: 72z (the XP recording), pBSq and I0hyp show elbows at nearly every turn, with balls only at pipe ends. MKqr and tCpn show many ball joints. "Ball and elbow mix observed" is true of 2 recordings, not 5 | Say which recordings are Mixed. If the app follows 72z, it should use Elbows (and then never draw a teapot) |
| Turning weights 1-4 (19/20) or 25-100 (1/20) | Holds | `NormalPipe` constructor and `NodeGrid.ChooseRandomDirection`, including the w = 100 always-straight shortcut | Nothing |
| 30 ticks per second | Holds with caveat; the cited evidence is invalid | 72z and pBSq are 30 fps captures with every frame doubled: the mean difference between alternate frame pairs is 0.00. The "93-94 % 2-frame intervals" therefore measure the capture rate, not the tick rate, and cannot detect two ticks in one captured frame. On distinct frames in the first quarter of each scene, growth shows on 95.5 % of frames in 72z (at least 28.7/s) and 90 % in pBSq (27/s). The two native 30 fps recordings show growth on only 82 % (tCpn) and 77 % (I0hyp) of frames, which works out to about 25/s and 23/s. The source names a 16 ms timer, not 30/s. The scene-length check depends on the chase model: 300 modeled ticks (sticky chase) gives 25.8/s for 72z; 340 ticks (chase 1 scene in 5) gives 29.2/s | Drop the cadence argument. State 30/s as a choice consistent with 72z, with 23-25/s seen in the native 30 fps recordings |
| 2-4 pipes at once, 7 per scene (multi); 1 at a time, 5 per scene (single) | Holds | `FrameReset`: (int)(5 x 1.5) = 7; NextInclusive(2, 4); "together" medians of 2 (multi) and 1 (single) | Nothing about the rule. Note that the port's default is single pipe |
| Chase: 1 scene in 5 | Refuted | `FrameReset` sets Chase with a 1-in-5 chance per scene and never sets it back; the port comments that STATE.CXX "never explicitly changes FRAME_SCHEME_CHASE back to RANDOM". After the first trigger (about 5 scenes in), every scene chases. `sim_model.py` already models it that way: 100 % chase scenes in its run, which is where the 300-tick median comes from. MKqr's later scenes (12-18) show tight, off-center clusters, which fits chase being latched | Make chase latch for the rest of the session, or say the app deliberately differs |
| Scene ends when the last pipe is trapped | Holds | `Step`: the reset is pending once `_active` is empty, and FrameReset runs on the next tick | Nothing |
| Scene length medians | Holds with caveat | 72z 11.63 and MKqr 12.12 match. pBSq is 27.02 s with n = 21 in `scenes-pBSq6RCJRCA.txt`, not 27.5 s with n = 16 (`cadence.txt` uses 27.48), so the total is 127 scenes, not 122. In tCpn, dissolve blocks count as growth "blobs": the frames at grow - 0.6 s in scenes 0, 8, 11 and 12 are already mid-dissolve, which inflates those scene lengths by up to about 1.6 s | Regenerate the tables and update the counts. Exclude dissolve frames from growth |
| Coverage 26-34 % | Holds with caveat | The file medians are 0.26, 0.31, 0.33, 0.28 and 0.31, so 26-33 %. tCpn values are read at contaminated "last growth" frames, some of them mid-dissolve (0.03, 0.10) | Say 26-33 % and fix the tCpn end frame |
| Materials: the 16 teapot materials, all 16 seen, every pipe matches | Holds with caveat | The `Materials.Good` indices give exactly the 16 listed. Discrimination: with the 0.004 tolerance, 83-91 % of colored pixels per scene (median) match one of the 24 materials, against 33 % for uniformly random RGB and 14 % for random saturated hues, so a fixed palette is supported. But silver, white plastic and white rubber have identical chromaticity (distance 0.0000); pearl is within 0.0028 of all three; jade and green rubber are 0.0012 apart; turquoise and cyan rubber 0.0028. All of these are inside the acceptance radius, so "all 16 occur" is not shown for about 6 of them. Unmatched pixels are silently dropped: 9-17 % of pixels per scene, and as low as 15-40 % matched in single scenes of 72z and tCpn | Report the unmatched share. Say which materials cannot be told apart |
| Light (90, 90, 150) in eye space, upper right | Refuted for 4 of 5 recordings, including XP | The source does say this: GL_POSITION is set in InitializeGl under an identity modelview. Footage: I measured where the brightness peaks across thousands of straight vertical and horizontal pipes in every final frame, and regressed that offset on screen position to cancel perspective. At screen center the horizontal offset (fraction of the half-width) is +0.01 (72z), -0.01 (MKqr), +0.01 (pBSq) and +0.03 (I0hyp), against +0.21 in tCpn. The vertical offset is 0.19-0.27 toward the top in all five. Per-scene values hold steady (horizontal SD 0.04-0.06 across scenes), so the light is fixed to the camera, not to the world. Only tCpn matches (90, 90, 150); the rest are lit from straight above and in front. Balls in 72z and MKqr do not show highlights up and to the right | Use a light with no horizontal component (about (0, 90, 150)) to match 72z, or say the app follows the NT sample and tCpn instead |
| Camera: 90 deg vertical FOV, 75/7 spacings, level, +9.73156 deg per scene | Holds | gluPerspective(90, aspect), glTranslatef(0, 0, -75), DivSize 7, rotation 9.73156 added on each normal reset. Thousands of pipe runs are exactly vertical to within 2 px over 20 rows in every recording | Footage does not test the FOV or the distance; that rests on the source alone |
| Hold 0.18-0.22 s | Refuted | The file medians are 0.22, 0.18 and 0.25 (pBSq), so 0.18-0.25. Worse, "hold" and "dissolve" in `scenes.py` trade off scene by scene (72z: 0.30/0.03, 0.05/0.45, ...; the sum is roughly constant at 0.3-0.5 s). The split is an artifact of a fade whose early frames barely change the lit fraction. The source holds the picture for about 1-2 ticks (a reset is pending, then the next tick clears), and tCpn, the only recording with a real dissolve, shows a 0.03 s median hold | Use a 1-2 tick hold (about 0.03-0.07 s), not 0.2 s |
| Clear: 1.67 s in tCpn (range 1.60-1.73), squares 1/180 of the height; 0.17-0.48 s in the others; only 72z fades | Refuted in part | tCpn's own table gives a range of 0.13-1.73. Six scenes are cut short by a bug: a duplicated frame where lit rises by 0.00005 stops the backward walk (for example frames 9355-9356). A robust re-measure gives a median of 1.70 and a range of 1.60-1.80 (n = 23), so 1.67 is right for tCpn. Square size: the gaps in a mid-dissolve tCpn frame come in multiples of 4 px at 1080 lines, which is 1/270 of the height, not 1/180. MKqr, pBSq and I0hyp also fade: per-frame changes stay below the threshold while coverage falls, and full-resolution crops show smooth darkening with no blocks. Their robust clear times are 0.45 s (72z), 0.65 s (MKqr), 0.58 s (pBSq) and about 0.2-0.6 s (I0hyp, noisy). tCpn is the only recording with visible squares, and also the only one with the upper-right light | Fix the range and use 1/270 for the squares. Say that 4 of 5 recordings fade over 0.45-0.65 s, and that the app takes its clear and its light from the one outlier recording while taking its pipe mode from 72z |
| Teapot: 1 in 1000 turns; none seen, "expected at that rate" | Refuted (the reasoning) | Modeled turns per scene are 437 (multi, sticky chase), 522-546 (multi, other chase models) and 484 (single). At 1/1000 per turn, that is about 0.5 teapots per Mixed scene. MKqr has 31 Mixed scenes, so about 14 are expected; I checked all 31 final frames and found none (the orange "peanut" at 134.4 s is two end caps). In Elbows mode (72z, pBSq, I0hyp) the source never draws a teapot | Drop "expected". Either the shipped builds use a lower rate, or the check was not done. Say so |
| Anti-aliasing: none | Holds | No multisample attributes in the port's pixel format; pipe edges are stair-stepped in close-ups | Nothing about the original. The current app code still asks for 4x MSAA (`PipesGLView.MultisampleChooser`) |

## Corrections the author must make

1. Speed row: remove the 2-frame cadence argument. 72z and pBSq are 30 fps captures with every frame duplicated. Report
   at least 28.7/s for 72z, about 25/s for tCpn and about 23/s for I0hyp, and say that the scene-length cross-check
   moves from 25.8/s to 29.2/s depending on the chase model.
2. Chase row: chase latches for the rest of the session once it triggers (1 in 5 per scene until then). This matches
   `sim_model.py`, which contradicts the table as written.
3. Light row: (90, 90, 150) matches only tCpn. The XP recording and three others are lit from above and in front, with no
   horizontal component (offset +0.01 vs +0.21). Either change the app's light or change "observed".
4. Joints and Teapot rows: 72z, pBSq and I0hyp are Elbows mode. Mixed is seen only in MKqr and tCpn. "None seen is
   expected" is false: about 14 teapots were expected in MKqr's 31 scenes.
5. Hold row: 0.2 s is an artifact of how `scenes.py` splits the fade. Use about 1-2 ticks.
6. Clear row: tCpn's tabulated range is 0.13-1.73 because of a splitting bug, and the real range is 1.60-1.80. The squares
   are 4 px of 1080 (1/270), not 1/180. MKqr, pBSq and I0hyp fade just like 72z, so "the others: 0.17-0.48 s
   dissolve" is wrong.
7. Recompute every count from one run: pBSq is n = 21 with a 27.02 s median in the current file, so the total is
   127 scenes, not 122. Coverage is 26-33 %.
8. Fix `scenes.py`: treat a lit change under about 5e-4 as flat when walking back through the clear, and drop
   dissolve-block blobs from "growth". Then regenerate the tables.
9. Colors: report the share of unmatched pixels, and say that silver, white plastic and white rubber (plus pearl,
   jade/green rubber and turquoise/cyan rubber) cannot be told apart, so "all 16 occur" is unproven for those.
10. "This app" column: the current code does not do what it describes. `PipesRenderer` lights from (-0.4, 0.6, 0.7),
    which is upper left; it uses a 65 degree FOV, a 1.6 s timed dissolve and a fixed scene timer; and
    `PipesGLView` asks for 4x MSAA. Mark the column as planned, or update it once the code changes.
11. The app takes its mode from 72z, but its clear and its light from tCpn, the only recording that matches the NT
    sample on those two points. State that mix openly, or pick one reference.

## Author's response

All eleven corrections are made in `docs/REFERENCE_ANALYSIS.md`, `docs/reference/cadence.txt`, `colors.txt` and
`tools/reference/scenes.py`, and the per-scene tables were regenerated (127 scenes). Point 10 described the app as it
was before the rebuild that this review ran alongside; the app now uses the values in the table's last column. On
point 11, the app takes its pipe mode from the XP recording, its joint style from MKqr, and its light and clear from
the four recordings that agree with the XP one, not from tCpn.
