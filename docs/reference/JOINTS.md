# Ball joints, elbows and teapots: what the footage shows

How the share of ball joints among turns, and the teapot rate, were measured. Method: [LABELING.md](LABELING.md).
Tools: `tools/reference/final_frames.py`, `make_label_crops.py`, `analyze_labels.py`.

## Method in brief

- **Footage.** The last frame of growth of 75 complete scenes of the single-pipe, mixed-joint recording
  (tCpn): 23 from the first ten minutes, 20 from minute 60 and 18 from minute 120, plus 14 from the same uploader's
  3:2 version.
- **Calibration renders.** 30 final frames of this app at 1920 x 1080 with the ball share set to 0, 1/3 and 2/3
  (10 each), logged from the app so the true counts are known.
- **Counting.** Every frame was cut into four quadrants, 420 crops in all, shuffled under random ids, and counted
  by two separate counters who were given only the protocol: balls at turns, elbows at turns, teapots. They did
  not know which crops were renders or what share the app was given.
- **Correction.** Counters see balls more easily than elbows (a ball bulges out past the pipe), so the raw share
  reads high. The renders measure that bias, and the footage's share is read back through them.

## Results

| | Counter A | Counter B |
|---|---|---|
| Crops counted | 420 | 420 |
| Raw share in the footage (turns) | 0.44 to 0.46 per recording | similar |
| Renders at true share 0.00 | read 0.02 | read 0.01 |
| Renders at true share 0.33 | read 0.49 | read 0.46 |
| Renders at true share 0.67 | read 0.66 | read 0.63 |
| **Footage share after correction** | **0.30 (0.28 to 0.32)** | **0.30 (0.28 to 0.33)** |

Intervals are 95 % bootstrap intervals over frames, with the calibration's own uncertainty included. The two
counters differ by 0.7 balls and 0.8 elbows per crop on average, and their overall raw shares are 0.43 and 0.41.
Each recording (three segments of tCpn, the 3:2 upload) gives 0.30 on its own.

## What the app uses

**1 turn in 3**, the value in the original's code. The footage reads 0.30, just under it. The footage is heavily
compressed video (about 180 kbps) and the renders were not, which hides joints in the footage slightly more than
in the renders, and that would bias the corrected share low. The renders were not compressed to match, so the
size of that bias is not measured. A share of 0.30 and one of 0.33 are not distinguishable by eye. The code's
value is kept because the footage does not clearly contradict it.

## Teapots

Counters found 5 (A) and 6 (B) teapots in the 75 footage frames. In the renders, which contain 8 teapots in 30
frames, the counters found 1 and 2. They miss most teapots (they are small and often hidden), so the footage's
true rate is several times what was counted: about 0.4 per scene, which is what the code's 1 turn in 1000 gives
for a single-pipe scene (about 480 turns, so 0.43 a scene). A rate of 1 in 20,000 would predict nearly none.
The app uses 1 in 1000. The estimate is rough (a handful of counts), and good only to a factor of two or three.

## Limits

- The counters are language-model reviewers, not people. Their agreement and their calibration against known
  renders are the evidence of their quality; nothing else was checked by a third party.
- Only recordings of one uploader's captures (the single-pipe, mixed ones) were counted.
- The calibration used three share levels and a straight line between them.
