# pipes-tv refinement: design

Date: 2026-10-21. Source: the owner's handwritten notes, transcribed and confirmed in chat.

## Goals

1. A more marketable name everywhere a person sees it.
2. US spelling and no sloppiness anywhere in the repo.
3. Higher realism, grounded in a reference audit of at least 30 scenes, with growth speed measured precisely and
   the color model checked.
4. Smooth on low-end Android TVs.
5. A README modeled on the styling of the cleanyourtv README.

## Decisions already made

| Topic | Decision |
|---|---|
| Name | "3D Pipes: XP Screensaver for Android TV" in the README title, GitHub description and showcase row. The TV app label and screensaver picker show "3D Pipes". |
| Slugs | The GitHub repo stays `pipes-tv`; the package ID stays `io.github.thewombraider.pipestv`, so links and installs keep working. |
| Joint style | Follow-up decision: use multiple pipes and elbow turns by default, matching the 33 measured scenes of the XP recording. Keep spherical end caps and the ball/mixed preview options. The recording establishes its selected setting, not a universal XP-only joint style. |
| Spelling | US English in every case: color, behavior, gray, center, license. "-our" never stands in for "-or". Third-party license text quoted in NOTICE stays verbatim. |
| Footage | Public recordings of the original screensaver only. At least three independent recordings, cross-checked. |
| Original code | Used if a legitimately published copy exists and its license allows it. Never leaked Windows source, never a disassembled `sspipes.scr`. Open recreations (1j01/pipes, xscreensaver's GPL pipes) may be consulted for behavior only; no code is copied. |
| Performance bar | Steady 60 fps at 1080p on Mali-G31/G52-class sticks (Chromecast HD, Onn 4K). Proxy: frame time of 8 ms or less on the TCL 75T7, plus a throttled emulator run. The README says "designed for", not "tested on", those sticks. |

## Phase 1: reference audit

Output: a rewritten `docs/REFERENCE_ANALYSIS.md` and the scripts that produced it under `tools/reference/`.

- **Footage.** Download each recording locally with `yt-dlp` for analysis; nothing downloaded is committed. Record
  source URL, resolution, container frame rate, and the real frame cadence (duplicate-frame detection), so a
  recording that was resampled or sped up is caught.
- **Scenes.** Split each recording into scenes at the dissolve (a frame that is near-black after a stipple run).
  Catalog at least 30 complete scenes across the recordings with source, start, end and duration.
- **Speed.** Measured per frame. Track a growing pipe end on a straight run across consecutive frames, and convert
  pixels to lattice units with that same run's joint-to-joint spacing, which cancels camera distance and angle along
  the run. Report the mean and spread per recording and overall; a recording that disagrees with the others is
  flagged and explained or excluded.
- **Colors.** Sample pipe colors in every scene (lit midtones, not highlights or shadow), cluster them, and decide
  between a fixed palette and random RGB, plus how many pipes and colors a scene holds.
- **Everything else re-measured:** tube and ball size relative to the lattice, joint types and their mix, pipe count
  and start times, scene length, end coverage, camera distance and field of view, light direction, material,
  dissolve length and pattern, teapot rate.
- **Labels.** Each finding is Observed, Measured (with n and spread) or Estimated, with its method.
- **Skeptic pass.** A separate reviewer gets only the raw measurements and the claims and tries to refute each.
  Refuted rows are re-measured or downgraded. Its verdicts are kept in an appendix.

## Phase 2: fidelity changes

Update the `PipeSim` and `PipesRenderer` defaults to the Phase 1 findings. Unit tests pin the new values. Emulator
captures are compared side by side with reference frames, scored with `tools/score_frames.py` and judged by eye.

## Phase 3: performance

Measure first, on the TCL and the emulator, then change only what the numbers point at. Candidates: fewer triangles
on distant pipes, fewer per-frame buffer uploads, cheaper anti-aliasing on weak GPUs, and a lower internal render
resolution chosen automatically when frames run slow. Before and after numbers are recorded in the README.

## Phase 4: text and branding

- Rename as above (strings, README, GitHub description).
- A US-English and sloppiness pass over every file: README, NOTICE, docs, code comments, test names.
- README rebuilt in the cleanyourtv style: a centered banner made from a real render, flat badges in its colors
  (`cfffcf` on `15181e`), "How it works", a "Quick start" table, "What you get" bullets with bold leads, and a 2x2
  screenshot table with italic captions.

## Phase 5: ship

Deploy to the TV, then put it in standby with wireless debugging left on. Push. Open a follow-up PR to
Gathin23/cleanyourtv that renames the showcase row.

## Testing

Unit tests and lint stay green after every phase. Emulator captures come before any use of the TV.

## Out of scope

Settings screens, new joint modes, network access of any kind.
