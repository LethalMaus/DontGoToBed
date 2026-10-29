"""Check an active Android multiplayer host after adb forward tcp:18765 tcp:8082.
Requires Python websockets. Start a hosted game before running."""
import asyncio,time,json,math
from pathlib import Path
import websockets
class Peer:
 def __init__(self,ws):
  self.ws=ws;self.id=None;self.round=None;self.links='_';self.tugs=[];self.appearances={};self.gear={};self.task=asyncio.create_task(self.read())
 async def send(self,s):await self.ws.send('ROUND|'+s)
 async def read(self):
  async for raw in self.ws:
   if raw.startswith('WELCOME|'):self.id=int(raw.split('|')[1])
   if not raw.startswith('ROUND|'):continue
   s=raw[6:];p=s.split(';')
   if p[0]=='COMPANION':self.round=int(p[1]);self.links=p[4]
   if p[0]=='ROPE_TUG':self.tugs.append(p)
   if p[0]=='PLAYER_STATE':
    self.appearances[int(p[1])]=p[8] if len(p)>8 else 'original'
    self.gear[int(p[1])]=p[9] if len(p)>9 else '_'
 async def wait(self,test):
  end=time.monotonic()+10
  while not test():
   if self.task.done():self.task.result()
   if time.monotonic()>end:raise AssertionError('Timed out waiting for state')
   await asyncio.sleep(.05)
async def main():
 uri='ws://127.0.0.1:18765'
 async with websockets.connect(uri) as wa,websockets.connect(uri) as wb:
  a=Peer(wa);b=Peer(wb)
  await a.send('WORLD_READY;192;54');await b.send('WORLD_READY;192;54')
  await a.wait(lambda:a.id is not None and a.round is not None);await b.wait(lambda:b.id is not None)
  for outfit in ['rugby','rock','mma','trex']:
   await a.send('PLAYER;80000;0;3000;true;0;5;'+outfit+';Rope,_,false,false,0')
   await b.wait(lambda:b.appearances.get(a.id)==outfit)
  await b.send('PLAYER;84000;0;3000;true;0;5;original;_,_,false,false,0')
  await asyncio.sleep(.2)
  await a.send(f'ROPE_CMD;{a.round};1;ATTACH;P,{b.id}')
  await b.wait(lambda:f'{a.id},P,{b.id}' in b.links)
  await a.send(f'ROPE_CMD;{a.round};2;PULL;_')
  await b.wait(lambda:len(b.tugs)==1)
  destination=int(b.tugs[0][3].split(',')[0]);assert 81000<=destination<84000,destination
  await a.send(f'ROPE_CMD;{a.round};2;PULL;_');await asyncio.sleep(.3);assert len(b.tugs)==1
  async with websockets.connect(uri) as wc:
   c=Peer(wc);await c.send('WORLD_READY;192;54');await c.wait(lambda:f'{a.id},P,{b.id}' in c.links)
   c.task.cancel();await asyncio.gather(c.task,return_exceptions=True)
  await b.send(f'ROPE_CMD;{b.round};1;RELEASE;_');await a.wait(lambda:a.links=='_')
  # Verify each equipment combination survives real host relay.
  for gear in ['Gloves,SpeedBoots,true,true,1','Rope,JumpBoots,false,true,2','_,_,false,false,0']:
   await a.send('PLAYER;80000;0;3000;true;0;5;mma;'+gear)
   await b.wait(lambda:b.gear.get(a.id)==gear)
  # A target in another dimension should move on BOTH world axes, not jump slices.
  await a.send('PLAYER;80000;0;3000;true;0;5;mma;Rope,_,false,false,0')
  await b.send('PLAYER;83000;2000;3000;true;0;5;original;_,_,false,false,0')
  await asyncio.sleep(1.6)
  await a.send(f'ROPE_CMD;{a.round};3;ATTACH;P,{b.id}')
  await b.wait(lambda:f'{a.id},P,{b.id}' in b.links)
  before=len(b.tugs)
  await a.send(f'ROPE_CMD;{a.round};4;PULL;_')
  await b.wait(lambda:len(b.tugs)==before+1)
  x,z,y=map(int,b.tugs[-1][3].split(','))
  assert 80000<x<83000 and 0<z<2000,(x,z)
  assert math.hypot(x-83000,z-2000)<=3002,(x,z)
  assert y==3000,y
  # Unequipping the hand tool also releases the live link.
  await a.send('PLAYER;80000;0;3000;true;0;5;mma;Gloves,_,false,false,0')
  await b.wait(lambda:b.links=='_')
  result={'passed':True,'checks':['Four themed outfit wire IDs relayed','Remote rope attach','Tug bounded to three blocks','Duplicate pull ignored','Late join receives rope link','Target player can release without rope equipped','Equipment combinations and armour colours relayed','Cross-dimension pull changes both coordinates within three blocks','Unequipping rope releases link']}
  (Path(__file__).resolve().parents[1] / 'docs/art-review/outfit-playtest/rope-network.json').write_text(json.dumps(result,indent=2));print(result,flush=True)
  a.task.cancel();b.task.cancel();await asyncio.gather(a.task,b.task,return_exceptions=True)
asyncio.run(main())
