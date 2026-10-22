const net = require('net');
const http = require('http');
const crypto = require('crypto');
const fs = require('fs');
const path = require('path');
const os = require('os');

const args = {};
for (let i = 2; i < process.argv.length - 1; i += 2) args[process.argv[i].replace(/^-/, '')] = process.argv[i + 1];

const DIR = path.join(os.homedir(), '.commanddeck');

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
      : Buffer.from([0x80 | op, 0x80 | 126, n >> 8, n & 255]);
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
  const lines = String(m.payload?.settings?.command || '').split(/\r?\n/).map(l => l.trim()).filter(Boolean);
  if (m.event === 'willAppear' || m.event === 'didReceiveSettings') ws.send({ event: 'setTitle', context: m.context, payload: { title: lines[0] || '', target: 0 } });
  else if (m.event === 'keyDown') run(m.context, lines);
});
ws.onopen = () => ws.send({ event: args.registerEvent, uuid: args.pluginUUID });

function post(game, command) {
  return new Promise(resolve => {
    const body = Buffer.from(JSON.stringify({ command }), 'utf8');
    const req = http.request({ host: '127.0.0.1', port: game.port, path: '/command', method: 'POST', timeout: 4000,
      headers: { 'X-CommandDeck': game.token, 'Content-Type': 'application/json', 'Content-Length': body.length } }, res => {
      res.resume();
      resolve(res.statusCode);
    });
    req.on('error', e => resolve(e.code === 'ECONNREFUSED' ? -1 : 0));
    req.on('timeout', () => { req.destroy(); resolve(0); });
    req.end(body);
  });
}

// Running games, newest first; files left by a closed or crashed game are removed when the port refuses.
function games() {
  let files = [];
  try { files = fs.readdirSync(DIR).filter(f => f.endsWith('.json')); } catch { return []; }
  const out = [];
  for (const f of files) {
    try { out.push({ file: path.join(DIR, f), ...JSON.parse(fs.readFileSync(path.join(DIR, f), 'utf8')) }); } catch {}
  }
  return out.sort((a, b) => (b.started || 0) - (a.started || 0));
}

async function run(ctx, lines) {
  let game = null;
  if (lines.length) {
    for (const g of games()) {
      const code = await post(g, lines[0]);
      if (code === -1) { try { fs.unlinkSync(g.file); } catch {} }
      if (code === 200) { game = g; break; }
    }
  }
  let ok = !!game;
  for (let i = 1; i < lines.length && ok; i++) {
    await new Promise(r => setTimeout(r, 150));
    ok = (await post(game, lines[i])) === 200;
  }
  ws.send({ event: ok ? 'showOk' : 'showAlert', context: ctx });
}
