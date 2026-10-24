# Pipe color variety: audit and fix

Date: 2026-10-23. Status: **paused; check A half done.** Work is on branch `wip/pipe-color-variety`.

Baseline: `db98415`. Report from watching the app on the TV: in a finished scene, several pipes often have
nearly the same color (for example two slightly different blues), so the eye joins them into one huge pipe.
That does not look like the original. Check whether the original does this, and fix the app if it does not.

## What the app does

Each pipe picks one of 16 materials at random when it starts, independently of the others
(`Materials.randomPipeMaterial`). A scene has 5 pipes. Repeats are allowed, and so are look-alikes: by
chromaticity, silver, white plastic, white rubber and pearl are nearly the same, as are jade and green rubber,
and turquoise and cyan rubber (docs/reference/colors.txt).

## What the source says

The NT sample's facts, as transcribed in the GPL port (facts only, no code copied): each pipe takes
`goodMaterials[rand() % 16]` when it starts, with no check against the other pipes. These are the same 16
materials the app uses. If that is what shipped, the original also repeats colors: 5 independent picks
from 16 share a material in half of all scenes, and share a look-alike group even more often.

The source and the report disagree, so the footage decides. There are also two ways the app could look more
uniform than the original even with the same rule:

1. **The rule.** The original might avoid repeats (a different build, or a rule the port missed).
2. **The rendering.** The app's lighting might pull the materials' colors closer together than the original
   does, for example through too much ambient light, which washes saturation out.

## Checks

**A. Distinct colors per scene.** Over the final frames of the single-pipe, mixed-joint recordings (tCpn,
about 145 scenes), count the distinct color groups per frame. Pixels are classed by chromaticity to the
nearest material, as in colors.txt, and the look-alike materials are merged into one group. Count the same
statistic on app renders made two ways, so detection losses (small, hidden or far pipes) affect all three
alike:

- (a) the current rule: independent picks;
- (b) no repeats within a scene: each new pipe takes a material that no earlier pipe in the scene has.

If the footage matches (a), the rule is right and the report is about something else. If it matches (b), or
falls between them clearly above (a), the original avoids repeats.

**B. Rendered color of each material.** For each color group, compare the mean chromaticity and brightness of
its pixels in the footage and in the app renders. If the app's groups sit closer together than the footage's,
the lighting is at fault.

## Fix

Decided by the checks; recorded below with the evidence.

## Results

**Footage (done):** `tools/reference/color_variety.py` on 122 tCpn final frames: 2.23 distinct color groups per
frame (95 % 2.07 to 2.39); frames with 1/2/3/4 groups: 30/44/38/10. 11 groups after merging look-alikes.

**Renders (to do):** the first batch is invalid. `tools/capture_at.py` was run with `hold=2` at the default
`timescale=4`, so the screenshot fell after the clear and caught the next scene's first pipes. Rerun with the
default hold (30 s): 20 renders each with `colors=independent` and `colors=distinct`, in the foreground (a
background job was killed for low memory). Then run `color_variety.py` on footage and both render sets, and
apply the decision rule in Checks A. The debug log line `materials=[...]` gives each render's true materials.

**Code added for the check (not a fix):** `PipeSim.Params.distinctMaterials` (default off), `PipeSim.materials()`,
launch extra `colors=independent|distinct`, and two tests. If the footage matches (a), drop the switch or keep
it as a test-only extra; if it matches (b), make it the default.
