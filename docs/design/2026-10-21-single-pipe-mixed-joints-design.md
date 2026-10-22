# Single pipe, mixed joints, and the showcase description: plan

Date: 2026-10-21. Status: **approved by the owner, recommendation A (light and clear included). The TV gets a silent install at the end; its screen stays off throughout.**

## What the owner reported

1. The 3D Pipes row in cleanyourtv's `SHOWCASE.md` still describes the project as it was, and no longer describes it
   accurately.
2. In the original screensaver, only one pipe grows at a time, and the turns are a mix of ball joints and elbows. The
   app currently grows several pipes at once and turns only with elbows. The share of ball joints must be measured and
   built in. A 1:1 recreation, and the nostalgia that comes with it, is the first goal.
3. Test on an emulator only; do not touch the TV tonight. The exact XP teapot data is good enough as it is.

## What went wrong, plainly

The app defaulted to "multiple pipes, elbow turns" because the recording titled "Windows XP screensaver" shows that,
and the earlier analysis treated the one recording that disagreed as an outlier. That reasoning was weak:

- The recordings split into two groups, not one majority and one outlier. The group that matches the original
  program's published code on every checkable point (single pipe, mixed joints, light from the upper right, a slow
  clear in visible squares) is recording tCpn. The other recordings differ from the code on light and on the clear, so
  they cannot be the reference for a 1:1 copy of the code's behavior.
- The owner's own memory of the screensaver (one pipe at a time, a mix of joints) agrees with tCpn and with the code.
  First-hand memory of the real thing outranks my choice of which video to trust.
- Nothing in the earlier work measured the ball-to-elbow share. It was taken from the code and never checked against
  footage, and then it was switched off.

The current defaults are therefore wrong, and the plan below corrects the cause (the choice of reference), not only the
two settings.

## Plan

### A. Pick one reference version, then follow it everywhere

A mix of features from different recordings gives something that never existed. The plan re-baselines on one coherent
version.

**Recommendation: follow the original program's code and the recording that matches it (tCpn).** That means:

| Setting | Now | After |
|---|---|---|
| Pipes | several at once, 7 per scene | one at a time, 5 per scene (as in the code) |
| Joints | elbows only | mix of elbows and balls (share measured, see B) |
| Light | straight above the camera, (0, 90, 150) | upper right and in front, (90, 90, 150), as in the code and tCpn |
| Clear | 0.57 s, squares 1/270 of the height | about 1.7 s, squares 1/270 of the height, as in tCpn |
| Speed | 30 steps a second | re-measured on single-pipe recordings (see B) |

The light and the clear are included because they come with the same reference. If the owner would rather keep the
current light and clear and change only pipes and joints, say so in the review and A is reduced to the first two rows.

### B. Measure, do not assume

1. **Share of ball joints.** Two independent methods, each on at least 300 turns from at least 40 scenes in the
   single-pipe, mixed recordings (tCpn, extended with more minutes of the same upload for sample size):
   - *Calibrated image statistic.* Render scenes from the app itself at 1920 x 1080 with known ball shares (0, 1/4,
     1/3, 1/2, 1), run one detector over both the renders and the footage (circles wider than the neighboring pipe,
     about 1.5 times its width), and read the footage's share off the calibration curve.
   - *Blind hand count.* Crops of individual turns are labeled ball or elbow by two separate reviewers who are not told
     which recording or expected share, and the labels are compared.
   - **Decision rule (so it needs no one awake):** the code says 1 turn in 3. Use 1/3 if the footage estimate's 95 %
     interval includes it. If the interval excludes 1/3 and both methods agree, use the footage value. If the methods
     disagree, use 1/3 and record the disagreement.
2. **Speed.** Three estimates, compared: the cadence of new pipe on native 30 and 60 fps recordings; scene length
   divided by the modeled ticks for single-pipe scenes (about 777 ticks per scene at 16:9); and a direct count of
   segments added over a fixed time in straight runs. The earlier cadence argument was invalid for two recordings
   (doubled frames), so only native recordings count. Keep 30 steps a second unless the three estimates agree on a
   different value to within the measurement error.
