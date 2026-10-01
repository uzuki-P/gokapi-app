#!/usr/bin/env python3
"""Install a built release APK and verify two cold launches without clearing app data."""
import argparse
import subprocess
import time
from pathlib import Path

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--flavor', choices=['mainApp', 'dev'], default='mainApp')
args = parser.parse_args()
package = 'com.uzukip.gokapi' + ('.dev' if args.flavor == 'dev' else '')
root = Path(__file__).resolve().parent.parent
apk = root / f'app/build/outputs/apk/{args.flavor}/release/app-{args.flavor}-release.apk'

def adb(*argv):
    return subprocess.run(['adb', '-s', args.serial, *argv], text=True,
                          capture_output=True, timeout=45, check=True).stdout

if not apk.exists():
    parser.error(f'Build the {args.flavor} release APK first.')
print(adb('install', '-r', str(apk)).strip())
for attempt in range(1, 3):
    adb('shell', 'am', 'force-stop', package)
    adb('shell', 'am', 'start', '-W', '-n', f'{package}/com.uzukip.gokapi.MainActivity')
    time.sleep(4)
    process = subprocess.run(['adb', '-s', args.serial, 'shell', 'pidof', package],
                             text=True, capture_output=True, timeout=10)
    pid = process.stdout.strip() if process.returncode == 0 else ''
    activity = adb('shell', 'dumpsys', 'activity', 'activities')
    resumed = [line for line in activity.splitlines() if 'ResumedActivity' in line]
    if not pid or not any(package in line and 'MainActivity' in line for line in resumed):
        raise SystemExit(f'FAIL: {args.flavor} cold launch {attempt} exited before its screen resumed. Inspect adb logcat -b crash.')
    adb('shell', 'uiautomator', 'dump', '/sdcard/gokapi-startup-check.xml')
    ui = adb('shell', 'cat', '/sdcard/gokapi-startup-check.xml')
    if f'package="{package}"' not in ui or 'text="Files"' not in ui:
        raise SystemExit(f'FAIL: {args.flavor} cold launch {attempt} did not show the Files screen.')
    print(f'PASS: {args.flavor} cold launch {attempt} shows Files and keeps the app running.')
