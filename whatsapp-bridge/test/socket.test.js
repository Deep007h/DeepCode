import test from 'node:test';
import assert from 'node:assert/strict';
import { WhatsAppSocket } from '../src/socket.js';
import { mkdtempSync, rmSync, existsSync } from 'fs';
import { tmpdir } from 'os';
import { join } from 'path';

test('WhatsAppSocket initializes with clean directory', () => {
  const tmpAuth = mkdtempSync(join(tmpdir(), 'wa-test-'));
  try {
    const wa = new WhatsAppSocket(tmpAuth);
    assert.equal(wa.connected, false);
    assert.equal(wa.phone, null);
    assert.equal(wa.reconnectAttempts, 0);
    assert.deepEqual(wa.messages, []);
    assert.deepEqual(wa.contacts.size, 0);
  } finally {
    rmSync(tmpAuth, { recursive: true, force: true });
  }
});

test('WhatsAppSocket manages in-memory messages and contacts', async () => {
  const tmpAuth = mkdtempSync(join(tmpdir(), 'wa-test-'));
  try {
    const wa = new WhatsAppSocket(tmpAuth);
    wa.messages.push({
      id: 'msg_1',
      jid: '1234567890@s.whatsapp.net',
      from: 'Alice',
      text: 'Hello DeepCode',
      timestamp: 1700000000,
      isGroup: false
    });
    wa.messages.push({
      id: 'msg_2',
      jid: '9876543210@s.whatsapp.net',
      from: 'Bob',
      text: 'General message',
      timestamp: 1700000001,
      isGroup: false
    });

    const aliceMsgs = await wa.getMessages('1234567890', 10);
    assert.equal(aliceMsgs.length, 1);
    assert.equal(aliceMsgs[0].id, 'msg_1');
    assert.equal(aliceMsgs[0].text, 'Hello DeepCode');

    const allMsgs = await wa.getMessages(null, 10);
    assert.equal(allMsgs.length, 2);

    wa.storeContacts['1234567890@s.whatsapp.net'] = {
      jid: '1234567890@s.whatsapp.net',
      name: 'Alice Cooper'
    };
    const contacts = await wa.getContactsList();
    assert.equal(contacts.length, 1);
    assert.equal(contacts[0].name, 'Alice Cooper');
  } finally {
    rmSync(tmpAuth, { recursive: true, force: true });
  }
});

test('WhatsAppSocket formats jid and handles logout cleanup', async () => {
  const tmpAuth = mkdtempSync(join(tmpdir(), 'wa-test-'));
  try {
    const wa = new WhatsAppSocket(tmpAuth);
    assert.rejects(async () => {
      await wa.sendMessage('1234567890', 'test text');
    }, /Not connected/);

    await wa.logout();
    assert.equal(wa.connected, false);
    assert.equal(wa.phone, null);
    assert.deepEqual(wa.messages, []);
  } finally {
    rmSync(tmpAuth, { recursive: true, force: true });
  }
});