3. **Teapot rate.** Mixed joints bring teapots back. The code makes 1 turn in 1000 a teapot, which would put roughly
   one teapot in every other single-pipe scene. Count teapots in at least 60 minutes of tCpn (automated candidate
   detection, then viewed and counted) and set the rate from the count. If none are found where the code predicts
   many, keep the current estimate (1 in 20,000) and say so in the analysis.
4. **Other checks while in the footage:** pipe count per scene (5 sequential pipes), start-cap and end-cap balls, and
   the share of turns that are ball joints versus end caps, so the two are not confused.

### C. Code changes

- `PipeSim.Params`: defaults `multiple = false`, `joints = MIXED`, `ballChance` from B1, `teapotChance` from B3. Keep
  the options for multiple pipes and for elbow-only or ball-only joints for testing.
- `PipesRenderer`: light vector (90, 90, 150) and its half vector; clear duration; tick rate if B2 changes it.
- Tests: defaults are single and mixed; over thousands of turns the share of balls is within a statistical bound of
  the chosen value; a scene starts exactly one pipe at a time and ends after five; scene length against the reference
  model for single pipe; teapot paths still work in the default mode. Update tests that assumed multiple pipes.
- Keep every unrelated behavior: camera, materials, geometry, teapot asset, performance work.

### D. Documentation and the showcase

- `README.md`, `docs/REFERENCE_ANALYSIS.md`: single pipe, mixed joints, the measured share, light and clear, with the
  measurements and their limits. Replace the outdated "preview mode" wording. Remove claims that no longer hold.
- `docs/reference/`: new measurement scripts, tables and the independent labels; extend `SKEPTIC.md` with a second
  review of the new findings by a reviewer who sees only the data.
- **Showcase row** (item 1): rewrite it to describe the current app (single pipe growing at a time, mix of ball joints
  and elbows, 16 classic materials, the original's rules checked against recordings, the clear, the rare teapot, no
  internet permission, the TCL `AUTO_START` note). Keep the name and link. The row is edited in a new branch off the
  latest upstream `main`. `SHOWCASE.md` is the only file in that pull request.

### E. Verification (emulator only; the TV is not touched, not even to install)

1. Unit tests, lint, debug and release builds; a clean clone of GitHub builds and passes (the teapot hashes included).
2. Emulator renders of at least 20 scenes at 1920 x 1080 with the final defaults, run through the same detectors as the
   footage: ball share, scene length, coverage, light offset, clear duration, speed in ticks. Each must land inside the
   footage's range; anything outside is investigated before shipping, not tuned to pass.
3. Side-by-side contact sheets of final app frames and tCpn frames, viewed.
4. Emulator soak: 15 minutes at default settings with no crash, no GL error, and flat memory.
5. A final check for US spelling and accurate documentation.

### F. Delivery

1. Commit to pipes-tv `main` in small commits (measurements, code, docs) and push.
2. Sync the fork (the token now has `workflow` scope), branch from the latest upstream `main`, change the showcase
   row, push, and open the pull request to Gathin23/cleanyourtv. Check its CI; fix anything it flags.
3. Update the project memory notes and leave the emulators stopped.

## Risks and how they are handled

- **Footage disagrees with the code or with memory.** The decision rules in B state what wins, in advance. Memory and
  code agree with each other here, so a conflict would be reported, not silently resolved.
- **The detectors are fooled by perspective or overlap.** The calibration against the app's own renders at a known
  share, plus the blind hand count, exist to catch exactly that. Disagreement defaults to the code's 1 in 3.
- **Mixed joints bring back the teapot in a visible rate.** B3 measures it before it ships.
- **The TV keeps the old build until the next install.** That is intended; the owner asked not to touch it tonight.

## Not in scope

The XP binary's exact teapot data (accepted as close enough), TV testing, the performance work, new settings screens.
