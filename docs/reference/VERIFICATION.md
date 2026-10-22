# Verification of the app against the footage, and where the work departed from the plan

The plan: [single-pipe, mixed-joints design](../design/2026-10-21-single-pipe-mixed-joints-design.md).
All runs were on an Android 14 emulator (1920 x 1080, software rendering). The TV was only updated at the end, with
its screen left off.

## The app's default scenes beside the recording

20 default scenes rendered by the app, measured the way the footage was. Images: [app-vs-recording.jpg](app-vs-recording.jpg).

| | App | Recording (tCpn, six 10-minute segments) |
|---|---|---|
| Ball share of turns | 0.332 over 20,284 turns in the 19-minute run | 0.30 (0.28 to 0.33) by hand count; 0.26 (0.17 to 0.35) by detector |
| Teapots a scene | 0.41 in that run | about 0.5 (0.1 to 2) by hand count |
| Light offset, horizontal / vertical (brightest line on straight pipes, center of the picture) | +0.28 / -0.30 | +0.20 to +0.28 / -0.29 to -0.35 by segment |
| Coverage at the end of a scene | median 0.33 (0.19 to 0.64) | median 0.36 (10 to 90 %: 0.22 to 0.50) |
| Scene length | median 28.3 s per scene including the clear (41 scenes), so about 26.6 s of growth | growth 22.6 to 26.6 s by segment, median 25.2 s |
| Steps a second | 30 | 29.8 (28.8 to 31.0) from 162 single-pipe scenes |
| Clear | 1.7 s in squares 1/270 of the height | 1.67 s (4 segments), 0.9 s (2 segments); 1.0 to 1.2 s in sibling uploads; squares 4 px at 1080 lines |

The app's scenes run about 5 % longer than the recording's. The emulator's software renderer sometimes falls behind
and skips steps, which stretches scenes; a real GPU does not. The clear time is not one number in the original: it
is sized by timing the graphics hardware, and the recording shows two values.

## Soak

15 minutes were planned; the run lasted 19 minutes at default settings: 41 scenes, no crash, no GL error, no
fallback to the placeholder teapot; memory (PSS) 44.8 MB at 6 minutes, 50.3 MB at 19 minutes and 48.7 MB over the
next two minutes. Log: [renders/soak-log.txt](renders/soak-log.txt).

## Departures from the plan

- **Calibration levels.** The plan named ball shares 0, 1/4, 1/3, 1/2 and 1. The first hand-count calibration used
  0, 1/3 and 2/3. The automatic detector used all six levels (0, 1/4, 1/3, 1/2, 2/3, 1).
- **Automatic detector.** Written after a first version fired on crossing pipes; the roundness test was added.
  Per frame it is weak (correlation 0.63) and is used only averaged.
- **Teapots.** The plan asked for automatic candidate detection then viewing. The candidate stage did not find
  teapots, so the rate rests on hand counts of the final frames over 60 minutes of tCpn. See [JOINTS.md](JOINTS.md).
- **Speed, third estimate.** A direct count of segments in straight runs gives only the shape of the distribution:
  almost no frame holds two segments (about 0.4 % of 6,600 near-field growth blobs), so the recording cannot be
  ticking much faster than the 30 frames a second. Foreshortening and partial visibility make the scale unusable.
- **Pipes per scene.** Counting jumps of the growing head between frames gave 4 to 12 depending on the threshold and
  is not reliable. Five pipes a scene is supported only weakly and indirectly: the model gives 785 steps a scene for 5 pipes and 876
  for 7 (12 % more), so 7 pipes would need about 35 steps a second to match the measured scene lengths, above the 30 a
  second the frame counts allow, while 5 pipes fit 29.8.
- **Ball end caps against turns.** Counters were told to exclude end caps. The renders (which have 10 end caps a scene)
  absorb the effect, so it is calibrated, not separately counted.
- **Encoding match.** Renders were not re-encoded to match the footage's compression (see JOINTS.md).
- **Review.** The plan's second skeptic review is in [SKEPTIC.md](SKEPTIC.md).
