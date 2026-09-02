import { chromium } from 'playwright';
import { randomDelay, simulateMouseMove, logger } from './utils.js';

export class BrowserManager {
  constructor() {
    this.browser = null;
    this.context = null;
    this.pages = [];
    this.maxConcurrent = parseInt(process.env.MAX_CONCURRENT_SESSIONS || '3');
  }

  async initialize() {
    if (this.browser) return;

    const launchOptions = {
      headless: process.env.NODE_ENV === 'production' || !process.env.DEBUG,
      args: [
        '--no-sandbox',
        '--disable-setuid-sandbox',
        '--disable-blink-features=AutomationControlled',
        '--disable-infobars',
        '--window-size=1920,1080'
      ]
    };

    if (process.env.CHROMIUM_PATH) {
      launchOptions.executablePath = process.env.CHROMIUM_PATH;
    }

    this.browser = await chromium.launch(launchOptions);
    logger.info('Browser launched');

    this.context = await this.browser.newContext({
      viewport: { width: 1920, height: 1080 },
      userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/125.0.0.0 Safari/537.36',
      locale: 'en-US',
      permissions: [],
      bypassCSP: true
    });

    await this.context.addInitScript(() => {
      Object.defineProperty(navigator, 'webdriver', { get: () => false });
      Object.defineProperty(navigator, 'plugins', { get: () => [1, 2, 3, 4, 5] });
      Object.defineProperty(navigator, 'languages', { get: () => ['en-US', 'en'] });
      window.chrome = { runtime: {} };
    });
  }

  async newPage(freshSession = true) {
    if (!this.context) await this.initialize();

    if (freshSession) {
      const page = await this.context.newPage();
      this.pages.push(page);
      return page;
    }

    if (this.pages.length === 0) {
      const page = await this.context.newPage();
      this.pages.push(page);
      return page;
    }

    return this.pages[0];
  }

  async shutdown() {
    for (const page of this.pages) {
      try { await page.close(); } catch {}
    }
    this.pages = [];
    if (this.context) {
      try { await this.context.close(); } catch {}
    }
    if (this.browser) {
      try { await this.browser.close(); } catch {}
    }
    logger.info('Browser shut down');
  }
}
