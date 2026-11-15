import {
  makeWASocket,
  useMultiFileAuthState,
  DisconnectReason,
} from '@whiskeysockets/baileys';
import QRCode from 'qrcode';
import pino from 'pino';
import { existsSync, mkdirSync, writeFileSync, readFileSync, readdirSync, unlinkSync } from 'fs';
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
    this.qrListeners = [];
    this.reconnectAttempts = 0;
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

  _clearAuthData() {
    try {
      if (existsSync(this.authDir)) {
        const files = readdirSync(this.authDir);
        for (const file of files) {
          if (file !== 'store.json') {
            try { unlinkSync(resolve(this.authDir, file)); } catch {}
          }
        }
      }
      this.logger.info('WhatsApp auth session cleared');
    } catch (e) {
      this.logger.warn(`Failed to clear auth data: ${e.message}`);
    }
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
        const listeners = [...this.qrListeners];
        this.qrListeners = [];
        for (const fn of listeners) {
          try { fn(this.currentQR); } catch {}
        }
      }

      if (connection === 'close') {
        const reason = lastDisconnect?.error?.output?.statusCode;
        const isLoggedOut = reason === DisconnectReason.loggedOut;
        const shouldReconnect = !isLoggedOut;
        this.connected = false;
        this.phone = null;
        this.logger.info(`Connection closed (reason: ${reason}), reconnecting: ${shouldReconnect}`);
        if (isLoggedOut) {
          this._clearAuthData();
        } else if (shouldReconnect) {
          const delayMs = Math.min(this.reconnectAttempts * 2000, 30000) || 1000;
          this.reconnectAttempts++;
          setTimeout(() => this.connect(), delayMs);
        }
      }

      if (connection === 'open') {
        this.connected = true;
        this.reconnectAttempts = 0;
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
      const listener = (qr) => {
        clearTimeout(timer);
        resolve(qr);
      };
      const timer = setTimeout(() => {
        this.qrListeners = this.qrListeners.filter(l => l !== listener);
        reject(new Error('QR timeout'));
      }, timeoutMs);

      this.qrListeners.push(listener);
    });
  }

  async sendMessage(jid, text) {
    if (!this.sock || !this.connected) {
      throw new Error('Not connected to WhatsApp');
    }
    const formattedJid = jid.includes('@') ? jid : `${jid.replace(/[^0-9]/g, '')}@s.whatsapp.net`;
    const result = await this.sock.sendMessage(formattedJid, { text });
    return { id: result.key.id };
  }

  async getMessages(jid, limit = 20) {
    const formattedJid = jid ? (jid.includes('@') ? jid : `${jid.replace(/[^0-9]/g, '')}@s.whatsapp.net`) : null;
    const filtered = this.messages
      .filter(m => !formattedJid || m.jid === formattedJid)
      .slice(-limit);
    return filtered;
  }

  async getContactsList() {
    return Object.values(this.storeContacts);
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
    this._clearAuthData();
  }

  disconnect() {
    if (this.sock) {
      try {
        this.sock.end(new Error('manual disconnect'));
      } catch {}
    }
    this.sock = null;
    this.connected = false;
    this.phone = null;
    this.currentQR = null;
  }
}
