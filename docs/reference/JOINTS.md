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
| **Counter A, footage after correction** | **0.30 (0.28 to 0.32)** | **0.29 (0.27 to 0.32)** |
| Counter B, renders at true 0.00 / 0.33 / 0.60-0.67 | read 0.01 / 0.46 / 0.63 | read 0.02 / 0.53 / 0.68 |
| **Counter B, footage after correction** | **0.30 (0.28 to 0.33)** | **0.30 (0.28 to 0.33)** |

Intervals are 95 % bootstrap intervals over frames, with the calibration's own uncertainty included. The counters
differ by 0.6 to 0.7 balls and 0.7 to 0.8 elbows per crop on average. Every recording or segment gives 0.29 to 0.31
on its own. More than 4,900 turns were counted in more than 40 scenes, as the plan required.

## Method 2: an automatic detector, calibrated on renders

`ball_detector.py` finds round swellings at least 1.3 times as wide as the pipe next to them whose outline is round
(a ball), and the density of those per pipe radius of visible length is the statistic. A line is fitted through the
92 renders of known share (density = 0.0089 + 0.0207 x share, correlation 0.63, rising at every level from 0 to 1.0),
and the footage is read off it. Per frame it is noisy, because pipes crossing at different depths and pipe ends also
make swellings, so it is only useful averaged over many frames.

| | 136 footage frames |
|---|---|
| Calibrated ball share | **0.26 (0.17 to 0.35)** |
| By segment | 0.20 to 0.32, each with an interval of 0.3 or more |

Full output: [detector-results.txt](detector-results.txt).

## Decision

The plan's rule: use the code's 1 in 3 if the footage interval includes it; if it excludes it and both methods agree,
use the footage value; if the methods disagree, use 1 in 3 and record it.

The automatic interval includes 1/3. The hand-count intervals stop just short of it (upper end 0.32 to 0.33). The
methods therefore do not both exclude it, and **the app uses 1 turn in 3**. One systematic bias would push the hand
counts low: the footage is heavily compressed video (about 180 kbps) and the renders are not, which hides joints in
the footage a little more than in the renders. That was not corrected for, because the renders were not re-encoded to
match. A share of 0.30 and one of 0.33 are not distinguishable by eye.

## Teapots

Two approaches, and one of them did not work.

- **Candidates, then viewing (the plan's method).** The detector marks large round swellings (at least 1.9 times the
  neighbor's radius) as teapot candidates, and two viewers judge each crop. Of 246 candidates (122 in the renders,
  124 in the footage) the viewers marked none in the renders, which hold 40 real teapots, and 1 or 2 in the footage. The detector almost never proposes a teapot, so this method **cannot measure the rate**, and its footage
  count is not used.
- **Hand counts.** In round 1 the counters found 5 and 6 teapots in the 75 footage frames; in round 2, 1 and 1 in
  the 61 frames. In the renders (38 real teapots in the 54 frames counted by hand) they found 3 to 4, a recall of
  about 9 %. So the footage holds about 0.5 teapots a scene, with a wide interval (0.1 to 2). That is what the code's
  1 turn in 1000 gives for a single-pipe scene (about 480 turns, so 0.43 a scene). A rate of 1 in 20,000 (0.02 a scene)
  lies outside it.

The app uses **1 in 1000**. The estimate is good to a factor of two or three, not better.

## Limits

- The counters and viewers are language-model reviewers, not people. Their agreement with each other and their
  calibration against known renders are the evidence of their quality; nothing was checked by a third party except
  the review in [SKEPTIC.md](SKEPTIC.md).
- Only the single-pipe, mixed recordings were counted.
- The calibration used a straight line through the render levels.
