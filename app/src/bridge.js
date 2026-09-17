const { EventEmitter } = require('events');
const WebSocket = require('ws');

/**
 * One connection to a running Minecraft client that has the PVPTraining mod.
 * The mod only listens on loopback and rejects any socket that does not open
 * with the per-launch token from its instance file.
 */
class Bridge extends EventEmitter {
  constructor(instance) {
    super();
    this.instance = instance;
    this.ws = null;
    this.closedByUs = false;
  }

  open() {
    return new Promise((resolve) => {
      let settled = false;
      const done = (result) => {
        if (settled) return;
        settled = true;
        resolve(result);
      };
      const url = `ws://127.0.0.1:${this.instance.port}/`;
      this.emit('status', { state: 'connecting' });
      const ws = new WebSocket(url, { handshakeTimeout: 4000, origin: 'pvptraining://app' });
      this.ws = ws;

      ws.on('open', () => {
        ws.send(JSON.stringify({ t: 'hello', token: this.instance.token, client: 'pvptraining-app' }));
      });
      ws.on('message', (raw) => {
        let msg;
        try {
          msg = JSON.parse(raw.toString());
        } catch {
          return;
        }
        if (msg.t === 'welcome') {
          this.emit('status', { state: 'connected', instance: this.instance });
          done({ ok: true });
        } else if (msg.t === 'denied') {
          done({ ok: false, error: msg.reason || 'The game refused the connection.' });
        }
        this.emit('message', msg);
      });
      ws.on('error', (err) => {
        done({
          ok: false,
          error: err.code === 'ECONNREFUSED' ? 'Minecraft is not answering. Is it still open?' : err.message,
        });
      });
      ws.on('close', () => {
        this.emit('status', { state: 'disconnected', byUser: this.closedByUs });
        done({ ok: false, error: 'The connection closed before the game answered.' });
      });
    });
  }

  send(msg) {
    if (!this.ws || this.ws.readyState !== WebSocket.OPEN) return false;
    this.ws.send(JSON.stringify(msg));
    return true;
  }

  close() {
    this.closedByUs = true;
    try {
      this.ws?.close();
    } catch {
      /* already gone */
    }
  }
}

module.exports = { Bridge };
