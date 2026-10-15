// Rylaide UDP relay. Fastest internet path: no connection, no queue, lost packets are just skipped.
// Datagrams: 1 [len][room] join/keep-alive -> server answers [3]
//            2 [len][room][data] -> forwarded as [2][data] to the other riders in the room
// A rider is dropped after 20 s of silence. Nothing is stored or decoded.
// Run: node server.js   (UDP_PORT env var, default 9000). Open that UDP port in the firewall.
const dgram = require('dgram');

function createRelay(port, { peerTtlMs = 20000, maxPerRoom = 32 } = {}) {
  const rooms = new Map(); // room -> Map(addrKey -> {address, port, seen})
  const sock = dgram.createSocket('udp4');
  const touch = (room, rinfo) => {
    let peers = rooms.get(room);
    if (!peers) { peers = new Map(); rooms.set(room, peers); }
    const key = rinfo.address + ':' + rinfo.port;
    let p = peers.get(key);
    if (!p) {
      if (peers.size >= maxPerRoom) return null;
      p = { address: rinfo.address, port: rinfo.port, seen: 0 };
      peers.set(key, p);
    }
    p.seen = Date.now();
    return peers;
  };
  sock.on('message', (msg, rinfo) => {
    if (msg.length < 3) return;
    const type = msg[0], n = msg[1];
    if (n < 1 || n > 32 || msg.length < 2 + n) return;
    const room = msg.toString('utf8', 2, 2 + n);
    if (!/^[A-Za-z0-9_-]+$/.test(room)) return;
    const peers = touch(room, rinfo);
    if (!peers) return;
    if (type === 1) { sock.send(Buffer.from([3]), rinfo.port, rinfo.address); return; }
    if (type === 2 && msg.length > 2 + n) {
      const out = Buffer.concat([Buffer.from([2]), msg.subarray(2 + n)]);
      const me = rinfo.address + ':' + rinfo.port;
      for (const [key, p] of peers) if (key !== me) sock.send(out, p.port, p.address);
    }
  });
  const sweep = setInterval(() => {
    const now = Date.now();
    for (const [room, peers] of rooms) {
      for (const [key, p] of peers) if (now - p.seen > peerTtlMs) peers.delete(key);
      if (peers.size === 0) rooms.delete(room);
    }
  }, 5000);
  sock.on('error', () => {});
  return new Promise((resolve) => sock.bind(port, () => resolve({
    port: sock.address().port,
    close: () => { clearInterval(sweep); sock.close(); },
  })));
}
module.exports = { createRelay };
if (require.main === module) {
  createRelay(Number(process.env.UDP_PORT) || 9000).then((r) => console.log('Rylaide UDP relay on UDP port', r.port));
}
