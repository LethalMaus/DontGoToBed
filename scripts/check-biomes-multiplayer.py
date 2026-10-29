#!/usr/bin/env python3
"""Disposable six-biome host required; verifies dog authority and hazard damage using scripted peers."""
import asyncio, importlib.util, json, sys
from pathlib import Path
import websockets
spec=importlib.util.spec_from_file_location('peer',Path(__file__).with_name('check-multiplayer.py'))
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
Peer=module.Peer
async def main():
 uri=sys.argv[1] if len(sys.argv)>1 else 'ws://127.0.0.1:18765'
 peers=[];states={};damage=[]
 def observe(message):
  p=message.split(';')
  if p[0]=='DOG': states['dog']=p
  if p[0]=='DAMAGE': damage.append(p)
 async def pos(peer,x,z,y=3):await peer.send(f'PLAYER;{x*1000};{z*1000};{y*1000};true;0;5;original;_;false;0')
 try:
  async with websockets.connect(uri) as sa,websockets.connect(uri) as sb:
   a,b=Peer(sa),Peer(sb);peers.extend([a,b]);a.on_round=observe
   await a.synchronize();await b.synchronize()
   await pos(a,49,10);await pos(b,53,10)
   await a.wait_until(lambda:'dog' in states and len(states['dog'])==7)
   await a.send('DOG_FOLLOW')
   await a.wait_until(lambda:states['dog'][3]==str(a.id))
   await pos(a,50,298)
   await a.wait_until(lambda:int(states['dog'][2].split(',')[1])>288000)
   assert states['dog'][3]==str(a.id)
   assert a.player_states[b.id][1]=='10000'
   # Client-authored dog snapshots are not authoritative.
   await b.send('DOG;999999999;10000,10000,3000;999;false;true;true')
   await asyncio.sleep(.3);assert states['dog'][3]==str(a.id)
   damage.clear();await pos(a,72,298)
   await a.wait_until(lambda:any(p[2]==str(a.id) for p in damage))
   assert all(p[2]!=str(b.id) for p in damage)
   await pos(a,72,298,5);await asyncio.sleep(.3);damage.clear()
   await asyncio.sleep(1.5);assert not damage,'Jumping above lava must avoid damage'
   await pos(a,126,298)
   await a.wait_until(lambda:any(p[2]==str(a.id) for p in damage),timeout=6)
   await pos(a,50,298)
   async with websockets.connect(uri) as sc:
    late=Peer(sc);peers.append(late);last={}
    late.on_round=lambda m:last.update(dog=m) if m.startswith('DOG;') else None
    await late.synchronize();await late.wait_until(lambda:'dog' in last and late.blocks==a.blocks==b.blocks)
    assert last['dog'].split(';')[3]==str(a.id)
   result={'passed':True,'host':'Android emulator','scripted_peers':3,'checks':['Dog calling and owner follow','Dog follows through portal coordinates','Other player remains in original biome','Client cannot forge dog authority','Lava damages only touching player','Jumping clears lava damage','Fire-jet phase damage','Late join receives dog and matching terrain']}
   Path('.jc/biomes-2026-10-02/multiplayer.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result),flush=True)
 finally:
  for p in peers:p.task.cancel()
  await asyncio.gather(*(p.task for p in peers),return_exceptions=True)
asyncio.run(main())
