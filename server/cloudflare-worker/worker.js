// Rylaide relay on Cloudflare Workers (free plan). Same protocol as ../node-relay:
// connect to wss://<worker>/room/<groupCode>, binary messages go to the other riders.
export class Room {
  constructor(state) { this.state = state; }
  async fetch(request) {
    if (request.headers.get('Upgrade') !== 'websocket') return new Response('expected websocket', { status: 426 });
    if (this.state.getWebSockets().length >= 32) return new Response('room full', { status: 503 });
    const pair = new WebSocketPair();
    this.state.acceptWebSocket(pair[1]);
    return new Response(null, { status: 101, webSocket: pair[0] });
  }
  webSocketMessage(ws, message) {
    if (typeof message === 'string') return;
    for (const peer of this.state.getWebSockets()) {
      if (peer !== ws) { try { peer.send(message); } catch (e) {} }
    }
  }
  webSocketClose(ws, code) { try { ws.close(code || 1000); } catch (e) {} }
}
export default {
  async fetch(request, env) {
    const m = /^\/room\/([A-Za-z0-9_-]{1,32})$/.exec(new URL(request.url).pathname);
    if (!m) return new Response('Rylaide relay');
    return env.ROOM.get(env.ROOM.idFromName(m[1])).fetch(request);
  },
};
