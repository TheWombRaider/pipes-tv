"""Cuts final frames into blind crops for counting joints, and writes the key that maps them back.

Each frame is cut into four quadrants and saved as a JPEG named by a random id, so a person counting the joints in
a crop cannot tell whether it comes from a recording or from the app, or what share of balls the app was given.

Usage: python -I tools/reference/make_label_crops.py <out-dir> <spec.json>
  spec.json: [{"source": "tCpn", "kind": "footage", "dir": "<folder of pngs>", "truth": {"s00": [B, E, T], ...}}, ...]
  Frames narrower than 1600 px (the 4:3 uploads) are scaled up 2x first, so crops come out about 960 px wide.
Writes <out-dir>/crops/<id>.jpg, <out-dir>/key.json (private: never show it to a counter) and
<out-dir>/batches/batch-NN.txt (lists of ids, shuffled, 40 per batch).
"""
import glob
import json
import os
import random
import sys

import cv2


def main(out, spec_path):
    spec = json.load(open(spec_path))
    os.makedirs(os.path.join(out, "crops"), exist_ok=True)
    os.makedirs(os.path.join(out, "batches"), exist_ok=True)
    rng = random.Random(20261021)
    key = {}
    for item in spec:
        for path in sorted(glob.glob(os.path.join(item["dir"], "*.png"))):
            name = os.path.splitext(os.path.basename(path))[0]
            frame = cv2.imread(path)
            if frame.shape[1] < 1600:
                frame = cv2.resize(frame, None, fx=2, fy=2, interpolation=cv2.INTER_LANCZOS4)
            h, w = frame.shape[:2]
            for q, (y0, x0) in enumerate(((0, 0), (0, w // 2), (h // 2, 0), (h // 2, w // 2))):
                crop = frame[y0:y0 + h // 2, x0:x0 + w // 2]
                cid = "%08x" % rng.getrandbits(32)
                cv2.imwrite(os.path.join(out, "crops", cid + ".jpg"), crop, [cv2.IMWRITE_JPEG_QUALITY, 92])
                key[cid] = {"source": item["source"], "kind": item["kind"], "frame": name, "quadrant": q,
                            "truth": item.get("truth", {}).get(name)}
    ids = list(key)
    rng.shuffle(ids)
    json.dump(key, open(os.path.join(out, "key.json"), "w"), indent=1)
    for b in range(0, len(ids), 40):
        with open(os.path.join(out, "batches", "batch-%02d.txt" % (b // 40)), "w") as f:
            f.write("\n".join(ids[b:b + 40]) + "\n")
    print(len(ids), "crops,", (len(ids) + 39) // 40, "batches")


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
