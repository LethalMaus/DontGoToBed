#!/usr/bin/env python3
"""Disposable unlimited exploration host: dog discovery, special authority and late join."""
import asyncio, importlib.util, json, sys
from pathlib import Path
import websockets
spec=importlib.util.spec_from_file_location('peer',Path(__file__).with_name('check-multiplayer.py'))
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
Peer=module.Peer
async def main():
 uri=sys.argv[1] if len(sys.argv)>1 else 'ws://127.0.0.1:18765'
 peers=[];state={};found=[];events=[]
 def observe(message):
  p=message.split(';')
  if p[0] in ('DOG','SPECIAL_STATE','STATE'):state[p[0]]=p
  if p[0]=='DOG_FOUND':found.append(p)
  if p[0]=='SPECIAL_STATE' and p[4]!='_':events.append(p)
 async def pos(p,x,z,y=3,outfit='trex'):
  await p.send(f'PLAYER;{int(x*1000)};{int(z*1000)};{int(y*1000)};true;0;5;{outfit};_;false;0')
 try:
  async with websockets.connect(uri) as sa,websockets.connect(uri) as sb:
   a,b=Peer(sa),Peer(sb);peers.extend([a,b]);a.on_round=observe
   await sa.send('CHAR|Ian');await sb.send('CHAR|Papa')
   await a.synchronize();await b.synchronize()
   assert a.unlimited and b.unlimited and a.exploration and a.width==576
   await pos(a,49,10);await pos(b,53,10,outfit='mma')
   await a.wait_until(lambda:len(state.get('DOG',[]))==7)
   assert state['DOG'][3]=='-1',state['DOG']
   dogpoint=[int(v)/1000 for v in state['DOG'][2].split(',')]
   await pos(a,*dogpoint)
   await a.wait_until(lambda:state['DOG'][3]==str(a.id) and len(found)==1)
   await a.send('DOG_FOLLOW');await asyncio.sleep(.3);assert len(found)==1
   await pos(a,50,298)
   await a.wait_until(lambda:int(state['DOG'][2].split(',')[1])>288000)
   assert a.player_states[b.id][1]=='10000'
   await b.send('DOG;999999999;10000,10000,3000;999;false;true;true')
   await asyncio.sleep(.2);assert state['DOG'][3]==str(a.id)
   await a.wait_until(lambda:any(e[0] in ('Z','S') and int(e[4])<50000 for e in a.entities))
   enemy=next(e for e in a.entities if e[0] in ('Z','S') and int(e[4])<50000)
   await pos(a,int(enemy[2])/1000-8,int(enemy[3])/1000,int(enemy[4])/1000)
   await a.wait_until(lambda:a.appearances.get(a.id)=='trex' or b.appearances.get(a.id)=='trex')
   await a.send('SPECIAL_USE;-30;100;0;54')
   await a.wait_until(lambda:len(state.get('SPECIAL_STATE',[]))>5)
   first=state['SPECIAL_STATE'];event_id=first[4]
   assert first[5]==str(a.id) and first[7]=='trex',first
   await b.send('SPECIAL_USE;-30;100;0;54')
   await b.send('SPECIAL_STATE;999999999;_;_;_')
   await asyncio.sleep(.2);assert state['SPECIAL_STATE'][4]==event_id
   # A third peer joins during the intro and gets its costume, phase and world baseline.
   async with websockets.connect(uri) as sc:
    late=Peer(sc);peers.append(late);latest={}
    late.on_round=lambda m:latest.update(special=m.split(';')) if m.startswith('SPECIAL_STATE;') else None
    await late.synchronize();await late.wait_until(lambda:latest.get('special',[None]*5)[4]==event_id)
    assert latest['special'][7]=='trex' and late.unlimited and late.blocks==a.blocks==b.blocks
    await a.wait_until(lambda:any(int(e[11])>=3500 for e in events),timeout=12)
    impacts=[e for e in events if int(e[11])>=3500]
    targets=impacts[0][12]
    assert targets!='_', 'Live enemies must be selected by the special'
    assert all(e[12]==targets for e in impacts),'Target set must be stable through the effect'
    await a.wait_until(lambda:state['SPECIAL_STATE'][4]=='_',timeout=8)
    hit_ids={(t.split(',')[0],t.split(',')[1]) for t in targets.split('/')}
    assert all((e[0],e[1]) not in hit_ids or int(e[6])<=0 for e in a.entities), 'Three-damage rush must defeat baseline enemies'
    await a.send('SPECIAL_USE;-30;100;0;54');await asyncio.sleep(.3)
    assert state['SPECIAL_STATE'][4]=='_','Cooldown must reject repeat activation'
   result={'passed':True,'host':'Android emulator','scripted_peers':3,'checks':['Unlimited mode negotiated with host','Random undiscovered dog baseline','Dog discovery event exactly once','Dog follows owner between biomes','Other player remains independent','Forged dog and special snapshots rejected','Costumed special relayed to other peers','Concurrent special rejected','Late join receives active intro, costume and matching world','Intro transitions to authoritative world effect and defeats selected live enemies','Cooldown rejects repeat activation'],'special_target_count':0 if targets=='_' else len(targets.split('/')),'note':'Network smoke test; unit tests and debug scene cover damage to more than six targets.'}
   Path('.jc/adventure-2026-10-02/multiplayer.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result),flush=True)
 finally:
  for p in peers:p.task.cancel()
  await asyncio.gather(*(p.task for p in peers),return_exceptions=True)
asyncio.run(main())
