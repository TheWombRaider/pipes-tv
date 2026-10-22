"""Capture deterministic teapot views and monitor mixed/forced scenes on an explicit ADB device.

python -I tools/teapot_qa.py --serial emulator-5570 --out shots/teapot-qa
The device must already have the debug APK installed. This starts preview activities on that device.
"""
import argparse
from datetime import datetime, timezone
import json
import os
from pathlib import Path
import re
import statistics
import subprocess
import time
from io import BytesIO
from PIL import Image

PACKAGE = "io.github.thewombraider.pipestv"


class Device:
    def __init__(self, serial):
        sdk = Path(os.environ.get("ANDROID_HOME", Path.home() / "AppData/Local/Android/Sdk"))
        self.adb = str(sdk / "platform-tools" / ("adb.exe" if os.name == "nt" else "adb"))
        self.serial = serial

    def call(self, *args, binary=False):
        result = subprocess.run([self.adb, "-s", self.serial, *map(str, args)],
                                capture_output=True, check=True, timeout=45)
        return result.stdout if binary else result.stdout.decode(errors="replace")

    def launch(self, *extras):
        self.call("shell", "am", "force-stop", PACKAGE)
        return self.call("shell", "am", "start", "-W", "-n", PACKAGE + "/.PipesActivity", *extras)

    def log(self):
        return self.call("logcat", "-d", "-s", "XPPipes:V", "AndroidRuntime:E", "*:S")

    def assert_healthy(self, pid=None):
        current = self.call("shell", "pidof", PACKAGE).strip()
        if not current or (pid and current != pid):
            raise RuntimeError(f"process missing/restarted: {pid!r} -> {current!r}")
        log = self.log()
        if any(s in log for s in ("FATAL EXCEPTION", "Teapot asset failed", "OpenGL error:", "exceeds proven vertex bound")):
            raise RuntimeError("app reported a QA failure; inspect logcat")
        return current

    def screenshot(self):
        deadline = time.monotonic() + 20
        while time.monotonic() < deadline:
            binary = self.call("exec-out", "screencap", "-p", binary=True)
            image = Image.open(BytesIO(binary)).convert("RGB")
            corners = [image.getpixel(p) for p in ((0, 0), (image.width - 1, 0),
                       (0, image.height - 1), (image.width - 1, image.height - 1))]
            sample = list(image.resize((96, 54)).getdata())
            lit = sum(max(p) > 32 for p in sample)
            if all(max(p) < 5 for p in corners) and lit > len(sample) * .005:
                return binary
            time.sleep(.3)
        raise RuntimeError("no visible app geometry after launch; refusing a blank/transition screenshot")


def capture(device, out):
    views = [("front", 0, 0), ("back", 180, 0), ("left", 90, 0), ("right", 270, 0),
             ("top", 0, 90), ("bottom", 0, -90), ("oblique", 25, 15)]
    for name, turn, pitch in views:
        device.launch("--es", "fixture", "teapot", "--ei", "material", 8,
                      "--ef", "turn", turn, "--ef", "pitch", pitch)
        time.sleep(.5)
        device.assert_healthy()
        (out / f"{name}.png").write_bytes(device.screenshot())
    for mode in ("wire", "normals"):
        device.launch("--es", "fixture", "teapot", "--es", "meshview", mode,
                      "--ef", "turn", 25, "--ef", "pitch", 15)
        time.sleep(.5)
        device.assert_healthy()
        (out / f"{mode}.png").write_bytes(device.screenshot())
    for material in range(16):
        device.launch("--es", "fixture", "teapot", "--ei", "material", material,
                      "--ef", "turn", material * 22.5, "--ef", "pitch", 15)
        time.sleep(.3)
        device.assert_healthy()
        (out / f"material-{material:02}.png").write_bytes(device.screenshot())
    return {"views": len(views) + 2, "materials": 16}


