"""Saves the last frame of growth of every complete scene in a recording, found with the scene tables.

Usage: python -I tools/reference/final_frames.py <video> <stats.csv> <fps> <out-dir>
Writes <out-dir>/s00.png, s01.png, ... (one per complete scene) and prints how many.
"""
import os
import sys

import cv2

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import scenes as S  # noqa: E402


def main(video, stats, fps, out):
    found = S.scenes(S.load(stats), fps)
    os.makedirs(out, exist_ok=True)
    cap = cv2.VideoCapture(video)
    for i, s in enumerate(found):
        cap.set(cv2.CAP_PROP_POS_FRAMES, int((s["start"] + s["grow"] - 0.05) * fps))
        ok, frame = cap.read()
        if ok:
            cv2.imwrite(os.path.join(out, f"s{i:02d}.png"), frame)
    print(len(found))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2], float(sys.argv[3]), sys.argv[4])
