import express from 'express';
import 'dotenv/config';
import { WhatsAppSocket } from './socket.js';

const app = express();
app.use(express.json());

const PORT = parseInt(process.env.PORT || '3001');
const HOST = process.env.HOST || '0.0.0.0';
const AUTH_DIR = process.env.AUTH_DIR || './auth';

const wa = new WhatsAppSocket(AUTH_DIR);

// Start connecting on boot (non-blocking)
wa.connect().catch(err => console.error('Initial connect error:', err));

// ── Health ──
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    connected: wa.connected,
    phone: wa.phone,
    uptime: process.uptime(),
  });
});

// ── Auth / QR ──
app.get('/auth/qr', async (req, res) => {
  try {
    if (wa.connected) {
      return res.json({ connected: true, phone: wa.phone, qr: null });
    }
    const qr = await wa.getQR(parseInt(req.query.timeout) || 120_000);
    res.json({ connected: false, phone: null, qr });
  } catch (err) {
    res.status(408).json({ connected: false, phone: null, qr: null, error: err.message });
  }
});

app.get('/auth/status', (req, res) => {
  res.json({ connected: wa.connected, phone: wa.phone });
});

app.post('/auth/logout', async (req, res) => {
  try {
    await wa.logout();
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// ── Messaging ──
app.post('/message/send', async (req, res) => {
  try {
    const { jid, text } = req.body;
    if (!jid || !text) {
      return res.status(400).json({ success: false, error: 'jid and text are required' });
    }
    const result = await wa.sendMessage(jid, text);
    res.json({ success: true, id: result.id });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

app.get('/messages', async (req, res) => {
  try {
    const { jid, limit } = req.query;
    if (!jid) {
      return res.status(400).json({ messages: [], error: 'jid query param is required' });
    }
    const messages = await wa.getMessages(jid, parseInt(limit) || 20);
    res.json({ messages });
  } catch (err) {
    res.status(500).json({ messages: [], error: err.message });
  }
});

// Incoming messages stream (polled)
app.get('/messages/incoming', (req, res) => {
  const since = parseInt(req.query.since) || 0;
  const jid = req.query.jid || null;
  let filtered = wa.messages;
  if (jid) filtered = filtered.filter(m => m.jid === jid);
  filtered = filtered.filter(m => (m.timestamp * 1000) > since);
  res.json({ messages: filtered.slice(-50) });
});

// ── Contacts ──
app.get('/contacts', async (req, res) => {
  try {
    const contacts = await wa.getContactsList();
    res.json({ contacts });
  } catch (err) {
    res.status(500).json({ contacts: [], error: err.message });
  }
});

// ── Presence ──
app.post('/presence', async (req, res) => {
  try {
    const { type } = req.body;
    await wa.setPresence(type || 'available');
    res.json({ success: true });
  } catch (err) {
    res.status(500).json({ success: false, error: err.message });
  }
});

// ── Start ──
app.listen(PORT, HOST, () => {
  console.log(`WhatsApp Bridge server listening on http://${HOST}:${PORT}`);
});
