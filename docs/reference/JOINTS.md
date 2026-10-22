# Ball joints, elbows and teapots: what the footage shows

How the share of ball joints among turns, and the teapot rate, were measured. Methods:
[LABELING.md](LABELING.md) (hand counts) and [TEAPOT_CANDIDATES.md](TEAPOT_CANDIDATES.md) (candidate viewing).
Tools: `tools/reference/` (`final_frames.py`, `make_label_crops.py`, `analyze_labels.py`, `ball_detector.py`,
`analyze_detector.py`, `teapot_candidates.py`).

## Footage and renders

- **Footage.** The last frame of growth of 136 complete scenes of the single-pipe, mixed-joint recording (tCpn, 60
  minutes in six 10-minute segments at minutes 0, 60, 120, 180, 240 and 300): 122 frames, plus 14 from the same
  uploader's 3:2 version.
- **Renders.** Final frames of this app at 1920 x 1080 with a known ball share (0, 0.25, 0.33, 0.5, 0.67, 1.0), 92
  frames from the first calibration run, with the true counts logged by the app. A further 24 renders, chosen for
  holding teapots, calibrate the second counting round.

## Method 1: blind hand counts

Every frame was cut into four quadrants (760 crops in two rounds), shuffled under random ids, and counted by two
separate counters who were given only the protocol: balls at turns, elbows at turns, teapots. They did not know which
crops were renders or what share the app was given. Counters see balls more easily than elbows (a ball bulges out
past the pipe), so the raw share reads high; the renders measure that bias and the footage's share is read back
through them.

| | Round 1: 75 frames (tCpn minutes 0, 60, 120 and the 3:2 upload), 2,657 turns | Round 2: 61 frames (tCpn minutes 180, 240, 300), 2,276 turns |
|---|---|---|
| Counter A, raw share in the footage | 0.44 to 0.46 | 0.46 to 0.48 |
| Counter A, renders at true 0.00 / 0.33 / 0.60-0.67 | read 0.02 / 0.49 / 0.66 | read 0.02 / 0.54 / 0.70 |
| **Counter A, footage after correction** | **0.301 (0.277 to 0.326)** | **0.289 (0.266 to 0.315)** |
| Counter B, renders at true 0.00 / 0.33 / 0.60-0.67 | read 0.01 / 0.46 / 0.63 | read 0.02 / 0.53 / 0.68 |
| **Counter B, footage after correction** | **0.302 (0.279 to 0.329)** | **0.301 (0.278 to 0.325)** |

Intervals use 2,000 bootstrap resamples of frames, with the calibration's sampling uncertainty included. The counters
differ by 0.6 to 0.7 balls and 0.7 to 0.8 elbows per crop on average. Every recording or segment gives 0.29 to 0.31
on its own for counter A; counter B's segment estimates range from 0.285 to 0.323. Counter A counted 4,933 turns
and counter B counted 4,796 in the same 136 footage frames. These are overlapping observations, not independent
sets of turns. The intervals assume frames are exchangeable within each group and calibration transfers to footage;
they do not include systematic counting errors, compression differences, or selection of teapot-rich round-2 renders.

## Method 2: an automatic detector, calibrated on renders

`ball_detector.py` finds round swellings at least 1.3 times as wide as the pipe next to them whose outline is round
(a ball), and the density of those per pipe radius of visible length is the statistic. A line is fitted through the
92 renders of known share (density = 0.0089 + 0.0207 x share, correlation 0.63),
and the footage is read off it. Per frame it is noisy, because pipes crossing at different depths and pipe ends also
make swellings, so it is only useful averaged over many frames. The mean density is not monotonic at every level:
it is 0.0172 near a true share of 0.25 and 0.0151 near 0.33. The fitted trend is positive; the individual levels
do not establish that every nearby share can be distinguished.

| | 136 footage frames |
|---|---|
| Calibrated ball share | **0.26 (0.17 to 0.35)** |
| By segment | 0.20 to 0.32, each with an interval of 0.3 or more |

Full output: [detector-results.txt](detector-results.txt).

## Decision

The plan's rule: use the code's 1 in 3 if the footage interval includes it; if it excludes it and both methods agree,
use the footage value; if the methods disagree, use 1 in 3 and record it.

The automatic interval includes 1/3. The hand-count intervals stop just short of it (upper ends 0.315 to 0.329). The
methods therefore do not both exclude it, and **the app uses 1 turn in 3**. The footage is heavily compressed video
(about 180 kbps) and the renders are not. Compression can change which joints are visible, but neither the direction
nor the size of its effect on the ball share was measured. The renders were not re-encoded to match.

## Teapots

Two approaches were attempted. Neither establishes the population rate.

- **Candidates, then viewing (the plan's method).** The detector marks large round swellings (at least 1.9 times the
  neighbor's radius) as teapot candidates, and two viewers judge each crop. Of 246 candidates (122 in the renders,
  124 in the footage) the viewers marked none in the renders, which hold 40 real teapots, and 1 or 2 in the footage.
  This failed calibration means the method **cannot measure the rate**, and its footage
  count is not used. The failure is in the combined proposal-and-viewing pipeline; these data do not isolate which
  stage missed each real teapot.
- **Hand counts.** In round 1 the counters found 5 and 6 teapots in the 75 footage frames; in round 2, 1 and 1 in
  the 61 frames. Round 1's 30 render frames hold 8 true teapots; the counters reported 1 and 2. Round 2's 24 selected
  render frames hold 30 true teapots; both counters reported 2. Pooled reported-to-true count ratios are 3/38 = 7.89 %
  and 4/38 = 10.53 %. These are not matched detections: the labels do not locate teapots, so false positives cannot
  be separated from true detections. Calling those ratios recall assumes every reported teapot was real.

Dividing footage counts by those pooled ratios would give 0.56 and 0.49 teapots per scene. That calculation assumes
zero false positives, the same visibility and sensitivity in footage and renders, and a representative calibration.
None is established. The second render set was selected for containing teapots, and compression, occlusion, size and
material can change visibility. Even under an independent-binomial interpretation, Wilson 95 % intervals for 3/38
and 4/38 are approximately 2.72 to 20.80 % and 4.17 to 24.13 %. They are intervals for the assumed detection
probability, not the footage population rate, and omit transfer and false-positive uncertainty.

The previous claim of 0.5 teapots per scene with an interval of 0.1 to 2, the exclusion of 1 in 20,000, and the
factor-of-two-or-three precision claim are withdrawn. These labels cannot establish a calibrated population rate.
The same visibility problem prevents the earlier zero count in MKqr from resolving its teapot rate.

The app retains **1 in 1000 as a code-derived default**, not a measured footage rate. For a fixed 480 eligible turns,
that means an expected count of 480/1000 = **0.48 teapots per scene**, while the probability of at least one is
1 - (1 - 0.001)^480 = **0.3814**. At 1 in 20,000, the expected count is 0.024. Turn counts vary by scene.
The soak's 17 teapots in 41 scenes (0.4146 per scene) describe the app's run, not the original recording.

## Limits

- The counters and viewers are language-model reviewers, not people. Agreement and render calibration are internal
  consistency checks, not independent validation of the footage labels. No object-level footage ground truth was
  established. [SKEPTIC.md](SKEPTIC.md) reviews the arithmetic and inference limits.
- Only the single-pipe, mixed recordings were counted.
- The hand-count calibration used piecewise linear interpolation through three render groups; the detector used
  a fitted straight line through all render frames. Neither interval includes calibration transfer bias.
