import express from "express";
import { createServer } from "http";
import { attachSignalingServer } from "./signaling/socketServer";

const app = express();
const port = process.env.PORT ?? 3000;

// Simple health check - useful for confirming the server is alive once
// deployed, and for Render's health checks.
app.get("/health", (_req, res) => {
  res.json({ status: "ok" });
});

const httpServer = createServer(app);
attachSignalingServer(httpServer);

httpServer.listen(port, () => {
  console.log(`Signaling server listening on port ${port}`);
  console.log(`WebSocket endpoint: ws://localhost:${port}/signaling`);
});
