import {
  makeWASocket,
  useMultiFileAuthState,
  DisconnectReason,
} from '@whiskeysockets/baileys';
import QRCode from 'qrcode';
import pino from 'pino';
import { existsSync, mkdirSync, writeFileSync, readFileSync } from 'fs';
import { resolve, dirname } from 'path';
import { fileURLToPath } from 'url';

const __dirname = dirname(fileURLToPath(import.meta.url));

export class WhatsAppSocket {
  constructor(authDir) {
    this.authDir = resolve(authDir || resolve(__dirname, '..', 'auth'));
    this.sock = null;
    this.connected = false;
    this.phone = null;
    this.currentQR = null;
    this.qrResolve = null;
    this.messages = [];
    this.contacts = new Map();
    this.storeContacts = {};
    this.logger = pino({
      level: process.env.LOG_LEVEL || 'info',
      transport: { target: 'pino/file', options: { destination: 1 } }
    });

    if (!existsSync(this.authDir)) {
      mkdirSync(this.authDir, { recursive: true });
    }

    this._loadStore();
  }

  _storePath() {
    return resolve(this.authDir, 'store.json');
  }

  _loadStore() {
    try {
      const data = readFileSync(this._storePath(), 'utf-8');
      const parsed = JSON.parse(data);
      this.storeContacts = parsed.contacts || {};
    } catch {
      this.storeContacts = {};
    }
  }

  _saveStore() {
    try {
      writeFileSync(this._storePath(), JSON.stringify({
        contacts: this.storeContacts,
        updatedAt: Date.now()
      }));
    } catch {}
  }

  async connect() {
    const { state, saveCreds } = await useMultiFileAuthState(this.authDir);

    const sock = makeWASocket({
      auth: state,
      logger: this.logger,
      syncFullHistory: false,
      markOnlineOnConnect: true,
      generateHighQualityLinkPreview: true,
      defaultQueryTimeoutMs: 30_000,
      emitOwnEvents: false,
    });

    this.sock = sock;

    sock.ev.on('creds.update', saveCreds);

    sock.ev.on('connection.update', async (update) => {
      const { connection, lastDisconnect, qr } = update;

      if (qr) {
        this.currentQR = await QRCode.toDataURL(qr, {
          margin: 1,
          scale: 8,
          color: { dark: '#000000', light: '#FFFFFF' }
        });
        this.connected = false;
        this.phone = null;
        if (this.qrResolve) {
          this.qrResolve(this.currentQR);
          this.qrResolve = null;
        }
      }

      if (connection === 'close') {
        const reason = lastDisconnect?.error?.output?.statusCode;
        const shouldReconnect = reason !== DisconnectReason.loggedOut;
        this.connected = false;
        this.phone = null;
        this.logger.info(`Connection closed (reason: ${reason}), reconnecting: ${shouldReconnect}`);
        if (shouldReconnect) {
          setTimeout(() => this.connect(), 1000);
        }
      }

      if (connection === 'open') {
        this.connected = true;
        this.phone = sock.user?.id?.split(':')[0] || 'unknown';
        this.currentQR = null;
        this.logger.info(`Connected as ${this.phone}`);
      }
    });

    sock.ev.on('messages.upsert', (msgEvent) => {
      for (const msg of msgEvent.messages) {
        if (!msg.key.fromMe) {
          const text = msg.message?.conversation
            || msg.message?.extendedTextMessage?.text
            || msg.message?.imageMessage?.caption
            || '';
          if (text) {
            this.messages.push({
              id: msg.key.id,
              jid: msg.key.remoteJid,
              from: msg.pushName || 'Unknown',
              text,
              timestamp: msg.messageTimestamp,
              isGroup: msg.key.remoteJid?.includes('@g.us'),
            });
          }
        }
      }
      if (this.messages.length > 500) {
        this.messages = this.messages.slice(-500);
      }
    });

    sock.ev.on('contacts.update', (contacts) => {
      for (const contact of contacts) {
        if (contact.id) {
          const entry = {
            jid: contact.id,
            name: contact.notify || contact.name || contact.pushName || contact.id.split('@')[0],
          };
          this.contacts.set(contact.id, entry);
          this.storeContacts[contact.id] = entry;
        }
      }
      this._saveStore();
    });

    sock.ev.on('contacts.set', ({ contacts }) => {
      for (const [jid, contact] of Object.entries(contacts)) {
        const entry = {
          jid,
          name: contact.notify || contact.name || contact.pushName || jid.split('@')[0],
        };
        this.contacts.set(jid, entry);
        this.storeContacts[jid] = entry;
      }
      this._saveStore();
    });

    return sock;
  }

  async getQR(timeoutMs = 120_000) {
    if (this.connected) return null;
    if (this.currentQR) return this.currentQR;

    return new Promise((resolve, reject) => {
      this.qrResolve = resolve;
      setTimeout(() => {
        if (this.qrResolve) {
          this.qrResolve = null;
          reject(new Error('QR timeout'));
        }
      }, timeoutMs);
    });
  }

  async sendMessage(jid, text) {
    if (!this.sock || !this.connected) {
      throw new Error('Not connected to WhatsApp');
    }
    const result = await this.sock.sendMessage(jid, { text });
    return { id: result.key.id };
  }

  async getMessages(jid, limit = 20) {
    if (!this.sock) return [];
    try {
      const msgs = await this.sock.loadMessages(jid, limit);
      return msgs
        .filter(m => m.message?.conversation || m.message?.extendedTextMessage?.text)
        .map(m => ({
          id: m.key.id,
          jid: m.key.remoteJid,
          fromMe: m.key.fromMe,
          text: m.message?.conversation || m.message?.extendedTextMessage?.text || '',
          timestamp: m.messageTimestamp,
        }))
        .slice(-limit);
    } catch {
      return [];
    }
  }

  async getContactsList() {
    if (!this.sock) return Object.values(this.storeContacts);
    try {
      const contacts = await this.sock.fetchAllContacts();
      return Object.entries(contacts)
        .filter(([jid]) => !jid.includes('@broadcast') && !jid.includes('status'))
        .map(([jid, c]) => ({
          jid,
          name: c.notify || c.name || c.pushName || jid.split('@')[0],
          verifiedName: c.verifiedName || null,
        }));
    } catch {
      return Object.values(this.storeContacts);
    }
  }

  async setPresence(type = 'available') {
    if (!this.sock || !this.connected) return;
    try {
      await this.sock.sendPresenceUpdate(type);
    } catch {}
  }

  async logout() {
    if (this.sock) {
      try {
        await this.sock.logout();
      } catch {}
    }
    this.connected = false;
    this.phone = null;
    this.currentQR = null;
    this.messages = [];
  }

  disconnect() {
    if (this.sock) {
      this.sock.end(new Error('manual disconnect'));
    }
    this.sock = null;
    this.connected = false;
  }
}
