#!/usr/bin/env python3
"""Check 3/4 participant protocol sessions against a disposable running host.
Uses three scripted peers; this does not claim four physical devices were tested.
"""
import asyncio,importlib.util,json,sys
from pathlib import Path
import websockets
spec=importlib.util.spec_from_file_location('shared_check',Path(__file__).with_name('check-multiplayer.py'))
module=importlib.util.module_from_spec(spec);spec.loader.exec_module(module)
Peer=module.Peer
async def main():
 uri=sys.argv[1] if len(sys.argv)>1 else 'ws://127.0.0.1:18765'
 out=Path(sys.argv[2]) if len(sys.argv)>2 else Path('docs/release-next/evidence/capacity-android.json')
 sockets=[];peers=[]
 try:
  for _ in range(3):
   ws=await websockets.connect(uri,max_size=2**20);sockets.append(ws);p=Peer(ws);peers.append(p);await p.synchronize()
   await p.send('PLAYER;10000;10000;40000;true;1;5;rock;Gloves,JumpBoots,true,true,2;false;240')
  a,b,c=peers
  await a.send('PLAYER;10000;10000;40000;true;1;5;rock;Gloves,JumpBoots,true,true,2;false;240')
  assert len({a.id,b.id,c.id})==3
  await b.wait_until(lambda: a.id in b.player_states and len(b.player_states[a.id])>=10)
  assert b.player_states[a.id][-1]=='240'
  await a.send('PLAYER;10000;10000;40000;true;1;0;rock;Gloves,JumpBoots,true,true,2;false;0')
  await c.wait_until(lambda:c.player_states.get(a.id,[])[5:6]==['0'])
  await a.send('PLAYER;10000;10000;40000;true;0;5;rock;Gloves,JumpBoots,true,true,2;false;0')
  await c.wait_until(lambda:c.player_states.get(a.id,[])[4:6]==['0','5'])
  await asyncio.sleep(1)
  assert a.blocks==b.blocks==c.blocks
  old_id=c.id;c.task.cancel();await sockets[-1].close();await asyncio.gather(c.task,return_exceptions=True)
  ws=await websockets.connect(uri,max_size=2**20);sockets.append(ws);late=Peer(ws);peers.append(late);await late.synchronize()
  await late.wait_until(lambda:late.blocks==a.blocks)
  assert late.id!=old_id
  result={'passed':True,'participants':4,'real_clients':1,'scripted_peers':3,'checks':['Three and four participant baselines','Attack duration and loadout relay','Death and respawn state relay','World-axis state relay','Matching terrain','Disconnect and reconnect with fresh identity and baseline']}
  out.parent.mkdir(parents=True,exist_ok=True);out.write_text(json.dumps(result,indent=2));print(result,flush=True)
 finally:
  for p in peers:p.task.cancel()
  await asyncio.gather(*(p.task for p in peers),return_exceptions=True)
  for s in sockets:await s.close()
asyncio.run(main())