def soak(device, out, name, seconds, forced):
    device.call("logcat", "-c")
    device.launch("--es", "joints", "mixed", "--el", "seed", 1975,
                  "--ef", "teapot", 1 if forced else .00005)
    pid = device.assert_healthy()
    layers = device.call("shell", "dumpsys", "SurfaceFlinger", "--list").splitlines()
    layer = next((x.strip() for x in layers if x.startswith("SurfaceView[") and PACKAGE in x and "BLAST" in x), None)
    presents, memory = set(), []
    start = time.monotonic()
    while time.monotonic() - start < seconds:
        time.sleep(min(5, max(0, seconds - (time.monotonic() - start))))
        device.assert_healthy(pid)
        if layer:
            raw = device.call("shell", "dumpsys", "SurfaceFlinger", "--latency", "'" + layer + "'")
            for line in raw.splitlines()[1:]:
                fields = line.split()
                if len(fields) == 3 and fields[1] not in ("0", "9223372036854775807"):
                    presents.add(int(fields[1]))
        if not memory or time.monotonic() - start - memory[-1]["elapsed_s"] >= 30:
            raw = device.call("shell", "dumpsys", "meminfo", PACKAGE)
            match = re.search(r"TOTAL PSS:\s*(\d+)", raw) or re.search(r"TOTAL\s+(\d+)\s+", raw)
            memory.append({"elapsed_s": round(time.monotonic() - start, 1),
                           "pss_kib": int(match.group(1)) if match else None})
    log = device.log()
    (out / f"{name}-logcat.txt").write_text(log, encoding="utf-8")
    (out / f"{name}.png").write_bytes(device.call("exec-out", "screencap", "-p", binary=True))
    times = sorted(presents)
    intervals = sorted((b - a) / 1e6 for a, b in zip(times, times[1:]) if 0 < b - a < 1e9)
    cpu = [float(x) for x in re.findall(r"worstCpuMs=([\d.]+)", log)]
    pots = [int(x) for x in re.findall(r"teapots=(\d+)", log)]
    return {"duration_s": round(time.monotonic() - start, 1), "pid": pid,
            "completed_scenes": log.count("scene over:"), "max_teapots_logged": max(pots, default=0),
            "worst_cpu_ms": max(cpu, default=None), "memory": memory,
            "sampled_present_intervals": len(intervals),
            "present_interval_median_ms": statistics.median(intervals) if intervals else None,
            "present_interval_p95_ms": intervals[min(len(intervals) - 1, int(.95 * len(intervals)))] if intervals else None}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--serial", required=True)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--mixed-seconds", type=float, default=1800)
    parser.add_argument("--forced-seconds", type=float, default=600)
    parser.add_argument("--skip-captures", action="store_true")
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    device = Device(args.serial)
    report = {"started_utc": datetime.now(timezone.utc).isoformat(), "serial": args.serial,
              "build": device.call("shell", "getprop", "ro.build.fingerprint").strip(),
              "viewport": device.call("shell", "wm", "size").strip()}
    try:
        if not args.skip_captures:
            report["captures"] = capture(device, args.out)
            print("captured geometry views and all 16 materials", flush=True)
        for name, seconds, forced in (("mixed", args.mixed_seconds, False), ("forced", args.forced_seconds, True)):
            if seconds <= 0:
                continue
            print(f"starting {name}: {seconds:g}s", flush=True)
            report[name] = soak(device, args.out, name, seconds, forced)
            (args.out / "report.json").write_text(json.dumps(report, indent=2) + "\n")
            print(f"{name}: {json.dumps(report[name])}", flush=True)
        report["status"] = "passed"
    except Exception as error:
        report["status"] = "failed"
        report["error"] = str(error)
        (args.out / "failure-logcat.txt").write_text(device.log(), encoding="utf-8")
        raise
    finally:
        (args.out / "report.json").write_text(json.dumps(report, indent=2) + "\n")


if __name__ == "__main__":
    main()
