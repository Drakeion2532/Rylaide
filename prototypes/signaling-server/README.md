# Rylaide signaling server (prototype)

This carries **no audio at all** - it just helps two riders' apps find each
other and exchange WebRTC connection details (SDP offers/answers, ICE
candidates). Once that handshake is done, audio flows directly between
phones (or through TURN as a fallback), never through this server.

## Run it locally

```bash
npm install
npm run dev
```

Server starts on `http://localhost:3000`, WebSocket signaling on
`ws://localhost:3000/signaling`.

Check it's alive:
```bash
curl http://localhost:3000/health
```

## Deploy it for free

1. Push this `backend/` folder to a GitHub repo
2. Create a free account at render.com
3. New > Blueprint, point it at your repo (it will read `render.yaml`)
4. Render gives you a free subdomain like `rylaide-signaling.onrender.com`

Note: Render's free tier sleeps after 15 minutes of no traffic and takes
30-60 seconds to wake up on the next request. Fine for early testing, worth
upgrading once you have real ride groups depending on it.

## What's here

- `src/index.ts` - starts the HTTP + WebSocket server
- `src/signaling/socketServer.ts` - the actual message relay logic (create
  group, join group, forward signal messages, track who left)
- `src/groups/groupManager.ts` - generates and tracks 6-character join codes
- `src/presence/presenceManager.ts` - tracks which rider is in which group

## Next steps for this piece specifically

- Swap the in-memory `Map`s in `groupManager.ts`/`presenceManager.ts` for a
  real store if you want groups to survive a server restart
- Add basic rate limiting before this is public-facing
- This is where you'd add TURN credential generation if you set up
  time-limited coturn credentials per rider
