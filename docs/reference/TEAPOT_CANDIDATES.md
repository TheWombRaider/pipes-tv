# Judging teapot candidates: the protocol

An automatic detector (`tools/reference/ball_detector.py`) marks every round bulge much wider than the pipe next to
it. A teapot is one such bulge, but so are many other things: a pipe close to the camera, two pipes crossing, a
large ball joint. Every marked spot is cropped (`tools/reference/teapot_candidates.py`) and a person decides which
are teapots. The viewers are given only this page and a list of crop files, and are not told where a crop comes from.

## The task

Each crop is a 240 x 240 picture centered on one candidate. Decide whether the thing at the center is a **Utah
teapot**: a rounded body with a flat lid and a small knob on top, a curved spout on one side and a loop handle on
the other, drawn in one flat color like the pipes around it. At this size it may be only a few dozen pixels across,
and parts of it may be hidden behind pipes. It sits where a pipe joint would be, with pipes running in and out.

Write one line of JSON per crop, in the order given:

    {"id": "1a2b3c4d", "teapot": true}

- `true` only if you can see the lid or the spout and handle clearly enough to be sure.
- `false` for ball joints, elbows, pipes passing in front of each other, plain pipe ends, or anything you cannot
  identify. When in doubt, `false`. Do not guess to reach a number.
- Add `"unsure": true` if the crop was too small, dark or crowded to decide.

Judge each crop on its own. Do not look for patterns across crops and do not try to infer where a crop came from.
No other text in the file.
