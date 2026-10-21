"""Launches the preview activity on the emulator with tuning extras and screenshots it when the scene
is a chosen age, so frames can be compared with reference frames taken at the same stage of a scene.

Usage: python -I tools/capture_at.py <out.png> <scene-seconds> [extra key=value ...]
  extras: grid=21 radius=0.2 fov=65 dmin=12 dmax=20 timescale=1.5 teapot=0.0
Always targets emulator-5570; it never touches the TV.
"""
import os
import re
import subprocess
import sys
import time

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe")
SERIAL = "emulator-5570"


def adb(*args, capture=False, timeout=60):
    cmd = [ADB, "-s", SERIAL, *args]
    return subprocess.run(cmd, capture_output=capture, timeout=timeout, env={**os.environ, "MSYS_NO_PATHCONV": "1"})


def main():
    out, target = sys.argv[1], float(sys.argv[2])
    extras = dict(kv.split("=") for kv in sys.argv[3:])
    extras.setdefault("timescale", "1.5")
    scale = float(extras["timescale"])
    ints = {"grid"}
    adb("shell", "am", "force-stop", "io.github.thewombraider.pipestv")
    adb("logcat", "-c")
    cmd = ["shell", "am", "start", "-n", "io.github.thewombraider.pipestv/.PipesActivity"]
    for k, v in extras.items():
        cmd += ["--ei" if k in ints else "--ef", k, v]
    adb(*cmd, capture=True)
    time.sleep(2)
    adb("shell", "input", "tap", "1100", "266")
    deadline = time.time() + 120
    pattern = re.compile(r"sceneT=([\d.]+) dissolve=(-?[\d.]+)")
    while time.time() < deadline:
        log = adb("logcat", "-d", "-s", "XPPipes:I", capture=True).stdout.decode(errors="ignore")
        last = None
        for m in pattern.finditer(log):
            last = m
        if last:
            t, d = float(last.group(1)), float(last.group(2))
            # The log line is up to 2 s old and the capture takes about 1 s: aim a little early.
            if target < 0 and 0.15 <= d <= 0.8:
                # A negative target means: catch the dissolve part-way through.
                png = adb("exec-out", "screencap", "-p", capture=True).stdout
                open(out, "wb").write(png)
                print("captured dissolve=%.2f" % d, out)
                return
            if target >= 0 and target - 2.0 * scale <= t <= target + 1.0 and d < 0:
                png = adb("exec-out", "screencap", "-p", capture=True).stdout
                open(out, "wb").write(png)
                print("captured at sceneT~%.1f" % t, out)
                return
        time.sleep(0.4)
    print("timed out")


main()
