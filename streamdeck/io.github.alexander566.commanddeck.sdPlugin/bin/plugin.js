const net = require('net');
const http = require('http');
const crypto = require('crypto');

const args = {};
for (let i = 2; i < process.argv.length - 1; i += 2) args[process.argv[i].replace(/^-/, '')] = process.argv[i + 1];

const DEFAULT_PORT = 39401;
const settings = new Map();

function connect(port, onText) {
  const sock = net.connect(port, '127.0.0.1');
  const key = crypto.randomBytes(16).toString('base64');
  let buf = Buffer.alloc(0), open = false;
  sock.on('connect', () => sock.write(`GET / HTTP/1.1\r\nHost: 127.0.0.1:${port}\r\nUpgrade: websocket\r\nConnection: Upgrade\r\nSec-WebSocket-Key: ${key}\r\nSec-WebSocket-Version: 13\r\n\r\n`));
  sock.on('data', d => {
    buf = Buffer.concat([buf, d]);
    if (!open) {
      const end = buf.indexOf('\r\n\r\n');
      if (end < 0) return;
      buf = buf.subarray(end + 4);
      open = true;
      ws.onopen();
    }
    while (buf.length >= 2) {
      const op = buf[0] & 0x0f;
      let len = buf[1] & 0x7f, off = 2;
      if (len === 126) { if (buf.length < 4) return; len = buf.readUInt16BE(2); off = 4; }
      else if (len === 127) { if (buf.length < 10) return; len = Number(buf.readBigUInt64BE(2)); off = 10; }
      if (buf.length < off + len) return;
      const body = buf.subarray(off, off + len);
      buf = buf.subarray(off + len);
      if (op === 1) onText(body.toString('utf8'));
      else if (op === 9) frame(10, body);
      else if (op === 8) process.exit(0);
    }
  });
  sock.on('close', () => process.exit(0));
  sock.on('error', () => process.exit(1));
  function frame(op, payload) {
    const mask = crypto.randomBytes(4), n = payload.length;
    const head = n < 126 ? Buffer.from([0x80 | op, 0x80 | n])
      : n < 65536 ? Buffer.from([0x80 | op, 0x80 | 126, n >> 8, n & 255])
      : Buffer.concat([Buffer.from([0x80 | op, 0x80 | 127]), (() => { const b = Buffer.alloc(8); b.writeBigUInt64BE(BigInt(n)); return b; })()]);
    const out = Buffer.from(payload);
    for (let i = 0; i < n; i++) out[i] ^= mask[i & 3];
    sock.write(Buffer.concat([head, mask, out]));
  }
  const ws = { send: o => frame(1, Buffer.from(JSON.stringify(o), 'utf8')), onopen: () => {} };
  return ws;
}

const ws = connect(Number(args.port), text => {
  let m;
  try { m = JSON.parse(text); } catch { return; }
  const ctx = m.context;
  if (m.event === 'willAppear' || m.event === 'didReceiveSettings') settings.set(ctx, m.payload.settings || {});
  else if (m.event === 'willDisappear') settings.delete(ctx);
  else if (m.event === 'keyDown') run(ctx, m.payload.settings || settings.get(ctx) || {});
});
ws.onopen = () => ws.send({ event: args.registerEvent, uuid: args.pluginUUID });

function post(port, command) {
  return new Promise(resolve => {
    const body = Buffer.from(JSON.stringify({ command }), 'utf8');
    const req = http.request({ host: '127.0.0.1', port, path: '/command', method: 'POST', timeout: 4000,
      headers: { 'Content-Type': 'application/json', 'Content-Length': body.length, 'X-CommandDeck': '1' } },
      res => { res.resume(); res.on('end', () => resolve(res.statusCode === 200)); });
    req.on('error', () => resolve(false));
    req.on('timeout', () => { req.destroy(); resolve(false); });
    req.end(body);
  });
}

async function run(ctx, s) {
  const port = Number(s.port) || DEFAULT_PORT;
  const lines = String(s.command || '').split(/\r?\n/).map(l => l.trim()).filter(Boolean);
  if (!lines.length) { ws.send({ event: 'showAlert', context: ctx }); return; }
  let ok = true;
  for (let i = 0; i < lines.length && ok; i++) {
    if (i) await new Promise(r => setTimeout(r, 150));
    ok = await post(port, lines[i]);
  }
  ws.send({ event: ok ? 'showOk' : 'showAlert', context: ctx });
}
