# Counting joints in crops: the protocol

Used to measure the share of ball joints among turns, and to count teapots, in recordings of the original and in
renders of this app. The counters are given only this page and a list of crop files. They are not told where a crop
comes from, what is expected, or what the app is set to.

## The task

Each crop is a quarter of a final frame: a finished scene, with the pipes drawn on black. For every crop, count what
you can see clearly, and write one line of JSON:

    {"id": "1a2b3c4d", "B": 12, "E": 9, "T": 0}

- **B, ball joints at turns.** A pipe turns a corner and the corner is a sphere, wider than the pipes that meet
  there, so it bulges out past the pipe on both sides. Count it when a pipe runs in on one side and out on another.
- **E, elbows at turns.** A pipe turns a corner and the corner is a smooth rounded bend as thick as the pipe, with no
  bulge. Count it when you can see the bend.
- **T, teapots.** A small Utah teapot (body, lid knob, spout, handle) sitting at a pipe, drawn like the pipes.

## What not to count

- A ball at the free end of a pipe, with the pipe on one side only (an end cap). It is not a turn.
- Any joint cut by the edge of the crop, mostly hidden behind another pipe, or too small to tell apart. When in
  doubt, leave it out. Do not guess to reach a number.
- Pipes that bend away from the camera so you cannot see the corner.
- Joints that look unclear because of blur or compression.

Count each joint once. Count only what is in the crop. Work through the crops in the order listed. Do not look for
patterns across crops, and do not try to infer what a share "should" be.

## Output

One JSON line per crop, in the order given, with no other text in the file. Add `"unsure": true` to a line if the
crop was too crowded or dark to count reliably (still give your best counts).
