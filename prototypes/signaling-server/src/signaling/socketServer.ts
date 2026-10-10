import { WebSocketServer, WebSocket } from "ws";
import { Server } from "http";
import { randomUUID } from "crypto";
import { createGroup, getGroup, addMember, removeMember } from "../groups/groupManager";
import {
  registerConnection,
  setGroup,
  getConnection,
  getGroupMembers,
  removeConnection,
} from "../presence/presenceManager";

// Message shapes exchanged with the app. Keep this file in sync with
// shared-docs/api-contracts.md so both the Android and iOS clients agree
// on the exact same message format.
//
// Client -> server:
//   { type: "create-group", name: string }
//   { type: "join-group", code: string }
//   { type: "signal", targetRiderId: string, payload: any }  // SDP/ICE, opaque to the server
//   { type: "leave-group" }
//
// Server -> client:
//   { type: "group-created", code: string }
//   { type: "group-joined", code: string, memberIds: string[] }
//   { type: "member-joined", riderId: string }
//   { type: "member-left", riderId: string }
//   { type: "signal", fromRiderId: string, payload: any }
//   { type: "error", message: string }

export function attachSignalingServer(httpServer: Server): void {
  const wss = new WebSocketServer({ server: httpServer, path: "/signaling" });

  wss.on("connection", (socket: WebSocket) => {
    const riderId = randomUUID();
    registerConnection(riderId, socket);

    socket.on("message", (raw) => {
      let message: any;
      try {
        message = JSON.parse(raw.toString());
      } catch {
        send(socket, { type: "error", message: "Invalid JSON" });
        return;
      }

      switch (message.type) {
        case "create-group": {
          const group = createGroup(message.name ?? "Ride group");
          addMember(group.code, riderId);
          setGroup(riderId, group.code);
          send(socket, { type: "group-created", code: group.code });
          break;
        }

        case "join-group": {
          const group = getGroup(message.code);
          if (!group) {
            send(socket, { type: "error", message: "Group not found" });
            return;
          }
          addMember(group.code, riderId);
          setGroup(riderId, group.code);

          const members = getGroupMembers(group.code, riderId);
          send(socket, {
            type: "group-joined",
            code: group.code,
            memberIds: members.map((m) => m.riderId),
          });

          // Tell everyone already in the group that a new rider joined,
          // so their apps can start a WebRTC connection to the newcomer.
          for (const member of members) {
            send(member.socket, { type: "member-joined", riderId });
          }
          break;
        }

        case "signal": {
          const target = getConnection(message.targetRiderId);
          if (!target) return;
          send(target.socket, {
            type: "signal",
            fromRiderId: riderId,
            payload: message.payload,
          });
          break;
        }

        case "leave-group": {
          const conn = getConnection(riderId);
          if (conn?.groupCode) {
            const groupCode = conn.groupCode;
            removeMember(groupCode, riderId);
            for (const member of getGroupMembers(groupCode)) {
              send(member.socket, { type: "member-left", riderId });
            }
          }
          setGroup(riderId, null);
          break;
        }

        default:
          send(socket, { type: "error", message: `Unknown message type: ${message.type}` });
      }
    });

    socket.on("close", () => {
      const conn = getConnection(riderId);
      if (conn?.groupCode) {
        removeMember(conn.groupCode, riderId);
        for (const member of getGroupMembers(conn.groupCode)) {
          send(member.socket, { type: "member-left", riderId });
        }
      }
      removeConnection(riderId);
    });
  });
}

function send(socket: WebSocket, message: unknown): void {
  if (socket.readyState === WebSocket.OPEN) {
    socket.send(JSON.stringify(message));
  }
}
