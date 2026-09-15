#!/usr/bin/env python3
"""Record a sustained real-device game session without the UI test exiting early.

Start the Release game and enter gameplay before running. The default attaches
to the running app, without a UI-test runner controlling its lifetime. --drive-ui
can start a sustained test session when device UI automation is available.
Keeps raw traces locally; does not modify game state or benchmark FPS.
"""
import argparse
import json
import os
from pathlib import Path
import subprocess
import time
import xml.etree.ElementTree as ET

p = argparse.ArgumentParser(description=__doc__)
p.add_argument('--device', required=True, help='Physical device UDID')
p.add_argument('--output', required=True, type=Path, help='Fresh local evidence directory')
p.add_argument('--drive-ui', action='store_true', help='Launch gameplay through the signed capture test')
p.add_argument('--seconds', type=int, default=30, choices=range(10, 46), metavar='10..45')
a = p.parse_args()
if a.output.exists():
    p.error('Choose a fresh output directory to preserve existing evidence')
a.output.mkdir(parents=True)
a.output = a.output.resolve()
root = Path(__file__).resolve().parents[1]
env = dict(os.environ)
env.setdefault('DEVELOPER_DIR', '/Applications/Xcode.app/Contents/Developer')
log = a.output / 'ui-test.log'
trace = a.output / 'game.trace'
command = ['xcodebuild', '-project', 'iosApp/iosApp.xcodeproj', '-scheme', 'StoreCapture',
           '-configuration', 'Release', '-destination', f'id={a.device}',
           '-derivedDataPath', str(a.output / 'derived'), '-resultBundlePath', str(a.output / 'session.xcresult'),
           '-allowProvisioningUpdates', '-parallel-testing-enabled', 'NO',
           '-only-testing:StoreCaptureTests/StoreCaptureTests/testSustainedGameplayRecording', 'test']
with log.open('w') as output:
    test = subprocess.Popen(command, cwd=root, env=env, stdout=output, stderr=subprocess.STDOUT) if a.drive_ui else None
    try:
        deadline = time.monotonic() + 300
        print('Starting sustained UI session…' if test else 'Attaching to the running game without a UI-test runner.', flush=True)
        while test and 'DGTB_RECORDING_READY' not in log.read_text(errors='replace'):
            if test and test.poll() is not None:
                raise RuntimeError(f'UI session exited before recording was ready; see {log}')
            if time.monotonic() > deadline:
                raise TimeoutError(f'Timed out waiting for gameplay; see {log}')
            time.sleep(.5)
        print(f'Recording the running game for {a.seconds} seconds with Instruments.', flush=True)
        with (a.output / 'recorder.log').open('w') as recorder_log:
            subprocess.run(['xcrun', 'xctrace', 'record', '--device', a.device,
                            '--template', 'Game Performance', '--attach', 'dontgotobed',
                            '--time-limit', f'{a.seconds}s', '--window', f'{a.seconds}s', '--output', str(trace)],
                           env=env, stdout=recorder_log, stderr=subprocess.STDOUT, check=True, timeout=80)
        if test and test.poll() is not None:
            raise RuntimeError('UI session ended before the recording completed; do not use this trace')
        toc = a.output / 'trace-toc.xml'
        try:
            with (a.output / 'export.log').open('w') as export_log:
                subprocess.run(['xcrun', 'xctrace', 'export', '--input', str(trace), '--toc', '--output', str(toc)],
                               env=env, stdout=export_log, stderr=subprocess.STDOUT, check=True, timeout=60)
        except (subprocess.CalledProcessError, subprocess.TimeoutExpired) as error:
            raise RuntimeError(f'Instruments could not export the trace metadata. Raw recording preserved at {trace}; '
                               'duration is unverified. Inspect it in Instruments; see export.log.') from error
        tree = ET.parse(toc)
        durations = [float(node.text) for node in tree.findall('.//duration') if node.text]
        if not durations or max(durations) < a.seconds * .9:
            raise RuntimeError('Recorded duration is too short; inspect trace-toc.xml')
        if test: print('Recording complete; waiting for the foreground/lifecycle check.', flush=True)
        if test and test.wait(timeout=120) != 0:
            raise RuntimeError(f'UI session failed; see {log}')
        (a.output / 'recording.json').write_text(json.dumps({
            'device': a.device, 'requestedSeconds': a.seconds, 'recordedDurations': durations,
            'template': 'Game Performance', 'mode': 'ui-test' if test else 'attach',
            'uiSessionPassed': True if test else None,
            'note': 'Recording duration verified; content depends on the running scene. Not a controlled performance comparison.'
        }, indent=2) + '\n')
        print(f'PASS: recording duration verified; evidence saved in {a.output}', flush=True)
    finally:
        if test and test.poll() is None:
            test.terminate()
            try:
                test.wait(timeout=15)
            except subprocess.TimeoutExpired:
                test.kill()
                test.wait()
