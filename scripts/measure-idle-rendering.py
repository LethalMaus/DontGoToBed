#!/usr/bin/env python3
"""Measure Android idle gameplay frame timings with the ordinary initial enemies.

Install the selected build first. Starts fresh solo sessions on a disposable test
device. Random enemy locations vary; this is a diagnostic comparison, not an iOS
benchmark or a guarantee of physical-device frame rates.
"""
import argparse
import json
from pathlib import Path
import re
import subprocess
import time
import xml.etree.ElementTree as ET

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--serial', required=True)
parser.add_argument('--output', type=Path, required=True)
parser.add_argument('--repeats', type=int, default=3)
args = parser.parse_args()
args.output.mkdir(parents=True, exist_ok=True)
package = 'dev.jamescullimore.dontgotobed'
def adb(*parts):
    return subprocess.check_output(['adb', '-s', args.serial, *parts], text=True, timeout=30)
results = []
for take in range(args.repeats):
    adb('shell', 'am', 'force-stop', package)
    adb('shell', 'am', 'start', '-n', package + '/dev.jamescullimore.dontgotobed.MainActivity')
    time.sleep(2)
    adb('shell', 'uiautomator', 'dump', '/sdcard/dgtb-measure.xml')
    tree = ET.fromstring(adb('shell', 'cat', '/sdcard/dgtb-measure.xml'))
    node = next(n for n in tree.iter('node') if n.get('text') == 'Leo')
    x1,y1,x2,y2 = map(int, re.findall(r'\d+', node.get('bounds')))
    adb('shell', 'input', 'tap', str((x1+x2)//2), str((y1+y2)//2))
    time.sleep(7)
    adb('shell', 'dumpsys', 'gfxinfo', package, 'reset')
    time.sleep(8)
    raw = adb('shell', 'dumpsys', 'gfxinfo', package, 'framestats')
    (args.output / f'take-{take+1}.txt').write_text(raw)
    fields = {}
    for line in raw.splitlines():
        if any(line.startswith(key) for key in ('Total frames rendered:', 'Janky frames:', '50th percentile:', '90th percentile:', '95th percentile:', '99th percentile:')):
            key,value = line.split(':',1);fields[key] = value.strip()
    results.append(fields)
    print(take+1, fields, flush=True)
(args.output / 'summary.json').write_text(json.dumps(results,indent=2)+'\n')
