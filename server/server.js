// PhoneLink signalling + relay server.
// Phones connect with a WebSocket, register with their ID, and the server forwards binary frames
// between IDs. Voice/video media does NOT pass through here (WebRTC goes phone-to-phone);
// only call setup, chat messages, voice messages and (small) files are relayed.
const http = require('http');
const { WebSocketServer } = require('ws');

const PORT = process.env.PORT || 8080;

const server = http.createServer((req, res) => {
  res.writeHead(200, { 'Content-Type': 'text/plain' });
  res.end('PhoneLink server is running. Online phones: ' + clients.size + '\n');
});

const wss = new WebSocketServer({ server, maxPayload: 1024 * 1024 });
const clients = new Map(); // id -> ws
const tokens = new Map();  // id -> token (first registration wins, in memory)

function sendJson(ws, obj) {
  if (ws.readyState === 1) ws.send(JSON.stringify(obj));
}

wss.on('connection', (ws) => {
  ws.id = null;
  ws.isAlive = true;
  ws.on('pong', () => { ws.isAlive = true; });

  ws.on('message', (data, isBinary) => {
    if (!isBinary) {
      let j;
      try { j = JSON.parse(data.toString()); } catch (e) { return; }
      if (j.t === 'register') {
        const id = String(j.id || '');
        const token = String(j.token || '');
        if (!/^[A-Z0-9]{6,20}$/.test(id) || token.length < 8) {
          sendJson(ws, { t: 'error', msg: 'bad id' });
          return;
        }
        if (tokens.has(id) && tokens.get(id) !== token) {
          sendJson(ws, { t: 'error', msg: 'ID already used by another phone' });
          return;
        }
        tokens.set(id, token);
        const old = clients.get(id);
        if (old && old !== ws) { try { old.terminate(); } catch (e) {} }
        ws.id = id;
        clients.set(id, ws);
        sendJson(ws, { t: 'ok' });
      }
      return;
    }

    if (!ws.id) return;
    // binary message: [idLen:1][targetId][frame...]
    const n = data[0];
    if (data.length < 1 + n + 5) return;
    const target = data.toString('utf8', 1, 1 + n);
    const dest = clients.get(target);
    if (!dest || dest.readyState !== 1) {
      sendJson(ws, { t: 'offline', id: target });
      return;
    }
    const me = Buffer.from(ws.id);
    dest.send(Buffer.concat([Buffer.from([me.length]), me, data.subarray(1 + n)]), { binary: true });
  });

  ws.on('close', () => {
    if (ws.id && clients.get(ws.id) === ws) clients.delete(ws.id);
  });
  ws.on('error', () => {});
});

// drop dead connections
setInterval(() => {
  wss.clients.forEach((ws) => {
    if (!ws.isAlive) return ws.terminate();
    ws.isAlive = false;
    try { ws.ping(); } catch (e) {}
  });
}, 30000);

server.listen(PORT, () => console.log('PhoneLink server listening on ' + PORT));
