import { WebSocketServer, WebSocket } from "ws";
import { randomBytes } from "node:crypto";

const port = Number(process.env.PORT ?? 8787);
const host = process.env.HOST ?? "0.0.0.0";

type Client = { ws: WebSocket; id: string; code: string };
const clients = new Map<string, Client>();
const codes = new Map<string, string>();

function token() {
  return randomBytes(24).toString("hex");
}

function send(ws: WebSocket, message: object) {
  if (ws.readyState === WebSocket.OPEN) ws.send(JSON.stringify(message));
}

const wss = new WebSocketServer({ host, port });

wss.on("connection", (ws) => {
  let client: Client | undefined;
  let sessionToken: string | undefined;

  ws.on("message", (raw) => {
    try {
      const msg = JSON.parse(raw.toString()) as Record<string, unknown>;
      const type = String(msg.type ?? "");

      if (type === "REGISTER") {
        const id = String(msg.deviceId ?? "");
        const code = String(msg.deviceCode ?? "");
        if (!id || !/^MYT-[A-Z0-9]{8}$/.test(code)) {
          send(ws, { type: "ERROR", message: "invalid_device" });
          return;
        }
        client = { ws, id, code };
        clients.set(id, client);
        codes.set(code, id);
        send(ws, { type: "REGISTERED", deviceId: id, deviceCode: code });
        return;
      }

      if (!client) {
        send(ws, { type: "ERROR", message: "not_registered" });
        return;
      }

      if (type === "REQUEST") {
        const targetCode = String(msg.targetCode ?? "");
        const targetId = codes.get(targetCode);
        const target = targetId ? clients.get(targetId) : undefined;
        if (!target) {
          send(ws, { type: "ERROR", message: "device_offline" });
          return;
        }
        sessionToken = token();
        send(target.ws, {
          type: "INCOMING_REQUEST",
          requesterCode: client.code,
          requesterId: client.id,
          sessionToken,
        });
        send(ws, { type: "REQUEST_SENT", sessionToken });
        return;
      }

      if (type === "ACCEPT" || type === "REJECT") {
        const targetId = String(msg.targetId ?? "");
        const target = clients.get(targetId);
        if (!target) return;
        const requestToken = String(msg.sessionToken ?? "");
        if (!requestToken) return;
        send(target.ws, { type, sessionToken: requestToken });
        return;
      }

      if (["OFFER", "ANSWER", "ICE"].includes(type)) {
        const targetId = String(msg.targetId ?? "");
        const target = clients.get(targetId);
        if (!target || !sessionToken) return;
        send(target.ws, { ...msg, sessionToken });
      }
    } catch {
      send(ws, { type: "ERROR", message: "invalid_json" });
    }
  });

  ws.on("close", () => {
    if (client) {
      clients.delete(client.id);
      if (codes.get(client.code) === client.id) codes.delete(client.code);
    }
  });
});

console.log(`Suporte Mythøs signaling server listening on ws://${host}:${port}`);
