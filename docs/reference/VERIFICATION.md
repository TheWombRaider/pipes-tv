# Verification of the app against the footage, and where the work departed from the plan

The plan: [single-pipe, mixed-joints design](../design/2026-10-21-single-pipe-mixed-joints-design.md).
The visual runs below were recorded on an Android 14 emulator (1920 x 1080, software rendering) in the earlier
session. That session reported a final TV update with its screen off. The second review used committed evidence
and local checks only; it performed no TV actions and started no emulator.

## The app's default scenes beside the recording

The earlier comparison used 20 default scenes rendered by the app. Images: [app-vs-recording.jpg](app-vs-recording.jpg).
The image-based coverage and light summaries below are retained from that comparison; their individual measurement
rows are not committed, so the second review did not independently reproduce them. Counts and timing from the soak
log, and summaries from the committed footage tables, were recomputed.

| | App | Recording (tCpn, six 10-minute segments) |
|---|---|---|
| Ball share of turns | 0.332 over 20,284 turns in the 19-minute run | 0.30 (0.28 to 0.33) by hand count; 0.26 (0.17 to 0.35) by detector |
| Teapots a scene | 17/41 = 0.4146 in that run | Not established: 6 to 7 reported in 136 frames, with unreliable calibration and unknown false positives |
| Light offset, horizontal / vertical (brightest line on straight pipes, center of the picture) | +0.28 / -0.30 | +0.20 to +0.28 / -0.29 to -0.35 by segment |
| Coverage at the end of a scene | median 0.33 (0.19 to 0.64) | median 0.36 (10 to 90 %: 0.22 to 0.50) |
| Scene length | median 28.340 s between scene-over logs (40 intervals, 41 completed scenes); subtracting nominal hold and clear gives about 26.606 s growth | Growth median 24.9 s across 122 tCpn scenes; segment medians 22.6 to 25.9 s, from rounded tables |
| Steps a second | Configured at 30; realized tick delivery was not measured separately | Model-dependent fit 29.75 (200-resample interval 29.0 to 31.0) from 162 single-pipe scenes |
| Clear | 1.7 s in squares 1/270 of the height | 1.67 s (4 segments), 0.9 s (2 segments); 1.0 to 1.2 s in sibling uploads; squares 4 px at 1080 lines |

The estimated app growth median is about 7 % longer than the pooled tCpn median, using the subtraction above.
`PipesRenderer.advance` caps catch-up at three simulation ticks after a stall, advancing its scheduled tick counter
past older deadlines. It still waits for the simulation to finish, so discarded deadlines can stretch wall-clock
scene lengths. This code applies on every device. The available log does not count discarded deadlines or establish
their contribution to the difference; there is no measurement showing that a real GPU avoids them. Model mismatch
and growth visibility also affect the comparison. The clear time is not one number in the original: it is sized by
timing the graphics hardware, and the recording shows two values.

## Soak

15 minutes were planned. The committed log spans about 19 minutes 46 seconds and records 41 completed scenes at
default settings, 6,733 ball turns, 13,551 elbow turns and 17 teapots. The ball share is 6,733/20,284 = 0.331937.
The earlier session reported no crash, GL error or placeholder fallback, plus PSS samples of 44.8 MB at 6 minutes,
50.3 MB at 19 minutes and 48.7 MB over the next two minutes. Those memory snapshots and full-system error logs are
not committed. The tag-filtered [soak log](renders/soak-log.txt) supports continued scene completion, but cannot by
itself prove flat memory or the absence of all errors.

## Departures from the plan

- **Calibration levels.** The plan named ball shares 0, 1/4, 1/3, 1/2 and 1. The first hand-count calibration used
  0, 1/3 and 2/3. The automatic detector used all six levels (0, 1/4, 1/3, 1/2, 2/3, 1).
- **Automatic detector.** Written after a first version fired on crossing pipes; the roundness test was added.
  Per frame it is weak (correlation 0.63) and is used only averaged.
- **Teapots.** The plan asked for automatic candidate detection then viewing. That pipeline failed calibration.
  Hand counts also lack a transferable sensitivity estimate and false-positive control, so the original population
  rate was not measured successfully. The app retains the source's 1 in 1000; the plan's rate-measurement goal remains
  unmet. See [JOINTS.md](JOINTS.md).
- **Speed, third estimate.** A direct count of segments in straight runs gives only the shape of the distribution:
  about 0.4 % of 6,600 near-field growth blobs were classified as holding two segments. Foreshortening, blob
  selection and partial visibility make the scale unusable; this does not establish an upper bound of 30 ticks/s.
- **Pipes per scene.** Counting jumps of the growing head between frames gave 4 to 12 depending on the threshold and
  is not reliable. Five pipes a scene is supported only weakly and indirectly: the model gives 785 steps a scene for 5 pipes and 876
  for 7 (12 % more), so more pipes require a higher fitted tick rate for the same scene lengths. Because the frame
  analysis gives no reliable upper rate bound, this cannot independently prove five pipes. Five remains the source's
  single-pipe default.
- **Ball end caps against turns.** Counters were told to exclude end caps. Renders include the same nominal ten end
  caps per five-pipe scene, but residual confusion was not separately measured. Calibration assumes that confusion
  transfers between renders and footage; it does not prove it was removed.
- **Encoding match.** Renders were not re-encoded to match the footage's compression (see JOINTS.md).
- **Review.** The plan's second skeptic review is in [SKEPTIC.md](SKEPTIC.md).

## Checks during the second review (2026-10-22)

Local checks passed: five speed-fit regression tests, six teapot geometry tests, both 2,000-resample label analyses,
and `review_evidence.py --speed`. The corrected KS calculation gives a pooled fit of 29.75 ticks/s, KS 0.07991.
The reproducible pooled bootstrap uses seed 3, 200 resamples, and order statistics 5 and 194: 29.0 to 31.0 ticks/s.
Its RNG starts at the pooled fit; it does not replay the old per-recording bootstrap draws. This interval conditions
on the simulation model and measured growth windows and excludes model and visibility bias.

The native sandbox could not open the global Gradle wrapper lock for writing, so its requested Android checks did
not run there. The coordinator independently ran the same command on the host with JDK 21 and the cached toolchain:
`gradlew.bat --offline --no-daemon --max-workers=2 testDebugUnitTest lintRelease assembleDebug assembleRelease`.
The reported result was BUILD SUCCESSFUL in 33 seconds, 31 JVM tests with no failures/errors, release lint with no
issues, and both debug and release assemblies successful. The coordinator also reported all six teapot tests passing.
No app source changed during this review.

A fresh shallow clone from GitHub at commit `14eba3a` then passed the same Android command on the host in
46 seconds, with all 88 tasks executed. All 31 JVM tests passed, release lint reported no issues, and debug and
release APKs assembled successfully. Six teapot checks, five speed-fit regression tests and the default evidence
summary also passed in that clone. Its working tree remained clean. This verifies a fresh source checkout with
the installed JDK 21, SDK 34 and cached dependencies, not a machine without those prerequisites.
