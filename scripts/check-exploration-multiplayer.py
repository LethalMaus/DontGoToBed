#!/usr/bin/env python3
"""Disposable Portal worlds host required. Scripted peers verify cross-biome authority,
not physical Android/iOS playtesting or the touch-driven portal entrance itself.
"""
import asyncio, importlib.util, json, sys, time
from pathlib import Path
import websockets
spec = importlib.util.spec_from_file_location('shared_check', Path(__file__).with_name('check-multiplayer.py'))
module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
Peer = module.Peer

async def main():
    uri = sys.argv[1] if len(sys.argv) > 1 else 'ws://127.0.0.1:18765'
    output = Path(sys.argv[2]) if len(sys.argv) > 2 else Path('.jc/exploration-2026-10-02/multiplayer.json')
    peers = []
    async def position(peer, lat):
        await peer.send(f'PLAYER;{lat*1000};10000;3000;true;0;5;original;_;false;0')
    async def action(peer, lat, verb, args):
        ident = time.monotonic_ns()
        await peer.send(f'ACTION;{ident};{verb};0;{lat*1000};10000;3000;' + ';'.join(map(str,args)))
        await peer.wait_until(lambda: ident in peer.receipts)
        return peer.receipts[ident]
    def placed(peer, col):
        return any(b[1:3] == [str(col), '3'] and b[8:12] == ['1','0','0','10'] for b in peer.blocks.values())
    try:
        async with websockets.connect(uri) as sa, websockets.connect(uri) as sb:
            a,b=Peer(sa),Peer(sb);peers.extend([a,b])
            await a.synchronize();await b.synchronize()
            assert a.exploration and b.exploration and a.width == b.width == 576
            await position(a,50);await position(b,242)
            await a.wait_until(lambda:a.player_states.get(b.id,[])[:1]==['242000'])
            await b.wait_until(lambda:b.player_states.get(a.id,[])[:1]==['50000'])
            assert await action(a,50,'PLACE',[52,3,1,0])=='true;_;_'
            assert await action(b,242,'PLACE',[244,3,1,0])=='true;_;_'
            await b.wait_until(lambda:placed(a,52) and placed(a,244) and a.blocks==b.blocks)
            # A player staying in Meadow cannot hit the Forest block from there.
            assert await action(a,50,'HIT',[244,3,7])=='false;_;_'
            # Travel changes only this peer's canonical position; the other stays in Forest.
            await position(a,434)
            await b.wait_until(lambda:b.player_states.get(a.id,[])[:1]==['434000'])
            assert a.player_states[b.id][0]=='242000'
            await position(a,50)
            await b.wait_until(lambda:b.player_states.get(a.id,[])[:1]==['50000'])
            assert placed(a,52) and placed(a,244)
            for _ in range(5): result=await action(a,50,'HIT',[52,3,7])
            assert result=='true;Block;Wood',result
            await b.wait_until(lambda:not placed(a,52) and not placed(b,52))
            assert placed(a,244) and placed(b,244)
            async with websockets.connect(uri) as sc:
                late=Peer(sc);peers.append(late);await late.synchronize()
                await late.wait_until(lambda:late.blocks==a.blocks==b.blocks)
                assert not placed(late,52) and placed(late,244)
            result={'passed':True,'host':'Android emulator','scripted_peers':3,
                    'checks':['Exploration mode and dimensions negotiated','Concurrent players in different biomes',
                              'Independent position changes and return','Builds preserved across travel',
                              'Cross-biome hit rejected','Mining affects only the intended biome',
                              'Late join receives persistent terrain from all biomes']}
            output.parent.mkdir(parents=True,exist_ok=True);output.write_text(json.dumps(result,indent=2)+'\n')
            print(json.dumps(result),flush=True)
    finally:
        for peer in peers:peer.task.cancel()
        await asyncio.gather(*(peer.task for peer in peers),return_exceptions=True)

asyncio.run(main())
