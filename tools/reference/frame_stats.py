"""Per-frame statistics of a recording of the original screensaver.

Every frame is decoded by ffmpeg straight to a working size of 480 pixels wide, and for each one:
  lit     fraction of pixels that are not black (any channel above THRESH)
  diff    fraction of pixels that changed from the previous frame by more than THRESH
  blobs   connected regions of change of at least MIN_BLOB pixels (new pipe pieces, roughly)
  big     area of the largest such region, in working pixels
  bx, by, bw, bh   bounding box of that region (working pixels)
  dr      radius of the largest circle inside it (working pixels): about a pipe's radius for a plain segment, more
          for a ball joint or a teapot, which are wider than the pipe

Usage: python -I tools/reference/frame_stats.py <video> <out.csv>
Needs: numpy, opencv-python-headless, imageio-ffmpeg.
"""
import csv
import json
import re
import subprocess
import sys

import cv2
import imageio_ffmpeg
import numpy as np

THRESH = 28
MIN_BLOB = 6
WIDTH = 480


def probe(path):
    """Width, height and frame rate of the first video stream, read from ffmpeg's banner."""
    ff = imageio_ffmpeg.get_ffmpeg_exe()
    info = subprocess.run([ff, "-hide_banner", "-i", path], capture_output=True, text=True).stderr
    for line in info.splitlines():
        if "Video:" in line:
            w, h = (int(v) for v in re.search(r"\b(\d{2,5})x(\d{2,5})\b", line).groups())
            fps = float(re.search(r"([\d.]+) fps", line).group(1))
            return w, h, fps
    raise SystemExit(f"no video stream in {path}")


def frames(path, width, height):
    ff = imageio_ffmpeg.get_ffmpeg_exe()
    cmd = [ff, "-v", "error", "-i", path, "-vf", f"scale={width}:{height}:flags=area", "-f", "rawvideo",
           "-pix_fmt", "bgr24", "-"]
    proc = subprocess.Popen(cmd, stdout=subprocess.PIPE, bufsize=width * height * 3 * 8)
    size = width * height * 3
    while True:
        buf = proc.stdout.read(size)
        if len(buf) < size:
            break
        yield np.frombuffer(buf, np.uint8).reshape(height, width, 3)
    proc.wait()


def main(path, out):
    w, h, fps = probe(path)
    height = round(WIDTH * h / w / 2) * 2
    kernel = np.ones((3, 3), np.uint8)
    prev = None
    count = 0
    with open(out, "w", newline="") as f:
        wr = csv.writer(f)
        wr.writerow(["frame", "t", "lit", "diff", "blobs", "big", "bx", "by", "bw", "bh", "dr"])
        for i, small in enumerate(frames(path, WIDTH, height)):
            lit = float((small.max(axis=2) > THRESH).mean())
            bx = by = bw = bh = 0
            dr = 0.0
            if prev is None:
                diff, blobs, big = 0.0, 0, 0
            else:
                change = (cv2.absdiff(small, prev).max(axis=2) > THRESH).astype(np.uint8)
                diff = float(change.mean())
                change = cv2.dilate(change, kernel)
                n, labels_, stats, _ = cv2.connectedComponentsWithStats(change, connectivity=8)
                areas = stats[1:, cv2.CC_STAT_AREA]
                areas = areas[areas >= MIN_BLOB]
                blobs = int(len(areas))
                big = int(areas.max()) if len(areas) else 0
                if big:
                    k = 1 + int(np.argmax(stats[1:, cv2.CC_STAT_AREA]))
                    bx, by, bw, bh = (int(v) for v in stats[k, :4])
                    part = (labels_[by:by + bh, bx:bx + bw] == k).astype(np.uint8)
                    dr = float(cv2.distanceTransform(part, cv2.DIST_L2, 3).max())
            wr.writerow([i, f"{i / fps:.4f}", f"{lit:.5f}", f"{diff:.5f}", blobs, big, bx, by, bw, bh, f"{dr:.1f}"])
            prev = small
            count = i + 1
    print(json.dumps({"video": path, "frames": count, "fps": fps, "width": w, "height": h}))


if __name__ == "__main__":
    main(sys.argv[1], sys.argv[2])
