"""Launches the preview activity on the emulator and screenshots the first finished scene, so renders can be
compared with frames from the end of scenes in the reference footage.

Usage: python -I tools/capture_at.py <out.png> [extra key=value ...]
  extras: timescale=4 turn=0 pipes=multiple|single joints=mixed|elbow|ball colors=independent|distinct teapot=0.0 dissolve=1.67
  The picture is held for 30 s after the scene ends (hold=30) unless hold is given.
Always targets emulator-5570; it never touches the TV.
"""
import os
import subprocess
import sys
import time

ADB = os.path.expandvars(r"%LOCALAPPDATA%\Microsoft\WinGet\Packages\Google.PlatformTools_Microsoft.Winget.Source_8wekyb3d8bbwe\platform-tools\adb.exe")
SERIAL = "emulator-5570"
STRINGS = {"pipes", "joints", "colors"}


def adb(*args, capture=False, timeout=60):
    cmd = [ADB, "-s", SERIAL, *args]
    return subprocess.run(cmd, capture_output=capture, timeout=timeout, env={**os.environ, "MSYS_NO_PATHCONV": "1"})


def main():
    out = sys.argv[1]
    extras = dict(kv.split("=", 1) for kv in sys.argv[2:])
    extras.setdefault("timescale", "4")
    extras.setdefault("hold", "30")
    adb("shell", "am", "force-stop", "io.github.thewombraider.pipestv")
    adb("logcat", "-c")
    cmd = ["shell", "am", "start", "-n", "io.github.thewombraider.pipestv/.PipesActivity"]
    for k, v in extras.items():
        cmd += ["--es" if k in STRINGS else "--ef", k, v]
    adb(*cmd, capture=True)
    deadline = time.time() + 180
    while time.time() < deadline:
        log = adb("logcat", "-d", "-s", "XPPipes:I", capture=True).stdout.decode(errors="ignore")
        line = next((l for l in log.splitlines() if "scene over" in l), None)
        if line:
            time.sleep(0.5)
            png = adb("exec-out", "screencap", "-p", capture=True).stdout
            open(out, "wb").write(png)
            print(line.split("XPPipes:")[-1].strip(), "->", out)
            return
        time.sleep(0.5)
    print("timed out")


main()
