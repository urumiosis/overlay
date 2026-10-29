#!/usr/bin/env python3
"""Build both Android APKs locally; requires JDK 17, Android SDK 35, Gradle 8.11.1."""
import os
from pathlib import Path
import shutil
import subprocess
import sys

ROOT = Path(__file__).resolve().parent


def find_sdk():
    possibilities = [
        os.environ.get("ANDROID_HOME"), os.environ.get("ANDROID_SDK_ROOT"),
        str(Path.home() / "Android" / "Sdk"),
        str(Path.home() / "Library" / "Android" / "sdk"),
        str(Path(os.environ.get("LOCALAPPDATA", "")) / "Android" / "Sdk") if os.environ.get("LOCALAPPDATA") else None,
    ]
    for location in possibilities:
        if location and (Path(location) / "platforms" / "android-35" / "android.jar").is_file():
            return Path(location)
    return None


def main():
    sdk = find_sdk()
    gradle = shutil.which("gradle")
    java = shutil.which("java")
    if not all([sdk, gradle, java]):
        print("Missing build prerequisites:")
        if not sdk: print("  - Android SDK platform 35; install Android Studio and SDK Platform 35")
        if not gradle: print("  - Gradle 8.11.1 (or use the GitHub Actions workflow)")
        if not java: print("  - JDK 17")
        print("\nAndroid Studio can build the unpacked project without this script.")
        return 1
    env = os.environ.copy()
    env["ANDROID_HOME"] = str(sdk)
    command = [gradle, "--no-daemon", ":admin:assembleDebug", ":receiver:assembleDebug"]
    print("Compiling from:", ROOT)
    code = subprocess.call(command, cwd=ROOT, env=env)
    if code: return code
    print("\nBuild outputs:")
    for name in ("admin", "receiver"):
        print(ROOT / name / "build" / "outputs" / "apk" / "debug" / (name + "-debug.apk"))
    return 0


if __name__ == "__main__":
    sys.exit(main())
