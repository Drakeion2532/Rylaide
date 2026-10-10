import type { WebSocket } from "ws";

// Maps a rider's connection id to their live WebSocket, plus which group
// they're currently in. This is how the signaling server knows who to
// forward an offer/answer/ICE candidate to - it never looks at the audio
// itself, only routes small JSON messages between the right people.

interface RiderConnection {
  riderId: string;
  groupCode: string | null;
  socket: WebSocket;
}

const connections = new Map<string, RiderConnection>();

export function registerConnection(riderId: string, socket: WebSocket): void {
  connections.set(riderId, { riderId, groupCode: null, socket });
}

export function setGroup(riderId: string, groupCode: string | null): void {
  const conn = connections.get(riderId);
  if (conn) conn.groupCode = groupCode;
}

export function getConnection(riderId: string): RiderConnection | undefined {
  return connections.get(riderId);
}

export function getGroupMembers(groupCode: string, excludingRiderId?: string): RiderConnection[] {
  return Array.from(connections.values()).filter(
    (c) => c.groupCode === groupCode && c.riderId !== excludingRiderId
  );
}

export function removeConnection(riderId: string): void {
  connections.delete(riderId);
}
