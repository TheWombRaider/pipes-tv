"""Measures how smoothly the running preview presents frames on a device, from the outside, so any two builds
(debuggable or not) can be compared the same way.

It samples SurfaceFlinger's present times for the app's GL surface, which cover the last 128 frames, every couple
of seconds for the given duration, and reports frames per second, the spread of intervals between frames, and the
app's CPU use from top.

Usage: python -I tools/frame_timing.py <serial> [seconds=60]
"""
import os
import re
import statistics
import subprocess
import sys
import time

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe")
PACKAGE = "io.github.thewombraider.pipestv"


def adb(serial, *args):
    return subprocess.run([ADB, "-s", serial, *args], capture_output=True, timeout=60,
                          env={**os.environ, "MSYS_NO_PATHCONV": "1"}).stdout.decode(errors="ignore")


def layer(serial):
    for line in adb(serial, "shell", "dumpsys", "SurfaceFlinger", "--list").splitlines():
        if line.startswith("SurfaceView[") and PACKAGE in line and "(BLAST)" in line:
            # Quoted for the device's shell: the name has brackets and parentheses in it.
            return "'" + line.strip() + "'"
    raise SystemExit("the preview is not on screen")


def main():
    serial = sys.argv[1]
    seconds = float(sys.argv[2]) if len(sys.argv) > 2 else 60
    name = layer(serial)
    adb(serial, "shell", "dumpsys", "SurfaceFlinger", "--latency-clear", name)
    presents = set()
    cpu = []
    pid = adb(serial, "shell", "pidof", PACKAGE).strip()
    end = time.time() + seconds
    while time.time() < end:
        out = adb(serial, "shell", "dumpsys", "SurfaceFlinger", "--latency", name).splitlines()
        for row in out[1:]:
            parts = row.split()
            if len(parts) == 3 and parts[1] not in ("0", "9223372036854775807"):
                presents.add(int(parts[1]))
        top = adb(serial, "shell", "top", "-b", "-n", "1", "-p", pid)
        for row in top.splitlines():
            if row.strip().startswith(pid):
                fields = row.split()
                cpu.append(float(fields[8]))
        time.sleep(1.5)
    times = sorted(presents)
    if len(times) < 3:
        raise SystemExit("too few frames")
    gaps = [(b - a) / 1e6 for a, b in zip(times, times[1:]) if (b - a) < 1e9]
    span = (times[-1] - times[0]) / 1e9
    gaps_sorted = sorted(gaps)
    pct = lambda f: gaps_sorted[min(len(gaps_sorted) - 1, int(f * len(gaps_sorted)))]
    long_gaps = sum(1 for g in gaps if g > 1.5 * statistics.median(gaps))
    print(f"frames {len(times)} over {span:.1f}s = {len(times) / span:.1f} fps")
    print(f"interval ms: median {statistics.median(gaps):.1f}, p95 {pct(.95):.1f}, p99 {pct(.99):.1f}, max {max(gaps):.1f}")
    print(f"intervals over 1.5x the median: {long_gaps} ({100 * long_gaps / len(gaps):.1f}%)")
    if cpu:
        print(f"app CPU (top, % of one core): median {statistics.median(cpu):.0f}, max {max(cpu):.0f}, n={len(cpu)}")


if __name__ == "__main__":
    main()
