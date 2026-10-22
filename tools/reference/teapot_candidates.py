"""Cuts a crop around every big bulge the detector finds in a set of final frames, for judging by eye.

A teapot's body is about 2.5 pipe radii, so it shows up as a bulge 1.9 times or more the neighboring pipe's radius
(ball_detector.BIG_RATIO). Most big bulges are other things (pipes crossing, a pipe close to the camera), so every
candidate is cropped, 7 radii on a side, enlarged to 240 pixels, and a person decides which are teapots.

Usage: python -I tools/reference/teapot_candidates.py <out-dir> <name=frames-dir> ...
Writes <out-dir>/crops/<id>.jpg and <out-dir>/key.json mapping each id to its source, frame and position.
"""
import json
import os
import random
import sys

import cv2

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import ball_detector  # noqa: E402


def main():
    out = sys.argv[1]
    os.makedirs(os.path.join(out, "crops"), exist_ok=True)
    rng = random.Random(77)
    key = {}
    for spec in sys.argv[2:]:
        name, folder = spec.split("=", 1)
        for f in sorted(os.listdir(folder)):
            if not f.endswith(".png"):
                continue
            frame = cv2.imread(os.path.join(folder, f))
            _, bigs, _ = ball_detector.analyze(frame, detail=True)
            for r, x, y, q in bigs:
                half = int(7 * r / 1.0)
                half = max(half, 40)
                x0, y0 = max(0, int(x) - half), max(0, int(y) - half)
                crop = frame[y0:int(y) + half, x0:int(x) + half]
                if crop.size == 0:
                    continue
                crop = cv2.resize(crop, (240, 240), interpolation=cv2.INTER_CUBIC)
                cid = "%08x" % rng.getrandbits(32)
                cv2.imwrite(os.path.join(out, "crops", cid + ".jpg"), crop, [cv2.IMWRITE_JPEG_QUALITY, 92])
                key[cid] = {"source": name, "frame": f[:-4], "x": int(x), "y": int(y), "r": round(r, 1), "ratio": round(q, 2)}
    json.dump(key, open(os.path.join(out, "key.json"), "w"), indent=1)
    print(len(key), "candidates")


if __name__ == "__main__":
    main()
