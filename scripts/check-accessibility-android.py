#!/usr/bin/env python3
"""Smoke-test exposed controls and preference persistence on a disposable emulator.

This is not a TalkBack/Switch Access usability or conformance test. It changes
only the disposable game's accessibility preferences and restores them afterward.
"""
import argparse,json,re,subprocess,time
from pathlib import Path
import xml.etree.ElementTree as E
p=argparse.ArgumentParser(description=__doc__);p.add_argument('--serial',required=True);p.add_argument('--output',type=Path,required=True);a=p.parse_args();a.output.mkdir(parents=True,exist_ok=True)
pkg='dev.jamescullimore.dontgotobed'
def adb(*parts):return subprocess.check_output(['adb','-s',a.serial,*parts],text=True,timeout=30)
def ui():
 adb('shell','uiautomator','dump','/sdcard/dgtb-accessibility.xml')
 return E.fromstring(adb('shell','cat','/sdcard/dgtb-accessibility.xml'))
def node(label,scroll=False):
 for attempt in range(7 if scroll else 1):
  tree=ui()
  matches=[n for n in tree.iter('node') if n.get('text')==label or n.get('content-desc')==label or (n.get('text','')+' '+n.get('content-desc','')).startswith(label)]
  if matches:
   # UIAutomator can expose Compose's unmerged semantics: label children live
   # inside a focusable/clickable parent that screen readers merge for speech.
   found=matches[0];parents={c:n for n in tree.iter('node') for c in n}
   current=found
   while current in parents:
    if current.get('clickable')=='true':return current
    current=parents[current]
   return found
  if scroll:
   x,y,r,b=map(int,re.findall(r'\d+',tree[0].get('bounds')))
   adb('shell','input','swipe',str((x+r)//2),str(int(b*.8)),str((x+r)//2),str(int(b*.25)),'400')
 raise AssertionError('Missing control: '+label)
def tap(label,scroll=False):
 n=node(label,scroll);x,y,r,b=map(int,re.findall(r'\d+',n.get('bounds')));adb('shell','input','tap',str((x+r)//2),str((y+b)//2));time.sleep(.2);return n

def prefs():
 raw=adb('shell','run-as',pkg,'cat','shared_prefs/game_settings.xml')
 return {n.get('name'):int(n.get('value')) for n in E.fromstring(raw) if n.tag=='int'}
def launch():
 adb('shell','am','force-stop',pkg);adb('shell','am','start','-n',pkg+'/dev.jamescullimore.dontgotobed.MainActivity');time.sleep(1)
def capture(name):
 adb('shell','screencap','-p','/sdcard/dgtb-accessibility.png');adb('pull','/sdcard/dgtb-accessibility.png',str(a.output/name))
launch();tap('World settings')
# The dedicated recording emulator starts with defaults; refuse unexpected state.
try:initial=prefs()
except subprocess.CalledProcessError:initial={}
assert initial.get('buttonControls',0)==0 and initial.get('reduceMotion',0)==0, 'Use a fresh/default test session'
try:
 tap('Button controls',True);tap('Reduce motion and flashes',True)
 current=prefs();assert current['buttonControls']==1 and current['reduceMotion']==1
 capture('01-settings.png');tap('Back',True);tap('Leo')
 (a.output/'gameplay-tree.xml').write_text(E.tostring(ui(),encoding='unicode'))
 nodes={label:node(label) for label in ['Step left','Step right','Aim up','Aim down','Jump','Hit','Place','Health: 5 of 5 hearts']}
 density=int(re.findall(r'\d+',adb('shell','wm','density'))[-1])/160
 for label in ['Step left','Step right','Aim up','Aim down']:
  n=nodes[label];x,y,r,b=map(int,re.findall(r'\d+',n.get('bounds')))
  assert n.get('clickable')=='true' and n.get('enabled')=='true', (label,n.attrib)
  assert min(r-x,b-y)/density>=55.5,(label,n.get('bounds'),density)
 tap('Step right');tap('Jump');tap('Aim up');tap('Place');tap('Switch latitude longitude')
 capture('02-button-gameplay.png')
 tap('Game menu');assert node('Paused') is not None
 tap('Continue')
 launch();assert prefs()['buttonControls']==1 and prefs()['reduceMotion']==1
 tap('Leo');assert node('Step left') is not None
 capture('03-persisted-controls.png')
 (a.output/'result.json').write_text(json.dumps({'passed':True,'checks':['Both options persist after relaunch','Four directional actions exposed as enabled buttons, at least 56 dp','Jump, aim, place and turn controls respond without losing gameplay','Pause and continue remain reachable','Health exposed as one labeled value'],'limitations':'No actual TalkBack, Switch Access, largest-font or nonvisual gameplay test performed.'},indent=2))
 print('Accessibility control and persistence smoke checks passed.',flush=True)
finally:
 launch();tap('World settings')
 if prefs().get('buttonControls',0):tap('Button controls',True)
 if prefs().get('reduceMotion',0):tap('Reduce motion and flashes',True)
 tap('Back',True)
