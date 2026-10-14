// Rylaide relay (Node). Voice packets are tiny binary WebSocket messages.
// A rider connects to /room/<groupCode>; whatever they send goes to every other
// rider in the same room. Nothing is stored and nothing is decoded.
// Run:  npm install && node server.js      (PORT env var, default 8080)
const { WebSocketServer } = require('ws');
const http = require('http');

function createRelay(port) {
  const rooms = new Map();
  const http_ = http.createServer((req, res) => { res.end('Rylaide relay\n'); });
  const wss = new WebSocketServer({ noServer: true, maxPayload: 4096 });
  http_.on('upgrade', (req, socket, head) => {
    const m = /^\/room\/([A-Za-z0-9_-]{1,32})$/.exec((req.url || '').split('?')[0]);
    if (!m) { socket.destroy(); return; }
    wss.handleUpgrade(req, socket, head, (ws) => {
      const room = m[1];
      if (!rooms.has(room)) rooms.set(room, new Set());
      const peers = rooms.get(room);
      if (peers.size >= 32) { ws.close(1013, 'room full'); return; }
      peers.add(ws);
      ws.isAlive = true;
      ws.on('pong', () => { ws.isAlive = true; });
      ws.on('message', (data, isBinary) => {
        if (!isBinary) return;
        for (const p of peers) if (p !== ws && p.readyState === 1) p.send(data, { binary: true });
      });
      ws.on('close', () => { peers.delete(ws); if (peers.size === 0) rooms.delete(room); });
      ws.on('error', () => {});
    });
  });
  const sweep = setInterval(() => {
    for (const peers of rooms.values()) for (const ws of peers) {
      if (!ws.isAlive) { ws.terminate(); continue; }
      ws.isAlive = false; ws.ping();
    }
  }, 20000);
  return new Promise((resolve) => http_.listen(port, () => resolve({
    port: http_.address().port,
    close: () => { clearInterval(sweep); wss.clients.forEach((c) => c.terminate()); http_.close(); },
  })));
}

module.exports = { createRelay };
if (require.main === module) {
  createRelay(Number(process.env.PORT) || 8080).then((r) => console.log('Rylaide relay on port', r.port));
}
