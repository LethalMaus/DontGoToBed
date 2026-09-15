#!/usr/bin/env python3
"""Live smoke test against a disposable hosted game (requires Python websockets).

Start Host and choose a character. For an emulator:
  adb forward tcp:18765 tcp:8082
  python3 scripts/check-multiplayer.py ws://127.0.0.1:18765
The test joins two peers, builds/mines one stone ramp, and fires one arrow.
"""
import asyncio
import hashlib
import sys
import time
import websockets


class Peer:
    def __init__(self, socket):
        self.socket = socket
        self.id = None
        self.width = 192
        self.height = 54
        self.ready = asyncio.Event()
        self.receipts = {}
        self.blocks = {}
        self.transactions = {}
        self.entities = []
        self.chunks = []
        self.task = asyncio.create_task(self.read())

    async def send(self, payload):
        await self.socket.send('ROUND|' + payload)

    async def read(self):
        async for raw in self.socket:
            if raw.startswith('WELCOME|'):
                self.id = int(raw.split('|')[1])
            if not raw.startswith('ROUND|'):
                continue
            message = raw[6:]
            if message.startswith('WORLD_CONFIG;'):
                _, width, height = message.split(';')
                dimensions = int(width), int(height)
                if dimensions != (self.width, self.height):
                    self.width, self.height = dimensions
                    await self.send(f'WORLD_READY;{width};{height}')
            elif message.startswith('ACTION_RESULT;'):
                p = message.split(';')
                if int(p[2]) == self.id:
                    self.receipts[int(p[1])] = ';'.join(p[3:])
            elif message.startswith('WORLD;'):
                _, epoch, sequence, kind, index, count, body = message.split(';', 6)
                if index == '0':
                    self.chunks = []
                assert int(index) == len(self.chunks), 'Missing or interleaved world packet'
                self.chunks.append(body)
                if int(index) + 1 != int(count):
                    continue
                body = ''.join(self.chunks)
                self.transactions[(epoch, sequence, kind)] = hashlib.sha256(body.encode()).hexdigest()
                if kind == 'BASE':
                    self.blocks.clear()
                if kind in ('BASE', 'TERRAIN'):
                    for record in filter(None, body.split('/')):
                        fields = record.split(',')
                        block_id = int(fields[0])
                        if len(fields) == 1:
                            self.blocks.pop(block_id, None)
                        else:
                            self.blocks[block_id] = fields
                elif kind == 'ENTITIES':
                    self.entities = [entry.split(',') for entry in body.split('/') if entry]
                elif kind == 'READY':
                    self.ready.set()

    async def synchronize(self):
        deadline = time.monotonic() + 30
        while not self.ready.is_set():
            if self.task.done():
                self.task.result()
            await self.send(f'WORLD_READY;{self.width};{self.height}')
            try:
                await asyncio.wait_for(self.ready.wait(), 3)
            except TimeoutError:
                if time.monotonic() >= deadline:
                    raise TimeoutError('Host did not finish joining; choose a character on the host')

    async def wait_until(self, predicate, timeout=15):
        deadline = time.monotonic() + timeout
        while not predicate():
            if self.task.done():
                self.task.result()
                raise AssertionError('Connection closed during test')
            if time.monotonic() > deadline:
                raise TimeoutError('Host did not produce the expected state')
            await asyncio.sleep(.05)

    async def command(self, verb, arguments, request_id=None):
        request_id = request_id or time.monotonic_ns()
        self.receipts.pop(request_id, None)
        await self.send(f'ACTION;{request_id};{verb};1;10000;10000;40000;' + ';'.join(map(str, arguments)))
        await self.wait_until(lambda: request_id in self.receipts)
        return self.receipts[request_id], request_id


async def main(uri):
    async with websockets.connect(uri, max_size=2**20) as sa, websockets.connect(uri, max_size=2**20) as sb:
        a, b = Peer(sa), Peer(sb)
        await asyncio.gather(a.synchronize(), b.synchronize())
        await a.send('PLAYER;10000;10000;40000;true;1;5')
        await b.send('PLAYER;10000;10000;40000;true;1;5')
        await asyncio.sleep(.2)
        receipt, request_id = await a.command('PLACE', [12, 40, 2, 4])
        assert receipt == 'true;_;_', receipt
        duplicate, _ = await a.command('PLACE', [12, 40, 2, 4], request_id)
        assert duplicate == receipt
        def ramps(peer):
            return [p for p in peer.blocks.values() if p[1:3] == ['12', '40'] and p[8:12] == ['2', '4', '1', '10']]
        await b.wait_until(lambda: len(ramps(a)) == len(ramps(b)) == 1)
        for hit in range(6):
            receipt, _ = await b.command('HIT', [12, 40, 7])
            assert receipt == ('true;Block;Stone' if hit == 5 else 'true;_;_'), receipt
        await b.wait_until(lambda: not ramps(a) and not ramps(b))
        receipt, _ = await a.command('FIRE', [18000, 42100])
        assert receipt == 'true;_;_', receipt
        await a.wait_until(lambda: any(e[0] == 'A' and e[2] == '10000' for e in a.entities))
        await asyncio.sleep(1)
        common = a.transactions.keys() & b.transactions.keys()
        assert any(k[2] == 'BASE' for k in common), 'No shared baseline'
        assert sum(k[2] == 'ENTITIES' for k in common) >= 5, 'No sustained shared entity stream'
        assert all(a.transactions[k] == b.transactions[k] for k in common), 'Divergent snapshots'
        assert a.blocks == b.blocks, 'Divergent terrain'
        assert any(e[0] in ('Z', 'S') and int(e[3]) != 0 for e in a.entities), 'Missing enemy depth coordinates'
        async with websockets.connect(uri, max_size=2**20) as late_socket:
            late = Peer(late_socket)
            await late.synchronize()
            await late.wait_until(lambda: late.blocks == a.blocks == b.blocks)
            assert all(p[1:3] != ['12', '40'] or p[11] != '10' for p in late.blocks.values()), 'Late join restored destroyed terrain'
            late.task.cancel()
            await asyncio.gather(late.task, return_exceptions=True)
        print(f'PASS: {len(common)} identical world transactions; {len(a.blocks)} matching blocks; '
              f'{len(a.entities)} entities. Placement, retry, six hits, destruction, remote arrow and late join verified.')
        for p in (a, b):
            p.task.cancel()
        await asyncio.gather(a.task, b.task, return_exceptions=True)


if __name__ == '__main__':
    asyncio.run(main(sys.argv[1] if len(sys.argv) > 1 else 'ws://127.0.0.1:18765'))
