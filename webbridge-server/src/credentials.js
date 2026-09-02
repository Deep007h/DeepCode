const crypto = await import('crypto');

const ENCRYPTION_KEY = crypto.createHash('sha256').update('webbridge-session-key').digest();

export class CredentialVault {
  constructor() {
    this._store = new Map();
  }

  set(platform, email, secret, extra = null) {
    const encrypted = this._encrypt(secret);
    this._store.set(platform.toLowerCase(), {
      email,
      encryptedSecret: encrypted,
      extra,
      createdAt: Date.now()
    });
  }

  get(platform) {
    const entry = this._store.get(platform.toLowerCase());
    if (!entry) return null;
    return {
      email: entry.email,
      password: this._decrypt(entry.encryptedSecret),
      extra: entry.extra
    };
  }

  has(platform) {
    return this._store.has(platform.toLowerCase());
  }

  delete(platform) {
    this._store.delete(platform.toLowerCase());
  }

  clear() {
    this._store.clear();
  }

  get keys() {
    return Array.from(this._store.keys());
  }

  _encrypt(text) {
    const iv = crypto.randomBytes(16);
    const cipher = crypto.createCipheriv('aes-256-cbc', ENCRYPTION_KEY, iv);
    let encrypted = cipher.update(text, 'utf8', 'hex');
    encrypted += cipher.final('hex');
    return iv.toString('hex') + ':' + encrypted;
  }

  _decrypt(encoded) {
    try {
      const parts = encoded.split(':');
      const iv = Buffer.from(parts[0], 'hex');
      const encrypted = parts[1];
      const decipher = crypto.createDecipheriv('aes-256-cbc', ENCRYPTION_KEY, iv);
      let decrypted = decipher.update(encrypted, 'hex', 'utf8');
      decrypted += decipher.final('utf8');
      return decrypted;
    } catch {
      return encoded;
    }
  }
}
