#!/usr/bin/env python3
"""Copy privately supplied, licensed audio from ignored .jc into app resources."""
from pathlib import Path
import shutil

root = Path(__file__).resolve().parents[1]
source = root / '.jc/release-private/audio'
if not source.is_dir():
    source = root / '.jc'
names = ['menu', 'search1', 'search2', 'search3', 'search4', 'panic',
         'jump', 'hit', 'place', 'arrow', 'damage_taken', 'drink', 'turn']
missing = [name + '.mp3' for name in names if not (source / (name + '.mp3')).is_file()]
if missing:
    raise SystemExit('Supply licensed files in .jc first: ' + ', '.join(missing))
target = root / 'composeApp/src/commonMain/composeResources/files/audio'
target.mkdir(parents=True, exist_ok=True)
for name in names:
    shutil.copy2(source / (name + '.mp3'), target / (name + '.mp3'))
print('Prepared 13 local audio files. Keep them out of public Git.')
